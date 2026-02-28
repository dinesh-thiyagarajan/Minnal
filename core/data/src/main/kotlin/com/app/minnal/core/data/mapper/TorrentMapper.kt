package com.app.minnal.core.data.mapper

import com.app.minnal.core.data.engine.TorrentEngine
import com.app.minnal.core.data.local.TorrentEntity
import com.app.minnal.core.domain.model.Torrent
import com.app.minnal.core.domain.model.TorrentFileInfo
import com.app.minnal.core.domain.model.TorrentStatus
import com.app.minnal.torrent.model.TorrentMetadata

/**
 * Mapping functions for converting between data layer types and domain models.
 *
 * Provides conversions from [TorrentEngine.TorrentSession] to the domain [Torrent] model,
 * from [TorrentMetadata] to [TorrentEntity] for persistence, and status mappings.
 */
object TorrentMapper {

    /**
     * Converts a [TorrentEngine.TorrentSession] into a domain [Torrent] model.
     *
     * @param session the engine's internal torrent session state
     * @return the corresponding domain [Torrent] model
     */
    fun sessionToTorrent(session: TorrentEngine.TorrentSession): Torrent {
        return Torrent(
            id = session.id,
            name = session.name,
            totalSize = session.totalSize,
            downloadedSize = session.downloadedSize,
            uploadedSize = session.uploadedSize,
            progress = session.progress,
            status = session.status,
            downloadSpeed = session.downloadSpeed,
            uploadSpeed = session.uploadSpeed,
            connectedPeers = session.connectedPeers,
            totalPeers = session.totalPeers,
            savePath = session.savePath,
            addedDate = session.addedDate,
            eta = session.eta,
            files = session.files
        )
    }

    /**
     * Creates a [TorrentEntity] for persistence from a [TorrentMetadata] and additional context.
     *
     * @param metadata the parsed torrent metadata
     * @param savePath the directory path where downloaded files are saved
     * @param torrentFilePath the path to the stored .torrent file on disk
     * @return a [TorrentEntity] ready for persistence
     */
    fun metadataToEntity(
        metadata: TorrentMetadata,
        savePath: String,
        torrentFilePath: String
    ): TorrentEntity {
        return TorrentEntity(
            id = metadata.infoHashHex,
            name = metadata.name,
            totalSize = metadata.totalSize,
            savePath = savePath,
            addedDate = System.currentTimeMillis(),
            torrentFilePath = torrentFilePath
        )
    }

    /**
     * Creates a [TorrentEntity] for a magnet-link-based torrent.
     *
     * @param id the info hash hex string extracted from the magnet URI
     * @param name the display name extracted from the magnet URI
     * @param savePath the directory path where downloaded files are saved
     * @return a [TorrentEntity] ready for persistence
     */
    fun magnetToEntity(
        id: String,
        name: String,
        savePath: String
    ): TorrentEntity {
        return TorrentEntity(
            id = id,
            name = name,
            totalSize = 0,
            savePath = savePath,
            addedDate = System.currentTimeMillis(),
            torrentFilePath = ""
        )
    }

    /**
     * Converts a [TorrentEntity] into a domain [Torrent] with default/initial state values.
     * Used when restoring torrents from storage before the engine has populated live state.
     *
     * @param entity the stored torrent entity
     * @return a domain [Torrent] with initial state values
     */
    fun entityToTorrent(entity: TorrentEntity): Torrent {
        return Torrent(
            id = entity.id,
            name = entity.name,
            totalSize = entity.totalSize,
            downloadedSize = 0,
            uploadedSize = 0,
            progress = 0f,
            status = TorrentStatus.STOPPED,
            downloadSpeed = 0,
            uploadSpeed = 0,
            connectedPeers = 0,
            totalPeers = 0,
            savePath = entity.savePath,
            addedDate = entity.addedDate,
            eta = -1,
            files = emptyList()
        )
    }

    /**
     * Converts a list of [TorrentFileInfo] from torrent metadata files.
     *
     * @param files the list of torrent file metadata
     * @return a list of domain [TorrentFileInfo] objects with initial progress
     */
    fun metadataFilesToFileInfo(files: List<com.app.minnal.torrent.model.TorrentFile>): List<TorrentFileInfo> {
        return files.map { file ->
            TorrentFileInfo(
                path = file.path,
                size = file.length,
                progress = 0f
            )
        }
    }
}
