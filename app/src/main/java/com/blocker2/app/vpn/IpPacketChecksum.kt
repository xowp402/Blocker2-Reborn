package com.blocker2.app.vpn

/**
 * Internet checksum helpers used when synthesizing IPv4 and IPv6 UDP packets.
 * Functions are pure so their arithmetic can be covered by local JVM tests.
 */
internal object IpPacketChecksum {
    fun internetChecksum(data: ByteArray, start: Int = 0, length: Int = data.size): Int {
        require(start >= 0 && length >= 0 && start <= data.size - length) {
            "Checksum range is outside the packet."
        }
        var sum = 0L
        var index = start
        val end = start + length
        while (index + 1 < end) {
            sum += (((data[index].toInt() and 0xff) shl 8) or
                (data[index + 1].toInt() and 0xff)).toLong()
            index += 2
        }
        if (index < end) sum += ((data[index].toInt() and 0xff) shl 8).toLong()
        return foldAndComplement(sum)
    }

    /**
     * Computes the mandatory IPv6 UDP checksum over the IPv6 pseudo-header
     * and the complete UDP segment (the checksum field must be zero initially).
     */
    fun ipv6UdpChecksum(
        source: ByteArray,
        destination: ByteArray,
        udpPacket: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        require(source.size == IPV6_ADDRESS_SIZE && destination.size == IPV6_ADDRESS_SIZE) {
            "IPv6 addresses must contain 16 bytes."
        }
        require(udpOffset >= 0 && udpLength >= UDP_HEADER_SIZE &&
            udpOffset <= udpPacket.size - udpLength) {
            "UDP segment range is invalid."
        }

        var sum = 0L
        sum = addWords(sum, source, 0, source.size)
        sum = addWords(sum, destination, 0, destination.size)
        sum += ((udpLength ushr 16) and 0xffff).toLong()
        sum += (udpLength and 0xffff).toLong()
        sum += IPPROTO_UDP.toLong()
        sum = addWords(sum, udpPacket, udpOffset, udpLength)
        val result = foldAndComplement(sum)
        // A computed zero is transmitted as all ones for UDP over IPv6.
        return if (result == 0) 0xffff else result
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

    private fun foldAndComplement(value: Long): Int {
        var sum = value
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }

    private const val IPV6_ADDRESS_SIZE = 16
    private const val UDP_HEADER_SIZE = 8
    private const val IPPROTO_UDP = 17
}
