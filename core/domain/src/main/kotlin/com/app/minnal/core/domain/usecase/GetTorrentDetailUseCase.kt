package com.app.minnal.core.domain.usecase

import com.app.minnal.core.domain.model.Torrent
import com.app.minnal.core.domain.repository.TorrentRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for observing a single torrent's detailed state.
 *
 * Provides a reactive stream of a specific torrent's current state,
 * identified by its info hash ID.
 *
 * @property repository the torrent repository for performing torrent operations
 */
class GetTorrentDetailUseCase(private val repository: TorrentRepository) {

    /**
     * Observes a single torrent's state by its ID.
     *
     * @param id the info hash hex string identifying the torrent
     * @return a [Flow] emitting the torrent's current state, or null if not found
     */
    operator fun invoke(id: String): Flow<Torrent?> {
        return repository.getTorrentById(id)
    }
}
