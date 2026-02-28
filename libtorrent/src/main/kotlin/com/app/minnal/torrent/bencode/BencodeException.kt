package com.app.minnal.torrent.bencode

/**
 * Exception thrown when bencode encoding or decoding fails due to malformed input.
 *
 * @param message description of the error
 * @param cause optional underlying cause
 */
class BencodeException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
