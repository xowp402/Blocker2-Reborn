package com.blocker2.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ScanThrottleTest {

    @Test
    fun allowsFirstScanAndRejectsEventsInsideInterval() {
        val throttle = ScanThrottle()
        assertTrue(throttle.tryAcquire(1_000L, 350L))
        assertFalse(throttle.tryAcquire(1_349L, 350L))
        assertTrue(throttle.tryAcquire(1_350L, 350L))
    }

    @Test
    fun rejectsTimestampThatMovesBackward() {
        val throttle = ScanThrottle()
        assertTrue(throttle.tryAcquire(2_000L, 350L))
        assertFalse(throttle.tryAcquire(1_900L, 350L))
    }

    @Test
    fun onlyOneConcurrentEventAcquiresTheSameWindow() {
        val throttle = ScanThrottle()
        val pool = Executors.newFixedThreadPool(16)
        val ready = CountDownLatch(16)
        val start = CountDownLatch(1)
        val accepted = AtomicInteger(0)
        try {
            val futures = (0 until 16).map {
                pool.submit {
                    ready.countDown()
                    start.await()
                    if (throttle.tryAcquire(5_000L, 350L)) accepted.incrementAndGet()
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, accepted.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeInterval() {
        ScanThrottle().tryAcquire(1_000L, -1L)
    }
}
