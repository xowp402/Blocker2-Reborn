package com.blocker2.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IpPacketChecksumTest {
    @Test fun ipv4HeaderChecksumVerifiesToZero() {
        val header = byteArrayOf(
            0x45, 0x00, 0x00, 0x54, 0x00, 0x00, 0x40, 0x00,
            0x40, 0x11, 0x00, 0x00, 0xc0.toByte(), 0xa8.toByte(),
            0x00, 0x01, 0xc0.toByte(), 0xa8.toByte(), 0x00, 0xc7.toByte()
        )
        val checksum = IpPacketChecksum.internetChecksum(header)
        header[10] = (checksum ushr 8).toByte()
        header[11] = checksum.toByte()

        assertEquals(0, IpPacketChecksum.internetChecksum(header))
    }

    @Test fun internetChecksumSupportsOddLengthAndOffset() {
        val data = byteArrayOf(99, 0x01, 0x02, 0x03, 0x04, 0x05, 99)
        val checksum = IpPacketChecksum.internetChecksum(data, start = 1, length = 5)
        data[5] = checksum.toByte()
        data[4] = (checksum ushr 8).toByte()
        assertEquals(0, IpPacketChecksum.internetChecksum(data, start = 1, length = 5))
    }

    @Test fun ipv6UdpChecksumVerifiesPseudoHeaderAndOddPayload() {
        val source = ByteArray(16) { (it + 1).toByte() }
        val destination = ByteArray(16) { (0x20 + it).toByte() }
        val udpLength = 13
        val udp = ByteArray(udpLength)
        udp[0] = 0x14
        udp[1] = 0xe9.toByte()
        udp[2] = 0x00
        udp[3] = 0x35
        udp[4] = 0x00
        udp[5] = udpLength.toByte()
        udp[8] = 0x61
        udp[9] = 0x62
        udp[10] = 0x63
        udp[11] = 0x64
        udp[12] = 0x65

        val checksum = IpPacketChecksum.ipv6UdpChecksum(
            source = source,
            destination = destination,
            udpPacket = udp,
            udpOffset = 0,
            udpLength = udpLength
        )
        udp[6] = (checksum ushr 8).toByte()
        udp[7] = checksum.toByte()

        assertEquals(0xffff, foldedSum(source, destination, udp))
    }

    @Test fun rejectsInvalidRangesAndAddressLengths() {
        assertThrows(IllegalArgumentException::class.java) {
            IpPacketChecksum.internetChecksum(byteArrayOf(1, 2), start = 1, length = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IpPacketChecksum.ipv6UdpChecksum(
                source = ByteArray(4),
                destination = ByteArray(16),
                udpPacket = ByteArray(8),
                udpOffset = 0,
                udpLength = 8
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            IpPacketChecksum.ipv6UdpChecksum(
                source = ByteArray(16),
                destination = ByteArray(16),
                udpPacket = ByteArray(8),
                udpOffset = 2,
                udpLength = 8
            )
        }
    }

    private fun foldedSum(source: ByteArray, destination: ByteArray, udp: ByteArray): Int {
        var sum = 0L
        fun add(bytes: ByteArray) {
            var index = 0
            while (index + 1 < bytes.size) {
                sum += (((bytes[index].toInt() and 0xff) shl 8) or
                    (bytes[index + 1].toInt() and 0xff)).toLong()
                index += 2
            }
            if (index < bytes.size) sum += ((bytes[index].toInt() and 0xff) shl 8).toLong()
        }
        add(source)
        add(destination)
        sum += ((udp.size ushr 16) and 0xffff).toLong()
        sum += (udp.size and 0xffff).toLong()
        sum += 17L
        add(udp)
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.toInt() and 0xffff
    }
}
