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
            if (previous == null || (now >= previous && now - previous >= cooldownMillis)) {
                acquired = true
                now
            } else {
                previous
            }
        }
        return acquired
    }

    fun clear() {
        lastAcceptedAt.clear()
    }
}
