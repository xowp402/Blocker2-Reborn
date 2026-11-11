package com.blocker2.app.vpn

/**
 * Strict parser for a single, standard, uncompressed DNS question.
 *
 * The local VPN only handles ordinary UDP DNS queries. Rejecting compression,
 * non-standard opcodes, response packets and malformed label boundaries keeps
 * packet classification deterministic and prevents malformed names reaching
 * the site-rule matcher.
 */
internal object DnsQuestionParser {
    fun readHostname(dns: ByteArray): String? {
        if (dns.size < DNS_HEADER_SIZE + DNS_QUESTION_TAIL_SIZE) return null

        val flags = readU16(dns, 2)
        if ((flags and QR_MASK) != 0) return null
        if ((flags and OPCODE_MASK) != 0) return null
        if (readU16(dns, 4) != 1) return null

        val labels = ArrayList<String>()
        var offset = DNS_HEADER_SIZE
        var totalNameLength = 0

        while (offset < dns.size) {
            val labelLength = dns[offset].toInt() and 0xff
            offset++

            if (labelLength == 0) {
                if (labels.isEmpty()) return null
                if (offset + DNS_QUESTION_TAIL_SIZE > dns.size) return null
                return labels.joinToString(".")
            }

            // Compression and reserved label encodings are deliberately unsupported.
            if (labelLength > MAX_LABEL_LENGTH || (labelLength and 0xc0) != 0) return null
            if (offset + labelLength > dns.size) return null

            totalNameLength += labelLength + 1
            if (totalNameLength > MAX_NAME_LENGTH) return null

            for (index in offset until offset + labelLength) {
                val character = dns[index].toInt() and 0xff
                // DNS wire labels are ASCII here; reject control/non-ASCII bytes.
                if (character !in 33..126) return null
            }

            labels += dns.copyOfRange(offset, offset + labelLength).toString(Charsets.US_ASCII)
            offset += labelLength
        }

        return null
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private const val DNS_HEADER_SIZE = 12
    private const val DNS_QUESTION_TAIL_SIZE = 4
    private const val MAX_LABEL_LENGTH = 63
    private const val MAX_NAME_LENGTH = 255
    private const val QR_MASK = 0x8000
    private const val OPCODE_MASK = 0x7800
}
