package com.blocker2.app.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ScanProcessingGateTest {

    @Test
    fun rejectsSecondProcessingUntilFirstIsReleased() {
        val gate = ScanProcessingGate()
        assertTrue(gate.tryAcquire())
        assertTrue(gate.isProcessing())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertFalse(gate.isProcessing())
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun onlyOneConcurrentCallerAcquiresGate() {
        val gate = ScanProcessingGate()
        val pool = Executors.newFixedThreadPool(12)
        val ready = CountDownLatch(12)
        val start = CountDownLatch(1)
        val accepted = AtomicInteger()
        try {
            val futures = (0 until 12).map {
                pool.submit {
                    ready.countDown()
                    start.await()
                    if (gate.tryAcquire()) accepted.incrementAndGet()
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            futures.forEach { it.get(5, TimeUnit.SECONDS) }
            assertTrue(accepted.get() == 1)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsReleaseWithoutAcquisition() {
        ScanProcessingGate().release()
    }
}
