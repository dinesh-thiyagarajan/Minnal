package com.app.minnal.torrent.peer

import com.app.minnal.torrent.model.Peer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Manages a TCP connection to a single BitTorrent peer.
 *
 * Handles the handshake exchange, message framing (4-byte length prefix),
 * and tracks connection state (choking/interested status and peer bitfield).
 *
 * @param peer the remote peer to connect to
 */
class PeerConnection(val peer: Peer) {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_MESSAGE_LENGTH = 2 * 1024 * 1024 // 2 MB max message size
    }

    private var socket: Socket? = null
    private var inputStream: DataInputStream? = null
    private var outputStream: DataOutputStream? = null

    /** Whether we are choking the remote peer (default: true). */
    @Volatile
    var amChoking: Boolean = true
        private set

    /** Whether we are interested in the remote peer's pieces (default: false). */
    @Volatile
    var amInterested: Boolean = false
        private set

    /** Whether the remote peer is choking us (default: true). */
    @Volatile
    var peerChoking: Boolean = true
        private set

    /** Whether the remote peer is interested in our pieces (default: false). */
    @Volatile
    var peerInterested: Boolean = false
        private set

    /** The remote peer's bitfield (which pieces they have). Null until a bitfield message is received. */
    @Volatile
    var peerBitfield: ByteArray? = null
        private set

    /** The remote peer's ID received during handshake. */
    @Volatile
    var remotePeerId: ByteArray? = null
        private set

    /** Whether this connection is currently active. */
    val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    /** Timestamp of the last message received from this peer, for timeout tracking. */
    @Volatile
    var lastMessageTime: Long = System.currentTimeMillis()
        private set

    /** Total bytes downloaded from this peer. */
    @Volatile
    var downloadedBytes: Long = 0L

    /** Total bytes uploaded to this peer. */
    @Volatile
    var uploadedBytes: Long = 0L

    /**
     * Establishes a TCP connection to the peer.
     *
     * @throws IOException if the connection cannot be established
     */
    suspend fun connect(peer: Peer): Unit = withContext(Dispatchers.IO) {
        val sock = Socket()
        sock.soTimeout = READ_TIMEOUT_MS
        sock.tcpNoDelay = true
        sock.connect(InetSocketAddress(peer.ip, peer.port), CONNECT_TIMEOUT_MS)
        socket = sock
        inputStream = DataInputStream(sock.getInputStream().buffered())
        outputStream = DataOutputStream(sock.getOutputStream().buffered())
    }

    /**
     * Establishes a connection using an already-connected socket (for incoming connections).
     *
     * @param connectedSocket the pre-connected socket
     */
    fun connectWithSocket(connectedSocket: Socket) {
        connectedSocket.soTimeout = READ_TIMEOUT_MS
        connectedSocket.tcpNoDelay = true
        socket = connectedSocket
        inputStream = DataInputStream(connectedSocket.getInputStream().buffered())
        outputStream = DataOutputStream(connectedSocket.getOutputStream().buffered())
    }

    /**
     * Performs the BitTorrent handshake with the remote peer.
     *
     * Sends our handshake and reads the peer's handshake. Verifies that the
     * info hash matches. Returns false if the handshake fails or info hashes don't match.
     *
     * @param infoHash the 20-byte info hash of the torrent
     * @param peerId our 20-byte peer ID
     * @return true if handshake was successful and info hashes match
     */
    suspend fun performHandshake(infoHash: ByteArray, peerId: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val handshake = PeerHandshake(infoHash = infoHash, peerId = peerId)
            val handshakeBytes = PeerHandshake.encode(handshake)

            // Send our handshake
            val out = outputStream ?: return@withContext false
            out.write(handshakeBytes)
            out.flush()

            // Read the peer's handshake
            val input = inputStream ?: return@withContext false

            // Read pstrlen
            val pstrlen = input.read()
            if (pstrlen < 0) return@withContext false

            // Read the rest of the handshake
            val remaining = pstrlen + 8 + 20 + 20
            val handshakeData = ByteArray(1 + remaining)
            handshakeData[0] = pstrlen.toByte()
            readExactly(input, handshakeData, 1, remaining)

            val peerHandshake = PeerHandshake.decode(handshakeData)

            // Verify info hash matches
            if (!peerHandshake.infoHash.contentEquals(infoHash)) {
                return@withContext false
            }

            remotePeerId = peerHandshake.peerId
            lastMessageTime = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sends a [PeerMessage] to the remote peer.
     *
     * @param message the message to send
     * @throws IOException if writing fails
     */
    suspend fun sendMessage(message: PeerMessage): Unit = withContext(Dispatchers.IO) {
        val out = outputStream ?: throw IOException("Not connected")
        val encoded = PeerMessage.encode(message)
        out.write(encoded)
        out.flush()

        // Track uploaded bytes for Piece messages
        if (message is PeerMessage.Piece) {
            uploadedBytes += message.data.size
        }

        // Update our local state based on what we sent
        when (message) {
            is PeerMessage.Choke -> amChoking = true
            is PeerMessage.Unchoke -> amChoking = false
            is PeerMessage.Interested -> amInterested = true
            is PeerMessage.NotInterested -> amInterested = false
            else -> { /* No state change needed */ }
        }
    }

    /**
     * Reads and decodes the next [PeerMessage] from the remote peer.
     *
     * Handles the 4-byte length prefix framing. Updates internal state
     * based on the received message type (choking, interested, bitfield, have).
     *
     * @return the decoded message
     * @throws IOException if reading fails
     */
    suspend fun receiveMessage(): PeerMessage = withContext(Dispatchers.IO) {
        val input = inputStream ?: throw IOException("Not connected")

        // Read 4-byte message length
        val lengthBytes = ByteArray(4)
        readExactly(input, lengthBytes, 0, 4)
        val length = ByteBuffer.wrap(lengthBytes).int

        if (length < 0 || length > MAX_MESSAGE_LENGTH) {
            throw IOException("Invalid message length: $length")
        }

        // Keep-alive message (length = 0)
        if (length == 0) {
            lastMessageTime = System.currentTimeMillis()
            return@withContext PeerMessage.KeepAlive
        }

        // Read message payload
        val payload = ByteArray(length)
        readExactly(input, payload, 0, length)

        lastMessageTime = System.currentTimeMillis()

        val message = PeerMessage.decode(payload)

        // Update state based on received message
        when (message) {
            is PeerMessage.Choke -> peerChoking = true
            is PeerMessage.Unchoke -> peerChoking = false
            is PeerMessage.Interested -> peerInterested = true
            is PeerMessage.NotInterested -> peerInterested = false
            is PeerMessage.Bitfield -> peerBitfield = message.bitfield.copyOf()
            is PeerMessage.Have -> updateBitfieldForHave(message.pieceIndex)
            is PeerMessage.Piece -> downloadedBytes += message.data.size
            else -> { /* No state change needed */ }
        }

        message
    }

    /**
     * Checks if the remote peer has a specific piece.
     *
     * @param pieceIndex the zero-based piece index
     * @return true if the peer has the piece, false if not or if bitfield is unknown
     */
    fun hasPiece(pieceIndex: Int): Boolean {
        val bf = peerBitfield ?: return false
        val byteIndex = pieceIndex / 8
        val bitIndex = 7 - (pieceIndex % 8)
        if (byteIndex >= bf.size) return false
        return (bf[byteIndex].toInt() and (1 shl bitIndex)) != 0
    }

    /**
     * Initializes the peer bitfield with the given total number of pieces.
     * Called when connecting to a peer that doesn't send a bitfield message.
     *
     * @param totalPieces the total number of pieces in the torrent
     */
    fun initBitfield(totalPieces: Int) {
        if (peerBitfield == null) {
            peerBitfield = ByteArray((totalPieces + 7) / 8)
        }
    }

    /**
     * Updates the peer's bitfield to mark a piece as available (from a Have message).
     */
    private fun updateBitfieldForHave(pieceIndex: Int) {
        val bf = peerBitfield ?: return
        val byteIndex = pieceIndex / 8
        val bitIndex = 7 - (pieceIndex % 8)
        if (byteIndex < bf.size) {
            bf[byteIndex] = (bf[byteIndex].toInt() or (1 shl bitIndex)).toByte()
        }
    }

    /**
     * Closes the connection to the peer and releases resources.
     */
    fun close() {
        try {
            inputStream?.close()
        } catch (_: Exception) { }
        try {
            outputStream?.close()
        } catch (_: Exception) { }
        try {
            socket?.close()
        } catch (_: Exception) { }
        socket = null
        inputStream = null
        outputStream = null
    }

    /**
     * Reads exactly [length] bytes from the input stream into [buffer] starting at [offset].
     *
     * @throws IOException if the stream ends before all bytes are read
     */
    private fun readExactly(input: DataInputStream, buffer: ByteArray, offset: Int, length: Int) {
        var totalRead = 0
        while (totalRead < length) {
            val bytesRead = input.read(buffer, offset + totalRead, length - totalRead)
            if (bytesRead < 0) {
                throw IOException("Unexpected end of stream after reading $totalRead of $length bytes")
            }
            totalRead += bytesRead
        }
    }
}
