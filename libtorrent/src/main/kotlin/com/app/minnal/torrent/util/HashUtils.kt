package com.app.minnal.torrent.util

import java.security.MessageDigest

/**
 * Utility functions for hashing, hex encoding/decoding, and URL encoding
 * used throughout the BitTorrent library.
 */
object HashUtils {

    /**
     * Computes the SHA-1 hash of the given byte array.
     *
     * @param data the input data to hash
     * @return 20-byte SHA-1 hash
     */
    fun sha1(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(data)
    }

    /**
     * Converts a byte array to its lowercase hexadecimal string representation.
     *
     * @return hex string (lowercase, two chars per byte)
     */
    fun ByteArray.toHexString(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val unsigned = b.toInt() and 0xFF
            if (unsigned < 16) sb.append('0')
            sb.append(Integer.toHexString(unsigned))
        }
        return sb.toString()
    }

    /**
     * Converts a hexadecimal string to a byte array.
     *
     * @return byte array decoded from hex
     * @throws IllegalArgumentException if the string has odd length or contains invalid hex chars
     */
    fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length, got $length" }
        return ByteArray(length / 2) { i ->
            val high = Character.digit(this[i * 2], 16)
            val low = Character.digit(this[i * 2 + 1], 16)
            require(high != -1 && low != -1) { "Invalid hex character at position ${i * 2}" }
            ((high shl 4) or low).toByte()
        }
    }

    /**
     * URL-encodes raw bytes for tracker communication.
     *
     * Each byte is encoded as %XX where XX is the uppercase hex representation,
     * except for unreserved characters (letters, digits, '-', '_', '.', '~')
     * which are passed through as-is per RFC 3986.
     *
     * @param bytes the raw bytes to encode
     * @return URL-encoded string
     */
    fun urlEncode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 3)
        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            val c = unsigned.toChar()
            if (c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c)
            } else {
                sb.append('%')
                val hex = Integer.toHexString(unsigned).uppercase()
                if (hex.length == 1) sb.append('0')
                sb.append(hex)
            }
        }
        return sb.toString()
    }
}
