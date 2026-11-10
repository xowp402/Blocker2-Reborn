package com.blocker2.app.core.site

import com.blocker2.app.data.local.entity.AllowedSiteEntity
import com.blocker2.app.data.local.entity.SiteRuleEntity
import java.util.Locale

/**
 * Matches DNS question names only. URL paths and HTTPS query strings are not
 * present in DNS packets and therefore cannot be evaluated here.
 *
 * Explicit blocked rules take precedence over allow-list entries.
 */
object SiteRuleMatcher {
    enum class Decision { BLOCK, ALLOW, PASS }

    fun evaluate(
        hostname: String,
        blockedRules: List<SiteRuleEntity>,
        allowedRules: List<AllowedSiteEntity>
    ): Decision {
        val host = normalizeHost(hostname)
        if (host.isEmpty()) return Decision.PASS

        if (blockedRules.any { it.enabled && matchesBlocked(host, it) }) {
            return Decision.BLOCK
        }

        if (allowedRules.any { matchesAllowed(host, it) }) {
            return Decision.ALLOW
        }

        return Decision.PASS
    }

    fun normalizeHost(value: String): String =
        value.trim()
            .trimEnd('.')
            .lowercase(Locale.ROOT)
            .removePrefix("[").removeSuffix("]")

    private fun matchesBlocked(host: String, rule: SiteRuleEntity): Boolean {
        val pattern = normalizeHost(rule.normalizedPattern)
        if (pattern.isEmpty()) return false
        return when (rule.ruleType.uppercase(Locale.ROOT)) {
            "DOMAIN" -> host == pattern
            "DOMAIN_AND_SUBDOMAINS" -> host == pattern || host.endsWith(".$pattern")
            "IP" -> host == pattern
            // A DNS question contains a hostname, never an HTTPS path.
            "URL_PREFIX" -> false
            else -> false
        }
    }

    private fun matchesAllowed(host: String, rule: AllowedSiteEntity): Boolean {
        val pattern = normalizeHost(rule.normalizedPattern)
        if (pattern.isEmpty()) return false
        return host == pattern || (rule.includeSubdomains && host.endsWith(".$pattern"))
    }
}
