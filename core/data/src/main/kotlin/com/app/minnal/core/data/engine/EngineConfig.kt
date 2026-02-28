package com.app.minnal.core.data.engine

import com.app.minnal.core.common.Constants

/**
 * Configuration for the torrent engine.
 *
 * @property port listening port for incoming peer connections
 * @property maxConnections maximum total peer connections
 * @property maxConnectionsPerTorrent maximum connections per torrent
 * @property maxDownloadSpeed maximum download speed in bytes/sec (0 = unlimited)
 * @property maxUploadSpeed maximum upload speed in bytes/sec (0 = unlimited)
 */
data class EngineConfig(
    val port: Int = Constants.DEFAULT_PORT,
    val maxConnections: Int = Constants.MAX_CONNECTIONS,
    val maxConnectionsPerTorrent: Int = Constants.MAX_CONNECTIONS_PER_TORRENT,
    val maxDownloadSpeed: Long = 0,
    val maxUploadSpeed: Long = 0
)
