package com.app.minnal.core.domain.usecase

import com.app.minnal.core.common.MinnalResult
import com.app.minnal.core.domain.repository.TorrentRepository

/**
 * Use case for removing a torrent from the client.
 *
 * Delegates to [TorrentRepository.removeTorrent] to stop the torrent and
 * optionally delete its downloaded files.
 *
 * @property repository the torrent repository for performing torrent operations
 */
class RemoveTorrentUseCase(private val repository: TorrentRepository) {

    /**
     * Removes the specified torrent.
     *
     * @param id the info hash hex string identifying the torrent to remove
     * @param deleteFiles whether to also delete the downloaded files from disk
     * @return a [MinnalResult] indicating success or failure
     */
    suspend operator fun invoke(id: String, deleteFiles: Boolean): MinnalResult<Unit> {
        return repository.removeTorrent(id, deleteFiles)
    }
}
