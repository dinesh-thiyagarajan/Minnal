package com.app.minnal.torrent.peer

import com.app.minnal.torrent.model.Peer
import com.app.minnal.torrent.model.TorrentMetadata
import com.app.minnal.torrent.piece.PieceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Events emitted by the [PeerManager] for higher-level components.
 */
sealed class PeerEvent {
    /** A block of piece data was received from a peer. */
    data class PieceReceived(val pieceIndex: Int, val begin: Int, val data: ByteArray) : PeerEvent()

    /** A new peer connection was established. */
    data class PeerConnected(val peer: Peer) : PeerEvent()

    /** A peer connection was lost. */
    data class PeerDisconnected(val peer: Peer) : PeerEvent()

    /** A peer reported having a new piece. */
    data class PeerHavePiece(val peer: Peer, val pieceIndex: Int) : PeerEvent()
}

/**
 * Manages all peer connections for a single torrent.
 *
 * Maintains a pool of active connections (up to [maxConnections]), handles
 * message routing, implements the choking algorithm, and emits events for
 * piece data and connection state changes.
 *
 * @param metadata the torrent metadata
 * @param pieceManager the piece manager for tracking download progress
 * @param infoHash 20-byte info hash
 * @param peerId 20-byte peer ID
 * @param maxConnections maximum number of simultaneous peer connections
 */
class PeerManager(
    private val metadata: TorrentMetadata,
    private val pieceManager: PieceManager,
    private val infoHash: ByteArray,
    private val peerId: ByteArray,
    private val maxConnections: Int = 50
) {
    private val _events = MutableSharedFlow<PeerEvent>(replay = 0, extraBufferCapacity = 256)

    /** Flow of peer events for higher-level components. */
    val events: SharedFlow<PeerEvent> = _events.asSharedFlow()

    private val connections = mutableMapOf<Peer, PeerConnection>()
    private val connectionJobs = mutableMapOf<Peer, Job>()
    private val mutex = Mutex()
    private var scope: CoroutineScope? = null
    private var chokeJob: Job? = null
    private val pendingPeers = mutableSetOf<Peer>()
    private val failedPeers = mutableSetOf<Peer>()

    /** Block size for piece requests (16 KB). */
    private val blockSize = PieceManager.BLOCK_SIZE

    /** Number of currently active connections. */
    val connectedPeerCount: Int
        get() = connections.size

    /** Total number of known peers (connected + pending). */
    val totalPeerCount: Int
        get() = connections.size + pendingPeers.size

    /**
     * Starts the peer manager within the given coroutine scope.
     *
     * @param parentScope the parent scope for structured concurrency
     */
    fun start(parentScope: CoroutineScope) {
        val peerScope = CoroutineScope(
            parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
        )
        scope = peerScope

        // Start choking algorithm: unchoke top 4 uploaders + 1 optimistic unchoke every 30s
        chokeJob = peerScope.launch {
            while (isActive) {
                delay(30_000)
                runChokingAlgorithm()
            }
        }
    }

    /**
     * Stops all peer connections and cancels the peer manager.
     */
    suspend fun stop() {
        chokeJob?.cancel()
        mutex.withLock {
            for ((_, conn) in connections) {
                conn.close()
            }
            connections.clear()
            connectionJobs.values.forEach { it.cancel() }
            connectionJobs.clear()
        }
        scope?.cancel()
        scope = null
    }

    /**
     * Adds new peers discovered from tracker announces.
     * Initiates connections to peers up to the maximum connection limit.
     *
     * @param peers list of peers to add
     */
    suspend fun addPeers(peers: List<Peer>) {
        mutex.withLock {
            for (peer in peers) {
                if (peer !in connections && peer !in failedPeers && peer !in pendingPeers) {
                    pendingPeers.add(peer)
                }
            }
        }
        connectToPendingPeers()
    }

    /**
     * Adds an incoming peer connection (from the server socket).
     *
     * @param connection the pre-established peer connection
     */
    suspend fun addIncomingConnection(connection: PeerConnection) {
        mutex.withLock {
            if (connections.size >= maxConnections) {
                connection.close()
                return
            }
            connections[connection.peer] = connection
        }
        _events.emit(PeerEvent.PeerConnected(connection.peer))
        startPeerMessageLoop(connection)
    }

    /**
     * Sends a Have message to all connected peers for a newly completed piece.
     *
     * @param pieceIndex the index of the completed piece
     */
    suspend fun broadcastHave(pieceIndex: Int) {
        val conns = mutex.withLock { connections.values.toList() }
        for (conn in conns) {
            try {
                conn.sendMessage(PeerMessage.Have(pieceIndex))
            } catch (_: Exception) {
                // Ignore send errors
            }
        }
    }

    /**
     * Requests pieces from unchoked peers that have pieces we need.
     * Called periodically by the session to drive the download.
     */
    suspend fun requestPieces() {
        val conns = mutex.withLock { connections.values.toList() }
        for (conn in conns) {
            if (conn.peerChoking || !conn.amInterested) continue
            requestFromPeer(conn)
        }
    }

    /**
     * Gets the bitfield of our downloaded pieces to send to peers.
     */
    fun getOurBitfield(): ByteArray = pieceManager.getOurBitfield()

    /**
     * Connects to pending peers, up to the max connection limit.
     */
    private suspend fun connectToPendingPeers() {
        val currentScope = scope ?: return
        val peersToConnect = mutex.withLock {
            val available = maxConnections - connections.size
            if (available <= 0) return
            val batch = pendingPeers.take(available)
            pendingPeers.removeAll(batch.toSet())
            batch
        }

        for (peer in peersToConnect) {
            currentScope.launch {
                connectToPeer(peer)
            }
        }
    }

    /**
     * Attempts to connect to a single peer and perform the handshake.
     */
    private suspend fun connectToPeer(peer: Peer) {
        val connection = PeerConnection(peer)
        try {
            connection.connect(peer)

            if (!connection.performHandshake(infoHash, peerId)) {
                connection.close()
                mutex.withLock { failedPeers.add(peer) }
                return
            }

            connection.initBitfield(metadata.pieces.size)

            mutex.withLock {
                if (connections.size >= maxConnections) {
                    connection.close()
                    return
                }
                connections[peer] = connection
            }

            _events.emit(PeerEvent.PeerConnected(peer))

            // Send our bitfield
            val ourBitfield = pieceManager.getOurBitfield()
            if (ourBitfield.any { it.toInt() != 0 }) {
                connection.sendMessage(PeerMessage.Bitfield(ourBitfield))
            }

            // Express interest if they have pieces we need
            if (shouldBeInterested(connection)) {
                connection.sendMessage(PeerMessage.Interested)
            }

            startPeerMessageLoop(connection)
        } catch (_: Exception) {
            connection.close()
            mutex.withLock { failedPeers.add(peer) }
        }
    }

    /**
     * Starts the message receiving loop for a peer connection.
     */
    private fun startPeerMessageLoop(connection: PeerConnection) {
        val currentScope = scope ?: return
        val job = currentScope.launch {
            try {
                while (isActive && connection.isConnected) {
                    val message = connection.receiveMessage()
                    handleMessage(connection, message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Connection lost
            } catch (_: Exception) {
                // Protocol error
            } finally {
                disconnectPeer(connection)
            }
        }
        currentScope.launch {
            mutex.withLock {
                connectionJobs[connection.peer] = job
            }
        }
    }

    /**
     * Handles an incoming message from a peer.
     */
    private suspend fun handleMessage(connection: PeerConnection, message: PeerMessage) {
        when (message) {
            is PeerMessage.KeepAlive -> {
                // Nothing to do
            }
            is PeerMessage.Choke -> {
                // State already updated in PeerConnection.receiveMessage
            }
            is PeerMessage.Unchoke -> {
                // We are unchoked, start requesting pieces
                requestFromPeer(connection)
            }
            is PeerMessage.Interested -> {
                // Peer is interested, choking algorithm will decide whether to unchoke
            }
            is PeerMessage.NotInterested -> {
                // Peer is no longer interested
            }
            is PeerMessage.Have -> {
                _events.emit(PeerEvent.PeerHavePiece(connection.peer, message.pieceIndex))
                // Re-evaluate interest
                if (!connection.amInterested && shouldBeInterested(connection)) {
                    connection.sendMessage(PeerMessage.Interested)
                }
            }
            is PeerMessage.Bitfield -> {
                // Bitfield already stored in PeerConnection.receiveMessage
                if (shouldBeInterested(connection)) {
                    connection.sendMessage(PeerMessage.Interested)
                }
            }
            is PeerMessage.Request -> {
                handlePieceRequest(connection, message)
            }
            is PeerMessage.Piece -> {
                _events.emit(PeerEvent.PieceReceived(message.index, message.begin, message.data))
            }
            is PeerMessage.Cancel -> {
                // Cancel outstanding request - we handle this by not caching outgoing data
            }
        }
    }

    /**
     * Handles an incoming piece request from a peer (for seeding).
     */
    private suspend fun handlePieceRequest(connection: PeerConnection, request: PeerMessage.Request) {
        if (connection.amChoking) return // Don't send data if we're choking them

        val pieceData = pieceManager.getPieceData(request.index) ?: return
        if (request.begin + request.length > pieceData.size) return

        val blockData = pieceData.copyOfRange(request.begin, request.begin + request.length)
        try {
            connection.sendMessage(PeerMessage.Piece(request.index, request.begin, blockData))
        } catch (_: Exception) {
            // Send failed, peer will re-request
        }
    }

    /**
     * Requests blocks from a peer for pieces they have that we need.
     * Requests up to 5 blocks at a time (pipelining).
     */
    private suspend fun requestFromPeer(connection: PeerConnection) {
        if (connection.peerChoking) return

        val peerBf = connection.peerBitfield ?: return
        val maxPipelined = 5

        for (i in 0 until maxPipelined) {
            val pieceIndex = pieceManager.selectPiece(peerBf) ?: break
            val blocks = pieceManager.getNeededBlocks(pieceIndex, blockSize)
            if (blocks.isEmpty()) continue

            val (begin, length) = blocks.first()
            try {
                connection.sendMessage(PeerMessage.Request(pieceIndex, begin, length))
            } catch (_: Exception) {
                break
            }
        }
    }

    /**
     * Checks whether we should be interested in a peer (they have pieces we need).
     */
    private suspend fun shouldBeInterested(connection: PeerConnection): Boolean {
        val peerBf = connection.peerBitfield ?: return false
        return pieceManager.selectPiece(peerBf) != null
    }

    /**
     * Disconnects a peer and cleans up resources.
     */
    private suspend fun disconnectPeer(connection: PeerConnection) {
        connection.close()
        mutex.withLock {
            connections.remove(connection.peer)
            connectionJobs.remove(connection.peer)?.cancel()
        }
        _events.emit(PeerEvent.PeerDisconnected(connection.peer))
        // Try to fill the slot with a pending peer
        connectToPendingPeers()
    }

    /**
     * Implements the BitTorrent choking algorithm.
     *
     * Unchokes the top 4 peers by download speed (reciprocation) plus
     * 1 random peer (optimistic unchoke) to discover new fast peers.
     */
    private suspend fun runChokingAlgorithm() {
        val conns = mutex.withLock { connections.values.toList() }
        if (conns.isEmpty()) return

        // Sort peers by upload rate to us (downloaded bytes as a proxy for speed)
        val interestedPeers = conns.filter { it.peerInterested }
        val sortedBySpeed = interestedPeers.sortedByDescending { it.downloadedBytes }

        // Unchoke top 4
        val toUnchoke = sortedBySpeed.take(4).toMutableSet()

        // Optimistic unchoke: pick one random choked interested peer
        val chokedInterested = interestedPeers.filter { it !in toUnchoke }
        if (chokedInterested.isNotEmpty()) {
            toUnchoke.add(chokedInterested.random())
        }

        // Apply choking decisions
        for (conn in conns) {
            try {
                if (conn in toUnchoke) {
                    if (conn.amChoking) {
                        conn.sendMessage(PeerMessage.Unchoke)
                    }
                } else {
                    if (!conn.amChoking) {
                        conn.sendMessage(PeerMessage.Choke)
                    }
                }
            } catch (_: Exception) {
                // Ignore send errors
            }
        }
    }
}
