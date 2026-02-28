package com.app.minnal.core.data.local

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * File-based storage for persisting torrent metadata and .torrent file data.
 *
 * Stores data in the app's internal files directory under a `torrents/` subdirectory.
 * Each torrent gets its own subdirectory named by its info hash ID, containing:
 * - `metadata.json`: the serialized [TorrentEntity]
 * - `torrent.file`: the raw .torrent file data
 *
 * Also provides utility methods for managing download directories on external storage.
 *
 * @property context the Android application context for accessing internal storage
 */
class TorrentStorage(private val context: Context) {

    private val torrentsDir: File
        get() = File(context.filesDir, TORRENTS_DIR_NAME).also {
            if (!it.exists()) it.mkdirs()
        }

    /**
     * Returns the default download directory path.
     * Uses the public Downloads/Minnal directory.
     *
     * @return the absolute path to the default download directory
     */
    fun getDefaultDownloadPath(): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val minnalDir = File(downloadsDir, "Minnal")
        if (!minnalDir.exists()) {
            minnalDir.mkdirs()
        }
        return minnalDir.absolutePath
    }

    /**
     * Ensures that the specified directory path exists.
     *
     * @param path the directory path to create
     * @return true if the directory exists or was created successfully
     */
    fun ensureDirectoryExists(path: String): Boolean {
        val dir = File(path)
        return dir.exists() || dir.mkdirs()
    }

    /**
     * Deletes the downloaded files associated with a torrent.
     *
     * @param savePath the directory containing the torrent's files
     * @param torrentName the name of the torrent (file or directory)
     * @return true if deletion was successful
     */
    fun deleteTorrentFiles(savePath: String, torrentName: String): Boolean {
        val file = File(savePath, torrentName)
        return if (file.exists()) {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } else {
            true
        }
    }

    /**
     * Returns the application's internal cache directory for temporary files.
     *
     * @return the absolute path to the cache directory
     */
    fun getCacheDir(): String {
        return context.cacheDir.absolutePath
    }

    /**
     * Persists a [TorrentEntity] to disk as a JSON file.
     *
     * @param entity the torrent entity to save
     */
    fun saveTorrent(entity: TorrentEntity) {
        val torrentDir = getTorrentDir(entity.id)
        torrentDir.mkdirs()
        val metadataFile = File(torrentDir, METADATA_FILE_NAME)
        metadataFile.writeText(entity.toJson())
    }

    /**
     * Removes a torrent's persisted metadata and stored .torrent file from disk.
     *
     * @param id the info hash hex string identifying the torrent to remove
     */
    fun removeTorrent(id: String) {
        val torrentDir = getTorrentDir(id)
        if (torrentDir.exists()) {
            torrentDir.deleteRecursively()
        }
    }

    /**
     * Retrieves all persisted torrent entities from disk.
     *
     * @return a list of all stored [TorrentEntity] objects; entities with
     *         corrupted metadata files are silently skipped
     */
    fun getAllTorrents(): List<TorrentEntity> {
        val dir = torrentsDir
        if (!dir.exists()) return emptyList()

        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { torrentDir ->
                val metadataFile = File(torrentDir, METADATA_FILE_NAME)
                if (metadataFile.exists()) {
                    try {
                        TorrentEntity.fromJson(metadataFile.readText())
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            } ?: emptyList()
    }

    /**
     * Retrieves a single torrent entity by its ID.
     *
     * @param id the info hash hex string identifying the torrent
     * @return the [TorrentEntity] if found and valid, or null otherwise
     */
    fun getTorrent(id: String): TorrentEntity? {
        val metadataFile = File(getTorrentDir(id), METADATA_FILE_NAME)
        if (!metadataFile.exists()) return null

        return try {
            TorrentEntity.fromJson(metadataFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Stores the raw .torrent file data to disk.
     *
     * @param id the info hash hex string identifying the torrent
     * @param data the raw bytes of the .torrent file
     * @return the absolute file path where the data was stored
     */
    fun storeTorrentFile(id: String, data: ByteArray): String {
        val torrentDir = getTorrentDir(id)
        torrentDir.mkdirs()
        val torrentFile = File(torrentDir, TORRENT_DATA_FILE_NAME)
        torrentFile.writeBytes(data)
        return torrentFile.absolutePath
    }

    /**
     * Retrieves the raw .torrent file data from disk.
     *
     * @param id the info hash hex string identifying the torrent
     * @return the raw bytes of the stored .torrent file, or null if not found
     */
    fun getTorrentFileData(id: String): ByteArray? {
        val torrentFile = File(getTorrentDir(id), TORRENT_DATA_FILE_NAME)
        return if (torrentFile.exists()) {
            torrentFile.readBytes()
        } else {
            null
        }
    }

    /**
     * Deletes the stored .torrent file data from disk.
     *
     * @param id the info hash hex string identifying the torrent
     */
    fun deleteTorrentFile(id: String) {
        val torrentFile = File(getTorrentDir(id), TORRENT_DATA_FILE_NAME)
        if (torrentFile.exists()) {
            torrentFile.delete()
        }
    }

    private fun getTorrentDir(id: String): File = File(torrentsDir, id)

    companion object {
        private const val TORRENTS_DIR_NAME = "torrents"
        private const val METADATA_FILE_NAME = "metadata.json"
        private const val TORRENT_DATA_FILE_NAME = "torrent.file"
    }
}
