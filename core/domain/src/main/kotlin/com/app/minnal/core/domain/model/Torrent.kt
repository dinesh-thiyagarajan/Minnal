package com.app.minnal.core.domain.model

/**
 * Domain model representing a torrent with its current state and metadata.
 *
 * @property id unique identifier for the torrent, typically the info hash in hexadecimal
 * @property name the display name of the torrent
 * @property totalSize total size of all files in bytes
 * @property downloadedSize number of bytes downloaded so far
 * @property uploadedSize number of bytes uploaded so far
 * @property progress download progress as a value between 0.0 and 1.0
 * @property status current lifecycle state of the torrent
 * @property downloadSpeed current download speed in bytes per second
 * @property uploadSpeed current upload speed in bytes per second
 * @property connectedPeers number of peers currently connected
 * @property totalPeers total number of known peers in the swarm
 * @property savePath directory path where files are being saved
 * @property addedDate timestamp (milliseconds since epoch) when the torrent was added
 * @property eta estimated time remaining in seconds, or -1 if unknown
 * @property files list of individual files contained in the torrent
 */
data class Torrent(
    val id: String,
    val name: String,
    val totalSize: Long,
    val downloadedSize: Long,
    val uploadedSize: Long,
    val progress: Float,
    val status: TorrentStatus,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val connectedPeers: Int,
    val totalPeers: Int,
    val savePath: String,
    val addedDate: Long,
    val eta: Long,
    val files: List<TorrentFileInfo>
)

/**
 * Information about a single file within a torrent.
 *
 * @property path relative file path within the torrent's save directory
 * @property size total size of the file in bytes
 * @property progress download progress of this specific file as a value between 0.0 and 1.0
 */
data class TorrentFileInfo(
    val path: String,
    val size: Long,
    val progress: Float
)
