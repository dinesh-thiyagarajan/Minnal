package com.app.minnal.torrent.session

/**
 * Enumeration of possible states for a torrent session.
 */
enum class TorrentSessionState {
    /** Verifying existing data on disk against piece hashes. */
    CHECKING,

    /** Actively downloading pieces from peers. */
    DOWNLOADING,

    /** All pieces downloaded; now uploading to other peers. */
    SEEDING,

    /** Download/upload is paused by the user. */
    PAUSED,

    /** Session has been stopped. */
    STOPPED,

    /** An unrecoverable error occurred. */
    ERROR
}

/**
 * Immutable snapshot of a torrent session's current state.
 *
 * Emitted via a StateFlow from [TorrentSession] so that UI or other consumers
 * can observe progress, speed, and connection information in real-time.
 *
 * @property state the current session state
 * @property progress download progress from 0.0 (nothing) to 1.0 (complete)
 * @property downloadSpeed current download speed in bytes per second
 * @property uploadSpeed current upload speed in bytes per second
 * @property connectedPeers number of currently connected peers
 * @property totalPeers total number of known peers (connected + discovered)
 * @property downloaded total bytes downloaded so far
 * @property uploaded total bytes uploaded so far
 * @property eta estimated time remaining in seconds, or -1 if unknown
 */
data class SessionState(
    val state: TorrentSessionState = TorrentSessionState.STOPPED,
    val progress: Float = 0f,
    val downloadSpeed: Long = 0L,
    val uploadSpeed: Long = 0L,
    val connectedPeers: Int = 0,
    val totalPeers: Int = 0,
    val downloaded: Long = 0L,
    val uploaded: Long = 0L,
    val eta: Long = -1L
)
