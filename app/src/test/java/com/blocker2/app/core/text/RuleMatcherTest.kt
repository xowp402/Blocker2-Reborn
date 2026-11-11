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

    @Test
    fun normalizedWordMatchesAtPunctuationBoundaries() {
        assertTrue(RuleMatcher.matches("(سلام)،", "سلام", RuleMatchMode.NORMALIZED_WORD))
    }

    @Test
    fun normalizedWordDoesNotMatchWhenAdjacentToLatinLettersOrDigits() {
        assertFalse(RuleMatcher.matches("xسلام", "سلام", RuleMatchMode.NORMALIZED_WORD))
        assertFalse(RuleMatcher.matches("سلام2", "سلام", RuleMatchMode.NORMALIZED_WORD))
    }

    @Test
    fun phraseModeTreatsSupplementaryUnicodeLettersAsPartOfWords() {
        val supplementaryLetter = String(Character.toChars(0x10400))
        assertFalse(
            RuleMatcher.matches(
                "${supplementaryLetter}سلام",
                "سلام",
                RuleMatchMode.PHRASE
            )
        )
        assertFalse(
            RuleMatcher.matches(
                "سلام${supplementaryLetter}",
                "سلام",
                RuleMatchMode.PHRASE
            )
        )
    }

    @Test
    fun phraseModeAllowsEmojiAsWordBoundary() {
        assertTrue(RuleMatcher.matches("🙂سلام🙂", "سلام", RuleMatchMode.PHRASE))
    }

    @Test
    fun phraseModeMatchesCaseInsensitiveEnglishText() {
        assertTrue(RuleMatcher.matches("Open YouTube NOW", "open youtube", RuleMatchMode.PHRASE))
    }

    @Test
    fun phraseModeDoesNotMatchInsideLongerUnicodeWord() {
        assertFalse(RuleMatcher.matches("کتابسلامت", "سلام", RuleMatchMode.PHRASE))
    }
}
