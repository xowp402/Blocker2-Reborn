package com.blocker2.app.vpn

/**
 * Validates that a UDP DNS response belongs to the exact question sent and
 * that every declared resource record is fully contained in the message.
 *
 * Question-name compression is rejected intentionally. Compression is
 * accepted for resource-record owner names, with bounds and loop checks.
 */
internal object DnsResponseValidator {
    fun isMatchingResponse(query: ByteArray, response: ByteArray): Boolean {
        if (query.size < DNS_HEADER_SIZE || response.size < DNS_HEADER_SIZE) return false
        if (readU16(query, 4) != 1 || readU16(response, 4) != 1) return false

        val queryFlags = readU16(query, 2)
        val responseFlags = readU16(response, 2)
        if ((queryFlags and QR_MASK) != 0) return false
        if ((responseFlags and QR_MASK) == 0) return false
        if ((responseFlags and OPCODE_MASK) != (queryFlags and OPCODE_MASK)) return false
        if ((responseFlags and TRUNCATED_MASK) != 0) return false
        if (readU16(query, 0) != readU16(response, 0)) return false

        val queryEnd = questionEnd(query) ?: return false
        val responseEnd = questionEnd(response) ?: return false
        if (!sameQuestion(query, queryEnd, response, responseEnd)) return false

        return hasValidRecordSections(response, responseEnd)
    }

    private fun sameQuestion(
        query: ByteArray,
        queryEnd: Int,
        response: ByteArray,
        responseEnd: Int
    ): Boolean {
        val queryNameLength = queryEnd - DNS_HEADER_SIZE - QUESTION_TAIL_SIZE
        val responseNameLength = responseEnd - DNS_HEADER_SIZE - QUESTION_TAIL_SIZE
        if (queryNameLength != responseNameLength) return false

        for (index in DNS_HEADER_SIZE until queryEnd - QUESTION_TAIL_SIZE) {
            val left = query[index].toInt() and 0xff
            val right = response[index].toInt() and 0xff
            val normalizedLeft = if (left in 65..90) left + 32 else left
            val normalizedRight = if (right in 65..90) right + 32 else right
            if (normalizedLeft != normalizedRight) return false
        }
        return readU16(query, queryEnd - 4) == readU16(response, responseEnd - 4) &&
            readU16(query, queryEnd - 2) == readU16(response, responseEnd - 2)
    }

    private fun hasValidRecordSections(packet: ByteArray, questionEnd: Int): Boolean {
        val answerCount = readU16(packet, 6)
        val authorityCount = readU16(packet, 8)
        val additionalCount = readU16(packet, 10)
        val recordCount = answerCount + authorityCount + additionalCount
        // Bound the amount of record parsing by the bytes available.
        if (recordCount > (packet.size - questionEnd) / MINIMUM_RECORD_BYTES) return false

        val optSeen = booleanArrayOf(false)
        var offset = questionEnd
        offset = parseRecordSection(packet, offset, answerCount, isAdditional = false, optSeen = optSeen) ?: return false
        offset = parseRecordSection(packet, offset, authorityCount, isAdditional = false, optSeen) ?: return false
        offset = parseRecordSection(packet, offset, additionalCount, isAdditional = true, optSeen = optSeen) ?: return false
        return offset == packet.size
    }

    private fun parseRecordSection(
        packet: ByteArray,
        start: Int,
        count: Int,
        isAdditional: Boolean,
        optSeen: BooleanArray
    ): Int? {
        var offset = start
        repeat(count) {
            val owner = skipName(packet, offset) ?: return null
            offset = owner.nextOffset
            if (offset > packet.size - RESOURCE_RECORD_FIXED_BYTES) return null

            val type = readU16(packet, offset)
            val dataLength = readU16(packet, offset + 8)
            offset += RESOURCE_RECORD_FIXED_BYTES
            if (dataLength > packet.size - offset) return null

            if (type == OPT_TYPE) {
                // EDNS OPT is a pseudo-record: it belongs only in Additional,
                // has a literal root owner and may occur at most once.
                if (!isAdditional || !owner.isUncompressedRoot || optSeen[0]) return null
                optSeen[0] = true
                val dataEnd = offset + dataLength
                while (offset < dataEnd) {
                    if (offset > dataEnd - EDNS_OPTION_HEADER_BYTES) return null
                    val optionLength = readU16(packet, offset + 2)
                    offset += EDNS_OPTION_HEADER_BYTES
                    if (optionLength > dataEnd - offset) return null
                    offset += optionLength
                }
                if (offset != dataEnd) return null
            } else {
                offset += dataLength
            }
        }
        return offset
    }

    /**
     * Skips a possibly compressed DNS name. Compression pointers must refer
     * backward into the DNS message, as required by the DNS wire format.
     */
    private data class NameInfo(val nextOffset: Int, val isUncompressedRoot: Boolean)\n\n    private fun skipName(packet: ByteArray, start: Int): NameInfo? {
        if (start !in packet.indices) return null
        var offset = start
        var nextOffset: Int? = null
        var expandedLength = 0
        val visitedPointers = HashSet<Int>()

        while (offset < packet.size) {
            val length = packet[offset].toInt() and 0xff
            when {
                length == 0 -> {
                    expandedLength++
                    if (expandedLength > MAX_NAME_LENGTH) return null
                    return NameInfo(\n                        nextOffset ?: (offset + 1),\n                        isUncompressedRoot = offset == start && offset == start && packet[start] == 0.toByte()\n                    )
                }
                (length and 0xc0) == 0xc0 -> {
                    if (offset + 1 >= packet.size) return null
                    val pointer = ((length and 0x3f) shl 8) or
                        (packet[offset + 1].toInt() and 0xff)
                    if (pointer < DNS_HEADER_SIZE || pointer >= offset ||
                        !visitedPointers.add(pointer)
                    ) return null
                    if (nextOffset == null) nextOffset = offset + 2
                    offset = pointer
                }
                (length and 0xc0) != 0 || length > MAX_LABEL_LENGTH -> return null
                else -> {
                    if (offset + 1 + length > packet.size) return null
                    expandedLength += length + 1
                    if (expandedLength > MAX_NAME_LENGTH) return null
                    offset += length + 1
                }
            }
        }
        return null
    }

    /** Parses the one uncompressed question name and its QTYPE/QCLASS. */
    private fun questionEnd(packet: ByteArray): Int? {
        var offset = DNS_HEADER_SIZE
        var totalNameLength = 0
        var hasLabel = false
        while (offset < packet.size) {
            val length = packet[offset].toInt() and 0xff
            if (length == 0) {
                if (!hasLabel) return null
                val end = offset + 1 + QUESTION_TAIL_SIZE
                return end.takeIf { it <= packet.size }
            }
            if (length > MAX_LABEL_LENGTH || (length and 0xc0) != 0 ||
                offset + 1 + length > packet.size
            ) return null
            totalNameLength += length + 1
            if (totalNameLength > MAX_NAME_LENGTH) return null
            hasLabel = true
            offset += length + 1
        }
        return null
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private const val DNS_HEADER_SIZE = 12
    private const val QUESTION_TAIL_SIZE = 4
    private const val RESOURCE_RECORD_FIXED_BYTES = 10\n    private const val MINIMUM_RECORD_BYTES = 11\n    private const val EDNS_OPTION_HEADER_BYTES = 4\n    private const val OPT_TYPE = 41
    private const val MAX_LABEL_LENGTH = 63
    private const val MAX_NAME_LENGTH = 255
    private const val QR_MASK = 0x8000
    private const val OPCODE_MASK = 0x7800
    private const val TRUNCATED_MASK = 0x0200
}
