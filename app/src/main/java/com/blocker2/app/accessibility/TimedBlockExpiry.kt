package com.blocker2.app.accessibility

/** Calculates an expiry timestamp without allowing positive-duration overflow. */
internal object TimedBlockExpiry {
    fun calculate(createdAtMillis: Long, durationMillis: Long): Long {
        require(durationMillis >= 0L) { "Block duration cannot be negative." }
        return if (durationMillis > 0L && createdAtMillis > Long.MAX_VALUE - durationMillis) {
            Long.MAX_VALUE
        } else {
            createdAtMillis + durationMillis
        }
    }
}
