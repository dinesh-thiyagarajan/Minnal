package com.app.minnal.core.domain.usecase

import com.app.minnal.core.common.MinnalResult
import com.app.minnal.core.domain.repository.TorrentRepository

/**
 * Use case for adding a torrent from a .torrent file.
 *
 * Delegates to [TorrentRepository.addTorrent] to parse the torrent data
 * and begin the download process.
 *
 * @property repository the torrent repository for performing torrent operations
 */
class AddTorrentUseCase(private val repository: TorrentRepository) {

    /**
     * Adds a torrent from raw .torrent file bytes.
     *
     * @param torrentData the raw bytes of the .torrent file
     * @param savePath the directory path where downloaded files should be saved
     * @return a [MinnalResult] containing the torrent's info hash ID on success
     */
    suspend operator fun invoke(torrentData: ByteArray, savePath: String): MinnalResult<String> {
        return repository.addTorrent(torrentData, savePath)
    }
}
