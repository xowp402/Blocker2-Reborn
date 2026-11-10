package com.blocker2.app.core.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    @Test
    fun normalizedWordMatchesWholePersianWord() {
        assertTrue(RuleMatcher.matches("امروز سلام دوست من", "سلام", RuleMatchMode.NORMALIZED_WORD))
    }

    @Test
    fun normalizedWordDoesNotMatchInsideAnotherWord() {
        assertFalse(RuleMatcher.matches("سلامت مهم است", "سلام", RuleMatchMode.NORMALIZED_WORD))
    }

    @Test
    fun normalizedWordMatchesArabicPersianEquivalent() {
        assertTrue(RuleMatcher.matches("كيف علي", "کیف علی", RuleMatchMode.NORMALIZED_WORD))
    }

    @Test
    fun phraseMatchesAcrossHalfSpaceNormalization() {
        assertTrue(RuleMatcher.matches("من می‌روم خانه", "می روم", RuleMatchMode.PHRASE))
    }

    @Test
    fun phraseDoesNotMatchAsPartOfLongerWord() {
        assertFalse(RuleMatcher.matches("پیشسلامت", "سلام", RuleMatchMode.PHRASE))
    }

    @Test
    fun exactRequiresTheWholeNormalizedText() {
        assertTrue(RuleMatcher.matches("سلام دنیا", "سلام دنیا", RuleMatchMode.EXACT))
        assertFalse(RuleMatcher.matches("سلام دنیا امروز", "سلام دنیا", RuleMatchMode.EXACT))
    }

    @Test
    fun containsIsAvailableOnlyAsAnExplicitMode() {
        assertTrue(RuleMatcher.matches("سلامت", "سلام", RuleMatchMode.CONTAINS))
    }

    @Test
    fun emptyRulesNeverMatch() {
        assertFalse(RuleMatcher.matches("سلام", "   ", RuleMatchMode.NORMALIZED_WORD))
    }
}
