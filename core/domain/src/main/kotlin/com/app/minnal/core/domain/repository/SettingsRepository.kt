package com.app.minnal.core.domain.repository

import com.app.minnal.core.domain.model.TorrentSettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user-configurable torrent client settings.
 *
 * All settings are persisted and observable through [Flow] to support reactive UI updates.
 */
interface SettingsRepository {

    /**
     * Observes the current torrent client settings.
     *
     * @return a [Flow] emitting the current [TorrentSettings] whenever any setting changes
     */
    fun getSettings(): Flow<TorrentSettings>

    /**
     * Updates the download directory path.
     *
     * @param path the new directory path for saving downloaded files
     */
    suspend fun updateDownloadPath(path: String)

    /**
     * Updates the maximum download speed limit.
     *
     * @param speed the maximum download speed in bytes per second; 0 for unlimited
     */
    suspend fun updateMaxDownloadSpeed(speed: Long)

    /**
     * Updates the maximum upload speed limit.
     *
     * @param speed the maximum upload speed in bytes per second; 0 for unlimited
     */
    suspend fun updateMaxUploadSpeed(speed: Long)

    /**
     * Updates the maximum total number of peer connections.
     *
     * @param max the maximum number of connections across all torrents
     */
    suspend fun updateMaxConnections(max: Int)

    /**
     * Updates the maximum number of peer connections per torrent.
     *
     * @param max the maximum number of connections for a single torrent
     */
    suspend fun updateMaxConnectionsPerTorrent(max: Int)

    /**
     * Updates whether torrents should start downloading immediately when added.
     *
     * @param enabled true to auto-start, false to add in paused state
     */
    suspend fun updateStartOnAdd(enabled: Boolean)

    /**
     * Updates whether completed torrents should continue seeding.
     *
     * @param enabled true to enable seeding, false to stop after download completes
     */
    suspend fun updateSeedingEnabled(enabled: Boolean)
}
