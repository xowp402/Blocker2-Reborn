package com.blocker2.app.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramRuleMatcherTest {
    @Test
    fun wordRuleUsesNormalizedWholeWordMatching() {
        assertTrue(TelegramRuleMatcher.matches("امروز سلام", "WORD", "سلام"))
        assertFalse(TelegramRuleMatcher.matches("سلامت", "WORD", "سلام"))
    }

    @Test
    fun phraseRuleRequiresPhraseBoundaries() {
        assertTrue(TelegramRuleMatcher.matches("این کانال خبر فوری دارد", "PHRASE", "خبر فوری"))
        assertFalse(TelegramRuleMatcher.matches("پیشخبر فوریه", "PHRASE", "خبر فوری"))
    }

    @Test
    fun channelIdDoesNotMatchAsPartOfAnotherNumber() {
        assertTrue(TelegramRuleMatcher.matches("channel id: 12345", "CHANNEL_ID", "12345"))
        assertFalse(TelegramRuleMatcher.matches("9123456", "CHANNEL_ID", "12345"))
    }

    @Test
    fun usernameMatchesWithOrWithoutAtSignButNotAsPrefix() {
        assertTrue(TelegramRuleMatcher.matches("کانال @news منتشر کرد", "USERNAME", "news"))
        assertTrue(TelegramRuleMatcher.matches("کانال news منتشر کرد", "USERNAME", "@news"))
        assertFalse(TelegramRuleMatcher.matches("کانال @newsroom منتشر کرد", "USERNAME", "news"))
    }

    @Test
    fun channelUrlMustNotMatchInsideLongerUrlText() {
        assertTrue(TelegramRuleMatcher.matches("https://t.me/example_channel?start=1", "CHANNEL_URL", "https://t.me/example_channel"))
        assertFalse(TelegramRuleMatcher.matches("https://t.me/example_channel_extra", "CHANNEL_URL", "https://t.me/example_channel"))
    }

    @Test
    fun rejectsBlankAndUnsupportedRules() {
        assertFalse(TelegramRuleMatcher.matches("متن", "USERNAME", " "))
        assertFalse(TelegramRuleMatcher.matches("متن", "UNKNOWN", "متن"))
    }
}