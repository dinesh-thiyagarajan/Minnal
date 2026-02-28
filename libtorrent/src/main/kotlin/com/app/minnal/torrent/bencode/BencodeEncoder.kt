package com.app.minnal.torrent.bencode

import java.io.ByteArrayOutputStream

/**
 * Encodes [BencodeElement] instances into bencoded byte arrays.
 *
 * Follows the bencode specification:
 * - Strings: `<length>:<data>`
 * - Integers: `i<number>e`
 * - Lists: `l<items>e`
 * - Dictionaries: `d<key><value>...e` (keys sorted lexicographically)
 */
object BencodeEncoder {

    /**
     * Encodes a [BencodeElement] to its bencoded byte representation.
     *
     * @param element the element to encode
     * @return the bencoded byte array
     */
    fun encode(element: BencodeElement): ByteArray {
        val output = ByteArrayOutputStream()
        encodeElement(element, output)
        return output.toByteArray()
    }

    private fun encodeElement(element: BencodeElement, output: ByteArrayOutputStream) {
        when (element) {
            is BencodeElement.BencodeString -> encodeString(element, output)
            is BencodeElement.BencodeInteger -> encodeInteger(element, output)
            is BencodeElement.BencodeList -> encodeList(element, output)
            is BencodeElement.BencodeDictionary -> encodeDictionary(element, output)
        }
    }

    /**
     * Encodes a bencode string: <length>:<data>
     */
    private fun encodeString(element: BencodeElement.BencodeString, output: ByteArrayOutputStream) {
        val lengthPrefix = "${element.value.size}:".toByteArray(Charsets.US_ASCII)
        output.write(lengthPrefix)
        output.write(element.value)
    }

    /**
     * Encodes a bencode integer: i<number>e
     */
    private fun encodeInteger(element: BencodeElement.BencodeInteger, output: ByteArrayOutputStream) {
        val encoded = "i${element.value}e".toByteArray(Charsets.US_ASCII)
        output.write(encoded)
    }

    /**
     * Encodes a bencode list: l<items>e
     */
    private fun encodeList(element: BencodeElement.BencodeList, output: ByteArrayOutputStream) {
        output.write('l'.code)
        for (item in element.value) {
            encodeElement(item, output)
        }
        output.write('e'.code)
    }

    /**
     * Encodes a bencode dictionary: d<key><value>...e
     * Keys are sorted in lexicographic byte order as per the bencode spec.
     */
    private fun encodeDictionary(element: BencodeElement.BencodeDictionary, output: ByteArrayOutputStream) {
        output.write('d'.code)
        // Sort keys lexicographically by raw byte value
        val sortedEntries = element.value.entries.sortedBy { it.key }
        for ((key, value) in sortedEntries) {
            // Encode the key as a bencode string
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            encodeString(BencodeElement.BencodeString(keyBytes), output)
            encodeElement(value, output)
        }
        output.write('e'.code)
    }
}
