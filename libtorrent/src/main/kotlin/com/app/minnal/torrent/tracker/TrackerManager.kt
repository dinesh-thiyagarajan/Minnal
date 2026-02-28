package com.app.minnal.torrent.tracker

import com.app.minnal.torrent.model.Peer
import com.app.minnal.torrent.model.TorrentMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manages tracker announces across multiple trackers for a single torrent.
 *
 * Handles periodic re-announces based on the interval returned by each tracker,
 * tries alternative trackers when one fails, and emits discovered peers via a [Flow].
 *
 * @param metadata the torrent metadata containing tracker URLs
 * @param peerId 20-byte peer ID for this client
 * @param port the port this client is listening on
 */
class TrackerManager(
    private val metadata: TorrentMetadata,
    private val peerId: ByteArray,
    private val port: Int
) {
    private val _peerFlow = MutableSharedFlow<List<Peer>>(replay = 0, extraBufferCapacity = 64)

    /** Flow that emits lists of newly discovered peers from tracker announces. */
    val peerFlow: Flow<List<Peer>> = _peerFlow.asSharedFlow()

    private var scope: CoroutineScope? = null
    private val knownPeers = mutableSetOf<Peer>()

    @Volatile
    var uploaded: Long = 0L

    @Volatile
    var downloaded: Long = 0L

    @Volatile
    var left: Long = metadata.totalSize

    /** Number of seeders last reported by any tracker. */
    @Volatile
    var seeders: Int = 0
        private set

    /** Number of leechers last reported by any tracker. */
    @Volatile
    var leechers: Int = 0
        private set

    /**
     * Starts the tracker manager, initiating announce requests to all known trackers.
     *
     * @param parentScope the parent coroutine scope for structured concurrency
     */
    fun start(parentScope: CoroutineScope) {
        val trackerScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
        scope = trackerScope

        for (announceUrl in metadata.announceUrls) {
            trackerScope.launch {
                announceLoop(announceUrl, "started")
            }
        }
    }

    /**
     * Stops all tracker communication and sends the "stopped" event.
     */
    suspend fun stop() {
        // Send stopped event to all trackers (best effort)
        val currentScope = scope ?: return
        val stopJobs = metadata.announceUrls.map { url ->
            currentScope.async {
                try {
                    announceOnce(url, "stopped")
                } catch (_: Exception) {
                    // Best effort - ignore errors when stopping
                }
            }
        }

        try {
            withTimeout(5000) {
                stopJobs.forEach { it.await() }
            }
        } catch (_: Exception) {
            // Timeout or cancellation - proceed with cleanup
        }

        scope?.cancel()
        scope = null
    }

    /**
     * Sends a "completed" event to all trackers.
     */
    suspend fun announceCompleted() {
        val currentScope = scope ?: return
        for (url in metadata.announceUrls) {
            currentScope.launch {
                try {
                    announceOnce(url, "completed")
                } catch (_: Exception) {
                    // Best effort
                }
            }
        }
    }

    /**
     * The main announce loop for a single tracker URL.
     * Sends the initial announce, then periodically re-announces based on the interval.
     */
    private suspend fun announceLoop(announceUrl: String, initialEvent: String) {
        var event = initialEvent
        var interval = 1800 // default 30 minutes

        while (currentCoroutineContext().isActive) {
            try {
                val response = announceOnce(announceUrl, event)
                event = "" // Subsequent announces have no event

                if (response.failureReason != null) {
                    // Tracker returned an error, wait and retry
                    delay(60_000)
                    continue
                }

                interval = response.interval.coerceIn(60, 3600)

                if (response.complete > 0) seeders = response.complete
                if (response.incomplete > 0) leechers = response.incomplete

                // Emit new peers
                val newPeers = response.peers.filter { it !in knownPeers }
                if (newPeers.isNotEmpty()) {
                    knownPeers.addAll(newPeers)
                    _peerFlow.emit(newPeers)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Network error, retry after a shorter interval
                delay(60_000)
                continue
            }

            delay(interval * 1000L)
        }
    }

    /**
     * Sends a single announce request to the given tracker URL.
     */
    private suspend fun announceOnce(announceUrl: String, event: String): TrackerResponse {
        return if (announceUrl.startsWith("udp://")) {
            UdpTrackerClient.announce(
                announceUrl = announceUrl,
                infoHash = metadata.infoHash,
                peerId = peerId,
                port = port,
                uploaded = uploaded,
                downloaded = downloaded,
                left = left,
                event = UdpTrackerClient.eventCode(event)
            )
        } else {
            HttpTrackerClient.announce(
                announceUrl = announceUrl,
                infoHash = metadata.infoHash,
                peerId = peerId,
                port = port,
                uploaded = uploaded,
                downloaded = downloaded,
                left = left,
                event = event
            )
        }
    }
}
