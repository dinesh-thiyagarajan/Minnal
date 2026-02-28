package com.app.minnal.torrent.model

import com.app.minnal.torrent.util.HashUtils.toHexString

/**
 * Parsed metadata from a .torrent file.
 *
 * Contains all the information needed to initiate a BitTorrent download:
 * tracker URLs, file information, piece hashes, and the info hash used
 * to identify the torrent in the swarm.
 *
 * @property infoHash 20-byte SHA-1 hash of the bencoded info dictionary
 * @property name the suggested name for the file or root directory
 * @property pieceLength number of bytes per piece
 * @property pieces list of 20-byte SHA-1 hashes, one per piece
 * @property totalSize total size of all files in the torrent in bytes
 * @property files list of files in the torrent
 * @property announceUrls list of tracker announce URLs (primary + announce-list)
 * @property creationDate creation timestamp (Unix epoch), or null if not present
 * @property comment optional comment about the torrent
 * @property createdBy optional string indicating the program that created the torrent
 * @property isPrivate whether the torrent is marked as private (disables DHT/PEX)
 */
data class TorrentMetadata(
    val infoHash: ByteArray,
    val name: String,
    val pieceLength: Long,
    val pieces: List<ByteArray>,
    val totalSize: Long,
    val files: List<TorrentFile>,
    val announceUrls: List<String>,
    val creationDate: Long? = null,
    val comment: String? = null,
    val createdBy: String? = null,
    val isPrivate: Boolean = false
) {
    /**
     * Hexadecimal string representation of the info hash (40 lowercase hex characters).
     */
    val infoHashHex: String
        get() = infoHash.toHexString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TorrentMetadata) return false
        return infoHash.contentEquals(other.infoHash)
    }

    override fun hashCode(): Int = infoHash.contentHashCode()

    override fun toString(): String =
        "TorrentMetadata(name='$name', infoHash=$infoHashHex, totalSize=$totalSize, " +
                "files=${files.size}, pieces=${pieces.size}, trackers=${announceUrls.size})"
}
