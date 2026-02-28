package com.app.minnal.torrent.bencode

/**
 * Decodes bencoded data from a byte array into [BencodeElement] instances.
 *
 * The decoder maintains an internal index pointer that advances through the byte array
 * as elements are parsed. It supports all four bencode types: strings, integers, lists,
 * and dictionaries.
 */
object BencodeDecoder {

    /**
     * Decodes bencoded data from a byte array.
     *
     * @param data the raw bencoded bytes
     * @return the decoded [BencodeElement]
     * @throws BencodeException if the data is malformed
     */
    fun decode(data: ByteArray): BencodeElement {
        val state = DecoderState(data, 0)
        val result = decodeElement(state)
        return result
    }

    /**
     * Decodes bencoded data and also returns the end position.
     * Useful for extracting raw segments (e.g., the info dictionary for hashing).
     *
     * @param data the raw bencoded bytes
     * @param offset the starting offset in the byte array
     * @return a pair of the decoded element and the position after parsing
     */
    fun decodeWithPosition(data: ByteArray, offset: Int = 0): Pair<BencodeElement, Int> {
        val state = DecoderState(data, offset)
        val result = decodeElement(state)
        return Pair(result, state.index)
    }

    private class DecoderState(val data: ByteArray, var index: Int)

    private fun decodeElement(state: DecoderState): BencodeElement {
        if (state.index >= state.data.size) {
            throw BencodeException("Unexpected end of data at position ${state.index}")
        }

        return when (val byte = state.data[state.index].toInt().toChar()) {
            'i' -> decodeInteger(state)
            'l' -> decodeList(state)
            'd' -> decodeDictionary(state)
            in '0'..'9' -> decodeString(state)
            else -> throw BencodeException(
                "Unexpected character '${byte}' (0x${Integer.toHexString(byte.code)}) at position ${state.index}"
            )
        }
    }

    /**
     * Parses a bencode integer: i<number>e
     * Examples: i42e, i-7e, i0e
     */
    private fun decodeInteger(state: DecoderState): BencodeElement.BencodeInteger {
        // Skip 'i'
        state.index++

        val start = state.index
        // Find the 'e' terminator
        while (state.index < state.data.size && state.data[state.index].toInt().toChar() != 'e') {
            state.index++
        }

        if (state.index >= state.data.size) {
            throw BencodeException("Unterminated integer starting at position $start")
        }

        val numStr = String(state.data, start, state.index - start, Charsets.US_ASCII)

        // Validate: no leading zeros (except for "0" itself), no empty string
        if (numStr.isEmpty()) {
            throw BencodeException("Empty integer at position $start")
        }
        if (numStr.length > 1 && numStr[0] == '0') {
            throw BencodeException("Leading zero in integer '$numStr' at position $start")
        }
        if (numStr.length > 1 && numStr[0] == '-' && numStr[1] == '0') {
            throw BencodeException("Negative zero in integer '$numStr' at position $start")
        }

        val value = try {
            numStr.toLong()
        } catch (e: NumberFormatException) {
            throw BencodeException("Invalid integer '$numStr' at position $start", e)
        }

        // Skip 'e'
        state.index++

        return BencodeElement.BencodeInteger(value)
    }

    /**
     * Parses a bencode string: <length>:<data>
     * The length is a decimal ASCII number, followed by a colon, followed by that many raw bytes.
     */
    private fun decodeString(state: DecoderState): BencodeElement.BencodeString {
        val start = state.index

        // Read the length prefix
        while (state.index < state.data.size && state.data[state.index].toInt().toChar() != ':') {
            val c = state.data[state.index].toInt().toChar()
            if (c !in '0'..'9') {
                throw BencodeException("Invalid character '$c' in string length at position ${state.index}")
            }
            state.index++
        }

        if (state.index >= state.data.size) {
            throw BencodeException("Unterminated string length starting at position $start")
        }

        val lengthStr = String(state.data, start, state.index - start, Charsets.US_ASCII)
        val length = try {
            lengthStr.toInt()
        } catch (e: NumberFormatException) {
            throw BencodeException("Invalid string length '$lengthStr' at position $start", e)
        }

        if (length < 0) {
            throw BencodeException("Negative string length $length at position $start")
        }

        // Skip ':'
        state.index++

        if (state.index + length > state.data.size) {
            throw BencodeException(
                "String length $length exceeds available data at position ${state.index} " +
                        "(${state.data.size - state.index} bytes remaining)"
            )
        }

        val value = state.data.copyOfRange(state.index, state.index + length)
        state.index += length

        return BencodeElement.BencodeString(value)
    }

    /**
     * Parses a bencode list: l<items>e
     */
    private fun decodeList(state: DecoderState): BencodeElement.BencodeList {
        // Skip 'l'
        state.index++

        val items = mutableListOf<BencodeElement>()

        while (state.index < state.data.size && state.data[state.index].toInt().toChar() != 'e') {
            items.add(decodeElement(state))
        }

        if (state.index >= state.data.size) {
            throw BencodeException("Unterminated list")
        }

        // Skip 'e'
        state.index++

        return BencodeElement.BencodeList(items)
    }

    /**
     * Parses a bencode dictionary: d<key><value>...e
     * Keys must be bencode strings, sorted in lexicographic order by the spec
     * (we parse them in whatever order they appear and store in a LinkedHashMap).
     */
    private fun decodeDictionary(state: DecoderState): BencodeElement.BencodeDictionary {
        // Skip 'd'
        state.index++

        val map = LinkedHashMap<String, BencodeElement>()

        while (state.index < state.data.size && state.data[state.index].toInt().toChar() != 'e') {
            // Keys must be strings
            val keyElement = decodeElement(state)
            if (keyElement !is BencodeElement.BencodeString) {
                throw BencodeException("Dictionary key must be a string, got ${keyElement::class.simpleName}")
            }
            val key = keyElement.asString()

            if (state.index >= state.data.size) {
                throw BencodeException("Missing value for dictionary key '$key'")
            }

            val value = decodeElement(state)
            map[key] = value
        }

        if (state.index >= state.data.size) {
            throw BencodeException("Unterminated dictionary")
        }

        // Skip 'e'
        state.index++

        return BencodeElement.BencodeDictionary(map)
    }
}
