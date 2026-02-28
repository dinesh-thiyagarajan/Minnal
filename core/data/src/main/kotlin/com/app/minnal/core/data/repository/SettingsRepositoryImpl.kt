package com.app.minnal.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.app.minnal.core.common.Constants
import com.app.minnal.core.domain.model.TorrentSettings
import com.app.minnal.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "minnal_settings")

/**
 * Implementation of [SettingsRepository] backed by Android DataStore Preferences.
 *
 * Persists user-configurable settings and provides reactive observation
 * through Kotlin Flow.
 *
 * @property context the Android application context
 */
class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {

    private object Keys {
        val DOWNLOAD_PATH = stringPreferencesKey("download_path")
        val MAX_DOWNLOAD_SPEED = longPreferencesKey("max_download_speed")
        val MAX_UPLOAD_SPEED = longPreferencesKey("max_upload_speed")
        val MAX_CONNECTIONS = intPreferencesKey("max_connections")
        val MAX_CONNECTIONS_PER_TORRENT = intPreferencesKey("max_connections_per_torrent")
        val START_ON_ADD = booleanPreferencesKey("start_on_add")
        val SEEDING_ENABLED = booleanPreferencesKey("seeding_enabled")
    }

    override fun getSettings(): Flow<TorrentSettings> {
        return context.dataStore.data.map { preferences ->
            TorrentSettings(
                downloadPath = preferences[Keys.DOWNLOAD_PATH] ?: "",
                maxDownloadSpeed = preferences[Keys.MAX_DOWNLOAD_SPEED] ?: 0L,
                maxUploadSpeed = preferences[Keys.MAX_UPLOAD_SPEED] ?: 0L,
                maxConnections = preferences[Keys.MAX_CONNECTIONS] ?: Constants.MAX_CONNECTIONS,
                maxConnectionsPerTorrent = preferences[Keys.MAX_CONNECTIONS_PER_TORRENT]
                    ?: Constants.MAX_CONNECTIONS_PER_TORRENT,
                startOnAdd = preferences[Keys.START_ON_ADD] ?: true,
                seedingEnabled = preferences[Keys.SEEDING_ENABLED] ?: true
            )
        }
    }

    override suspend fun updateDownloadPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DOWNLOAD_PATH] = path
        }
    }

    override suspend fun updateMaxDownloadSpeed(speed: Long) {
        context.dataStore.edit { preferences ->
            preferences[Keys.MAX_DOWNLOAD_SPEED] = speed
        }
    }

    override suspend fun updateMaxUploadSpeed(speed: Long) {
        context.dataStore.edit { preferences ->
            preferences[Keys.MAX_UPLOAD_SPEED] = speed
        }
    }

    override suspend fun updateMaxConnections(max: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.MAX_CONNECTIONS] = max
        }
    }

    override suspend fun updateMaxConnectionsPerTorrent(max: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.MAX_CONNECTIONS_PER_TORRENT] = max
        }
    }

    override suspend fun updateStartOnAdd(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.START_ON_ADD] = enabled
        }
    }

    override suspend fun updateSeedingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SEEDING_ENABLED] = enabled
        }
    }
}
