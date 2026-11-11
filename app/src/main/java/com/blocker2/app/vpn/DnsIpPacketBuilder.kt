package com.blocker2.app.vpn

/**
 * Builds UDP/IP response packets for DNS replies. Addresses and ports are
 * supplied in request direction; the output reverses them for the response.
 */
internal object DnsIpPacketBuilder {
    fun buildIpv4Response(
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        identification: Int,
        payload: ByteArray
    ): ByteArray {
        require(sourceAddress.size == 4 && destinationAddress.size == 4)
        require(sourcePort in 0..65535 && destinationPort in 0..65535)
        require(identification in 0..65535)
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        require(totalLength <= 65535)

        val packet = ByteArray(totalLength)
        packet[0] = 0x45
        writeU16(packet, 2, totalLength)
        writeU16(packet, 4, identification)
        writeU16(packet, 6, 0x4000) // Don't Fragment
        packet[8] = 64
        packet[9] = IPPROTO_UDP.toByte()
        System.arraycopy(sourceAddress, 0, packet, 12, 4)
        System.arraycopy(destinationAddress, 0, packet, 16, 4)
        writeU16(packet, 10, IpPacketChecksum.internetChecksum(packet, 0, 20))
        writeU16(packet, 20, sourcePort)
        writeU16(packet, 22, destinationPort)
        writeU16(packet, 24, udpLength)
        // A zero UDP checksum is valid for IPv4.
        writeU16(packet, 26, 0)
        System.arraycopy(payload, 0, packet, 28, payload.size)
        return packet
    }

    fun buildIpv6Response(
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray
    ): ByteArray {
        require(sourceAddress.size == 16 && destinationAddress.size == 16)
        require(sourcePort in 0..65535 && destinationPort in 0..65535)
        val udpLength = 8 + payload.size
        require(udpLength <= 65535)

        val packet = ByteArray(40 + udpLength)
        packet[0] = 0x60
        writeU16(packet, 4, udpLength)
        packet[6] = IPPROTO_UDP.toByte()
        packet[7] = 64
        System.arraycopy(sourceAddress, 0, packet, 8, 16)
        System.arraycopy(destinationAddress, 0, packet, 24, 16)
        writeU16(packet, 40, sourcePort)
        writeU16(packet, 42, destinationPort)
        writeU16(packet, 44, udpLength)
        writeU16(packet, 46, 0)
        System.arraycopy(payload, 0, packet, 48, payload.size)
        val checksum = IpPacketChecksum.ipv6UdpChecksum(
            source = sourceAddress,
            destination = destinationAddress,
            udpPacket = packet,
            udpOffset = 40,
            udpLength = udpLength
        )
        writeU16(packet, 46, checksum)
        return packet
    }

    private fun writeU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    private const val IPPROTO_UDP = 17
}
