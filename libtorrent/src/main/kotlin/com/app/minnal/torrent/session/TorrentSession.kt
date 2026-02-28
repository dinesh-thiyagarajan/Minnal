package com.app.minnal.torrent.session

import com.app.minnal.torrent.file.TorrentFileManager
import com.app.minnal.torrent.model.TorrentMetadata
import com.app.minnal.torrent.peer.PeerEvent
import com.app.minnal.torrent.peer.PeerManager
import com.app.minnal.torrent.piece.PieceManager
import com.app.minnal.torrent.tracker.TrackerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Orchestrates the download and upload of a single torrent.
 *
 * Coordinates the [TrackerManager], [PeerManager], [PieceManager], and
 * [TorrentFileManager] to drive the complete lifecycle of a torrent:
 * announcing to trackers, connecting to peers, requesting and verifying
 * pieces, writing to disk, and transitioning to seeding upon completion.
 *
 * @param metadata the parsed torrent metadata
 * @param savePath the directory to save downloaded files to
 * @param peerId the 20-byte peer ID for this client
 * @param port the port this client is listening on
 */
class TorrentSession(
    val metadata: TorrentMetadata,
    private val savePath: String,
    private val peerId: ByteArray,
    private val port: Int
) {
    private val _stateFlow = MutableStateFlow(SessionState())

    /** Observable state flow for the current session state. */
    val stateFlow: StateFlow<SessionState> = _stateFlow.asStateFlow()

    val pieceManager = PieceManager(metadata)
    private val fileManager = TorrentFileManager(metadata, savePath)
    private val trackerManager = TrackerManager(metadata, peerId, port)
    val peerManager = PeerManager(metadata, pieceManager, metadata.infoHash, peerId)

    private var scope: CoroutineScope? = null
    private var mainLoopJob: Job? = null
    private var speedTrackingJob: Job? = null
    private var peerEventJob: Job? = null
    private var trackerPeerJob: Job? = null

    // Speed tracking with sliding window
    private val downloadSamples = ConcurrentLinkedDeque<Pair<Long, Long>>() // timestamp -> cumulative bytes
    private val uploadSamples = ConcurrentLinkedDeque<Pair<Long, Long>>()
    private val speedWindowMs = 5000L

    @Volatile
    private var totalDownloaded = 0L

    @Volatile
    private var totalUploaded = 0L

    /**
     * Starts the torrent session: announces to trackers, connects to peers,
     * and begins downloading pieces.
     */
    suspend fun start() {
        val sessionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = sessionScope

        updateState { copy(state = TorrentSessionState.CHECKING) }

        // Allocate files on disk
        fileManager.allocateFiles()

        // Check existing data
        checkExistingData()

        if (pieceManager.isComplete()) {
            updateState {
                copy(
                    state = TorrentSessionState.SEEDING,
                    progress = 1.0f,
                    downloaded = metadata.totalSize
                )
            }
            startSeeding(sessionScope)
            return
        }

        val currentProgress = pieceManager.getProgress()
        val currentDownloaded = pieceManager.downloadedBytes()
        updateState {
            copy(
                state = TorrentSessionState.DOWNLOADING,
                progress = currentProgress,
                downloaded = currentDownloaded
            )
        }

        // Update tracker with remaining bytes
        trackerManager.left = metadata.totalSize - currentDownloaded

        // Start tracker announces
        trackerManager.start(sessionScope)

        // Start peer manager
        peerManager.start(sessionScope)

        // Listen for new peers from trackers
        trackerPeerJob = sessionScope.launch {
            trackerManager.peerFlow.collect { peers ->
                peerManager.addPeers(peers)
            }
        }

        // Listen for peer events (piece data, connections)
        peerEventJob = sessionScope.launch {
            peerManager.events.collect { event ->
                handlePeerEvent(event)
            }
        }

        // Start the main download loop
        mainLoopJob = sessionScope.launch {
            downloadLoop()
        }

        // Start speed tracking
        speedTrackingJob = sessionScope.launch {
            speedTrackingLoop()
        }
    }

    /**
     * Pauses the torrent session. Stops requesting pieces but keeps connections alive.
     */
    suspend fun pause() {
        mainLoopJob?.cancel()
        mainLoopJob = null
        updateState { copy(state = TorrentSessionState.PAUSED, downloadSpeed = 0, uploadSpeed = 0) }
    }

    /**
     * Resumes a paused torrent session.
     */
    suspend fun resume() {
        val currentScope = scope ?: return
        if (_stateFlow.value.state != TorrentSessionState.PAUSED) return

        updateState { copy(state = TorrentSessionState.DOWNLOADING) }

        mainLoopJob = currentScope.launch {
            downloadLoop()
        }
    }

    /**
     * Stops the torrent session completely, disconnecting all peers
     * and sending stop events to trackers.
     */
    suspend fun stop() {
        updateState { copy(state = TorrentSessionState.STOPPED, downloadSpeed = 0, uploadSpeed = 0) }

        mainLoopJob?.cancel()
        speedTrackingJob?.cancel()
        peerEventJob?.cancel()
        trackerPeerJob?.cancel()

        peerManager.stop()
        trackerManager.stop()
        fileManager.close()

        scope?.cancel()
        scope = null
    }

    /**
     * Checks existing data on disk for already-downloaded pieces.
     */
    private suspend fun checkExistingData() {
        for (i in 0 until pieceManager.totalPieces) {
            try {
                val data = fileManager.readPiece(i)
                val hash = com.app.minnal.torrent.util.HashUtils.sha1(data)
                if (hash.contentEquals(metadata.pieces[i])) {
                    pieceManager.markAlreadyDownloaded(i)
                }
            } catch (_: Exception) {
                // Piece not available on disk, needs downloading
            }
        }
    }

    /**
     * The main download loop that periodically requests pieces from peers
     * and updates the session state.
     */
    private suspend fun downloadLoop() {
        while (currentCoroutineContext().isActive) {
            if (pieceManager.isComplete()) {
                onDownloadComplete()
                return
            }

            // Request pieces from connected peers
            peerManager.requestPieces()

            delay(100) // Small delay to avoid busy-waiting
        }
    }

    /**
     * Handles events from the peer manager.
     */
    private suspend fun handlePeerEvent(event: PeerEvent) {
        when (event) {
            is PeerEvent.PieceReceived -> {
                val complete = pieceManager.addBlock(event.pieceIndex, event.begin, event.data)
                totalDownloaded += event.data.size

                if (complete) {
                    if (pieceManager.verifyPiece(event.pieceIndex)) {
                        // Piece verified, write to disk
                        val pieceData = pieceManager.getPieceData(event.pieceIndex)
                        if (pieceData != null) {
                            fileManager.writePiece(event.pieceIndex, pieceData)
                        }
                        pieceManager.markDownloaded(event.pieceIndex)
                        pieceManager.clearPieceData(event.pieceIndex)

                        // Broadcast Have to all peers
                        peerManager.broadcastHave(event.pieceIndex)

                        // Update tracker stats
                        val dlBytes = pieceManager.downloadedBytes()
                        val dlProgress = pieceManager.getProgress()
                        trackerManager.downloaded = totalDownloaded
                        trackerManager.left = metadata.totalSize - dlBytes

                        // Update state
                        updateState {
                            copy(
                                progress = dlProgress,
                                downloaded = dlBytes
                            )
                        }
                    } else {
                        // Hash mismatch, discard and re-download
                        pieceManager.markFailed(event.pieceIndex)
                    }
                }
            }
            is PeerEvent.PeerConnected -> {
                updateState {
                    copy(
                        connectedPeers = peerManager.connectedPeerCount,
                        totalPeers = peerManager.totalPeerCount
                    )
                }
            }
            is PeerEvent.PeerDisconnected -> {
                updateState {
                    copy(
                        connectedPeers = peerManager.connectedPeerCount,
                        totalPeers = peerManager.totalPeerCount
                    )
                }
            }
            is PeerEvent.PeerHavePiece -> {
                // Availability updates are handled within PeerManager
            }
        }
    }

    /**
     * Called when all pieces have been downloaded and verified.
     */
    private suspend fun onDownloadComplete() {
        updateState {
            copy(
                state = TorrentSessionState.SEEDING,
                progress = 1.0f,
                downloaded = metadata.totalSize,
                downloadSpeed = 0,
                eta = 0
            )
        }

        // Notify trackers of completion
        trackerManager.left = 0
        trackerManager.announceCompleted()

        // Continue seeding (peer manager keeps running)
    }

    /**
     * Starts seeding mode (all pieces already available).
     */
    private fun startSeeding(sessionScope: CoroutineScope) {
        trackerManager.left = 0
        trackerManager.start(sessionScope)
        peerManager.start(sessionScope)

        trackerPeerJob = sessionScope.launch {
            trackerManager.peerFlow.collect { peers ->
                peerManager.addPeers(peers)
            }
        }

        peerEventJob = sessionScope.launch {
            peerManager.events.collect { event ->
                handlePeerEvent(event)
            }
        }

        speedTrackingJob = sessionScope.launch {
            speedTrackingLoop()
        }
    }

    /**
     * Periodically calculates download and upload speeds using a sliding window.
     */
    private suspend fun speedTrackingLoop() {
        while (currentCoroutineContext().isActive) {
            delay(1000)

            val now = System.currentTimeMillis()

            // Record current cumulative totals
            downloadSamples.addLast(Pair(now, totalDownloaded))
            uploadSamples.addLast(Pair(now, totalUploaded))

            // Remove samples outside the window
            val cutoff = now - speedWindowMs
            while (downloadSamples.peekFirst()?.let { it.first < cutoff } == true) {
                downloadSamples.pollFirst()
            }
            while (uploadSamples.peekFirst()?.let { it.first < cutoff } == true) {
                uploadSamples.pollFirst()
            }

            // Calculate speed from the window
            val dlSpeed = calculateSpeed(downloadSamples)
            val ulSpeed = calculateSpeed(uploadSamples)

            // Calculate ETA
            val remaining = metadata.totalSize - pieceManager.downloadedBytes()
            val eta = if (dlSpeed > 0 && remaining > 0) {
                remaining / dlSpeed
            } else if (remaining <= 0) {
                0L
            } else {
                -1L
            }

            updateState {
                copy(
                    downloadSpeed = dlSpeed,
                    uploadSpeed = ulSpeed,
                    eta = eta,
                    connectedPeers = peerManager.connectedPeerCount,
                    totalPeers = peerManager.totalPeerCount
                )
            }
        }
    }

    /**
     * Calculates speed in bytes/sec from a sliding window of cumulative byte samples.
     */
    private fun calculateSpeed(samples: ConcurrentLinkedDeque<Pair<Long, Long>>): Long {
        if (samples.size < 2) return 0L
        val first = samples.peekFirst() ?: return 0L
        val last = samples.peekLast() ?: return 0L
        val timeDiffMs = last.first - first.first
        if (timeDiffMs <= 0) return 0L
        val bytesDiff = last.second - first.second
        return (bytesDiff * 1000) / timeDiffMs
    }

    /**
     * Updates the state flow atomically.
     */
    private suspend fun updateState(update: SessionState.() -> SessionState) {
        _stateFlow.value = _stateFlow.value.update()
    }
}
