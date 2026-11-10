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

    @Test fun urlPrefixCannotMatchDnsHostname() {
        val rule = SiteRuleEntity(ruleType = "URL_PREFIX", pattern = "example.com/private", normalizedPattern = "example.com/private")
        assertEquals(SiteRuleMatcher.Decision.PASS, SiteRuleMatcher.evaluate("example.com", listOf(rule), emptyList()))
    }
}
