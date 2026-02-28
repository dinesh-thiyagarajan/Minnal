package com.app.minnal.torrent.bencode

/**
 * Sealed class hierarchy representing bencode data types.
 *
 * Bencode supports four data types: strings (byte arrays), integers, lists, and dictionaries.
 */
sealed class BencodeElement {

    /**
     * A bencode string value, stored as a raw byte array to preserve binary data.
     *
     * @property value the raw byte data
     */
    data class BencodeString(val value: ByteArray) : BencodeElement() {

        /**
         * Decodes the byte array as a UTF-8 string.
         *
         * @return the string representation of the byte data
         */
        fun asString(): String = String(value, Charsets.UTF_8)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BencodeString) return false
            return value.contentEquals(other.value)
        }

        override fun hashCode(): Int = value.contentHashCode()

        override fun toString(): String = "BencodeString(\"${asString()}\")"
    }

    /**
     * A bencode integer value.
     *
     * @property value the integer value (64-bit)
     */
    data class BencodeInteger(val value: Long) : BencodeElement() {
        override fun toString(): String = "BencodeInteger($value)"
    }

    /**
     * A bencode list containing an ordered sequence of bencode elements.
     *
     * @property value the list of elements
     */
    data class BencodeList(val value: List<BencodeElement>) : BencodeElement() {
        override fun toString(): String = "BencodeList($value)"
    }

    /**
     * A bencode dictionary mapping string keys to bencode element values.
     * Preserves insertion order using [LinkedHashMap].
     *
     * @property value the ordered map of key-value pairs
     */
    data class BencodeDictionary(val value: LinkedHashMap<String, BencodeElement>) : BencodeElement() {
        override fun toString(): String = "BencodeDictionary($value)"
    }
}
