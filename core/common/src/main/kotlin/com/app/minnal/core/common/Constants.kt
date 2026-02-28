package com.app.minnal.core.common

/**
 * Application-wide constants for the Minnal torrent client.
 */
object Constants {

    /** Default subdirectory name for torrent downloads. */
    const val DEFAULT_DOWNLOAD_PATH = "Minnal"

    /** Maximum total number of peer connections across all torrents. */
    const val MAX_CONNECTIONS = 200

    /** Maximum number of peer connections per individual torrent. */
    const val MAX_CONNECTIONS_PER_TORRENT = 50

    /** Default listening port for incoming BitTorrent connections. */
    const val DEFAULT_PORT = 6881

    /** Standard BitTorrent block size in bytes (16 KB). */
    const val BLOCK_SIZE = 16384

    /** Notification channel ID for download progress notifications. */
    const val NOTIFICATION_CHANNEL_ID = "minnal_downloads"

    /** Base notification ID for the torrent service foreground notification. */
    const val NOTIFICATION_ID = 1001

    /** Intent action to start the torrent service. */
    const val TORRENT_SERVICE_ACTION_START = "com.app.minnal.action.START"

    /** Intent action to stop the torrent service. */
    const val TORRENT_SERVICE_ACTION_STOP = "com.app.minnal.action.STOP"
}
