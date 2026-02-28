package com.app.minnal.core.domain.usecase

import com.app.minnal.core.common.MinnalResult
import com.app.minnal.core.domain.repository.TorrentRepository

/**
 * Use case for resuming a paused torrent.
 *
 * Delegates to [TorrentRepository.resumeTorrent] to restart the torrent's
 * download/upload activity.
 *
 * @property repository the torrent repository for performing torrent operations
 */
class ResumeTorrentUseCase(private val repository: TorrentRepository) {

    /**
     * Resumes the specified paused torrent.
     *
     * @param id the info hash hex string identifying the torrent to resume
     * @return a [MinnalResult] indicating success or failure
     */
    suspend operator fun invoke(id: String): MinnalResult<Unit> {
        return repository.resumeTorrent(id)
    }
}
