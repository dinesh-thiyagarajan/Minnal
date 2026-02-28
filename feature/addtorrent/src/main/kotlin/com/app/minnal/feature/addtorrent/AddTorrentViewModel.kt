package com.app.minnal.feature.addtorrent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.minnal.core.common.MinnalResult
import com.app.minnal.core.domain.model.TorrentFileInfo
import com.app.minnal.core.domain.usecase.AddMagnetUseCase
import com.app.minnal.core.domain.usecase.AddTorrentUseCase
import com.app.minnal.core.domain.usecase.GetSettingsUseCase
import com.app.minnal.torrent.parser.TorrentParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Mode for adding a torrent: from a .torrent file or a magnet link.
 */
enum class AddMode {
    FILE,
    MAGNET
}

/**
 * UI state for the add torrent screen.
 *
 * @property torrentName the name of the parsed torrent
 * @property torrentSize the total size of the torrent in bytes
 * @property fileCount the number of files in the torrent
 * @property files the list of files in the torrent
 * @property savePath the directory path where files will be saved
 * @property magnetLink the magnet link URI string
 * @property isLoading whether an operation is in progress
 * @property isAdded whether the torrent has been successfully added
 * @property errorMessage an optional error message
 * @property mode the current add mode (FILE or MAGNET)
 * @property torrentData the raw bytes of the parsed .torrent file
 */
data class AddTorrentUiState(
    val torrentName: String = "",
    val torrentSize: Long = 0,
    val fileCount: Int = 0,
    val files: List<TorrentFileInfo> = emptyList(),
    val savePath: String = "",
    val magnetLink: String = "",
    val isLoading: Boolean = false,
    val isAdded: Boolean = false,
    val errorMessage: String? = null,
    val mode: AddMode = AddMode.FILE,
    val torrentData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AddTorrentUiState) return false
        return torrentName == other.torrentName &&
                torrentSize == other.torrentSize &&
                fileCount == other.fileCount &&
                files == other.files &&
                savePath == other.savePath &&
                magnetLink == other.magnetLink &&
                isLoading == other.isLoading &&
                isAdded == other.isAdded &&
                errorMessage == other.errorMessage &&
                mode == other.mode &&
                torrentData.contentEquals(other.torrentData)
    }

    override fun hashCode(): Int {
        var result = torrentName.hashCode()
        result = 31 * result + torrentSize.hashCode()
        result = 31 * result + fileCount
        result = 31 * result + files.hashCode()
        result = 31 * result + savePath.hashCode()
        result = 31 * result + magnetLink.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + isAdded.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + mode.hashCode()
        result = 31 * result + (torrentData?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * ViewModel for the add torrent screen.
 *
 * Handles parsing .torrent files, validating magnet links, and adding
 * new torrents to the engine through the domain use cases.
 *
 * @property addTorrentUseCase use case for adding a torrent from file data
 * @property addMagnetUseCase use case for adding a torrent from a magnet link
 * @property getSettingsUseCase use case for reading settings (default save path)
 */
class AddTorrentViewModel(
    private val addTorrentUseCase: AddTorrentUseCase,
    private val addMagnetUseCase: AddMagnetUseCase,
    private val getSettingsUseCase: GetSettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTorrentUiState())
    val uiState: StateFlow<AddTorrentUiState> = _uiState.asStateFlow()

    init {
        loadDefaultSavePath()
    }

    private fun loadDefaultSavePath() {
        viewModelScope.launch {
            try {
                val settings = getSettingsUseCase().first()
                if (settings.downloadPath.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(savePath = settings.downloadPath)
                }
            } catch (e: Exception) {
                // Use default path if settings are unavailable
            }
        }
    }

    /**
     * Parses a .torrent file from raw byte data and updates the UI state
     * with the parsed torrent information.
     *
     * @param data the raw bytes of the .torrent file
     */
    fun parseTorrentFile(data: ByteArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val metadata = TorrentParser.parse(data)
                val files = metadata.files.map { file ->
                    TorrentFileInfo(
                        path = file.path,
                        size = file.length,
                        progress = 0f
                    )
                }
                _uiState.value = _uiState.value.copy(
                    torrentName = metadata.name,
                    torrentSize = metadata.totalSize,
                    fileCount = metadata.files.size,
                    files = files,
                    torrentData = data,
                    isLoading = false,
                    mode = AddMode.FILE
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to parse torrent file: ${e.message}"
                )
            }
        }
    }

    /**
     * Sets the magnet link URI and switches to magnet mode.
     *
     * @param uri the magnet link URI string
     */
    fun setMagnetLink(uri: String) {
        _uiState.value = _uiState.value.copy(
            magnetLink = uri,
            mode = AddMode.MAGNET,
            errorMessage = null
        )
    }

    /**
     * Sets the save path for the torrent download.
     *
     * @param path the directory path to save files to
     */
    fun setSavePath(path: String) {
        _uiState.value = _uiState.value.copy(savePath = path)
    }

    /**
     * Sets the current add mode (FILE or MAGNET).
     *
     * @param mode the add mode to switch to
     */
    fun setMode(mode: AddMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    /**
     * Adds the torrent based on the current mode.
     * For FILE mode, uses the parsed torrent data.
     * For MAGNET mode, uses the magnet link URI.
     */
    fun addTorrent() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(isLoading = true, errorMessage = null)

            val result = when (state.mode) {
                AddMode.FILE -> {
                    val data = state.torrentData
                    if (data == null) {
                        _uiState.value = state.copy(
                            isLoading = false,
                            errorMessage = "No torrent file selected"
                        )
                        return@launch
                    }
                    addTorrentUseCase(data, state.savePath)
                }
                AddMode.MAGNET -> {
                    val magnetLink = state.magnetLink
                    if (magnetLink.isBlank()) {
                        _uiState.value = state.copy(
                            isLoading = false,
                            errorMessage = "Please enter a magnet link"
                        )
                        return@launch
                    }
                    if (!magnetLink.startsWith("magnet:")) {
                        _uiState.value = state.copy(
                            isLoading = false,
                            errorMessage = "Invalid magnet link format"
                        )
                        return@launch
                    }
                    addMagnetUseCase(magnetLink, state.savePath)
                }
            }

            when (result) {
                is MinnalResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAdded = true
                    )
                }
                is MinnalResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
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
}
