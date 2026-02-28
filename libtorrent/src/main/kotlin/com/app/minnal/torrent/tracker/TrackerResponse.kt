package com.app.minnal.torrent.tracker

import com.app.minnal.torrent.model.Peer

/**
 * Represents the parsed response from a tracker announce request.
 *
 * @property interval seconds between regular re-announces to the tracker
 * @property peers list of peers returned by the tracker
 * @property complete number of seeders (peers with complete file)
 * @property incomplete number of leechers (peers currently downloading)
 * @property failureReason if non-null, indicates the tracker returned an error
 */
data class TrackerResponse(
    val interval: Int,
    val peers: List<Peer>,
    val complete: Int = 0,
    val incomplete: Int = 0,
    val failureReason: String? = null
)
