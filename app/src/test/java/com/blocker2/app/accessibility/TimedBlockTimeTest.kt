package com.blocker2.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TimedBlockTimeTest {

    @Test
    fun remainingTimeIsPositiveBeforeExpiry() {
        assertEquals(5_000L, TimedBlockTime.remainingMillis(15_000L, 10_000L))
    }

    @Test
    fun remainingTimeIsZeroAtOrAfterExpiry() {
        assertEquals(0L, TimedBlockTime.remainingMillis(10_000L, 10_000L))
        assertEquals(0L, TimedBlockTime.remainingMillis(9_000L, 10_000L))
    }

    @Test
    fun remainingTimeSaturatesWhenSubtractionWouldOverflow() {
        assertEquals(
            Long.MAX_VALUE,
            TimedBlockTime.remainingMillis(Long.MAX_VALUE, Long.MIN_VALUE)
        )
    }

    @Test
    fun minuteCeilingRoundsUpWithoutAdditionOverflow() {
        assertEquals(1L, TimedBlockTime.ceilMinutes(1L))
        assertEquals(1L, TimedBlockTime.ceilMinutes(60_000L))
        assertEquals(2L, TimedBlockTime.ceilMinutes(60_001L))
        assertEquals(Long.MAX_VALUE / 60_000L + 1L, TimedBlockTime.ceilMinutes(Long.MAX_VALUE))
    }

    @Test
    fun zeroRemainingTimeHasZeroMinutes() {
        assertEquals(0L, TimedBlockTime.ceilMinutes(0L))
    }

    @Test
    fun negativeRemainingTimeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TimedBlockTime.ceilMinutes(-1L)
        }
    }
}
