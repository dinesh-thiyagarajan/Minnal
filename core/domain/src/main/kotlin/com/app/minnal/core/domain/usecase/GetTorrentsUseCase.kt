package com.app.minnal.core.domain.usecase

import com.app.minnal.core.domain.model.Torrent
import com.app.minnal.core.domain.repository.TorrentRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for observing the list of all torrents.
 *
 * Provides a reactive stream of the current torrent list that updates
 * whenever any torrent's state changes.
 *
 * @property repository the torrent repository for performing torrent operations
 */
class GetTorrentsUseCase(private val repository: TorrentRepository) {

    /**
     * Observes the list of all torrents with their current state.
     *
     * @return a [Flow] emitting the current list of [Torrent] objects
     */
    operator fun invoke(): Flow<List<Torrent>> {
        return repository.getTorrents()
    }
}
