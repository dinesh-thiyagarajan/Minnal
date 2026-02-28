package com.app.minnal.core.domain.usecase

import com.app.minnal.core.domain.model.TorrentSettings
import com.app.minnal.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for observing the current torrent client settings.
 *
 * Provides a reactive stream of the user's settings that updates
 * whenever any setting is modified.
 *
 * @property settingsRepository the settings repository for accessing persisted settings
 */
class GetSettingsUseCase(private val settingsRepository: SettingsRepository) {

    /**
     * Observes the current torrent client settings.
     *
     * @return a [Flow] emitting the current [TorrentSettings] whenever any setting changes
     */
    operator fun invoke(): Flow<TorrentSettings> {
        return settingsRepository.getSettings()
    }
}
