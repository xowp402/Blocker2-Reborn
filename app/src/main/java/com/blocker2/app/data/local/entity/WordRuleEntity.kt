package com.blocker2.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_rules",
    indices = [
        Index(value = ["normalizedText"]),
        Index(value = ["enabled"]),
        Index(value = ["category"])
    ]
)
data class WordRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val normalizedText: String,
    val category: String = "GENERAL",
    val language: String = "AUTO",
    val enabled: Boolean = true,
    val matchMode: String = "NORMALIZED_WORD",
    val reaction: String = "WARN_AND_EXIT",
    val blockDurationMillis: Long = 120_000L,
    val customWarning: String? = null,
    val targetPackages: String = "",
    val statisticsEnabled: Boolean = true,
    val detectionCount: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
