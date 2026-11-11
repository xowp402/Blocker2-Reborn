package com.blocker2.app.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class WordNormalizerTest {

    @Test
    fun convertsArabicYehAndKafToPersianForms() {
        assertEquals("کیف علی", WordNormalizer.normalize("كيف علي"))
    }

    @Test
    fun treatsHalfSpaceAsAWordSeparator() {
        assertEquals("می روم", WordNormalizer.normalize("می‌روم"))
    }

    @Test
    fun collapsesRepeatedWhitespace() {
        assertEquals("hello world", WordNormalizer.normalize("  Hello   WORLD  "))
    }

    @Test
    fun removesArabicDiacriticsAndTatweel() {
        assertEquals("سلام", WordNormalizer.normalize("سَــلام"))
    }

    @Test
    fun collapsesUnicodeSpacesIncludingNonBreakingAndEmSpace() {
        assertEquals("hello world", WordNormalizer.normalize("hello world"))
        assertEquals("hello world", WordNormalizer.normalize("hello world"))
    }

    @Test
    fun removesInvisibleFormattingCharactersThatCouldSplitWords() {
        assertEquals("سلام", WordNormalizer.normalize("سلا​م﻿"))
    }

    @Test
    fun keepsDistinctWordsSeparated() {
        assertEquals("آب سرد", WordNormalizer.normalize("آب سرد"))
        assertEquals("آب", WordNormalizer.normalize("آب"))
    }
}
