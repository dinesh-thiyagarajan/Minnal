package com.app.minnal.core.domain.repository

import com.app.minnal.core.common.MinnalResult
import com.app.minnal.core.domain.model.Torrent
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing torrent operations.
 *
 * This defines the contract for torrent lifecycle management including adding, removing,
 * pausing, resuming torrents, and observing their state changes.
 */
interface TorrentRepository {

    /**
     * Observes the list of all torrents with their current state.
     *
     * @return a [Flow] emitting the current list of torrents whenever any torrent's state changes
     */
    fun getTorrents(): Flow<List<Torrent>>

    /**
     * Observes a single torrent's state by its ID.
     *
     * @param id the info hash hex string identifying the torrent
     * @return a [Flow] emitting the torrent's current state, or null if not found
     */
    fun getTorrentById(id: String): Flow<Torrent?>

    /**
     * Adds a new torrent from a .torrent file's raw bytes.
     *
     * @param torrentData the raw bytes of the .torrent file
     * @param savePath the directory path where downloaded files should be saved
     * @return a [MinnalResult] containing the torrent's info hash ID on success
     */
    suspend fun addTorrent(torrentData: ByteArray, savePath: String): MinnalResult<String>

    /**
     * Adds a new torrent from a magnet URI.
     *
     * @param magnetUri the magnet link URI string
     * @param savePath the directory path where downloaded files should be saved
     * @return a [MinnalResult] containing the torrent's info hash ID on success
     */
    suspend fun addMagnetLink(magnetUri: String, savePath: String): MinnalResult<String>

    /**
     * Removes a torrent from the client.
     *
     * @param id the info hash hex string identifying the torrent
     * @param deleteFiles whether to also delete the downloaded files from disk
     * @return a [MinnalResult] indicating success or failure
     */
    suspend fun removeTorrent(id: String, deleteFiles: Boolean): MinnalResult<Unit>

    /**
     * Pauses a currently active torrent.
     *
     * @param id the info hash hex string identifying the torrent
     * @return a [MinnalResult] indicating success or failure
     */
    suspend fun pauseTorrent(id: String): MinnalResult<Unit>

    /**
     * Resumes a paused torrent.
     *
     * @param id the info hash hex string identifying the torrent
     * @return a [MinnalResult] indicating success or failure
     */
    suspend fun resumeTorrent(id: String): MinnalResult<Unit>

    /**
     * Pauses all active torrents.
     *
     * @return a [MinnalResult] indicating success or failure
     */
    suspend fun pauseAll(): MinnalResult<Unit>

    /**
     * Resumes all paused torrents.
     *
     * @return a [MinnalResult] indicating success or failure
     */
    suspend fun resumeAll(): MinnalResult<Unit>
}
