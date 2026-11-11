package com.blocker2.app.vpn

/**
 * Strict validation for the deliberately limited DNS packet formats accepted
 * by the local VPN. Unsupported or malformed packets must fail open.
 */
internal object IpPacketValidator {
    fun isValidIpv4Header(packet: ByteArray, totalLength: Int): Boolean {
        if (packet.size < IPV4_MIN_HEADER_SIZE || totalLength < IPV4_MIN_HEADER_SIZE ||
            totalLength != packet.size
        ) return false
        if ((packet[0].toInt() ushr 4 and 0x0f) != 4) return false
        val headerLength = (packet[0].toInt() and 0x0f) * 4
        if (headerLength < IPV4_MIN_HEADER_SIZE || headerLength > totalLength) return false
        if (readU16(packet, 2) != totalLength) return false

        // Reject reserved flag, MF, and non-zero fragment offset. DF is allowed.
        val fragmentField = readU16(packet, 6)
        if ((fragmentField and 0x3fff) != 0 || (fragmentField and 0x8000) != 0) return false
        if (IpPacketChecksum.internetChecksum(packet, 0, headerLength) != 0) return false
        return true
    }

    /**
     * The current tunnel parser accepts only UDP immediately after the IPv6 base
     * header. Extension headers (including fragments and AH/ESP) are unsupported
     * and must trigger the service's fail-open path rather than be misparsed.
     */
    fun isDirectIpv6Udp(nextHeader: Int): Boolean =
        nextHeader in 0..255 && nextHeader == IPPROTO_UDP

    /** IPv6 payload length must describe the complete packet, with no trailing bytes. */
    fun isExactIpv6PacketLength(packet: ByteArray, payloadLength: Int): Boolean {
        if (packet.size < IPV6_HEADER_SIZE || payloadLength < 0) return false
        return packet.size.toLong() == IPV6_HEADER_SIZE.toLong() + payloadLength.toLong()
    }

    fun isValidUdpLength(packet: ByteArray, offset: Int, udpLength: Int, enclosingLength: Int): Boolean {
        if (offset < 0 || udpLength < 8 || enclosingLength < udpLength) return false
        if (offset > packet.size - udpLength) return false
        return readU16(packet, offset + 4) == udpLength
    }

    /** IPv4 permits an omitted UDP checksum (zero); non-zero checksums are verified. */
    fun isValidIpv4UdpChecksum(
        packet: ByteArray,
        ipHeaderLength: Int,
        totalLength: Int
    ): Boolean {
        if (ipHeaderLength < IPV4_MIN_HEADER_SIZE || totalLength > packet.size ||
            totalLength < ipHeaderLength + UDP_HEADER_SIZE
        ) return false
        val udpLength = readU16(packet, ipHeaderLength + 4)
        if (udpLength != totalLength - ipHeaderLength) return false
        if (readU16(packet, ipHeaderLength + 6) == 0) return true

        var sum = 0L
        sum = addWords(sum, packet, 12, 8) // IPv4 source and destination addresses.
        sum += IPPROTO_UDP.toLong()
        sum += udpLength.toLong()
        sum = addWords(sum, packet, ipHeaderLength, udpLength)
        return fold(sum) == 0xffff
    }

    /** UDP checksums are mandatory for IPv6 and must validate to all ones. */
    fun isValidIpv6UdpChecksum(
        packet: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Boolean {
        if (packet.size < IPV6_HEADER_SIZE || udpOffset != IPV6_HEADER_SIZE ||
            udpLength < UDP_HEADER_SIZE || udpLength > packet.size - udpOffset
        ) return false
        if (readU16(packet, udpOffset + 6) == 0) return false

        var sum = 0L
        sum = addWords(sum, packet, 8, 32) // IPv6 source and destination addresses.
        sum += ((udpLength ushr 16) and 0xffff).toLong()
        sum += (udpLength and 0xffff).toLong()
        sum += IPPROTO_UDP.toLong()
        sum = addWords(sum, packet, udpOffset, udpLength)
        return fold(sum) == 0xffff
    }

    private fun addWords(initial: Long, bytes: ByteArray, start: Int, length: Int): Long {
        var sum = initial
        var index = start
        val end = start + length
        while (index + 1 < end) {
            sum += (((bytes[index].toInt() and 0xff) shl 8) or
                (bytes[index + 1].toInt() and 0xff)).toLong()
            index += 2
        }
        if (index < end) sum += ((bytes[index].toInt() and 0xff) shl 8).toLong()
        return sum
    }

    private fun fold(value: Long): Int {
        var sum = value
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.toInt() and 0xffff
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private const val IPV4_MIN_HEADER_SIZE = 20
    private const val IPV6_HEADER_SIZE = 40
    private const val UDP_HEADER_SIZE = 8
    private const val IPPROTO_UDP = 17
}
