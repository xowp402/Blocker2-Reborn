package com.blocker2.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsResponseValidatorTest {
    @Test fun acceptsMatchingResponseIgnoringQuestionCase() {
        val query = dnsMessage("Example.COM", flags = 0x0100)
        val response = dnsMessage("example.com", flags = 0x8180)
        assertTrue(DnsResponseValidator.isMatchingResponse(query, response))
    }

    @Test fun rejectsDifferentTransactionId() {
        val query = dnsMessage("example.com", flags = 0x0100)
        val response = dnsMessage("example.com", flags = 0x8180).also { it[1] = 7 }
        assertFalse(DnsResponseValidator.isMatchingResponse(query, response))
    }

    @Test fun rejectsDifferentQuestionName() {
        assertFalse(
            DnsResponseValidator.isMatchingResponse(
                dnsMessage("example.com", flags = 0x0100),
                dnsMessage("attacker.test", flags = 0x8180)
            )
        )
    }

    @Test fun rejectsTruncatedAndNonResponsePackets() {
        val query = dnsMessage("example.com", flags = 0x0100)
        assertFalse(DnsResponseValidator.isMatchingResponse(query, dnsMessage("example.com", flags = 0x8380)))
        assertFalse(DnsResponseValidator.isMatchingResponse(query, dnsMessage("example.com", flags = 0x0100)))
    }

    @Test fun rejectsDifferentQuestionType() {
        val query = dnsMessage("example.com", flags = 0x0100, type = 1)
        val response = dnsMessage("example.com", flags = 0x8180, type = 28)
        assertFalse(DnsResponseValidator.isMatchingResponse(query, response))
    }

    private fun dnsMessage(host: String, flags: Int, type: Int = 1): ByteArray {
        val name = host.split(".").flatMap { label ->
            listOf(label.length.toByte()) + label.toByteArray(Charsets.US_ASCII).toList()
        }.toByteArray() + byteArrayOf(0)
        val result = ByteArray(12 + name.size + 4)
        result[0] = 0x12
        result[1] = 0x34
        result[2] = (flags ushr 8).toByte()
        result[3] = flags.toByte()
        result[5] = 1
        System.arraycopy(name, 0, result, 12, name.size)
        val end = 12 + name.size
        result[end] = (type ushr 8).toByte()
        result[end + 1] = type.toByte()
        result[end + 2] = 0
        result[end + 3] = 1
        return result
    }
}
