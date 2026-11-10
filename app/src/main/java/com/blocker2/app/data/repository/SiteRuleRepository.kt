package com.blocker2.app.data.repository

import com.blocker2.app.data.local.dao.SiteRuleDao
import com.blocker2.app.data.local.entity.AllowedSiteEntity
import com.blocker2.app.data.local.entity.SiteRuleEntity
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class SiteRuleRepository(
    private val dao: SiteRuleDao
) {
    fun observeBlockedSites(): Flow<List<SiteRuleEntity>> = dao.observeBlockedSites()

    fun observeEnabledBlockedSites(): Flow<List<SiteRuleEntity>> =
        dao.observeEnabledBlockedSites()

    fun observeAllowedSites(): Flow<List<AllowedSiteEntity>> = dao.observeAllowedSites()

    suspend fun addBlockedSite(
        ruleType: String,
        pattern: String,
        enabled: Boolean = true
    ): Long {
        val type = ruleType.uppercase(Locale.ROOT)
        require(type in BLOCK_RULE_TYPES) { "Unsupported site rule type: $ruleType" }
        val cleanPattern = normalizeSitePattern(pattern)
        require(cleanPattern.isNotEmpty()) { "Site pattern cannot be empty." }

        val now = System.currentTimeMillis()
        return dao.upsertBlockedSite(
            SiteRuleEntity(
                ruleType = type,
                pattern = pattern.trim(),
                normalizedPattern = cleanPattern,
                enabled = enabled,
                updatedAt = now
            )
        )
    }

    suspend fun addAllowedSite(
        pattern: String,
        includeSubdomains: Boolean = false
    ): Long {
        val cleanPattern = normalizeSitePattern(pattern)
        require(cleanPattern.isNotEmpty()) { "Allowed site pattern cannot be empty." }
        return dao.upsertAllowedSite(
            AllowedSiteEntity(
                pattern = pattern.trim(),
                normalizedPattern = cleanPattern,
                includeSubdomains = includeSubdomains
            )
        )
    }

    suspend fun setBlockedSiteEnabled(id: Long, enabled: Boolean): Boolean =
        dao.setBlockedSiteEnabled(id, enabled, System.currentTimeMillis()) > 0

    suspend fun deleteBlockedSite(id: Long): Boolean = dao.deleteBlockedSite(id) > 0

    suspend fun deleteAllowedSite(id: Long): Boolean = dao.deleteAllowedSite(id) > 0

    private fun normalizeSitePattern(value: String): String =
        value.trim()
            .lowercase(Locale.ROOT)
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")

    private companion object {
        val BLOCK_RULE_TYPES = setOf("DOMAIN", "DOMAIN_AND_SUBDOMAINS", "URL_PREFIX", "IP")
    }
}
