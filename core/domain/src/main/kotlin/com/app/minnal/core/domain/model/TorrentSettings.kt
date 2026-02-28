package com.app.minnal.core.domain.model

/**
 * Domain model representing user-configurable settings for the torrent client.
 *
 * @property downloadPath the directory path where downloaded files are saved
 * @property maxDownloadSpeed maximum download speed limit in bytes per second; 0 means unlimited
 * @property maxUploadSpeed maximum upload speed limit in bytes per second; 0 means unlimited
 * @property maxConnections maximum total number of peer connections across all torrents
 * @property maxConnectionsPerTorrent maximum number of peer connections per individual torrent
 * @property startOnAdd whether torrents should start downloading immediately when added
 * @property seedingEnabled whether torrents should continue seeding after download completes
 */
data class TorrentSettings(
    val downloadPath: String = "",
    val maxDownloadSpeed: Long = 0,
    val maxUploadSpeed: Long = 0,
    val maxConnections: Int = 200,
    val maxConnectionsPerTorrent: Int = 50,
    val startOnAdd: Boolean = true,
    val seedingEnabled: Boolean = true
)
