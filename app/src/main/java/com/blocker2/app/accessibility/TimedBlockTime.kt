package com.blocker2.app.accessibility

/** Overflow-safe helpers for presenting a timed block's remaining duration. */
internal object TimedBlockTime {
    fun remainingMillis(expiresAtMillis: Long, nowMillis: Long): Long {
        if (expiresAtMillis <= nowMillis) return 0L
        val difference = expiresAtMillis - nowMillis
        return if (difference < 0L) Long.MAX_VALUE else difference
    }

    fun ceilMinutes(remainingMillis: Long): Long {
        require(remainingMillis >= 0L) { "Remaining time cannot be negative." }
        val wholeMinutes = remainingMillis / MILLIS_PER_MINUTE
        return if (remainingMillis % MILLIS_PER_MINUTE == 0L) {
            wholeMinutes
        } else {
            wholeMinutes + 1L
        }
    }

    private const val MILLIS_PER_MINUTE = 60_000L
}
