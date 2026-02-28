package com.app.minnal.torrent.model

/**
 * Represents a single file within a torrent.
 *
 * In a multi-file torrent, each file has a relative path and a byte offset
 * within the concatenated virtual file data that the torrent describes.
 *
 * @property path relative file path (using '/' as separator)
 * @property length size of the file in bytes
 * @property offset byte offset within the concatenated file data
 */
data class TorrentFile(
    val path: String,
    val length: Long,
    val offset: Long
)
