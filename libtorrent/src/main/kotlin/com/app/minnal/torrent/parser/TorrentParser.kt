package com.app.minnal.torrent.parser

import com.app.minnal.torrent.bencode.BencodeDecoder
import com.app.minnal.torrent.bencode.BencodeElement
import com.app.minnal.torrent.bencode.BencodeElement.*
import com.app.minnal.torrent.bencode.BencodeEncoder
import com.app.minnal.torrent.bencode.BencodeException
import com.app.minnal.torrent.model.TorrentFile
import com.app.minnal.torrent.model.TorrentMetadata
import com.app.minnal.torrent.util.HashUtils

/**
 * Parses raw .torrent file data into [TorrentMetadata].
 *
 * Handles both single-file and multi-file torrent formats.
 * Computes the SHA-1 info_hash from the raw bencoded info dictionary.
 */
object TorrentParser {

    /**
     * Parses a raw .torrent file byte array into [TorrentMetadata].
     *
     * @param data the raw bytes of the .torrent file
     * @return parsed torrent metadata
     * @throws BencodeException if the data is not valid bencode or is missing required fields
     */
    fun parse(data: ByteArray): TorrentMetadata {
        val root = BencodeDecoder.decode(data)
        if (root !is BencodeDictionary) {
            throw BencodeException("Torrent file root must be a dictionary")
        }

        val rootDict = root.value

        // Extract announce URLs
        val announceUrls = mutableListOf<String>()

        // Primary announce URL
        val announce = (rootDict["announce"] as? BencodeString)?.asString()
        if (announce != null) {
            announceUrls.add(announce)
        }

        // announce-list (BEP 12): list of lists of tracker URLs
        val announceList = rootDict["announce-list"] as? BencodeList
        if (announceList != null) {
            for (tier in announceList.value) {
                if (tier is BencodeList) {
                    for (trackerElement in tier.value) {
                        if (trackerElement is BencodeString) {
                            val url = trackerElement.asString()
                            if (url !in announceUrls) {
                                announceUrls.add(url)
                            }
                        }
                    }
                }
            }
        }

        // Extract optional top-level fields
        val creationDate = (rootDict["creation date"] as? BencodeInteger)?.value
        val comment = (rootDict["comment"] as? BencodeString)?.asString()
        val createdBy = (rootDict["created by"] as? BencodeString)?.asString()

        // Extract info dictionary
        val infoElement = rootDict["info"]
            ?: throw BencodeException("Missing 'info' dictionary in torrent file")
        if (infoElement !is BencodeDictionary) {
            throw BencodeException("'info' must be a dictionary")
        }
        val infoDict = infoElement.value

        // Compute info_hash: SHA-1 of the raw bencoded info dictionary
        val rawInfoBytes = BencodeEncoder.encode(infoElement)
        val infoHash = HashUtils.sha1(rawInfoBytes)

        // Extract common info fields
        val name = (infoDict["name"] as? BencodeString)?.asString()
            ?: throw BencodeException("Missing 'name' in info dictionary")

        val pieceLength = (infoDict["piece length"] as? BencodeInteger)?.value
            ?: throw BencodeException("Missing 'piece length' in info dictionary")

        val piecesRaw = (infoDict["pieces"] as? BencodeString)?.value
            ?: throw BencodeException("Missing 'pieces' in info dictionary")

        if (piecesRaw.size % 20 != 0) {
            throw BencodeException("Pieces field length (${piecesRaw.size}) is not a multiple of 20")
        }

        // Split the concatenated SHA-1 hashes into individual 20-byte arrays
        val pieces = mutableListOf<ByteArray>()
        for (i in piecesRaw.indices step 20) {
            pieces.add(piecesRaw.copyOfRange(i, i + 20))
        }

        // Check private flag
        val isPrivate = (infoDict["private"] as? BencodeInteger)?.value == 1L

        // Parse files - determine single-file vs multi-file
        val files: List<TorrentFile>
        val totalSize: Long

        val filesElement = infoDict["files"]
        if (filesElement != null) {
            // Multi-file torrent
            if (filesElement !is BencodeList) {
                throw BencodeException("'files' must be a list")
            }

            val fileList = mutableListOf<TorrentFile>()
            var offset = 0L

            for (fileElement in filesElement.value) {
                if (fileElement !is BencodeDictionary) {
                    throw BencodeException("Each file entry must be a dictionary")
                }
                val fileDict = fileElement.value

                val fileLength = (fileDict["length"] as? BencodeInteger)?.value
                    ?: throw BencodeException("Missing 'length' in file entry")

                val pathList = fileDict["path"] as? BencodeList
                    ?: throw BencodeException("Missing 'path' in file entry")

                val pathParts = pathList.value.map { part ->
                    if (part !is BencodeString) {
                        throw BencodeException("File path components must be strings")
                    }
                    part.asString()
                }

                val path = pathParts.joinToString("/")
                fileList.add(TorrentFile(path = path, length = fileLength, offset = offset))
                offset += fileLength
            }

            files = fileList
            totalSize = offset
        } else {
            // Single-file torrent
            val length = (infoDict["length"] as? BencodeInteger)?.value
                ?: throw BencodeException("Missing 'length' in single-file torrent info dictionary")

            files = listOf(TorrentFile(path = name, length = length, offset = 0))
            totalSize = length
        }

        return TorrentMetadata(
            infoHash = infoHash,
            name = name,
            pieceLength = pieceLength,
            pieces = pieces,
            totalSize = totalSize,
            files = files,
            announceUrls = announceUrls,
            creationDate = creationDate,
            comment = comment,
            createdBy = createdBy,
            isPrivate = isPrivate
        )
    }
}
