package com.blocker2.app.accessibility

import java.util.concurrent.atomic.AtomicLong

/**
 * Atomically limits how often an event stream may start an expensive scan.
 * The caller supplies monotonic elapsed-realtime timestamps.
 */
internal class ScanThrottle(initialAcceptedAt: Long = 0L) {
    private val lastAcceptedAt = AtomicLong(initialAcceptedAt)

    fun tryAcquire(now: Long, minimumIntervalMillis: Long): Boolean {
        require(minimumIntervalMillis >= 0L) { "Minimum interval cannot be negative." }

        while (true) {
            val previous = lastAcceptedAt.get()
            if (now < previous || now - previous < minimumIntervalMillis) return false
            if (lastAcceptedAt.compareAndSet(previous, now)) return true
        }
    }
}
