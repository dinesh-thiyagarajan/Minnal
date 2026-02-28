package com.app.minnal.torrent.tracker

import com.app.minnal.torrent.model.Peer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * UDP tracker client implementing BEP 15 (UDP Tracker Protocol).
 *
 * The UDP tracker protocol is a more efficient alternative to HTTP trackers,
 * using a two-step process: connect (to get a connection ID) then announce.
 * Implements timeout retries with exponential backoff (15 * 2^n seconds).
 */
object UdpTrackerClient {

    /** Magic connection ID used for the initial connect request. */
    private const val PROTOCOL_ID = 0x41727101980L

    /** Action codes. */
    private const val ACTION_CONNECT = 0
    private const val ACTION_ANNOUNCE = 1

    /** Max retries before giving up. */
    private const val MAX_RETRIES = 4

    /** Base timeout in milliseconds (15 seconds). */
    private const val BASE_TIMEOUT_MS = 15_000

    /**
     * Sends an announce request to a UDP tracker.
     *
     * Performs the connect handshake first to obtain a connection ID,
     * then sends the announce request with the connection ID.
     *
     * @param announceUrl the tracker's UDP announce URL (e.g., "udp://tracker.example.com:6969/announce")
     * @param infoHash 20-byte info hash of the torrent
     * @param peerId 20-byte peer ID
     * @param port the port this client is listening on
     * @param uploaded total bytes uploaded
     * @param downloaded total bytes downloaded
     * @param left bytes remaining to download
     * @param event event code: 0=none, 1=completed, 2=started, 3=stopped
     * @return parsed [TrackerResponse]
     * @throws Exception on network or protocol errors
     */
    suspend fun announce(
        announceUrl: String,
        infoHash: ByteArray,
        peerId: ByteArray,
        port: Int,
        uploaded: Long,
        downloaded: Long,
        left: Long,
        event: Int = 2 // started
    ): TrackerResponse = withContext(Dispatchers.IO) {
        val uri = URI(announceUrl)
        val host = uri.host ?: throw IllegalArgumentException("Invalid tracker URL: no host in $announceUrl")
        val trackerPort = if (uri.port > 0) uri.port else 6969
        val address = InetAddress.getByName(host)

        val socket = DatagramSocket()
        try {
            // Step 1: Connect
            val connectionId = performConnect(socket, address, trackerPort)

            // Step 2: Announce
            performAnnounce(
                socket, address, trackerPort, connectionId,
                infoHash, peerId, port, uploaded, downloaded, left, event
            )
        } finally {
            socket.close()
        }
    }

    /**
     * Performs the UDP tracker connect handshake.
     *
     * Send: connection_id (8 bytes, protocol magic) + action (4 bytes, 0) + transaction_id (4 bytes)
     * Receive: action (4 bytes, 0) + transaction_id (4 bytes) + connection_id (8 bytes)
     *
     * @return the connection ID to use for subsequent requests
     */
    private fun performConnect(
        socket: DatagramSocket,
        address: InetAddress,
        port: Int
    ): Long {
        val transactionId = Random.nextInt()

        val sendBuffer = ByteBuffer.allocate(16)
        sendBuffer.putLong(PROTOCOL_ID)       // connection_id (magic)
        sendBuffer.putInt(ACTION_CONNECT)      // action = connect
        sendBuffer.putInt(transactionId)       // transaction_id
        val sendData = sendBuffer.array()

        val recvBuffer = ByteArray(16)
        val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)

        for (retry in 0..MAX_RETRIES) {
            val timeout = BASE_TIMEOUT_MS * (1 shl retry)
            socket.soTimeout = timeout

            val sendPacket = DatagramPacket(sendData, sendData.size, address, port)
            socket.send(sendPacket)

            try {
                socket.receive(recvPacket)

                if (recvPacket.length < 16) continue

                val response = ByteBuffer.wrap(recvBuffer)
                val respAction = response.int
                val respTransactionId = response.int
                val connectionId = response.long

                if (respAction != ACTION_CONNECT || respTransactionId != transactionId) continue

                return connectionId
            } catch (e: java.net.SocketTimeoutException) {
                if (retry == MAX_RETRIES) {
                    throw e
                }
                // Retry with increased timeout
            }
        }

        throw java.net.SocketTimeoutException("UDP tracker connect timed out after $MAX_RETRIES retries")
    }

    /**
     * Performs the UDP tracker announce request.
     *
     * Send: connection_id + action(1) + transaction_id + info_hash + peer_id +
     *       downloaded + left + uploaded + event + IP(0) + key + num_want(-1) + port
     * Receive: action(1) + transaction_id + interval + leechers + seeders + peers(6 bytes each)
     */
    private fun performAnnounce(
        socket: DatagramSocket,
        address: InetAddress,
        port: Int,
        connectionId: Long,
        infoHash: ByteArray,
        peerId: ByteArray,
        listenPort: Int,
        uploaded: Long,
        downloaded: Long,
        left: Long,
        event: Int
    ): TrackerResponse {
        val transactionId = Random.nextInt()
        val key = Random.nextInt()

        val sendBuffer = ByteBuffer.allocate(98)
        sendBuffer.putLong(connectionId)       // connection_id
        sendBuffer.putInt(ACTION_ANNOUNCE)     // action = announce
        sendBuffer.putInt(transactionId)       // transaction_id
        sendBuffer.put(infoHash)               // info_hash (20 bytes)
        sendBuffer.put(peerId)                 // peer_id (20 bytes)
        sendBuffer.putLong(downloaded)         // downloaded
        sendBuffer.putLong(left)               // left
        sendBuffer.putLong(uploaded)           // uploaded
        sendBuffer.putInt(event)               // event
        sendBuffer.putInt(0)                   // IP address (0 = default)
        sendBuffer.putInt(key)                 // key (random)
        sendBuffer.putInt(-1)                  // num_want (-1 = default)
        sendBuffer.putShort(listenPort.toShort()) // port

        val sendData = sendBuffer.array()

        // Response can be large to accommodate many peers
        val recvBuffer = ByteArray(65535)
        val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)

        for (retry in 0..MAX_RETRIES) {
            val timeout = BASE_TIMEOUT_MS * (1 shl retry)
            socket.soTimeout = timeout

            val sendPacket = DatagramPacket(sendData, sendData.size, address, port)
            socket.send(sendPacket)

            try {
                socket.receive(recvPacket)

                if (recvPacket.length < 20) continue

                val response = ByteBuffer.wrap(recvBuffer, 0, recvPacket.length)
                val respAction = response.int
                val respTransactionId = response.int

                if (respAction != ACTION_ANNOUNCE || respTransactionId != transactionId) continue

                val interval = response.int
                val leechers = response.int
                val seeders = response.int

                // Parse compact peer list (6 bytes per peer)
                val peers = mutableListOf<Peer>()
                val remaining = recvPacket.length - 20
                val peerCount = remaining / 6

                for (i in 0 until peerCount) {
                    val b1 = response.get().toInt() and 0xFF
                    val b2 = response.get().toInt() and 0xFF
                    val b3 = response.get().toInt() and 0xFF
                    val b4 = response.get().toInt() and 0xFF
                    val peerPort = response.short.toInt() and 0xFFFF

                    val ip = "$b1.$b2.$b3.$b4"
                    if (peerPort > 0) {
                        peers.add(Peer(ip = ip, port = peerPort))
                    }
                }

                return TrackerResponse(
                    interval = interval,
                    peers = peers,
                    complete = seeders,
                    incomplete = leechers
                )
            } catch (e: java.net.SocketTimeoutException) {
                if (retry == MAX_RETRIES) {
                    throw e
                }
            }
        }

        throw java.net.SocketTimeoutException("UDP tracker announce timed out after $MAX_RETRIES retries")
    }

    /**
     * Converts an event string to the UDP protocol event code.
     *
     * @param event the event string ("started", "stopped", "completed", or empty)
     * @return the corresponding event code (0=none, 1=completed, 2=started, 3=stopped)
     */
    fun eventCode(event: String): Int = when (event) {
        "completed" -> 1
        "started" -> 2
        "stopped" -> 3
        else -> 0
    }
}
