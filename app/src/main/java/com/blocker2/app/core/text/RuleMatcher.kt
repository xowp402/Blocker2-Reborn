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
        // Avoid compiling a new regular expression for every rule on every scan.
        // Code-point checks also keep supplementary Unicode letters/numbers inside words.
        var searchFrom = 0
        while (searchFrom <= content.length - rule.length) {
            val index = content.indexOf(rule, searchFrom)
            if (index < 0) return false

            val end = index + rule.length
            val leftIsBoundary = index == 0 ||
                !Character.isLetterOrDigit(content.codePointBefore(index))
            val rightIsBoundary = end == content.length ||
                !Character.isLetterOrDigit(content.codePointAt(end))

            if (leftIsBoundary && rightIsBoundary) return true
            searchFrom = index + 1
        }
        return false
    }

    private fun containsWhitespace(value: String): Boolean = value.any(Char::isWhitespace)

    private val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
}
