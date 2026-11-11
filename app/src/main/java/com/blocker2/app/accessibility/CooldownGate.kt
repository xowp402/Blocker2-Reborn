package com.blocker2.app.accessibility

import java.util.concurrent.ConcurrentHashMap

/** Atomically allows one event per key within a cooldown window. */
internal class CooldownGate {
    private val lastAcceptedAt = ConcurrentHashMap<String, Long>()

    fun tryAcquire(key: String, now: Long, cooldownMillis: Long): Boolean {
        require(key.isNotBlank()) { "Cooldown key cannot be blank." }
        require(cooldownMillis >= 0L) { "Cooldown cannot be negative." }

        var acquired = false
        lastAcceptedAt.compute(key) { _, previous ->
            if (previous == null || hasElapsed(previous, now, cooldownMillis)) {
                acquired = true
                now
            } else {
                previous
            }
        }
        return acquired
    }

    private fun hasElapsed(previous: Long, now: Long, cooldownMillis: Long): Boolean {
        if (now < previous) return false
        if (cooldownMillis == 0L) return true
        // Compare against a guarded deadline instead of subtracting timestamps:
        // now - previous can overflow when timestamps span the Long range.
        if (previous > Long.MAX_VALUE - cooldownMillis) return false
        return now >= previous + cooldownMillis
    }

    fun clear() {
        lastAcceptedAt.clear()
    }
}
