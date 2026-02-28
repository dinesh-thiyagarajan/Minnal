package com.app.minnal.torrent.peer

import java.nio.ByteBuffer

/**
 * Represents all peer wire protocol messages as defined in BEP 3.
 *
 * Each message (except KeepAlive) has a 4-byte length prefix followed by
 * a 1-byte message ID and an optional payload.
 */
sealed class PeerMessage {

    /** Keep-alive message (zero-length, no message ID). Sent periodically to maintain connections. */
    object KeepAlive : PeerMessage()

    /** Choke message (ID 0). Tells the peer that they are choked and should not request pieces. */
    object Choke : PeerMessage()

    /** Unchoke message (ID 1). Tells the peer that they are unchoked and may request pieces. */
    object Unchoke : PeerMessage()

    /** Interested message (ID 2). Tells the peer that we are interested in pieces they have. */
    object Interested : PeerMessage()

    /** Not interested message (ID 3). Tells the peer that we are not interested in their pieces. */
    object NotInterested : PeerMessage()

    /**
     * Have message (ID 4). Informs the peer that we have successfully downloaded a piece.
     *
     * @property pieceIndex the zero-based index of the completed piece
     */
    data class Have(val pieceIndex: Int) : PeerMessage()

    /**
     * Bitfield message (ID 5). Sent immediately after handshake to indicate which pieces we have.
     *
     * @property bitfield byte array where each bit represents whether a piece is available
     */
    data class Bitfield(val bitfield: ByteArray) : PeerMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Bitfield) return false
            return bitfield.contentEquals(other.bitfield)
        }

        override fun hashCode(): Int = bitfield.contentHashCode()
    }

    /**
     * Request message (ID 6). Requests a block of data from a piece.
     *
     * @property index piece index
     * @property begin byte offset within the piece
     * @property length number of bytes requested (typically 16384)
     */
    data class Request(val index: Int, val begin: Int, val length: Int) : PeerMessage()

    /**
     * Piece message (ID 7). Contains a block of piece data.
     *
     * @property index piece index
     * @property begin byte offset within the piece
     * @property data the raw block bytes
     */
    data class Piece(val index: Int, val begin: Int, val data: ByteArray) : PeerMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Piece) return false
            return index == other.index && begin == other.begin && data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = index
            result = 31 * result + begin
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * Cancel message (ID 8). Cancels a previously sent request.
     *
     * @property index piece index
     * @property begin byte offset within the piece
     * @property length number of bytes to cancel
     */
    data class Cancel(val index: Int, val begin: Int, val length: Int) : PeerMessage()

    companion object {
        const val ID_CHOKE = 0
        const val ID_UNCHOKE = 1
        const val ID_INTERESTED = 2
        const val ID_NOT_INTERESTED = 3
        const val ID_HAVE = 4
        const val ID_BITFIELD = 5
        const val ID_REQUEST = 6
        const val ID_PIECE = 7
        const val ID_CANCEL = 8

        /**
         * Encodes a [PeerMessage] to its wire format (4-byte length prefix + message ID + payload).
         *
         * @param message the message to encode
         * @return the encoded byte array including the length prefix
         */
        fun encode(message: PeerMessage): ByteArray {
            return when (message) {
                is KeepAlive -> {
                    // Keep-alive: 4 bytes of zero
                    ByteBuffer.allocate(4).putInt(0).array()
                }
                is Choke -> {
                    ByteBuffer.allocate(5).putInt(1).put(ID_CHOKE.toByte()).array()
                }
                is Unchoke -> {
                    ByteBuffer.allocate(5).putInt(1).put(ID_UNCHOKE.toByte()).array()
                }
                is Interested -> {
                    ByteBuffer.allocate(5).putInt(1).put(ID_INTERESTED.toByte()).array()
                }
                is NotInterested -> {
                    ByteBuffer.allocate(5).putInt(1).put(ID_NOT_INTERESTED.toByte()).array()
                }
                is Have -> {
                    ByteBuffer.allocate(9)
                        .putInt(5) // length: 1 (ID) + 4 (piece index)
                        .put(ID_HAVE.toByte())
                        .putInt(message.pieceIndex)
                        .array()
                }
                is Bitfield -> {
                    val length = 1 + message.bitfield.size
                    ByteBuffer.allocate(4 + length)
                        .putInt(length)
                        .put(ID_BITFIELD.toByte())
                        .put(message.bitfield)
                        .array()
                }
                is Request -> {
                    ByteBuffer.allocate(17)
                        .putInt(13) // length: 1 + 4 + 4 + 4
                        .put(ID_REQUEST.toByte())
                        .putInt(message.index)
                        .putInt(message.begin)
                        .putInt(message.length)
                        .array()
                }
                is Piece -> {
                    val length = 1 + 4 + 4 + message.data.size
                    ByteBuffer.allocate(4 + length)
                        .putInt(length)
                        .put(ID_PIECE.toByte())
                        .putInt(message.index)
                        .putInt(message.begin)
                        .put(message.data)
                        .array()
                }
                is Cancel -> {
                    ByteBuffer.allocate(17)
                        .putInt(13) // length: 1 + 4 + 4 + 4
                        .put(ID_CANCEL.toByte())
                        .putInt(message.index)
                        .putInt(message.begin)
                        .putInt(message.length)
                        .array()
                }
            }
        }

        /**
         * Decodes a [PeerMessage] from its wire format payload (after the 4-byte length prefix).
         *
         * The input [data] should be the message content AFTER the length prefix has been read.
         * For a keep-alive message, [data] should be empty.
         *
         * @param data the raw message bytes (without the 4-byte length prefix)
         * @return the decoded [PeerMessage]
         * @throws IllegalArgumentException for unknown message IDs or malformed payloads
         */
        fun decode(data: ByteArray): PeerMessage {
            if (data.isEmpty()) return KeepAlive

            val id = data[0].toInt() and 0xFF
            val payload = if (data.size > 1) data.copyOfRange(1, data.size) else ByteArray(0)

            return when (id) {
                ID_CHOKE -> Choke
                ID_UNCHOKE -> Unchoke
                ID_INTERESTED -> Interested
                ID_NOT_INTERESTED -> NotInterested
                ID_HAVE -> {
                    require(payload.size >= 4) { "Have message payload too short: ${payload.size}" }
                    val buf = ByteBuffer.wrap(payload)
                    Have(buf.int)
                }
                ID_BITFIELD -> {
                    Bitfield(payload)
                }
                ID_REQUEST -> {
                    require(payload.size >= 12) { "Request message payload too short: ${payload.size}" }
                    val buf = ByteBuffer.wrap(payload)
                    Request(buf.int, buf.int, buf.int)
                }
                ID_PIECE -> {
                    require(payload.size >= 8) { "Piece message payload too short: ${payload.size}" }
                    val buf = ByteBuffer.wrap(payload)
                    val index = buf.int
                    val begin = buf.int
                    val blockData = payload.copyOfRange(8, payload.size)
                    Piece(index, begin, blockData)
                }
                ID_CANCEL -> {
                    require(payload.size >= 12) { "Cancel message payload too short: ${payload.size}" }
                    val buf = ByteBuffer.wrap(payload)
                    Cancel(buf.int, buf.int, buf.int)
                }
                else -> throw IllegalArgumentException("Unknown peer message ID: $id")
            }
        }
    }
}

/**
 * Represents the BitTorrent handshake message exchanged at the start of a peer connection.
 *
 * Format: pstrlen (1 byte) + pstr (19 bytes) + reserved (8 bytes) + info_hash (20 bytes) + peer_id (20 bytes) = 68 bytes
 *
 * @property protocolName the protocol identifier string (always "BitTorrent protocol")
 * @property reserved 8-byte reserved field for protocol extensions
 * @property infoHash 20-byte SHA-1 info hash identifying the torrent
 * @property peerId 20-byte identifier of the peer
 */
data class PeerHandshake(
    val protocolName: String = PROTOCOL_NAME,
    val reserved: ByteArray = ByteArray(8),
    val infoHash: ByteArray,
    val peerId: ByteArray
) {
    companion object {
        const val PROTOCOL_NAME = "BitTorrent protocol"
        const val HANDSHAKE_LENGTH = 68

        /**
         * Encodes a handshake into its wire format.
         *
         * @param handshake the handshake to encode
         * @return 68-byte encoded handshake
         */
        fun encode(handshake: PeerHandshake): ByteArray {
            val pstr = handshake.protocolName.toByteArray(Charsets.US_ASCII)
            val buffer = ByteBuffer.allocate(1 + pstr.size + 8 + 20 + 20)
            buffer.put(pstr.size.toByte())
            buffer.put(pstr)
            buffer.put(handshake.reserved)
            buffer.put(handshake.infoHash)
            buffer.put(handshake.peerId)
            return buffer.array()
        }

        /**
         * Decodes a handshake from its wire format.
         *
         * @param data raw handshake bytes (must be at least 68 bytes)
         * @return the decoded [PeerHandshake]
         * @throws IllegalArgumentException if the data is too short or malformed
         */
        fun decode(data: ByteArray): PeerHandshake {
            require(data.size >= 49) { "Handshake data too short: ${data.size} bytes" }

            val pstrlen = data[0].toInt() and 0xFF
            require(data.size >= 1 + pstrlen + 8 + 20 + 20) {
                "Handshake data too short for pstrlen=$pstrlen: ${data.size} bytes"
            }

            var offset = 1
            val protocolName = String(data, offset, pstrlen, Charsets.US_ASCII)
            offset += pstrlen

            val reserved = data.copyOfRange(offset, offset + 8)
            offset += 8

            val infoHash = data.copyOfRange(offset, offset + 20)
            offset += 20

            val peerId = data.copyOfRange(offset, offset + 20)

            return PeerHandshake(
                protocolName = protocolName,
                reserved = reserved,
                infoHash = infoHash,
                peerId = peerId
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeerHandshake) return false
        return protocolName == other.protocolName &&
                reserved.contentEquals(other.reserved) &&
                infoHash.contentEquals(other.infoHash) &&
                peerId.contentEquals(other.peerId)
    }

    override fun hashCode(): Int {
        var result = protocolName.hashCode()
        result = 31 * result + reserved.contentHashCode()
        result = 31 * result + infoHash.contentHashCode()
        result = 31 * result + peerId.contentHashCode()
        return result
    }
}
