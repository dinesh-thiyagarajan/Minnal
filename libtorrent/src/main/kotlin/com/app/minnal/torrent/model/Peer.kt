package com.app.minnal.torrent.model

/**
 * Represents a peer in a BitTorrent swarm.
 *
 * @property ip the IP address of the peer (IPv4 dotted notation or IPv6)
 * @property port the TCP port the peer is listening on
 * @property peerId optional 20-byte peer identifier (may not be known from compact tracker responses)
 */
data class Peer(
    val ip: String,
    val port: Int,
    val peerId: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Peer) return false
        return ip == other.ip && port == other.port
    }

    override fun hashCode(): Int {
        var result = ip.hashCode()
        result = 31 * result + port
        return result
    }

    override fun toString(): String = "Peer($ip:$port)"
}
