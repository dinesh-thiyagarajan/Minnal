package com.app.minnal.torrent.tracker

import com.app.minnal.torrent.bencode.BencodeDecoder
import com.app.minnal.torrent.bencode.BencodeElement.*
import com.app.minnal.torrent.model.Peer
import com.app.minnal.torrent.util.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP tracker client implementing the BitTorrent tracker protocol.
 *
 * Sends HTTP GET announce requests to HTTP/HTTPS trackers and parses
 * the bencoded response. Supports both compact and dictionary peer formats.
 */
object HttpTrackerClient {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    /**
     * Sends an announce request to an HTTP tracker.
     *
     * @param announceUrl the tracker's announce URL
     * @param infoHash 20-byte info hash of the torrent
     * @param peerId 20-byte peer ID
     * @param port the port this client is listening on
     * @param uploaded total bytes uploaded
     * @param downloaded total bytes downloaded
     * @param left bytes remaining to download
     * @param event optional event string: "started", "stopped", "completed", or empty
     * @return parsed [TrackerResponse]
     * @throws Exception on network or parsing errors
     */
    suspend fun announce(
        announceUrl: String,
        infoHash: ByteArray,
        peerId: ByteArray,
        port: Int,
        uploaded: Long,
        downloaded: Long,
        left: Long,
        event: String = ""
    ): TrackerResponse = withContext(Dispatchers.IO) {
        val separator = if (announceUrl.contains('?')) '&' else '?'
        val urlBuilder = StringBuilder(announceUrl)
        urlBuilder.append(separator)
        urlBuilder.append("info_hash=").append(HashUtils.urlEncode(infoHash))
        urlBuilder.append("&peer_id=").append(HashUtils.urlEncode(peerId))
        urlBuilder.append("&port=").append(port)
        urlBuilder.append("&uploaded=").append(uploaded)
        urlBuilder.append("&downloaded=").append(downloaded)
        urlBuilder.append("&left=").append(left)
        urlBuilder.append("&compact=1")
        if (event.isNotEmpty()) {
            urlBuilder.append("&event=").append(event)
        }

        val url = URL(urlBuilder.toString())
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "Minnal/1.0")

        try {
            connection.connect()

            val responseCode = connection.responseCode
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseBytes = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                responseBytes.write(buffer, 0, bytesRead)
            }
            inputStream.close()

            parseResponse(responseBytes.toByteArray())
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parses a bencoded tracker response into a [TrackerResponse].
     */
    private fun parseResponse(data: ByteArray): TrackerResponse {
        val element = BencodeDecoder.decode(data)
        if (element !is BencodeDictionary) {
            throw IllegalStateException("Tracker response must be a dictionary")
        }

        val dict = element.value

        // Check for failure
        val failureReason = (dict["failure reason"] as? BencodeString)?.asString()
        if (failureReason != null) {
            return TrackerResponse(
                interval = 0,
                peers = emptyList(),
                failureReason = failureReason
            )
        }

        val interval = (dict["interval"] as? BencodeInteger)?.value?.toInt() ?: 1800
        val complete = (dict["complete"] as? BencodeInteger)?.value?.toInt() ?: 0
        val incomplete = (dict["incomplete"] as? BencodeInteger)?.value?.toInt() ?: 0

        val peers = parsePeers(dict["peers"])

        return TrackerResponse(
            interval = interval,
            peers = peers,
            complete = complete,
            incomplete = incomplete
        )
    }

    /**
     * Parses the peers field from a tracker response.
     * Handles both compact format (binary string) and dictionary format (list of dicts).
     */
    private fun parsePeers(peersElement: com.app.minnal.torrent.bencode.BencodeElement?): List<Peer> {
        if (peersElement == null) return emptyList()

        return when (peersElement) {
            is BencodeString -> parseCompactPeers(peersElement.value)
            is BencodeList -> parseDictionaryPeers(peersElement)
            else -> emptyList()
        }
    }

    /**
     * Parses compact peer format: 6 bytes per peer (4 bytes IP + 2 bytes port, big-endian).
     */
    private fun parseCompactPeers(data: ByteArray): List<Peer> {
        if (data.size % 6 != 0) return emptyList()

        val peers = mutableListOf<Peer>()
        for (i in data.indices step 6) {
            val ip = "${data[i].toInt() and 0xFF}." +
                    "${data[i + 1].toInt() and 0xFF}." +
                    "${data[i + 2].toInt() and 0xFF}." +
                    "${data[i + 3].toInt() and 0xFF}"
            val port = ((data[i + 4].toInt() and 0xFF) shl 8) or
                    (data[i + 5].toInt() and 0xFF)

            if (port > 0) {
                peers.add(Peer(ip = ip, port = port))
            }
        }
        return peers
    }

    /**
     * Parses dictionary peer format: list of dictionaries with "ip", "port", and optional "peer id" keys.
     */
    private fun parseDictionaryPeers(list: BencodeList): List<Peer> {
        val peers = mutableListOf<Peer>()
        for (item in list.value) {
            if (item !is BencodeDictionary) continue
            val dict = item.value
            val ip = (dict["ip"] as? BencodeString)?.asString() ?: continue
            val port = (dict["port"] as? BencodeInteger)?.value?.toInt() ?: continue
            val peerId = (dict["peer id"] as? BencodeString)?.value
            peers.add(Peer(ip = ip, port = port, peerId = peerId))
        }
        return peers
    }
}
