package com.blocker2.app.accessibility

import com.blocker2.app.core.text.RuleMatchMode
import com.blocker2.app.core.text.RuleMatcher
import com.blocker2.app.core.text.WordNormalizer

/** Applies type-specific Telegram matching without accidental substring matches. */
internal object TelegramRuleMatcher {
    fun matches(snapshot: String, ruleType: String, ruleValue: String): Boolean {
        if (snapshot.isBlank() || ruleValue.isBlank()) return false

        return when (ruleType.trim().uppercase()) {
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
        val normalizedSnapshot = WordNormalizer.normalize(snapshot)
        val normalizedUrl = canonicalUrl(ruleValue)
        if (normalizedUrl.isBlank()) return false

        // Telegram may display the same link with or without a scheme or www prefix.
        // Check both host forms while retaining strict URL-character boundaries.
        val candidates = linkedSetOf(normalizedUrl, normalizedUrl.removePrefix("www."))
        candidates += "www.${normalizedUrl.removePrefix("www.")}"

        return candidates.any { candidate ->
            hasBoundedUrlOccurrence(normalizedSnapshot, candidate)
        }
    }

    private fun canonicalUrl(value: String): String {
        return WordNormalizer.normalize(value)
            .replace(Regex("^(?:https?://)?"), "")
            .removePrefix("www.")
    }

    private fun hasBoundedUrlOccurrence(snapshot: String, url: String): Boolean {
        if (url.isBlank()) return false
        var from = 0
        while (from <= snapshot.length - url.length) {
            val index = snapshot.indexOf(url, from)
            if (index < 0) return false
            val end = index + url.length
            val leftBoundary = index == 0 || !isUrlCharacter(snapshot[index - 1])
            val rightBoundary = end == snapshot.length || !isUrlCharacter(snapshot[end])
            if (leftBoundary && rightBoundary) return true
            from = index + 1
        }
        return false
    }

    private fun isUrlCharacter(char: Char): Boolean =
        char.isLetterOrDigit() || char == '_' || char == '-' || char == '.'
}