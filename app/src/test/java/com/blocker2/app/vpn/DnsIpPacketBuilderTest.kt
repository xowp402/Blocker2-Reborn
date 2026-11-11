package com.blocker2.app.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsIpPacketBuilderTest {
    @Test fun buildsIpv4ResponseWithReversedEndpointsAndValidHeaderChecksum() {
        val client = byteArrayOf(10, 0, 0, 2)
        val resolver = byteArrayOf(1, 1, 1, 1)
        val dns = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0, 1)
        val packet = DnsIpPacketBuilder.buildIpv4Response(
            sourceAddress = resolver,
            destinationAddress = client,
            sourcePort = 53,
            destinationPort = 53000,
            identification = 0x4567,
            payload = dns
        )

        assertEquals(4, (packet[0].toInt() ushr 4) and 0x0f)
        assertEquals(packet.size, readU16(packet, 2))
        assertEquals(0x4567, readU16(packet, 4))
        assertEquals(17, packet[9].toInt() and 0xff)
        assertArrayEquals(resolver, packet.copyOfRange(12, 16))
        assertArrayEquals(client, packet.copyOfRange(16, 20))
        assertEquals(53, readU16(packet, 20))
        assertEquals(53000, readU16(packet, 22))
        assertEquals(8 + dns.size, readU16(packet, 24))
        assertEquals(0, readU16(packet, 26))
        assertArrayEquals(dns, packet.copyOfRange(28, packet.size))
        assertEquals(0, IpPacketChecksum.internetChecksum(packet, 0, 20))
    }

    @Test fun buildsIpv6ResponseWithReversedEndpointsAndValidUdpChecksum() {
        val client = ByteArray(16).also { it[0] = 0x20; it[15] = 2 }
        val resolver = ByteArray(16).also { it[0] = 0x20; it[15] = 1 }
        val dns = byteArrayOf(0x56, 0x78, 0x81.toByte(), 0x80.toByte(), 0, 1, 2)
        val packet = DnsIpPacketBuilder.buildIpv6Response(
            sourceAddress = resolver,
            destinationAddress = client,
            sourcePort = 53,
            destinationPort = 53001,
            payload = dns
        )

        assertEquals(6, (packet[0].toInt() ushr 4) and 0x0f)
        assertEquals(8 + dns.size, readU16(packet, 4))
        assertEquals(17, packet[6].toInt() and 0xff)
        assertArrayEquals(resolver, packet.copyOfRange(8, 24))
        assertArrayEquals(client, packet.copyOfRange(24, 40))
        assertEquals(53, readU16(packet, 40))
        assertEquals(53001, readU16(packet, 42))
        assertEquals(8 + dns.size, readU16(packet, 44))
        assertTrue(IpPacketValidator.isValidIpv6UdpChecksum(packet, 40, 8 + dns.size))
        assertArrayEquals(dns, packet.copyOfRange(48, packet.size))
    }

    @Test fun wrapsNxDomainInValidIpv4UdpResponse() {
        val query = dnsQuery("blocked.example")
        val nxDomain = DnsNxDomainBuilder.build(query)!!
        val client = byteArrayOf(10, 111, 0, 2)
        val resolver = byteArrayOf(10, 111, 0, 1)
        val packet = DnsIpPacketBuilder.buildIpv4Response(
            sourceAddress = resolver,
            destinationAddress = client,
            sourcePort = 53,
            destinationPort = 53000,
            identification = 0x1234,
            payload = nxDomain
        )

        assertTrue(IpPacketValidator.isValidIpv4Header(packet, packet.size))
        assertTrue(IpPacketValidator.isValidUdpLength(packet, 20, 8 + nxDomain.size, packet.size - 20))
        assertArrayEquals(nxDomain, packet.copyOfRange(28, packet.size))
        assertEquals(3, readU16(packet, 30) and 0x000f)
    }

    @Test fun wrapsNxDomainInValidIpv6UdpResponse() {
        val query = dnsQuery("blocked.example")
        val nxDomain = DnsNxDomainBuilder.build(query)!!
        val client = ByteArray(16).also { it[0] = 0xfd.toByte(); it[1] = 0x11; it[15] = 2 }
        val resolver = ByteArray(16).also { it[0] = 0xfd.toByte(); it[1] = 0x11; it[15] = 1 }
        val packet = DnsIpPacketBuilder.buildIpv6Response(
            sourceAddress = resolver,
            destinationAddress = client,
            sourcePort = 53,
            destinationPort = 53000,
            payload = nxDomain
        )

        assertTrue(IpPacketValidator.isExactIpv6PacketLength(packet, readU16(packet, 4)))
        assertTrue(IpPacketValidator.isValidIpv6UdpChecksum(packet, 40, 8 + nxDomain.size))
        assertArrayEquals(nxDomain, packet.copyOfRange(48, packet.size))
        assertEquals(3, readU16(packet, 50) and 0x000f)
    }

    private fun dnsQuery(host: String): ByteArray {
        val encodedName = host.split(".").flatMap { label ->
            listOf(label.length.toByte()) + label.toByteArray(Charsets.US_ASCII).toList()
        }.toByteArray() + byteArrayOf(0)
        return ByteArray(12 + encodedName.size + 4).also { query ->
            query[0] = 0x45
            query[1] = 0x67
            query[2] = 0x01
            query[5] = 1
            System.arraycopy(encodedName, 0, query, 12, encodedName.size)
            val questionEnd = 12 + encodedName.size
            query[questionEnd + 1] = 1
            query[questionEnd + 3] = 1
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidIpv4AddressLength() {
        DnsIpPacketBuilder.buildIpv4Response(
            byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6, 7),
            53, 53000, 1, byteArrayOf(1)
        )
    }

    private fun readU16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xff) shl 8) or (packet[offset + 1].toInt() and 0xff)
}
