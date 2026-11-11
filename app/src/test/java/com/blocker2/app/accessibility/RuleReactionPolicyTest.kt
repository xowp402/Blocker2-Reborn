package com.blocker2.app.accessibility

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
}
