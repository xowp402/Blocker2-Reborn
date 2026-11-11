package com.blocker2.app.accessibility

import com.blocker2.app.core.text.RuleMatchMode
import com.blocker2.app.core.text.RuleMatcher

/** Applies type-specific Telegram matching without accidental substring matches. */
internal object TelegramRuleMatcher {
    fun matches(snapshot: String, ruleType: String, ruleValue: String): Boolean {
        if (snapshot.isBlank() || ruleValue.isBlank()) return false

        return when (ruleType.uppercase()) {
            "WORD" -> RuleMatcher.matches(snapshot, ruleValue, RuleMatchMode.NORMALIZED_WORD)
            "PHRASE", "CHANNEL_URL", "CHANNEL_ID" ->
                RuleMatcher.matches(snapshot, ruleValue, RuleMatchMode.PHRASE)
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
}