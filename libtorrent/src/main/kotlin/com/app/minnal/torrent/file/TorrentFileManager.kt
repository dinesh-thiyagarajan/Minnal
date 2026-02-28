package com.app.minnal.torrent.file

import com.app.minnal.torrent.model.TorrentFile
import com.app.minnal.torrent.model.TorrentMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Manages file I/O operations for a torrent download.
 *
 * Maps pieces to the correct files (a piece can span multiple files in a
 * multi-file torrent), handles pre-allocation of the directory structure,
 * and provides thread-safe read/write access using [RandomAccessFile].
 *
 * @param metadata the torrent metadata
 * @param savePath the root directory where files will be saved
 */
class TorrentFileManager(
    private val metadata: TorrentMetadata,
    private val savePath: String
) {
    private val mutex = Mutex()
    private val openFiles = mutableMapOf<String, RandomAccessFile>()

    /** The base directory for this torrent's files. */
    private val baseDir: File
        get() = if (metadata.files.size > 1) {
            File(savePath, metadata.name)
        } else {
            File(savePath)
        }

    /**
     * Creates the directory structure and pre-allocates all files to their expected sizes.
     * Existing files are preserved; only missing directories and files are created.
     */
    suspend fun allocateFiles(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            for (file in metadata.files) {
                val targetFile = getFileForTorrentFile(file)
                targetFile.parentFile?.mkdirs()

                if (!targetFile.exists()) {
                    // Create and pre-allocate the file
                    val raf = RandomAccessFile(targetFile, "rw")
                    raf.setLength(file.length)
                    raf.close()
                }
            }
        }
    }

    /**
     * Writes a completed piece's data to the correct file(s).
     *
     * Handles the case where a piece spans multiple files by splitting the
     * data across files at the correct byte offsets.
     *
     * @param pieceIndex the zero-based piece index
     * @param data the piece data to write
     */
    suspend fun writePiece(pieceIndex: Int, data: ByteArray): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pieceOffset = pieceIndex.toLong() * metadata.pieceLength
            val segments = mapPieceToFiles(pieceOffset, data.size.toLong())

            for (segment in segments) {
                val raf = getOrOpenFile(segment.file, "rw")
                raf.seek(segment.fileOffset)
                raf.write(data, segment.dataOffset, segment.length)
            }
        }
    }

    /**
     * Reads a piece's data from the correct file(s) for seeding or verification.
     *
     * @param pieceIndex the zero-based piece index
     * @return the piece data
     */
    suspend fun readPiece(pieceIndex: Int): ByteArray = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pieceOffset = pieceIndex.toLong() * metadata.pieceLength
            val pieceSize = calculatePieceSize(pieceIndex)
            val data = ByteArray(pieceSize)

            val segments = mapPieceToFiles(pieceOffset, pieceSize.toLong())

            for (segment in segments) {
                val raf = getOrOpenFile(segment.file, "r")
                raf.seek(segment.fileOffset)
                raf.readFully(data, segment.dataOffset, segment.length)
            }

            data
        }
    }

    /**
     * Closes all open file handles.
     */
    suspend fun close(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            for ((_, raf) in openFiles) {
                try {
                    raf.close()
                } catch (_: Exception) { }
            }
            openFiles.clear()
        }
    }

    /**
     * Calculates the size of a specific piece.
     */
    private fun calculatePieceSize(pieceIndex: Int): Int {
        val totalPieces = metadata.pieces.size
        return if (pieceIndex == totalPieces - 1) {
            val remaining = (metadata.totalSize % metadata.pieceLength).toInt()
            if (remaining == 0) metadata.pieceLength.toInt() else remaining
        } else {
            metadata.pieceLength.toInt()
        }
    }

    /**
     * Maps a byte range (starting at the given global offset with the given length)
     * to one or more file segments, each specifying which file, the offset within
     * that file, the offset within the piece data, and the number of bytes.
     */
    private fun mapPieceToFiles(globalOffset: Long, length: Long): List<FileSegment> {
        val segments = mutableListOf<FileSegment>()
        var remaining = length
        var currentGlobalOffset = globalOffset
        var dataOffset = 0

        for (file in metadata.files) {
            val fileStart = file.offset
            val fileEnd = file.offset + file.length

            if (currentGlobalOffset >= fileEnd) continue
            if (remaining <= 0) break

            val fileOffset = currentGlobalOffset - fileStart
            val available = file.length - fileOffset
            val toWrite = minOf(remaining, available).toInt()

            segments.add(
                FileSegment(
                    file = file,
                    fileOffset = fileOffset,
                    dataOffset = dataOffset,
                    length = toWrite
                )
            )

            remaining -= toWrite
            currentGlobalOffset += toWrite
            dataOffset += toWrite
        }

        return segments
    }

    /**
     * Gets the actual file path on disk for a torrent file entry.
     */
    private fun getFileForTorrentFile(file: TorrentFile): File {
        return if (metadata.files.size > 1) {
            File(baseDir, file.path)
        } else {
            File(savePath, file.path)
        }
    }

    /**
     * Gets or opens a [RandomAccessFile] for the given torrent file.
     */
    private fun getOrOpenFile(file: TorrentFile, mode: String): RandomAccessFile {
        val path = getFileForTorrentFile(file).absolutePath
        return openFiles.getOrPut(path) {
            RandomAccessFile(getFileForTorrentFile(file), mode)
        }
    }

    /**
     * Represents a segment of data within a single file.
     */
    private data class FileSegment(
        val file: TorrentFile,
        val fileOffset: Long,
        val dataOffset: Int,
        val length: Int
    )
}
