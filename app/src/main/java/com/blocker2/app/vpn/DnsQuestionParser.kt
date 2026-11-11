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
                val questionEnd = offset + DNS_QUESTION_TAIL_SIZE
                if (!hasValidQueryAdditionalRecords(dns, questionEnd)) return null
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

    /**
     * Validate all declared additional records. EDNS0 OPT records are accepted
     * only once, with a root owner name and complete option TLVs.
     */
    private fun hasValidQueryAdditionalRecords(packet: ByteArray, questionEnd: Int): Boolean {
        if (readU16(packet, 6) != 0 || readU16(packet, 8) != 0) return false
        val count = readU16(packet, 10)
        if (count > (packet.size - questionEnd) / MINIMUM_RECORD_BYTES) return false

        var offset = questionEnd
        var optSeen = false
        repeat(count) {
            val ownerStart = offset
            var expandedLength = 0
            var ended = false
            while (offset < packet.size) {
                val length = packet[offset].toInt() and 0xff
                offset++
                if (length == 0) {
                    expandedLength++
                    ended = true
                    break
                }
                if ((length and 0xc0) != 0 || length > MAX_LABEL_LENGTH ||
                    offset + length > packet.size
                ) return false
                expandedLength += length + 1
                if (expandedLength > MAX_NAME_LENGTH) return false
                offset += length
            }
            if (!ended || offset > packet.size - RESOURCE_RECORD_FIXED_BYTES) return false

            val type = readU16(packet, offset)
            val dataLength = readU16(packet, offset + 8)
            val ownerIsRoot = offset - ownerStart == 1 && packet[ownerStart] == 0.toByte()
            offset += RESOURCE_RECORD_FIXED_BYTES
            if (dataLength > packet.size - offset) return false

            if (type == OPT_TYPE) {
                if (optSeen || !ownerIsRoot) return false
                optSeen = true
                val dataEnd = offset + dataLength
                while (offset < dataEnd) {
                    if (offset > dataEnd - EDNS_OPTION_HEADER_BYTES) return false
                    val optionLength = readU16(packet, offset + 2)
                    offset += EDNS_OPTION_HEADER_BYTES
                    if (optionLength > dataEnd - offset) return false
                    offset += optionLength
                }
                if (offset != dataEnd) return false
            } else {
                offset += dataLength
            }
        }
        return offset == packet.size
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private const val DNS_HEADER_SIZE = 12
    private const val DNS_QUESTION_TAIL_SIZE = 4
    private const val MAX_LABEL_LENGTH = 63
    private const val MAX_NAME_LENGTH = 255
    private const val RESOURCE_RECORD_FIXED_BYTES = 10
    private const val MINIMUM_RECORD_BYTES = 11
    private const val EDNS_OPTION_HEADER_BYTES = 4
    private const val OPT_TYPE = 41
    private const val QR_MASK = 0x8000
    private const val OPCODE_MASK = 0x7800
}
