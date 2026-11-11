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

    @Test fun acceptsWellFormedAnswerWithCompressedOwnerName() {
        val query = dnsMessage("example.com", flags = 0x0100)
        val response = dnsMessage("example.com", flags = 0x8180).append(
            byteArrayOf(
                0xc0.toByte(), 0x0c, // owner name points to QNAME
                0x00, 0x01, // A
                0x00, 0x01, // IN
                0x00, 0x00, 0x00, 0x3c, // TTL
                0x00, 0x04, // RDLENGTH
                0x7f, 0x00, 0x00, 0x01 // 127.0.0.1
            )
        ).also { it[7] = 1 }
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
        assertFalse(
            DnsResponseValidator.isMatchingResponse(
                dnsMessage("example.com", flags = 0x0100, type = 1),
                dnsMessage("example.com", flags = 0x8180, type = 28)
            )
        )
    }

    @Test fun rejectsDifferentQuestionClass() {
        val query = dnsMessage("example.com", flags = 0x0100)
        val response = dnsMessage("example.com", flags = 0x8180).also { it[it.lastIndex] = 3 }
        assertFalse(DnsResponseValidator.isMatchingResponse(query, response))
    }

    @Test fun rejectsDifferentOpcode() {
        assertFalse(
            DnsResponseValidator.isMatchingResponse(
                dnsMessage("example.com", flags = 0x0100),
                dnsMessage("example.com", flags = 0x8980)
            )
        )
    }

    @Test fun rejectsCompressedOrMalformedQuestionName() {
        val query = dnsMessage("example.com", flags = 0x0100)
        val compressed = dnsMessage("example.com", flags = 0x8180).also { it[12] = 0xc0.toByte() }
        val malformed = dnsMessage("example.com", flags = 0x8180).also { it[12] = 0x40 }
        assertFalse(DnsResponseValidator.isMatchingResponse(query, compressed))
        assertFalse(DnsResponseValidator.isMatchingResponse(query, malformed))
    }

    @Test fun rejectsResponseWithMultipleQuestions() {
        val query = dnsMessage("example.com", flags = 0x0100)
        val response = dnsMessage("example.com", flags = 0x8180).also { it[5] = 2 }
        assertFalse(DnsResponseValidator.isMatchingResponse(query, response))
    }

    @Test fun rejectsRootOnlyAndOverlongQuestionNames() {
        val rootQuery = dnsMessage("example.com", flags = 0x0100).also { it[12] = 0 }
        val rootResponse = dnsMessage("example.com", flags = 0x8180).also { it[12] = 0 }
        assertFalse(DnsResponseValidator.isMatchingResponse(rootQuery, rootResponse))

        val overlongHost = listOf("a".repeat(63), "b".repeat(63), "c".repeat(63), "d".repeat(63)).joinToString(".")
        assertFalse(
            DnsResponseValidator.isMatchingResponse(
                dnsMessage(overlongHost, flags = 0x0100),
                dnsMessage(overlongHost, flags = 0x8180)
            )
        )
    }

    @Test fun rejectsTruncatedResourceRecordAndDeclaredLengthOverflow() {
        val query = dnsMessage("example.com", flags = 0x0100)
        val truncated = dnsMessage("example.com", flags = 0x8180).append(
            byteArrayOf(0xc0.toByte(), 0x0c, 0x00, 0x01, 0x00, 0x01, 0, 0, 0, 1, 0, 4, 1, 2)
        ).also { it[7] = 1 }
        assertFalse(DnsResponseValidator.isMatchingResponse(query, truncated))
    }

    @Test fun rejectsTrailingUndeclaredBytes() {
        val query = dnsMessage("example.com", flags = 0x0100)
        val response = dnsMessage("example.com", flags = 0x8180).append(byteArrayOf(1, 2, 3))
        assertFalse(DnsResponseValidator.isMatchingResponse(query, response))
    }

    @Test fun rejectsMalformedCompressionPointerInRecordOwner() {
        val query = dnsMessage("example.com", flags = 0x0100)
        val response = dnsMessage("example.com", flags = 0x8180).append(
            byteArrayOf(0xc0.toByte(), 0x0c, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0)
        ).also { it[7] = 1; it[12 + 13 + 4] = 0xc0.toByte(); it[12 + 13 + 5] = 0x40 }
        assertFalse(DnsResponseValidator.isMatchingResponse(query, response))
    }

    @Test fun rejectsShortAndEmptyPackets() {
        assertFalse(DnsResponseValidator.isMatchingResponse(byteArrayOf(), byteArrayOf()))
        assertFalse(DnsResponseValidator.isMatchingResponse(ByteArray(12), ByteArray(12)))
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

    private fun ByteArray.append(suffix: ByteArray): ByteArray = this + suffix
}
