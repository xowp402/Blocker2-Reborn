package com.blocker2.app.accessibility

/** Prevents processing an accessibility tree that belongs to a different event package. */
internal object SnapshotPackageGuard {
    fun belongsToEvent(eventPackage: String, treePackage: String?): Boolean {
        if (eventPackage.isBlank()) return false
        return treePackage == null || treePackage == eventPackage
    }
}
