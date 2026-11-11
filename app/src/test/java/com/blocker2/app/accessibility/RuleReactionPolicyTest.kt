package com.blocker2.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleReactionPolicyTest {

    @Test
    fun warningAndExitStartsTimedBlockWhenDurationIsPositive() {
        val result = RuleReactionPolicy.evaluate("WARN_AND_EXIT", 2_000L, false)
        assertTrue(result.requiresExit)
        assertTrue(result.startsTimedBlock)
    }

    @Test
    fun temporaryBlockRequiresExitAndStartsTimedBlock() {
        val result = RuleReactionPolicy.evaluate("TEMPORARY_BLOCK", 1L, false)
        assertTrue(result.requiresExit)
        assertTrue(result.startsTimedBlock)
    }

    @Test
    fun exitOnlyDoesNotCreateTimedBlock() {
        val result = RuleReactionPolicy.evaluate("EXIT_ONLY", 60_000L, false)
        assertTrue(result.requiresExit)
        assertFalse(result.startsTimedBlock)
    }

    @Test
    fun logOnlyDoesNotExitOrBlock() {
        val result = RuleReactionPolicy.evaluate("LOG_ONLY", 60_000L, false)
        assertFalse(result.requiresExit)
        assertFalse(result.startsTimedBlock)
    }

    @Test
    fun zeroDurationDoesNotStartTimedBlock() {
        val result = RuleReactionPolicy.evaluate("WARN_AND_EXIT", 0L, false)
        assertTrue(result.requiresExit)
        assertFalse(result.startsTimedBlock)
    }

    @Test
    fun onlyFirstMatchingRuleCanStartTimedBlockForSnapshot() {
        val result = RuleReactionPolicy.evaluate("TEMPORARY_BLOCK", 5_000L, true)
        assertTrue(result.requiresExit)
        assertFalse(result.startsTimedBlock)
    }

    @Test
    fun unknownReactionDoesNotTriggerActions() {
        val result = RuleReactionPolicy.evaluate("UNKNOWN", 5_000L, false)
        assertFalse(result.requiresExit)
        assertFalse(result.startsTimedBlock)
    }

    @Test
    fun reactionMatchingIsLocaleIndependentAndCaseInsensitive() {
        val result = RuleReactionPolicy.evaluate("warn_and_exit", 5_000L, false)
        assertTrue(result.requiresExit)
        assertTrue(result.startsTimedBlock)
    }

    @Test
    fun reactionMatchingIgnoresSurroundingWhitespace() {
        val result = RuleReactionPolicy.evaluate("  TEMPORARY_BLOCK  ", 1_000L, false)
        assertTrue(result.requiresExit)
        assertTrue(result.startsTimedBlock)
    }

    @Test
    fun negativeDurationDoesNotStartTimedBlock() {
        val result = RuleReactionPolicy.evaluate("WARN_AND_EXIT", -1L, false)
        assertTrue(result.requiresExit)
        assertFalse(result.startsTimedBlock)
    }
    @Test
    fun normalizedLogOnlyReactionProducesDetectedOutcome() {
        val result = RuleReactionPolicy.evaluate("  log_only  ", 5_000L, false)
        assertFalse(result.requiresExit)
        assertFalse(result.startsTimedBlock)
        assertEquals("DETECTED", result.outcome)
    }

    @Test
    fun exitOnlyReactionProducesExitRequestedOutcome() {
        val result = RuleReactionPolicy.evaluate(" exit_only ", 5_000L, false)
        assertEquals("EXIT_REQUESTED", result.outcome)
    }

    @Test
    fun timedBlockProducesTemporaryBlockOutcome() {
        val result = RuleReactionPolicy.evaluate(" warn_and_exit ", 5_000L, false)
        assertEquals("TEMPORARY_BLOCK_STARTED", result.outcome)
    }

    @Test
    fun warningWithoutPositiveDurationProducesExitRequestedOutcome() {
        val result = RuleReactionPolicy.evaluate("WARN_AND_EXIT", 0L, false)
        assertEquals("EXIT_REQUESTED", result.outcome)
    }

    @Test
    fun unknownReactionProducesDetectedOutcome() {
        val result = RuleReactionPolicy.evaluate(" unknown ", 5_000L, false)
        assertEquals("DETECTED", result.outcome)
    }

}
