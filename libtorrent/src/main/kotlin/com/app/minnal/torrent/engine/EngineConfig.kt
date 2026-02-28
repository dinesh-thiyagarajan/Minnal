package com.app.minnal.torrent.engine

/**
 * Configuration settings for the [TorrentEngine].
 *
 * @property maxConnections maximum total number of peer connections across all torrents
 * @property maxConnectionsPerTorrent maximum peer connections per individual torrent
 * @property downloadRateLimit global download rate limit in bytes/sec (0 = unlimited)
 * @property uploadRateLimit global upload rate limit in bytes/sec (0 = unlimited)
 * @property listeningPort preferred TCP port for incoming peer connections (falls back to 6881-6889)
 */
data class EngineConfig(
    val maxConnections: Int = 200,
    val maxConnectionsPerTorrent: Int = 50,
    val downloadRateLimit: Long = 0L,
    val uploadRateLimit: Long = 0L,
    val listeningPort: Int = 6881
)
