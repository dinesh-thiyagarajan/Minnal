package com.app.minnal.torrent.piece

import com.app.minnal.torrent.model.TorrentMetadata
import com.app.minnal.torrent.util.HashUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages piece tracking, block-level download progress, piece selection,
 * and SHA-1 verification for a torrent download.
 *
 * Tracks which pieces are completed, in progress, or still needed. Supports
 * block-level granularity (16 KB blocks within each piece) for efficient
 * pipelining of requests.
 *
 * @param metadata the torrent metadata containing piece hashes and sizes
 */
class PieceManager(private val metadata: TorrentMetadata) {

    companion object {
        /** Standard block size: 16 KB (16384 bytes). */
        const val BLOCK_SIZE = 16384
    }

    /** Total number of pieces in the torrent. */
    val totalPieces: Int = metadata.pieces.size

    /** Piece states. */
    private enum class PieceState { NEEDED, IN_PROGRESS, DOWNLOADED }

    private val mutex = Mutex()

    /** State of each piece. */
    private val pieceStates = Array(totalPieces) { PieceState.NEEDED }

    /** Accumulated data for pieces being downloaded (pieceIndex -> blockData). */
    private val inProgressData = mutableMapOf<Int, ByteArray>()

    /** Tracks which blocks within an in-progress piece have been received. */
    private val receivedBlocks = mutableMapOf<Int, MutableSet<Int>>() // pieceIndex -> set of block begin offsets

    /** Completed pieces' data, kept in memory until written to disk (pieceIndex -> data). */
    private val completedPieceData = mutableMapOf<Int, ByteArray>()

    /** Availability count for each piece (how many peers have it). Used for rarest-first. */
    private val pieceCounts = IntArray(totalPieces)

    /**
     * Returns the size of a specific piece in bytes.
     * The last piece may be smaller than [TorrentMetadata.pieceLength].
     *
     * @param pieceIndex the zero-based piece index
     * @return size of the piece in bytes
     */
    fun getPieceSize(pieceIndex: Int): Int {
        return if (pieceIndex == totalPieces - 1) {
            val remaining = (metadata.totalSize % metadata.pieceLength).toInt()
            if (remaining == 0) metadata.pieceLength.toInt() else remaining
        } else {
            metadata.pieceLength.toInt()
        }
    }

    /**
     * Selects a piece to download using the rarest-first strategy.
     *
     * Finds pieces that the given peer has (according to their bitfield)
     * that we still need, and returns the one that is least available among
     * all known peers.
     *
     * @param peerBitfield the peer's bitfield indicating which pieces they have
     * @return the index of the selected piece, or null if no suitable piece is found
     */
    suspend fun selectPiece(peerBitfield: ByteArray): Int? = mutex.withLock {
        var bestPiece = -1
        var bestCount = Int.MAX_VALUE

        for (i in 0 until totalPieces) {
            if (pieceStates[i] != PieceState.NEEDED) continue

            // Check if peer has this piece
            val byteIndex = i / 8
            val bitIndex = 7 - (i % 8)
            if (byteIndex >= peerBitfield.size) continue
            if ((peerBitfield[byteIndex].toInt() and (1 shl bitIndex)) == 0) continue

            // Rarest first
            val count = pieceCounts[i]
            if (count < bestCount) {
                bestCount = count
                bestPiece = i
            }
        }

        if (bestPiece >= 0) {
            pieceStates[bestPiece] = PieceState.IN_PROGRESS
            val pieceSize = getPieceSize(bestPiece)
            inProgressData[bestPiece] = ByteArray(pieceSize)
            receivedBlocks[bestPiece] = mutableSetOf()
            bestPiece
        } else {
            null
        }
    }

    /**
     * Returns the needed blocks (begin offset and length) for a piece that is in progress.
     *
     * @param pieceIndex the piece index
     * @param blockSize the desired block size
     * @return list of (begin, length) pairs for blocks that have not yet been received
     */
    suspend fun getNeededBlocks(pieceIndex: Int, blockSize: Int): List<Pair<Int, Int>> = mutex.withLock {
        val received = receivedBlocks[pieceIndex] ?: return@withLock emptyList()
        val pieceSize = getPieceSize(pieceIndex)
        val needed = mutableListOf<Pair<Int, Int>>()

        var offset = 0
        while (offset < pieceSize) {
            if (offset !in received) {
                val length = minOf(blockSize, pieceSize - offset)
                needed.add(Pair(offset, length))
            }
            offset += blockSize
        }

        needed
    }

    /**
     * Adds a received block of data to a piece being downloaded.
     *
     * @param pieceIndex the piece index
     * @param begin the byte offset within the piece
     * @param data the block data
     * @return true if all blocks for this piece have been received, false otherwise
     */
    suspend fun addBlock(pieceIndex: Int, begin: Int, data: ByteArray): Boolean = mutex.withLock {
        val pieceData = inProgressData[pieceIndex] ?: return@withLock false
        val blocks = receivedBlocks[pieceIndex] ?: return@withLock false

        // Copy block data into the piece buffer
        if (begin + data.size > pieceData.size) return@withLock false
        System.arraycopy(data, 0, pieceData, begin, data.size)
        blocks.add(begin)

        // Check if all blocks are received
        val pieceSize = getPieceSize(pieceIndex)
        var offset = 0
        while (offset < pieceSize) {
            if (offset !in blocks) return@withLock false
            offset += BLOCK_SIZE
        }

        // All blocks received
        completedPieceData[pieceIndex] = pieceData
        true
    }

    /**
     * Verifies a completed piece against its expected SHA-1 hash.
     *
     * @param pieceIndex the piece index to verify
     * @return true if the SHA-1 hash matches, false otherwise
     */
    suspend fun verifyPiece(pieceIndex: Int): Boolean = mutex.withLock {
        val data = completedPieceData[pieceIndex] ?: inProgressData[pieceIndex] ?: return@withLock false
        val expectedHash = metadata.pieces[pieceIndex]
        val actualHash = HashUtils.sha1(data)
        actualHash.contentEquals(expectedHash)
    }

    /**
     * Marks a piece as successfully downloaded and verified.
     * Cleans up in-progress tracking data.
     *
     * @param pieceIndex the piece index
     */
    suspend fun markDownloaded(pieceIndex: Int) = mutex.withLock {
        pieceStates[pieceIndex] = PieceState.DOWNLOADED
        inProgressData.remove(pieceIndex)
        receivedBlocks.remove(pieceIndex)
    }

    /**
     * Marks a piece as failed (hash mismatch). Resets it to NEEDED so it can be re-downloaded.
     *
     * @param pieceIndex the piece index
     */
    suspend fun markFailed(pieceIndex: Int) = mutex.withLock {
        pieceStates[pieceIndex] = PieceState.NEEDED
        inProgressData.remove(pieceIndex)
        receivedBlocks.remove(pieceIndex)
        completedPieceData.remove(pieceIndex)
    }

    /**
     * Gets the data for a completed piece (for seeding or writing to disk).
     *
     * @param pieceIndex the piece index
     * @return the piece data, or null if the piece is not available
     */
    suspend fun getPieceData(pieceIndex: Int): ByteArray? = mutex.withLock {
        completedPieceData[pieceIndex]?.copyOf()
    }

    /**
     * Removes the completed piece data from memory (called after writing to disk).
     *
     * @param pieceIndex the piece index
     */
    suspend fun clearPieceData(pieceIndex: Int) = mutex.withLock {
        completedPieceData.remove(pieceIndex)
    }

    /**
     * Stores piece data for seeding (read from disk).
     *
     * @param pieceIndex the piece index
     * @param data the piece data
     */
    suspend fun setPieceData(pieceIndex: Int, data: ByteArray) = mutex.withLock {
        completedPieceData[pieceIndex] = data
    }

    /**
     * Returns the download progress as a float between 0.0 and 1.0.
     */
    suspend fun getProgress(): Float = mutex.withLock {
        if (totalPieces == 0) return@withLock 1.0f
        val downloaded = pieceStates.count { it == PieceState.DOWNLOADED }
        downloaded.toFloat() / totalPieces.toFloat()
    }

    /**
     * Returns true if all pieces have been downloaded.
     */
    suspend fun isComplete(): Boolean = mutex.withLock {
        pieceStates.all { it == PieceState.DOWNLOADED }
    }

    /**
     * Returns our bitfield indicating which pieces we have downloaded.
     *
     * @return byte array bitfield where set bits indicate completed pieces
     */
    fun getOurBitfield(): ByteArray {
        val bitfieldSize = (totalPieces + 7) / 8
        val bitfield = ByteArray(bitfieldSize)
        for (i in 0 until totalPieces) {
            if (pieceStates[i] == PieceState.DOWNLOADED) {
                val byteIndex = i / 8
                val bitIndex = 7 - (i % 8)
                bitfield[byteIndex] = (bitfield[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
        }
        return bitfield
    }

    /**
     * Updates the availability counts based on a peer's bitfield.
     * Called when a peer connects and sends their bitfield or Have messages.
     *
     * @param peerBitfield the peer's bitfield
     */
    suspend fun updateAvailability(peerBitfield: ByteArray) = mutex.withLock {
        for (i in 0 until totalPieces) {
            val byteIndex = i / 8
            val bitIndex = 7 - (i % 8)
            if (byteIndex < peerBitfield.size &&
                (peerBitfield[byteIndex].toInt() and (1 shl bitIndex)) != 0
            ) {
                pieceCounts[i]++
            }
        }
    }

    /**
     * Decrements the availability counts when a peer disconnects.
     *
     * @param peerBitfield the disconnecting peer's bitfield
     */
    suspend fun removeAvailability(peerBitfield: ByteArray) = mutex.withLock {
        for (i in 0 until totalPieces) {
            val byteIndex = i / 8
            val bitIndex = 7 - (i % 8)
            if (byteIndex < peerBitfield.size &&
                (peerBitfield[byteIndex].toInt() and (1 shl bitIndex)) != 0
            ) {
                pieceCounts[i] = (pieceCounts[i] - 1).coerceAtLeast(0)
            }
        }
    }

    /**
     * Marks a piece as already downloaded (e.g., during integrity check on resume).
     *
     * @param pieceIndex the piece index
     */
    suspend fun markAlreadyDownloaded(pieceIndex: Int) = mutex.withLock {
        pieceStates[pieceIndex] = PieceState.DOWNLOADED
    }

    /**
     * Returns the number of downloaded pieces.
     */
    suspend fun downloadedPieceCount(): Int = mutex.withLock {
        pieceStates.count { it == PieceState.DOWNLOADED }
    }

    /**
     * Returns the number of bytes downloaded so far.
     */
    suspend fun downloadedBytes(): Long = mutex.withLock {
        var total = 0L
        for (i in 0 until totalPieces) {
            if (pieceStates[i] == PieceState.DOWNLOADED) {
                total += getPieceSize(i)
            }
        }
        total
    }
}
