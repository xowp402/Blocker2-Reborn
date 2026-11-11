package com.blocker2.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpPacketValidatorTest {
    @Test fun acceptsValidIpv4HeaderAndRejectsBadChecksumOrLength() {
        val packet = ipv4Packet()
        assertTrue(IpPacketValidator.isValidIpv4Header(packet, packet.size))

        val badChecksum = packet.copyOf().also { it[8] = 63 }
        assertFalse(IpPacketValidator.isValidIpv4Header(badChecksum, badChecksum.size))
        assertFalse(IpPacketValidator.isValidIpv4Header(packet, packet.size - 1))
    }

    @Test fun rejectsIpv4TrailingBytesAndRequiresExactIpv6PacketLength() {
        val ipv4 = ipv4Packet()
        val withTrailingByte = ipv4 + byteArrayOf(0)
        assertFalse(IpPacketValidator.isValidIpv4Header(withTrailingByte, ipv4.size))
        assertTrue(IpPacketValidator.isExactIpv6PacketLength(ByteArray(48), 8))
        assertFalse(IpPacketValidator.isExactIpv6PacketLength(ByteArray(49), 8))
        assertFalse(IpPacketValidator.isExactIpv6PacketLength(ByteArray(48), -1))
    }

    @Test fun rejectsIpv4FragmentsAndReservedFlag() {
        for (fragmentField in listOf(0x2000, 0x0001, 0x8000)) {
            val packet = ipv4Packet()
            writeU16(packet, 6, fragmentField)
            refreshIpv4HeaderChecksum(packet)
            assertFalse(IpPacketValidator.isValidIpv4Header(packet, packet.size))
        }
    }

    @Test fun validatesIpv4UdpLengthAndAllowsOmittedChecksum() {
        val packet = ipv4Packet()
        assertTrue(IpPacketValidator.isValidUdpLength(packet, 20, 12, 12))
        assertTrue(IpPacketValidator.isValidIpv4UdpChecksum(packet, 20, packet.size))

        writeU16(packet, 24, 11)
        assertFalse(IpPacketValidator.isValidUdpLength(packet, 20, 12, 12))
        assertFalse(IpPacketValidator.isValidIpv4UdpChecksum(packet, 20, packet.size))
    }

    @Test fun validatesIpv6UdpChecksumAndRejectsZeroOrCorruptChecksum() {
        val packet = ipv6PacketWithValidUdpChecksum()
        assertTrue(IpPacketValidator.isValidIpv6UdpChecksum(packet, 40, 12))

        val zeroChecksum = packet.copyOf().also { writeU16(it, 46, 0) }
        assertFalse(IpPacketValidator.isValidIpv6UdpChecksum(zeroChecksum, 40, 12))

        val corrupted = packet.copyOf().also { it[51] = (it[51].toInt() xor 1).toByte() }
        assertFalse(IpPacketValidator.isValidIpv6UdpChecksum(corrupted, 40, 12))
    }

    private fun ipv4Packet(): ByteArray {
        val packet = ByteArray(32)
        packet[0] = 0x45
        writeU16(packet, 2, packet.size)
        writeU16(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = 17
        packet[12] = 10
        packet[15] = 1
        packet[16] = 10
        packet[19] = 2
        writeU16(packet, 20, 53000)
        writeU16(packet, 22, 53)
        writeU16(packet, 24, 12)
        packet[28] = 1
        packet[29] = 2
        packet[30] = 3
        packet[31] = 4
        refreshIpv4HeaderChecksum(packet)
        return packet
    }

    private fun ipv6PacketWithValidUdpChecksum(): ByteArray {
        val packet = ByteArray(52)
        packet[0] = 0x60
        writeU16(packet, 4, 12)
        packet[6] = 17
        packet[7] = 64
        packet[8] = 0x20
        packet[23] = 1
        packet[24] = 0x20
        packet[39] = 2
        writeU16(packet, 40, 53000)
        writeU16(packet, 42, 53)
        writeU16(packet, 44, 12)
        packet[48] = 1
        packet[49] = 2
        packet[50] = 3
        packet[51] = 4
        val checksum = IpPacketChecksum.ipv6UdpChecksum(
            packet.copyOfRange(8, 24),
            packet.copyOfRange(24, 40),
            packet,
            40,
            12
        )
        writeU16(packet, 46, checksum)
        return packet
    }

    private fun refreshIpv4HeaderChecksum(packet: ByteArray) {
        writeU16(packet, 10, 0)
        writeU16(packet, 10, IpPacketChecksum.internetChecksum(packet, 0, 20))
    }

    private fun writeU16(packet: ByteArray, offset: Int, value: Int) {
        packet[offset] = (value ushr 8).toByte()
        packet[offset + 1] = value.toByte()
    }
}
