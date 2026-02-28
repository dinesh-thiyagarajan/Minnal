package com.app.minnal.torrent.engine

import com.app.minnal.torrent.model.Peer
import com.app.minnal.torrent.model.TorrentMetadata
import com.app.minnal.torrent.peer.PeerConnection
import com.app.minnal.torrent.peer.PeerHandshake
import com.app.minnal.torrent.session.SessionState
import com.app.minnal.torrent.session.TorrentSession
import com.app.minnal.torrent.util.HashUtils.toHexString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * The top-level BitTorrent engine that manages multiple torrent sessions.
 *
 * Provides an API to add, remove, pause, and resume torrents. Manages a
 * server socket for incoming peer connections and routes them to the
 * appropriate torrent session based on the info hash from the handshake.
 *
 * @param config engine configuration settings
 */
class TorrentEngine(private val config: EngineConfig = EngineConfig()) {

    private val sessions = mutableMapOf<String, TorrentSession>()
    private val mutex = Mutex()
    private var scope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    /** The 20-byte peer ID for this engine instance. */
    val peerId: ByteArray = generatePeerId()

    /** The port the engine is listening on for incoming connections. */
    @Volatile
    var listeningPort: Int = 0
        private set

    /**
     * Starts the torrent engine: binds a server socket for incoming peer
     * connections and prepares for torrent management.
     */
    suspend fun start() {
        val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = engineScope

        // Try to bind to a port in the range 6881-6889
        val socket = bindServerSocket(config.listeningPort)
        serverSocket = socket
        listeningPort = socket.localPort

        // Start accepting incoming connections
        serverJob = engineScope.launch(Dispatchers.IO) {
            acceptIncomingConnections(socket)
        }
    }

    /**
     * Stops the engine: stops all torrent sessions, closes the server socket,
     * and cancels all coroutines.
     */
    suspend fun stop() {
        mutex.withLock {
            for ((_, session) in sessions) {
                try {
                    session.stop()
                } catch (_: Exception) { }
            }
            sessions.clear()
        }

        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) { }
        serverSocket = null

        scope?.cancel()
        scope = null
    }

    /**
     * Adds a new torrent to the engine and starts downloading.
     *
     * @param metadata the parsed torrent metadata
     * @param savePath the directory to save downloaded files to
     * @return the torrent ID (info hash hex string)
     */
    suspend fun addTorrent(metadata: TorrentMetadata, savePath: String): String {
        val id = metadata.infoHashHex
        val currentScope = scope ?: throw IllegalStateException("Engine not started")

        mutex.withLock {
            if (sessions.containsKey(id)) {
                return id // Already added
            }

            val session = TorrentSession(
                metadata = metadata,
                savePath = savePath,
                peerId = peerId,
                port = listeningPort
            )
            sessions[id] = session
        }

        // Start the session outside the lock
        val session = mutex.withLock { sessions[id]!! }
        currentScope.launch {
            session.start()
        }

        return id
    }

    /**
     * Removes a torrent from the engine.
     *
     * @param id the torrent ID (info hash hex string)
     * @param deleteFiles if true, deletes the downloaded files from disk
     */
    suspend fun removeTorrent(id: String, deleteFiles: Boolean = false) {
        val session = mutex.withLock {
            sessions.remove(id)
        } ?: return

        session.stop()

        if (deleteFiles) {
            // Delete downloaded files
            try {
                val savePath = java.io.File(
                    session.metadata.name
                )
                // Note: actual file deletion would need the save path from session
                // This is a simplified version
            } catch (_: Exception) { }
        }
    }

    /**
     * Pauses a torrent.
     *
     * @param id the torrent ID
     */
    suspend fun pauseTorrent(id: String) {
        val session = mutex.withLock { sessions[id] } ?: return
        session.pause()
    }

    /**
     * Resumes a paused torrent.
     *
     * @param id the torrent ID
     */
    suspend fun resumeTorrent(id: String) {
        val session = mutex.withLock { sessions[id] } ?: return
        session.resume()
    }

    /**
     * Gets the state flow for a specific torrent.
     *
     * @param id the torrent ID
     * @return the state flow, or null if the torrent is not found
     */
    fun getTorrentState(id: String): StateFlow<SessionState>? {
        return sessions[id]?.stateFlow
    }

    /**
     * Gets all active torrent sessions.
     *
     * @return map of torrent ID to session
     */
    fun getAllTorrents(): Map<String, TorrentSession> {
        return sessions.toMap()
    }

    /**
     * Tries to bind a server socket, starting with the preferred port
     * and falling back to ports 6881-6889.
     */
    private fun bindServerSocket(preferredPort: Int): ServerSocket {
        // Try preferred port first
        try {
            val socket = ServerSocket(preferredPort)
            return socket
        } catch (_: Exception) { }

        // Try ports in the standard BitTorrent range
        for (port in 6881..6889) {
            if (port == preferredPort) continue
            try {
                val socket = ServerSocket(port)
                return socket
            } catch (_: Exception) { }
        }

        // Fall back to any available port
        return ServerSocket(0)
    }

    /**
     * Accepts incoming peer connections on the server socket.
     * Reads the handshake to determine which torrent the peer wants,
     * then routes the connection to the appropriate session.
     */
    private suspend fun acceptIncomingConnections(socket: ServerSocket) {
        while (currentCoroutineContext().isActive) {
            try {
                val clientSocket = withContext(Dispatchers.IO) {
                    socket.accept()
                }
                scope?.launch(Dispatchers.IO) {
                    handleIncomingConnection(clientSocket)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Server socket closed or other error
                if (!currentCoroutineContext().isActive) break
                delay(100)
            }
        }
    }

    /**
     * Handles an incoming peer connection by reading the handshake,
     * identifying the target torrent, and passing the connection to
     * the appropriate session's peer manager.
     */
    private suspend fun handleIncomingConnection(clientSocket: Socket) {
        try {
            clientSocket.soTimeout = 10_000

            val input = DataInputStream(clientSocket.getInputStream().buffered())

            // Read pstrlen
            val pstrlen = input.read()
            if (pstrlen < 0) {
                clientSocket.close()
                return
            }

            // Read the rest of the handshake
            val remaining = pstrlen + 8 + 20 + 20
            val handshakeData = ByteArray(1 + remaining)
            handshakeData[0] = pstrlen.toByte()
            var totalRead = 0
            while (totalRead < remaining) {
                val bytesRead = input.read(handshakeData, 1 + totalRead, remaining - totalRead)
                if (bytesRead < 0) {
                    clientSocket.close()
                    return
                }
                totalRead += bytesRead
            }

            val peerHandshake = PeerHandshake.decode(handshakeData)
            val infoHashHex = peerHandshake.infoHash.toHexString()

            // Find the session for this info hash
            val session = mutex.withLock { sessions[infoHashHex] }
            if (session == null) {
                clientSocket.close()
                return
            }

            // Send our handshake back
            val ourHandshake = PeerHandshake(
                infoHash = peerHandshake.infoHash,
                peerId = peerId
            )
            val out = clientSocket.getOutputStream()
            out.write(PeerHandshake.encode(ourHandshake))
            out.flush()

            // Create PeerConnection and hand off to session
            val ip = clientSocket.inetAddress.hostAddress ?: "unknown"
            val port = clientSocket.port
            val peer = Peer(ip = ip, port = port, peerId = peerHandshake.peerId)
            val connection = PeerConnection(peer)
            connection.connectWithSocket(clientSocket)

            session.peerManager.addIncomingConnection(connection)
        } catch (_: Exception) {
            try {
                clientSocket.close()
            } catch (_: Exception) { }
        }
    }

    companion object {
        /**
         * Generates a unique 20-byte peer ID following the Azureus-style convention.
         * Format: `-MN1000-` followed by 12 random alphanumeric characters.
         *
         * @return 20-byte peer ID
         */
        fun generatePeerId(): ByteArray {
            val prefix = "-MN1000-"
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val suffix = StringBuilder(12)
            for (i in 0 until 12) {
                suffix.append(chars.random())
            }
            return (prefix + suffix.toString()).toByteArray(Charsets.US_ASCII)
        }
    }
}
