package com.app.minnal.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.minnal.core.domain.model.TorrentSettings
import com.app.minnal.core.domain.usecase.GetSettingsUseCase
import com.app.minnal.core.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * UI state for the settings screen.
 *
 * @property settings the current torrent client settings
 * @property isLoading whether settings are being loaded
 * @property errorMessage an optional error message
 */
data class SettingsUiState(
    val settings: TorrentSettings = TorrentSettings(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/**
 * ViewModel for the settings screen.
 *
 * Observes the current settings from the domain layer and provides methods
 * to update each individual setting.
 *
 * @property getSettingsUseCase use case for observing current settings
 * @property updateSettingsUseCase use case for updating individual settings
 */
class SettingsViewModel(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingsUseCase: UpdateSettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            getSettingsUseCase()
                .catch { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Failed to load settings"
                    )
                }
                .collect { settings ->
                    _uiState.value = _uiState.value.copy(
                        settings = settings,
                        isLoading = false,
                        errorMessage = null
                    )
                }
        }
    }

    /**
     * Updates the download directory path.
     *
     * @param path the new download path
     */
    fun updateDownloadPath(path: String) {
        viewModelScope.launch {
            try {
                updateSettingsUseCase.updateDownloadPath(path)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update download path: ${e.message}"
                )
            }
        }
    }

    /**
     * Updates the maximum download speed.
     *
     * @param speed the max download speed in bytes per second (0 = unlimited)
     */
    fun updateMaxDownloadSpeed(speed: Long) {
        viewModelScope.launch {
            try {
                updateSettingsUseCase.updateMaxDownloadSpeed(speed)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update download speed: ${e.message}"
                )
            }
        }
    }

    /**
     * Updates the maximum upload speed.
     *
     * @param speed the max upload speed in bytes per second (0 = unlimited)
     */
    fun updateMaxUploadSpeed(speed: Long) {
        viewModelScope.launch {
            try {
                updateSettingsUseCase.updateMaxUploadSpeed(speed)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update upload speed: ${e.message}"
                )
            }
        }
    }

    /**
     * Updates the maximum total connections.
     *
     * @param max the maximum number of total peer connections
     */
    fun updateMaxConnections(max: Int) {
        viewModelScope.launch {
            try {
                updateSettingsUseCase.updateMaxConnections(max)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update max connections: ${e.message}"
                )
            }
        }
    }

    /**
     * Updates the maximum connections per torrent.
     *
     * @param max the maximum number of connections per torrent
     */
    fun updateMaxConnectionsPerTorrent(max: Int) {
        viewModelScope.launch {
            try {
                updateSettingsUseCase.updateMaxConnectionsPerTorrent(max)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update max connections per torrent: ${e.message}"
                )
            }
        }
    }

    /**
     * Updates the start-on-add setting.
     *
     * @param enabled whether torrents should auto-start when added
     */
    fun updateStartOnAdd(enabled: Boolean) {
        viewModelScope.launch {
            try {
                updateSettingsUseCase.updateStartOnAdd(enabled)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update setting: ${e.message}"
                )
            }
        }
    }

    /**
     * Updates the seeding-enabled setting.
     *
     * @param enabled whether to continue seeding after download completes
     */
    fun updateSeedingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                updateSettingsUseCase.updateSeedingEnabled(enabled)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update setting: ${e.message}"
                )
            }
        }
    }

    /**
     * Clears any error message currently shown.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
