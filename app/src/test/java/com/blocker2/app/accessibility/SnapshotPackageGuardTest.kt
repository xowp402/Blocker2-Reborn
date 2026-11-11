package com.blocker2.app.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotPackageGuardTest {

    @Test
    fun acceptsMatchingPackages() {
        assertTrue(SnapshotPackageGuard.belongsToEvent("com.example.app", "com.example.app"))
    }

    @Test
    fun rejectsDifferentPackages() {
        assertFalse(SnapshotPackageGuard.belongsToEvent("com.example.app", "com.other.app"))
    }

    @Test
    fun acceptsUnavailableTreePackage() {
        assertTrue(SnapshotPackageGuard.belongsToEvent("com.example.app", null))
    }

    @Test
    fun rejectsBlankEventPackage() {
        assertFalse(SnapshotPackageGuard.belongsToEvent("", "com.example.app"))
        assertFalse(SnapshotPackageGuard.belongsToEvent(" ", null))
    }
}
