package com.app.minnal.core.domain.model

/**
 * Represents the possible states of a torrent in the download lifecycle.
 */
enum class TorrentStatus {

    /** Torrent is verifying existing data against piece hashes. */
    CHECKING,

    /** Torrent is actively downloading pieces from peers. */
    DOWNLOADING,

    /** Torrent download is complete and is being uploaded to peers. */
    SEEDING,

    /** Torrent has been temporarily paused by the user. */
    PAUSED,

    /** Torrent has been fully stopped and is not active. */
    STOPPED,

    /** Torrent is waiting in queue to start downloading. */
    QUEUED,

    /** Torrent has encountered an error and cannot proceed. */
    ERROR
}
