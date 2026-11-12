package com.blocker2.app.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionProcessingPolicyTest {
    @Test
    fun acceptedDetectionIsProcessedWithoutAnActiveBlock() {
        assertTrue(DetectionProcessingPolicy.shouldProcess(true, false, false))
    }

    @Test
    fun cooldownSuppressesDuplicateDetectionWithoutAnActiveBlock() {
        assertFalse(DetectionProcessingPolicy.shouldProcess(false, false, true))
    }

    @Test
    fun repeatedTimedBlockMatchRefreshesDuringAnalyticsCooldown() {
        assertTrue(DetectionProcessingPolicy.shouldProcess(false, true, true))
    }

    @Test
    fun nonBlockingMatchDoesNotBypassCooldownForAnActiveBlock() {
        assertFalse(DetectionProcessingPolicy.shouldProcess(false, true, false))
    }
}
