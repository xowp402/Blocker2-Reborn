package com.blocker2.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsQuestionParserTest {
    @Test fun parsesOrdinaryHostname() {
        assertEquals("www.example.com", DnsQuestionParser.readHostname(query("www.example.com")))
    }

    @Test fun rejectsResponsePacket() {
        assertNull(DnsQuestionParser.readHostname(query("example.com", flags = 0x8180)))
    }

    @Test fun rejectsNonStandardOpcode() {
        assertNull(DnsQuestionParser.readHostname(query("example.com", flags = 0x0900)))
    }

    @Test fun rejectsMultipleOrMissingQuestions() {
        assertNull(DnsQuestionParser.readHostname(query("example.com", questionCount = 2)))
        assertNull(DnsQuestionParser.readHostname(query("example.com", questionCount = 0)))
    }

    @Test fun rejectsCompressedQuestionName() {
        val packet = query("example.com")
        packet[12] = 0xc0.toByte()
        packet[13] = 0x0c
        assertNull(DnsQuestionParser.readHostname(packet))
    }

    @Test fun rejectsLabelLongerThanDnsLimit() {
        val packet = query("example.com")
        packet[12] = 64
        assertNull(DnsQuestionParser.readHostname(packet))
    }

    @Test fun rejectsTruncatedLabelAndMissingQuestionTail() {
        val packet = query("example.com")
        packet[12] = 20
        assertNull(DnsQuestionParser.readHostname(packet))

        val noTail = query("example.com").copyOf(12 + 1 + "example".length + 1)
        assertNull(DnsQuestionParser.readHostname(noTail))
    }

    @Test fun rejectsEmptyRootNameAndControlCharacters() {
        val rootOnly = ByteArray(17).also {
            it[5] = 1
            it[12] = 0
        }
        assertNull(DnsQuestionParser.readHostname(rootOnly))

        val control = query("example.com")
        control[13] = 1
        assertNull(DnsQuestionParser.readHostname(control))
    }

    private fun query(
        hostname: String,
        flags: Int = 0x0100,
        questionCount: Int = 1
    ): ByteArray {
        val encodedName = hostname.split(".").flatMap { label ->
            listOf(label.length.toByte()) + label.toByteArray(Charsets.US_ASCII).toList()
        }.toByteArray() + byteArrayOf(0)
        val packet = ByteArray(12 + encodedName.size + 4)
        packet[0] = 0x12
        packet[1] = 0x34
        packet[2] = (flags ushr 8).toByte()
        packet[3] = flags.toByte()
        packet[5] = questionCount.toByte()
        System.arraycopy(encodedName, 0, packet, 12, encodedName.size)
        val tail = 12 + encodedName.size
        packet[tail + 1] = 1
        packet[tail + 3] = 1
        return packet
    }
}
