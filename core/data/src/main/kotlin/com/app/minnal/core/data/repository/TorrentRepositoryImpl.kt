package com.app.minnal.core.data.repository

import com.app.minnal.core.common.MinnalResult
import com.app.minnal.core.common.getOrNull
import com.app.minnal.core.data.engine.TorrentEngineWrapper
import com.app.minnal.core.data.local.TorrentEntity
import com.app.minnal.core.data.local.TorrentStorage
import com.app.minnal.core.data.mapper.TorrentMapper
import com.app.minnal.core.domain.model.Torrent
import com.app.minnal.core.domain.repository.TorrentRepository
import com.app.minnal.torrent.parser.TorrentParser
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of [TorrentRepository] that coordinates between the torrent engine
 * for live session management and [TorrentStorage] for persistent storage.
 *
 * When adding a torrent, the raw .torrent file data and metadata are persisted to
 * local storage so torrents can be restored across app restarts. The engine wrapper
 * handles the actual download/upload operations and provides live state through flows.
 *
 * @property engineWrapper the torrent engine wrapper for managing torrent sessions
 * @property storage local file system storage manager for persistence
 */
class TorrentRepositoryImpl(
    private val engineWrapper: TorrentEngineWrapper,
    private val storage: TorrentStorage
) : TorrentRepository {

    override fun getTorrents(): Flow<List<Torrent>> {
        return engineWrapper.torrentsFlow
    }

    override fun getTorrentById(id: String): Flow<Torrent?> {
        return engineWrapper.getTorrentFlow(id)
    }

    override suspend fun addTorrent(torrentData: ByteArray, savePath: String): MinnalResult<String> {
        val effectivePath = savePath.ifEmpty { storage.getDefaultDownloadPath() }
        storage.ensureDirectoryExists(effectivePath)

        val result = engineWrapper.addTorrent(torrentData, effectivePath)
        val id = result.getOrNull()

        if (id != null) {
            try {
                val metadata = TorrentParser.parse(torrentData)
                val torrentFilePath = storage.storeTorrentFile(id, torrentData)
                val entity = TorrentMapper.metadataToEntity(metadata, effectivePath, torrentFilePath)
                storage.saveTorrent(entity)
            } catch (e: Exception) {
                // Persistence failure should not block the add operation
            }
        }

        return result
    }

    override suspend fun addMagnetLink(magnetUri: String, savePath: String): MinnalResult<String> {
        val effectivePath = savePath.ifEmpty { storage.getDefaultDownloadPath() }
        storage.ensureDirectoryExists(effectivePath)

        val result = engineWrapper.addMagnet(magnetUri, effectivePath)
        val id = result.getOrNull()

        if (id != null) {
            try {
                val name = extractNameFromMagnet(magnetUri) ?: "Unknown Torrent"
                val entity = TorrentMapper.magnetToEntity(id, name, effectivePath)
                storage.saveTorrent(entity)
            } catch (e: Exception) {
                // Persistence failure should not block the add operation
            }
        }

        return result
    }

    override suspend fun removeTorrent(id: String, deleteFiles: Boolean): MinnalResult<Unit> {
        if (deleteFiles) {
            val entity = storage.getTorrent(id)
            if (entity != null) {
                storage.deleteTorrentFiles(entity.savePath, entity.name)
            }
        }
        storage.removeTorrent(id)
        return engineWrapper.removeTorrent(id)
    }

    override suspend fun pauseTorrent(id: String): MinnalResult<Unit> {
        return engineWrapper.pauseTorrent(id)
    }

    override suspend fun resumeTorrent(id: String): MinnalResult<Unit> {
        return engineWrapper.resumeTorrent(id)
    }

    override suspend fun pauseAll(): MinnalResult<Unit> {
        return engineWrapper.pauseAll()
    }

    override suspend fun resumeAll(): MinnalResult<Unit> {
        return engineWrapper.resumeAll()
    }

    private fun extractNameFromMagnet(magnetUri: String): String? {
        val regex = Regex("dn=([^&]+)")
        val match = regex.find(magnetUri)
        return match?.groupValues?.get(1)?.replace('+', ' ')
    }
}
