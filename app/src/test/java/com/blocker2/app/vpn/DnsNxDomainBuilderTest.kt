package com.blocker2.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsNxDomainBuilderTest {
    @Test fun buildsNxDomainWithQuestionAndCorrectFlags() {
        val query = query("missing.example", flags = 0x0110)
        val response = DnsNxDomainBuilder.build(query)!!

        assertEquals(query.size, response.size)
        assertEquals(0x8193, readU16(response, 2))
        assertEquals(1, readU16(response, 4))
        assertEquals(0, readU16(response, 6))
        assertEquals(0, readU16(response, 8))
        assertEquals(0, readU16(response, 10))
        assertEquals(1, readU16(response, response.size - 4))
        assertEquals(1, readU16(response, response.size - 2))
        assertTrue((readU16(response, 2) and 0x8000) != 0)
    }

    @Test fun omitsOptionalEdnsAdditionalRecordFromResponse() {
        val base = query("missing.example")
        val withOpt = base + byteArrayOf(0, 0, 41, 4, 0, 0, 0, 0, 0, 0)
        withOpt[11] = 1
        val response = DnsNxDomainBuilder.build(withOpt)!!
        assertEquals(base.size, response.size)
        assertEquals(0, readU16(response, 10))
    }

    @Test fun rejectsResponsePacketsNonstandardOpcodesAndInvalidQuestionCounts() {
        assertNull(DnsNxDomainBuilder.build(query("example.com", flags = 0x8180)))
        assertNull(DnsNxDomainBuilder.build(query("example.com", flags = 0x0900)))
        assertNull(DnsNxDomainBuilder.build(query("example.com", questionCount = 2)))
        assertNull(DnsNxDomainBuilder.build(ByteArray(12)))
    }

    @Test fun rejectsMalformedQuestionAndDoesNotMutateOriginalQuery() {
        val malformed = query("example.com").also { it[12] = 0xc0.toByte() }
        assertNull(DnsNxDomainBuilder.build(malformed))

        val original = query("example.com")
        val snapshot = original.copyOf()
        DnsNxDomainBuilder.build(original)
        assertTrue(original.contentEquals(snapshot))
    }

    private fun query(host: String, flags: Int = 0x0100, questionCount: Int = 1): ByteArray {
        val name = host.split(".").flatMap { label ->
            listOf(label.length.toByte()) + label.toByteArray(Charsets.US_ASCII).toList()
        }.toByteArray() + byteArrayOf(0)
        val result = ByteArray(12 + name.size + 4)
        result[0] = 0x12
        result[1] = 0x34
        result[2] = (flags ushr 8).toByte()
        result[3] = flags.toByte()
        result[5] = questionCount.toByte()
        System.arraycopy(name, 0, result, 12, name.size)
        val tail = 12 + name.size
        result[tail + 1] = 1
        result[tail + 3] = 1
        return result
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)
}
