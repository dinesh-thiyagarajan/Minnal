package com.app.minnal.core.data.engine

import com.app.minnal.core.common.DispatcherProvider
import com.app.minnal.core.common.MinnalResult
import com.app.minnal.core.domain.model.Torrent
import com.app.minnal.torrent.parser.TorrentParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Wrapper around [TorrentEngine] that provides a safe API with error handling
 * and thread-safe access through the [DispatcherProvider].
 *
 * @property engine the underlying torrent engine
 * @property dispatcherProvider provider for coroutine dispatchers
 */
class TorrentEngineWrapper(
    private val engine: TorrentEngine,
    private val dispatcherProvider: DispatcherProvider
) {

    /**
     * Observable flow of all torrents' current state.
     */
    val torrentsFlow: Flow<List<Torrent>>
        get() = engine.torrentsFlow

    /**
     * Returns a flow of a specific torrent's state.
     *
     * @param id the torrent's info hash hex ID
     * @return flow emitting the torrent state or null
     */
    fun getTorrentFlow(id: String): Flow<Torrent?> = engine.getTorrentFlow(id)

    /**
     * Adds a torrent from raw .torrent file bytes.
     *
     * @param torrentData the raw bytes of the .torrent file
     * @param savePath directory to save downloaded files
     * @return a [MinnalResult] containing the torrent ID on success
     */
    suspend fun addTorrent(torrentData: ByteArray, savePath: String): MinnalResult<String> {
        return withContext(dispatcherProvider.io) {
            try {
                val metadata = TorrentParser.parse(torrentData)
                val id = engine.addTorrent(metadata, savePath)
                MinnalResult.Success(id)
            } catch (e: Exception) {
                MinnalResult.Error("Failed to add torrent: ${e.message}", e)
            }
        }
    }

    /**
     * Adds a torrent from a magnet URI.
     *
     * @param magnetUri the magnet link string
     * @param savePath directory to save downloaded files
     * @return a [MinnalResult] containing the torrent ID on success
     */
    suspend fun addMagnet(magnetUri: String, savePath: String): MinnalResult<String> {
        return withContext(dispatcherProvider.io) {
            try {
                val id = engine.addMagnet(magnetUri, savePath)
                MinnalResult.Success(id)
            } catch (e: Exception) {
                MinnalResult.Error("Failed to add magnet: ${e.message}", e)
            }
        }
    }

    /**
     * Pauses a torrent.
     *
     * @param id the torrent's info hash hex ID
     * @return a [MinnalResult] indicating success or failure
     */
    suspend fun pauseTorrent(id: String): MinnalResult<Unit> {
        return withContext(dispatcherProvider.io) {
            try {
                engine.pauseTorrent(id)
                MinnalResult.Success(Unit)
            } catch (e: Exception) {
                MinnalResult.Error("Failed to pause torrent: ${e.message}", e)
            }
        }
    }

    /**
     * Resumes a paused torrent.
     *
     * @param id the torrent's info hash hex ID
     * @return a [MinnalResult] indicating success or failure
     */
    suspend fun resumeTorrent(id: String): MinnalResult<Unit> {
        return withContext(dispatcherProvider.io) {
            try {
                engine.resumeTorrent(id)
                MinnalResult.Success(Unit)
            } catch (e: Exception) {
                MinnalResult.Error("Failed to resume torrent: ${e.message}", e)
            }
        }
    }

    /**
     * Removes a torrent.
     *
     * @param id the torrent's info hash hex ID
     * @return a [MinnalResult] indicating success or failure
     */
    suspend fun removeTorrent(id: String): MinnalResult<Unit> {
        return withContext(dispatcherProvider.io) {
            try {
                engine.removeTorrent(id)
                MinnalResult.Success(Unit)
            } catch (e: Exception) {
                MinnalResult.Error("Failed to remove torrent: ${e.message}", e)
            }
        }
    }

    /**
     * Pauses all active torrents.
     *
     * @return a [MinnalResult] indicating success or failure
     */
    suspend fun pauseAll(): MinnalResult<Unit> {
        return withContext(dispatcherProvider.io) {
            try {
                engine.pauseAll()
                MinnalResult.Success(Unit)
            } catch (e: Exception) {
                MinnalResult.Error("Failed to pause all torrents: ${e.message}", e)
            }
        }
    }

    /**
     * Resumes all paused torrents.
     *
     * @return a [MinnalResult] indicating success or failure
     */
    suspend fun resumeAll(): MinnalResult<Unit> {
        return withContext(dispatcherProvider.io) {
            try {
                engine.resumeAll()
                MinnalResult.Success(Unit)
            } catch (e: Exception) {
                MinnalResult.Error("Failed to resume all torrents: ${e.message}", e)
            }
        }
    }

    /**
     * Returns the total download speed across all active torrents.
     */
    fun getTotalDownloadSpeed(): Long = engine.getTotalDownloadSpeed()

    /**
     * Returns the count of currently active (downloading) torrents.
     */
    fun getActiveDownloadCount(): Int = engine.getActiveDownloadCount()
}
