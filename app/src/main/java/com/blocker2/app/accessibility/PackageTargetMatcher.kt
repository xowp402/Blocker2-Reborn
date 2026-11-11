package com.blocker2.app.accessibility

/** Matches an app package against the pipe-delimited package allowlist stored on a rule. */
internal object PackageTargetMatcher {
    fun matches(configuredPackages: String, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val configured = configuredPackages
            .split("|")
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        return configured.isEmpty() || packageName in configured
    }
}
