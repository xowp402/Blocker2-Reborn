package com.blocker2.app.core.site

import com.blocker2.app.data.local.entity.AllowedSiteEntity
import com.blocker2.app.data.local.entity.SiteRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SiteRuleMatcherTest {
    @Test fun domainMatchesOnlyExactHost() {
        val rule = SiteRuleEntity(ruleType = "DOMAIN", pattern = "example.com", normalizedPattern = "example.com")
        assertEquals(SiteRuleMatcher.Decision.BLOCK, SiteRuleMatcher.evaluate("example.com", listOf(rule), emptyList()))
        assertEquals(SiteRuleMatcher.Decision.PASS, SiteRuleMatcher.evaluate("www.example.com", listOf(rule), emptyList()))
        assertEquals(SiteRuleMatcher.Decision.PASS, SiteRuleMatcher.evaluate("notexample.com", listOf(rule), emptyList()))
    }

    @Test fun domainAndSubdomainsUsesLabelBoundary() {
        val rule = SiteRuleEntity(ruleType = "DOMAIN_AND_SUBDOMAINS", pattern = "example.com", normalizedPattern = "example.com")
        assertEquals(SiteRuleMatcher.Decision.BLOCK, SiteRuleMatcher.evaluate("a.b.example.com.", listOf(rule), emptyList()))
        assertEquals(SiteRuleMatcher.Decision.PASS, SiteRuleMatcher.evaluate("badexample.com", listOf(rule), emptyList()))
    }

    @Test fun blockedRuleOverridesAllowList() {
        val blocked = SiteRuleEntity(ruleType = "DOMAIN_AND_SUBDOMAINS", pattern = "example.com", normalizedPattern = "example.com")
        val allowed = AllowedSiteEntity(pattern = "example.com", normalizedPattern = "example.com", includeSubdomains = true)
        assertEquals(SiteRuleMatcher.Decision.BLOCK, SiteRuleMatcher.evaluate("www.example.com", listOf(blocked), listOf(allowed)))
    }

    @Test fun allowListIsReportedWhenNoBlockedRuleMatches() {
        val allowed = AllowedSiteEntity(pattern = "example.com", normalizedPattern = "example.com", includeSubdomains = false)
        assertEquals(SiteRuleMatcher.Decision.ALLOW, SiteRuleMatcher.evaluate("example.com", emptyList(), listOf(allowed)))
        assertEquals(SiteRuleMatcher.Decision.PASS, SiteRuleMatcher.evaluate("www.example.com", emptyList(), listOf(allowed)))
    }

    @Test fun unicodeDomainAndPunycodeNormalizeToSameHost() {
        val rule = SiteRuleEntity(ruleType = "DOMAIN", pattern = "مثال.ایران", normalizedPattern = "مثال.ایران")
        assertEquals(SiteRuleMatcher.Decision.BLOCK, SiteRuleMatcher.evaluate("xn--mgbh0fb.xn--mgba3a4f16a", listOf(rule), emptyList()))
        assertEquals("xn--mgbh0fb.xn--mgba3a4f16a", SiteRuleMatcher.normalizeHost("مثال.ایران."))
    }

    @Test fun malformedHostInputIsRejected() {
        assertEquals("", SiteRuleMatcher.normalizeHost("https://example.com/path"))
        assertEquals("", SiteRuleMatcher.normalizeHost("example.com:443"))
        assertEquals("", SiteRuleMatcher.normalizeHost("bad..example.com"))
    }

    @Test fun caseAndTrailingRootDotAreNormalized() {
        val rule = SiteRuleEntity(ruleType = "DOMAIN", pattern = "Example.COM", normalizedPattern = "Example.COM")
        assertEquals(SiteRuleMatcher.Decision.BLOCK, SiteRuleMatcher.evaluate("EXAMPLE.com.", listOf(rule), emptyList()))
    }

    @Test fun urlPrefixCannotMatchDnsHostname() {
        val rule = SiteRuleEntity(ruleType = "URL_PREFIX", pattern = "example.com/private", normalizedPattern = "example.com/private")
        assertEquals(SiteRuleMatcher.Decision.PASS, SiteRuleMatcher.evaluate("example.com", listOf(rule), emptyList()))
    }

    @Test fun disabledBlockedRuleDoesNotBlockHost() {
        val disabled = SiteRuleEntity(
            ruleType = "DOMAIN_AND_SUBDOMAINS",
            pattern = "example.com",
            normalizedPattern = "example.com",
            enabled = false
        )
        assertEquals(
            SiteRuleMatcher.Decision.PASS,
            SiteRuleMatcher.evaluate("www.example.com", listOf(disabled), emptyList())
        )
    }

    @Test fun ipRuleMatchesOnlyTheExactIpAddress() {
        val rule = SiteRuleEntity(
            ruleType = "IP",
            pattern = "192.0.2.10",
            normalizedPattern = "192.0.2.10"
        )
        assertEquals(
            SiteRuleMatcher.Decision.BLOCK,
            SiteRuleMatcher.evaluate("192.0.2.10", listOf(rule), emptyList())
        )
        assertEquals(
            SiteRuleMatcher.Decision.PASS,
            SiteRuleMatcher.evaluate("192.0.2.11", listOf(rule), emptyList())
        )
    }

    @Test fun exactAllowEntryDoesNotAllowSubdomains() {
        val allowed = AllowedSiteEntity(
            pattern = "example.com",
            normalizedPattern = "example.com",
            includeSubdomains = false
        )
        assertEquals(
            SiteRuleMatcher.Decision.ALLOW,
            SiteRuleMatcher.evaluate("example.com", emptyList(), listOf(allowed))
        )
        assertEquals(
            SiteRuleMatcher.Decision.PASS,
            SiteRuleMatcher.evaluate("sub.example.com", emptyList(), listOf(allowed))
        )
    }

    @Test fun blockedDomainTakesPrecedenceOverMatchingAllowEntry() {
        val blocked = SiteRuleEntity(
            ruleType = "DOMAIN",
            pattern = "example.com",
            normalizedPattern = "example.com"
        )
        val allowed = AllowedSiteEntity(
            pattern = "example.com",
            normalizedPattern = "example.com",
            includeSubdomains = true
        )
        assertEquals(
            SiteRuleMatcher.Decision.BLOCK,
            SiteRuleMatcher.evaluate("example.com", listOf(blocked), listOf(allowed))
        )
    }

}
