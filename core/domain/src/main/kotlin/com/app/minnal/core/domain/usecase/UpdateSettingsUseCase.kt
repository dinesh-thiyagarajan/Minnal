package com.app.minnal.core.domain.usecase

import com.app.minnal.core.domain.repository.SettingsRepository

/**
 * Use case for updating individual torrent client settings.
 *
 * Provides methods for each configurable setting, delegating to
 * [SettingsRepository] for persistence.
 *
 * @property settingsRepository the settings repository for persisting setting changes
 */
class UpdateSettingsUseCase(private val settingsRepository: SettingsRepository) {

    /**
     * Updates the download directory path.
     *
     * @param path the new directory path for saving downloaded files
     */
    suspend fun updateDownloadPath(path: String) {
        settingsRepository.updateDownloadPath(path)
    }

    /**
     * Updates the maximum download speed limit.
     *
     * @param speed the maximum download speed in bytes per second; 0 for unlimited
     */
    suspend fun updateMaxDownloadSpeed(speed: Long) {
        settingsRepository.updateMaxDownloadSpeed(speed)
    }

    /**
     * Updates the maximum upload speed limit.
     *
     * @param speed the maximum upload speed in bytes per second; 0 for unlimited
     */
    suspend fun updateMaxUploadSpeed(speed: Long) {
        settingsRepository.updateMaxUploadSpeed(speed)
    }

    /**
     * Updates the maximum total number of peer connections.
     *
     * @param max the maximum number of connections across all torrents
     */
    suspend fun updateMaxConnections(max: Int) {
        settingsRepository.updateMaxConnections(max)
    }

    /**
     * Updates the maximum number of peer connections per torrent.
     *
     * @param max the maximum number of connections for a single torrent
     */
    suspend fun updateMaxConnectionsPerTorrent(max: Int) {
        settingsRepository.updateMaxConnectionsPerTorrent(max)
    }

    /**
     * Updates whether torrents should start downloading immediately when added.
     *
     * @param enabled true to auto-start, false to add in paused state
     */
    suspend fun updateStartOnAdd(enabled: Boolean) {
        settingsRepository.updateStartOnAdd(enabled)
    }

    /**
     * Updates whether completed torrents should continue seeding.
     *
     * @param enabled true to enable seeding, false to stop after download completes
     */
    suspend fun updateSeedingEnabled(enabled: Boolean) {
        settingsRepository.updateSeedingEnabled(enabled)
    }
}
