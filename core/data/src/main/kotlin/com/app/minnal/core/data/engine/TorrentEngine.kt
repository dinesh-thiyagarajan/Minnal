package com.app.minnal.core.data.engine

import com.app.minnal.core.domain.model.Torrent
import com.app.minnal.core.domain.model.TorrentFileInfo
import com.app.minnal.core.domain.model.TorrentStatus
import com.app.minnal.torrent.model.TorrentMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Core torrent engine that manages the lifecycle and state of all active torrents.
 *
 * This engine maintains an in-memory map of active torrent sessions and emits
 * state updates through observable flows. It handles adding, removing, pausing,
 * and resuming torrents.
 *
 * @property config engine configuration parameters
 */
class TorrentEngine(private val config: EngineConfig) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _torrents = MutableStateFlow<Map<String, TorrentSession>>(emptyMap())

    /**
     * Observable flow of the current state of all torrent sessions.
     */
    val torrentsFlow: Flow<List<Torrent>>
        get() = _torrents.map { sessions ->
            sessions.values.map { it.toTorrent() }
        }

    /**
     * Returns a flow of a specific torrent's state by its ID.
     *
     * @param id the info hash hex string
     * @return a flow emitting the torrent's state or null if not found
     */
    fun getTorrentFlow(id: String): Flow<Torrent?> {
        return _torrents.map { sessions ->
            sessions[id]?.toTorrent()
        }
    }

    /**
     * Adds a torrent from parsed metadata and begins downloading.
     *
     * @param metadata parsed torrent metadata
     * @param savePath directory to save downloaded files
     * @return the torrent's info hash hex ID
     */
    fun addTorrent(metadata: TorrentMetadata, savePath: String): String {
        val id = metadata.infoHashHex
        val session = TorrentSession(
            id = id,
            name = metadata.name,
            totalSize = metadata.totalSize,
            savePath = savePath,
            files = metadata.files.map { file ->
                TorrentFileInfo(
                    path = file.path,
                    size = file.length,
                    progress = 0f
                )
            },
            metadata = metadata,
            status = TorrentStatus.DOWNLOADING,
            addedDate = System.currentTimeMillis()
        )

        val currentMap = _torrents.value.toMutableMap()
        currentMap[id] = session
        _torrents.value = currentMap

        startDownloadSimulation(id)
        return id
    }

    /**
     * Adds a torrent from a magnet URI.
     *
     * @param magnetUri the magnet link string
     * @param savePath directory to save downloaded files
     * @return the torrent ID extracted from the magnet URI
     */
    fun addMagnet(magnetUri: String, savePath: String): String {
        val id = extractInfoHashFromMagnet(magnetUri)
        val name = extractNameFromMagnet(magnetUri) ?: "Resolving magnet..."

        val session = TorrentSession(
            id = id,
            name = name,
            totalSize = 0,
            savePath = savePath,
            files = emptyList(),
            metadata = null,
            status = TorrentStatus.DOWNLOADING,
            addedDate = System.currentTimeMillis()
        )

        val currentMap = _torrents.value.toMutableMap()
        currentMap[id] = session
        _torrents.value = currentMap

        startDownloadSimulation(id)
        return id
    }

    /**
     * Pauses a torrent by its ID.
     *
     * @param id the torrent's info hash hex ID
     */
    fun pauseTorrent(id: String) {
        updateSession(id) { session ->
            session.copy(
                status = TorrentStatus.PAUSED,
                downloadSpeed = 0,
                uploadSpeed = 0
            )
        }
    }

    /**
     * Resumes a paused torrent.
     *
     * @param id the torrent's info hash hex ID
     */
    fun resumeTorrent(id: String) {
        updateSession(id) { session ->
            if (session.status == TorrentStatus.PAUSED) {
                val newStatus = if (session.progress >= 1.0f) {
                    TorrentStatus.SEEDING
                } else {
                    TorrentStatus.DOWNLOADING
                }
                session.copy(status = newStatus)
            } else {
                session
            }
        }
        startDownloadSimulation(id)
    }

    /**
     * Removes a torrent from the engine.
     *
     * @param id the torrent's info hash hex ID
     */
    fun removeTorrent(id: String) {
        val currentMap = _torrents.value.toMutableMap()
        currentMap.remove(id)
        _torrents.value = currentMap
    }

    /**
     * Pauses all active torrents.
     */
    fun pauseAll() {
        val currentMap = _torrents.value.toMutableMap()
        for ((id, session) in currentMap) {
            if (session.status == TorrentStatus.DOWNLOADING || session.status == TorrentStatus.SEEDING) {
                currentMap[id] = session.copy(
                    status = TorrentStatus.PAUSED,
                    downloadSpeed = 0,
                    uploadSpeed = 0
                )
            }
        }
        _torrents.value = currentMap
    }

    /**
     * Resumes all paused torrents.
     */
    fun resumeAll() {
        val currentMap = _torrents.value.toMutableMap()
        for ((id, session) in currentMap) {
            if (session.status == TorrentStatus.PAUSED) {
                val newStatus = if (session.progress >= 1.0f) {
                    TorrentStatus.SEEDING
                } else {
                    TorrentStatus.DOWNLOADING
                }
                currentMap[id] = session.copy(status = newStatus)
                startDownloadSimulation(id)
            }
        }
        _torrents.value = currentMap
    }

    /**
     * Returns the total download speed across all active torrents.
     */
    fun getTotalDownloadSpeed(): Long {
        return _torrents.value.values.sumOf { it.downloadSpeed }
    }

    /**
     * Returns the count of currently active (downloading) torrents.
     */
    fun getActiveDownloadCount(): Int {
        return _torrents.value.values.count { it.status == TorrentStatus.DOWNLOADING }
    }

    private fun startDownloadSimulation(id: String) {
        scope.launch {
            while (true) {
                val session = _torrents.value[id] ?: break
                if (session.status != TorrentStatus.DOWNLOADING) break

                val downloadIncrement = (50_000L..500_000L).random()
                val newDownloaded = (session.downloadedSize + downloadIncrement)
                    .coerceAtMost(session.totalSize.coerceAtLeast(1))
                val newProgress = if (session.totalSize > 0) {
                    (newDownloaded.toFloat() / session.totalSize).coerceIn(0f, 1f)
                } else {
                    session.progress
                }

                val bytesRemaining = session.totalSize - newDownloaded
                val newEta = if (downloadIncrement > 0 && bytesRemaining > 0) {
                    (bytesRemaining / downloadIncrement).coerceAtLeast(1)
                } else {
                    0L
                }

                val newStatus = if (newProgress >= 1.0f) {
                    TorrentStatus.SEEDING
                } else {
                    TorrentStatus.DOWNLOADING
                }

                updateSession(id) {
                    it.copy(
                        downloadedSize = newDownloaded,
                        progress = newProgress,
                        downloadSpeed = downloadIncrement,
                        uploadSpeed = (10_000L..100_000L).random(),
                        connectedPeers = (1..20).random(),
                        totalPeers = (10..50).random(),
                        eta = newEta,
                        status = newStatus
                    )
                }

                if (newStatus == TorrentStatus.SEEDING) break
                delay(2000)
            }
        }
    }

    private fun updateSession(id: String, update: (TorrentSession) -> TorrentSession) {
        val currentMap = _torrents.value.toMutableMap()
        val session = currentMap[id] ?: return
        currentMap[id] = update(session)
        _torrents.value = currentMap
    }

    private fun extractInfoHashFromMagnet(magnetUri: String): String {
        val regex = Regex("xt=urn:btih:([a-fA-F0-9]{40})")
        val match = regex.find(magnetUri)
        return match?.groupValues?.get(1)?.lowercase() ?: magnetUri.hashCode().toString(16)
    }

    private fun extractNameFromMagnet(magnetUri: String): String? {
        val regex = Regex("dn=([^&]+)")
        val match = regex.find(magnetUri)
        return match?.groupValues?.get(1)?.replace('+', ' ')
    }

    /**
     * Internal representation of a torrent session within the engine.
     */
    data class TorrentSession(
        val id: String,
        val name: String,
        val totalSize: Long,
        val downloadedSize: Long = 0,
        val uploadedSize: Long = 0,
        val progress: Float = 0f,
        val status: TorrentStatus,
        val downloadSpeed: Long = 0,
        val uploadSpeed: Long = 0,
        val connectedPeers: Int = 0,
        val totalPeers: Int = 0,
        val savePath: String,
        val addedDate: Long,
        val eta: Long = -1,
        val files: List<TorrentFileInfo>,
        val metadata: TorrentMetadata?,
        val errorMessage: String? = null
    ) {
        fun toTorrent(): Torrent = Torrent(
            id = id,
            name = name,
            totalSize = totalSize,
            downloadedSize = downloadedSize,
            uploadedSize = uploadedSize,
            progress = progress,
            status = status,
            downloadSpeed = downloadSpeed,
            uploadSpeed = uploadSpeed,
            connectedPeers = connectedPeers,
            totalPeers = totalPeers,
            savePath = savePath,
            addedDate = addedDate,
            eta = eta,
            files = files
        )
    }
}
