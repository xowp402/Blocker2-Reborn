package com.blocker2.app.core.text

/**
 * Applies explicit matching semantics to already-normalized rule text.
 *
 * The default NORMALIZED_WORD mode never treats a rule as an arbitrary substring:
 * single words are token-matched and multi-word rules are phrase-matched on boundaries.
 */
object RuleMatcher {

    fun matches(content: String, ruleText: String, mode: RuleMatchMode): Boolean {
        val normalizedContent = WordNormalizer.normalize(content)
        val normalizedRule = WordNormalizer.normalize(ruleText)

        if (normalizedContent.isEmpty() || normalizedRule.isEmpty()) return false

        return when (mode) {
            RuleMatchMode.EXACT -> normalizedContent == normalizedRule
            RuleMatchMode.PHRASE -> hasBoundedOccurrence(normalizedContent, normalizedRule)
            RuleMatchMode.NORMALIZED_WORD -> {
                if (containsWhitespace(normalizedRule)) {
                    hasBoundedOccurrence(normalizedContent, normalizedRule)
                } else {
                    normalizedContent.split(WORD_SEPARATOR)
                        .any { token -> token == normalizedRule }
                }
            }
            RuleMatchMode.WORD_BOUNDARY -> hasBoundedOccurrence(normalizedContent, normalizedRule)
            RuleMatchMode.CONTAINS -> normalizedContent.contains(normalizedRule)
        }
    }

    private fun hasBoundedOccurrence(content: String, rule: String): Boolean {
        val pattern = Regex(
            "(?<![\\p{L}\\p{N}])${Regex.escape(rule)}(?![\\p{L}\\p{N}])"
        )
        return pattern.containsMatchIn(content)
    }

    private fun containsWhitespace(value: String): Boolean = value.any(Char::isWhitespace)

    private val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
}
