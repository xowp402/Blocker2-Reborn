package com.blocker2.app.vpn

/**
 * Packet-size budget for DNS responses injected into the local TUN interface.
 *
 * The upstream UDP receive buffer bounds DNS payloads to 4096 bytes. The MTU
 * includes the largest supported network header (IPv6) and UDP header, so a
 * response accepted by the socket can be written as one complete TUN packet.
 */
internal object DnsTunnelSizing {
    const val MAX_UPSTREAM_DNS_RESPONSE_BYTES = 4096
    const val UDP_HEADER_BYTES = 8
    const val IPV4_HEADER_BYTES = 20
    const val IPV6_HEADER_BYTES = 40
    const val TUN_MTU =
        MAX_UPSTREAM_DNS_RESPONSE_BYTES + UDP_HEADER_BYTES + IPV6_HEADER_BYTES

    fun packetSize(ipVersion: Int, dnsPayloadBytes: Int): Int {
        require(dnsPayloadBytes in 0..MAX_UPSTREAM_DNS_RESPONSE_BYTES)
        val networkHeaderBytes = when (ipVersion) {
            4 -> IPV4_HEADER_BYTES
            6 -> IPV6_HEADER_BYTES
            else -> throw IllegalArgumentException("Unsupported IP version: $ipVersion")
        }
        return networkHeaderBytes + UDP_HEADER_BYTES + dnsPayloadBytes
    }
}
