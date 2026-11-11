package com.blocker2.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TimedBlockExpiryTest {

    @Test
    fun calculatesExpiryForPositiveDuration() {
        assertEquals(15_000L, TimedBlockExpiry.calculate(10_000L, 5_000L))
    }

    @Test
    fun zeroDurationKeepsCreationTimestamp() {
        assertEquals(10_000L, TimedBlockExpiry.calculate(10_000L, 0L))
    }

    @Test
    fun saturatesWhenPositiveDurationWouldOverflow() {
        assertEquals(
            Long.MAX_VALUE,
            TimedBlockExpiry.calculate(Long.MAX_VALUE - 5L, 10L)
        )
    }

    @Test
    fun allowsExactLongMaximumBoundary() {
        assertEquals(
            Long.MAX_VALUE,
            TimedBlockExpiry.calculate(Long.MAX_VALUE - 5L, 5L)
        )
    }

    @Test
    fun rejectsNegativeDuration() {
        assertThrows(IllegalArgumentException::class.java) {
            TimedBlockExpiry.calculate(10_000L, -1L)
        }
    }
}
