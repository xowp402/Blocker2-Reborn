package com.blocker2.app.vpn

/**
 * Validates that a UDP DNS response belongs to the exact question sent.
 * This is deliberately conservative: compressed question names are rejected
 * rather than risking acceptance of a mismatched or malformed response.
 */
internal object DnsResponseValidator {
    fun isMatchingResponse(query: ByteArray, response: ByteArray): Boolean {
        if (query.size < 17 || response.size < 17) return false
        if (readU16(query, 4) != 1 || readU16(response, 4) != 1) return false

        val queryFlags = readU16(query, 2)
        val responseFlags = readU16(response, 2)
        if ((queryFlags and 0x8000) != 0) return false
        if ((responseFlags and 0x8000) == 0) return false
        if ((responseFlags and 0x7800) != (queryFlags and 0x7800)) return false
        if ((responseFlags and 0x0200) != 0) return false

        if (readU16(query, 0) != readU16(response, 0)) return false
        val queryEnd = questionEnd(query) ?: return false
        val responseEnd = questionEnd(response) ?: return false
        val queryNameLength = queryEnd - 12 - 4
        val responseNameLength = responseEnd - 12 - 4
        if (queryNameLength != responseNameLength) return false

        for (index in 12 until queryEnd - 4) {
            val left = query[index].toInt() and 0xff
            val right = response[index].toInt() and 0xff
            val normalizedLeft = if (left in 65..90) left + 32 else left
            val normalizedRight = if (right in 65..90) right + 32 else right
            if (normalizedLeft != normalizedRight) return false
        }
        return readU16(query, queryEnd - 4) == readU16(response, responseEnd - 4) &&
            readU16(query, queryEnd - 2) == readU16(response, responseEnd - 2)
    }

    private fun questionEnd(packet: ByteArray): Int? {
        var offset = 12
        while (offset < packet.size) {
            val length = packet[offset].toInt() and 0xff
            if (length == 0) {
                val end = offset + 1 + 4
                return end.takeIf { end <= packet.size }
            }
            if (length > 63 || (length and 0xc0) != 0 || offset + 1 + length > packet.size) return null
            offset += length + 1
        }
        return null
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)
}
