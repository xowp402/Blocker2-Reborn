package com.blocker2.app.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CooldownGateTest {
    @Test
    fun firstDetectionIsAcceptedAndRepeatedDetectionIsSuppressed() {
        val gate = CooldownGate()
        assertTrue(gate.tryAcquire("telegram:1", 1_000L, 2_000L))
        assertFalse(gate.tryAcquire("telegram:1", 1_500L, 2_000L))
        assertTrue(gate.tryAcquire("telegram:1", 3_000L, 2_000L))
    }

    @Test
    fun differentRulesHaveIndependentCooldowns() {
        val gate = CooldownGate()
        assertTrue(gate.tryAcquire("app:word:1", 100L, 2_000L))
        assertTrue(gate.tryAcquire("app:word:2", 100L, 2_000L))
    }

    @Test
    fun clockMovingBackwardsDoesNotBypassCooldown() {
        val gate = CooldownGate()
        assertTrue(gate.tryAcquire("rule", 10_000L, 2_000L))
        assertFalse(gate.tryAcquire("rule", 9_000L, 2_000L))
    }

    @Test
    fun elapsedTimeComparisonDoesNotOverflowAcrossLongRange() {
        val gate = CooldownGate()
        assertTrue(gate.tryAcquire("extreme", Long.MIN_VALUE, Long.MAX_VALUE))
        assertTrue(gate.tryAcquire("extreme", 0L, Long.MAX_VALUE))
    }

    @Test
    fun cooldownDeadlineOverflowDoesNotAllowEarlyAcquisition() {
        val gate = CooldownGate()
        assertTrue(gate.tryAcquire("near-max", Long.MAX_VALUE - 5L, 10L))
        assertFalse(gate.tryAcquire("near-max", Long.MAX_VALUE, 10L))
    }

    @Test
    fun simultaneousEventsOnlyAcquireOnce() {
        val gate = CooldownGate()
        val workers = 24
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val accepted = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(workers)
        repeat(workers) {
            executor.execute {
                ready.countDown()
                start.await()
                if (gate.tryAcquire("same-rule", 5_000L, 2_000L)) accepted.incrementAndGet()
            }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertTrue(accepted.get() == 1)
    }

    @Test
    fun clearAllowsDetectionAgain() {
        val gate = CooldownGate()
        assertTrue(gate.tryAcquire("rule", 100L, 2_000L))
        gate.clear()
        assertTrue(gate.tryAcquire("rule", 101L, 2_000L))
    }
}
