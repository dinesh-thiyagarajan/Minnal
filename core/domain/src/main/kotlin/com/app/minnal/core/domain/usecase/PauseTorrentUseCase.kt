package com.app.minnal.core.domain.usecase

import com.app.minnal.core.common.MinnalResult
import com.app.minnal.core.domain.repository.TorrentRepository

/**
 * Use case for pausing a currently active torrent.
 *
 * Delegates to [TorrentRepository.pauseTorrent] to suspend the torrent's
 * download/upload activity.
 *
 * @property repository the torrent repository for performing torrent operations
 */
class PauseTorrentUseCase(private val repository: TorrentRepository) {

    /**
     * Pauses the specified torrent.
     *
     * @param id the info hash hex string identifying the torrent to pause
     * @return a [MinnalResult] indicating success or failure
     */
    suspend operator fun invoke(id: String): MinnalResult<Unit> {
        return repository.pauseTorrent(id)
    }
}
