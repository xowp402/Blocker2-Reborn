package com.blocker2.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "site_rules",
    indices = [
        Index(value = ["normalizedPattern"]),
        Index(value = ["ruleType"]),
        Index(value = ["enabled"])
    ]
)
data class SiteRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** DOMAIN, DOMAIN_AND_SUBDOMAINS, URL_PREFIX or IP */
    val ruleType: String,
    val pattern: String,
    val normalizedPattern: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
