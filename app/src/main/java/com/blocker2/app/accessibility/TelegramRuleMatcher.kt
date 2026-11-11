package com.blocker2.app.accessibility

import com.blocker2.app.core.text.RuleMatchMode
import com.blocker2.app.core.text.RuleMatcher

/** Applies type-specific Telegram matching without accidental substring matches. */
internal object TelegramRuleMatcher {
    fun matches(snapshot: String, ruleType: String, ruleValue: String): Boolean {
        if (snapshot.isBlank() || ruleValue.isBlank()) return false

        return when (ruleType.uppercase()) {
            "WORD" -> RuleMatcher.matches(snapshot, ruleValue, RuleMatchMode.NORMALIZED_WORD)
            "PHRASE", "CHANNEL_ID" ->
                RuleMatcher.matches(snapshot, ruleValue, RuleMatchMode.PHRASE)
            "CHANNEL_URL" -> matchesUrl(snapshot, ruleValue)
            // The UI stores usernames without '@'; phrase boundaries prevent matching
            // a shorter username inside a longer one (e.g. news vs newsroom).
            "USERNAME" -> RuleMatcher.matches(
                snapshot,
                ruleValue.removePrefix("@"),
                RuleMatchMode.PHRASE
            )
            else -> false
        }
    }

    private fun matchesUrl(snapshot: String, ruleValue: String): Boolean {
        val normalizedSnapshot = com.blocker2.app.core.text.WordNormalizer.normalize(snapshot)
        val normalizedUrl = com.blocker2.app.core.text.WordNormalizer.normalize(ruleValue)
        if (normalizedUrl.isBlank()) return false

        var from = 0
        while (from <= normalizedSnapshot.length - normalizedUrl.length) {
            val index = normalizedSnapshot.indexOf(normalizedUrl, from)
            if (index < 0) return false
            val end = index + normalizedUrl.length
            val leftBoundary = index == 0 || !isUrlCharacter(normalizedSnapshot[index - 1])
            val rightBoundary = end == normalizedSnapshot.length || !isUrlCharacter(normalizedSnapshot[end])
            if (leftBoundary && rightBoundary) return true
            from = index + 1
        }
        return false
    }

    private fun isUrlCharacter(char: Char): Boolean =
        char.isLetterOrDigit() || char == '_' || char == '-' || char == '.'
}