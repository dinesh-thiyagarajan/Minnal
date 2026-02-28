package com.app.minnal.core.data.local

import org.json.JSONObject

/**
 * Persistable data class representing a torrent's stored metadata.
 *
 * This entity is serialized to/from JSON for file-based storage in the app's
 * internal directory. It captures the essential information needed to restore
 * a torrent across app restarts.
 *
 * @property id the info hash hex string uniquely identifying the torrent
 * @property name the display name of the torrent
 * @property totalSize the total size of all files in bytes
 * @property savePath the directory path where downloaded files are saved
 * @property addedDate timestamp (milliseconds since epoch) when the torrent was added
 * @property torrentFilePath the path to the stored .torrent file on disk
 */
data class TorrentEntity(
    val id: String,
    val name: String,
    val totalSize: Long,
    val savePath: String,
    val addedDate: Long,
    val torrentFilePath: String
) {

    /**
     * Serializes this entity to a JSON string for file-based persistence.
     *
     * @return a JSON string representing this entity
     */
    fun toJson(): String {
        val json = JSONObject()
        json.put(KEY_ID, id)
        json.put(KEY_NAME, name)
        json.put(KEY_TOTAL_SIZE, totalSize)
        json.put(KEY_SAVE_PATH, savePath)
        json.put(KEY_ADDED_DATE, addedDate)
        json.put(KEY_TORRENT_FILE_PATH, torrentFilePath)
        return json.toString()
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_TOTAL_SIZE = "totalSize"
        private const val KEY_SAVE_PATH = "savePath"
        private const val KEY_ADDED_DATE = "addedDate"
        private const val KEY_TORRENT_FILE_PATH = "torrentFilePath"

        /**
         * Deserializes a [TorrentEntity] from a JSON string.
         *
         * @param json the JSON string to parse
         * @return the deserialized [TorrentEntity]
         * @throws org.json.JSONException if the JSON is malformed or missing required fields
         */
        fun fromJson(json: String): TorrentEntity {
            val obj = JSONObject(json)
            return TorrentEntity(
                id = obj.getString(KEY_ID),
                name = obj.getString(KEY_NAME),
                totalSize = obj.getLong(KEY_TOTAL_SIZE),
                savePath = obj.getString(KEY_SAVE_PATH),
                addedDate = obj.getLong(KEY_ADDED_DATE),
                torrentFilePath = obj.getString(KEY_TORRENT_FILE_PATH)
            )
        }
    }
}
