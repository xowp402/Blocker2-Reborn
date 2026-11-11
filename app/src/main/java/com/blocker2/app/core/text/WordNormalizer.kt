package com.blocker2.app.core.text

import java.text.Normalizer
import java.util.Locale

/**
 * Canonicalizes Persian, Arabic and English text before a filter rule is compared.
 *
 * This function deliberately preserves word boundaries. It does not perform substring
 * matching; callers must apply the selected matching mode after normalization.
 */
object WordNormalizer {

    fun normalize(input: String): String {
        if (input.isEmpty()) return input

        val canonical = Normalizer.normalize(input, Normalizer.Form.NFKC)
            .replace(ARABIC_YEH, PERSIAN_YEH)
            .replace(ALEF_MAKSURA, PERSIAN_YEH)
            .replace(ARABIC_KAF, PERSIAN_KAF)
            .replace(TATWEEL.toString(), "")
            .replace(DIACRITICS, "")

        return canonical
            .lowercase(Locale.ROOT)
            .replace(ZERO_WIDTH_NON_JOINER.toString(), " ")
            .replace(INVISIBLE_FORMATTING, "")
            .replace(WHITESPACE, " ")
            .trim()
    }

    private const val ARABIC_YEH = '\u064A'
    private const val ALEF_MAKSURA = '\u0649'
    private const val PERSIAN_YEH = '\u06CC'
    private const val ARABIC_KAF = '\u0643'
    private const val PERSIAN_KAF = '\u06A9'
    private const val TATWEEL = '\u0640'
    private const val ZERO_WIDTH_NON_JOINER = '\u200C'

    private val DIACRITICS = Regex("[\\u064B-\\u065F\\u0670]")
    // Ignore invisible formatting marks that can otherwise split a filtered word.
    private val INVISIBLE_FORMATTING = Regex("[\\u200B\\u200D\\u2060\\uFEFF]")
    // Unicode separator categories include NBSP, em space and other non-ASCII spaces.
    private val WHITESPACE = Regex("[\\p{Z}\\s]+")
}
