package com.blocker2.app.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageTargetMatcherTest {

    @Test
    fun emptyTargetListMatchesEveryNonBlankPackage() {
        assertTrue(PackageTargetMatcher.matches("", "com.example.app"))
        assertTrue(PackageTargetMatcher.matches(" |  | ", "com.example.app"))
    }

    @Test
    fun matchesExactPackageAmongMultipleTargets() {
        assertTrue(PackageTargetMatcher.matches("com.one.app| com.two.app ", "com.two.app"))
        assertFalse(PackageTargetMatcher.matches("com.one.app|com.two.app", "com.three.app"))
    }

    @Test
    fun doesNotUsePartialPackageMatches() {
        assertFalse(PackageTargetMatcher.matches("com.example", "com.example.app"))
        assertFalse(PackageTargetMatcher.matches("com.example.app", "com.example"))
    }

    @Test
    fun rejectsBlankPackageNameEvenWhenTargetListIsEmpty() {
        assertFalse(PackageTargetMatcher.matches("", ""))
        assertFalse(PackageTargetMatcher.matches("com.example.app", " "))
    }
}
