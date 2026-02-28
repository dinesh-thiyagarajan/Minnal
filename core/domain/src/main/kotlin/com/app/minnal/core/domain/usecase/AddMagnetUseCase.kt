package com.app.minnal.core.domain.usecase

import com.app.minnal.core.common.MinnalResult
import com.app.minnal.core.domain.repository.TorrentRepository

/**
 * Use case for adding a torrent from a magnet URI.
 *
 * Delegates to [TorrentRepository.addMagnetLink] to resolve the magnet link
 * and begin the download process.
 *
 * @property repository the torrent repository for performing torrent operations
 */
class AddMagnetUseCase(private val repository: TorrentRepository) {

    /**
     * Adds a torrent from a magnet URI string.
     *
     * @param magnetUri the magnet link URI (e.g., "magnet:?xt=urn:btih:...")
     * @param savePath the directory path where downloaded files should be saved
     * @return a [MinnalResult] containing the torrent's info hash ID on success
     */
    suspend operator fun invoke(magnetUri: String, savePath: String): MinnalResult<String> {
        return repository.addMagnetLink(magnetUri, savePath)
    }
}
