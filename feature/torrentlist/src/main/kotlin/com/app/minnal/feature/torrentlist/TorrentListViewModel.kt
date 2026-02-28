package com.app.minnal.feature.torrentlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.minnal.core.common.MinnalResult
import com.app.minnal.core.domain.model.Torrent
import com.app.minnal.core.domain.usecase.GetTorrentsUseCase
import com.app.minnal.core.domain.usecase.PauseTorrentUseCase
import com.app.minnal.core.domain.usecase.RemoveTorrentUseCase
import com.app.minnal.core.domain.usecase.ResumeTorrentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * UI state for the torrent list screen.
 *
 * @property torrents the list of all torrents
 * @property isLoading whether a loading operation is in progress
 * @property errorMessage an optional error message to display
 */
data class TorrentListUiState(
    val torrents: List<Torrent> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/**
 * ViewModel for the torrent list screen.
 *
 * Observes the list of all torrents from the domain layer and exposes
 * UI state as a [StateFlow]. Provides actions for pausing, resuming,
 * and removing torrents.
 *
 * @property getTorrentsUseCase use case for observing all torrents
 * @property pauseTorrentUseCase use case for pausing a torrent
 * @property resumeTorrentUseCase use case for resuming a torrent
 * @property removeTorrentUseCase use case for removing a torrent
 */
class TorrentListViewModel(
    private val getTorrentsUseCase: GetTorrentsUseCase,
    private val pauseTorrentUseCase: PauseTorrentUseCase,
    private val resumeTorrentUseCase: ResumeTorrentUseCase,
    private val removeTorrentUseCase: RemoveTorrentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TorrentListUiState())
    val uiState: StateFlow<TorrentListUiState> = _uiState.asStateFlow()

    init {
        observeTorrents()
    }

    private fun observeTorrents() {
        viewModelScope.launch {
            getTorrentsUseCase()
                .onStart {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
                .catch { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "An unknown error occurred"
                    )
                }
                .collect { torrents ->
                    _uiState.value = _uiState.value.copy(
                        torrents = torrents,
                        isLoading = false,
                        errorMessage = null
                    )
                }
        }
    }

    /**
     * Pauses the specified torrent.
     *
     * @param torrentId the ID of the torrent to pause
     */
    fun pauseTorrent(torrentId: String) {
        viewModelScope.launch {
            when (val result = pauseTorrentUseCase(torrentId)) {
                is MinnalResult.Success -> { /* State will update via flow */ }
                is MinnalResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.message
                    )
                }
                is MinnalResult.Loading -> { /* No-op */ }
            }
        }
    }

    /**
     * Resumes the specified torrent.
     *
     * @param torrentId the ID of the torrent to resume
     */
    fun resumeTorrent(torrentId: String) {
        viewModelScope.launch {
            when (val result = resumeTorrentUseCase(torrentId)) {
                is MinnalResult.Success -> { /* State will update via flow */ }
                is MinnalResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.message
                    )
                }
                is MinnalResult.Loading -> { /* No-op */ }
            }
        }
    }

    /**
     * Removes the specified torrent.
     *
     * @param torrentId the ID of the torrent to remove
     * @param deleteFiles whether to also delete downloaded files
     */
    fun removeTorrent(torrentId: String, deleteFiles: Boolean = false) {
        viewModelScope.launch {
            when (val result = removeTorrentUseCase(torrentId, deleteFiles)) {
                is MinnalResult.Success -> { /* State will update via flow */ }
                is MinnalResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.message
                    )
                }
                is MinnalResult.Loading -> { /* No-op */ }
            }
        }
    }

    /**
     * Clears any error message currently shown.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Refreshes the torrent list by re-subscribing to the flow.
     */
    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        observeTorrents()
    }
}
