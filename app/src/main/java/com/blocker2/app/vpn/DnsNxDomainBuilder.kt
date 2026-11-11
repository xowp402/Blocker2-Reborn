package com.blocker2.app.vpn

/**
 * Creates a minimal NXDOMAIN response containing the original DNS question.
 * Optional query additional records (for example EDNS OPT) are intentionally
 * omitted from the response.
 */
internal object DnsNxDomainBuilder {
    fun build(query: ByteArray): ByteArray? {
        if (query.size < 17) return null
        val flags = readU16(query, 2)
        if ((flags and 0x8000) != 0 || (flags and 0x7800) != 0) return null
        if (readU16(query, 4) != 1) return null
        if (DnsQuestionParser.readHostname(query) == null) return null

        var offset = 12
        var totalNameLength = 0
        while (offset < query.size) {
            val length = query[offset].toInt() and 0xff
            offset++
            if (length == 0) break
            if (length > 63 || (length and 0xc0) != 0 || offset + length > query.size) return null
            totalNameLength += length + 1
            if (totalNameLength > 255) return null
            offset += length
        }
        if (offset + 4 > query.size) return null

        val questionEnd = offset + 4
        val response = query.copyOf(questionEnd)
        // QR + RA + NXDOMAIN; echo only RD and CD from the request.
        writeU16(response, 2, (flags and 0x0110) or 0x8083)
        writeU16(response, 4, 1)
        writeU16(response, 6, 0)
        writeU16(response, 8, 0)
        writeU16(response, 10, 0)
        return response
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private fun writeU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }
}
