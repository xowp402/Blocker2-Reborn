package com.blocker2.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telegram_filter_rules",
    indices = [
        Index(value = ["normalizedValue"]),
        Index(value = ["ruleType"]),
        Index(value = ["enabled"])
    ]
)
data class TelegramFilterRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** WORD, PHRASE, CHANNEL_URL, CHANNEL_ID or USERNAME */
    val ruleType: String,
    val value: String,
    val normalizedValue: String,
    val enabled: Boolean = true,
    val reaction: String = "WARN_AND_EXIT",
    val blockDurationMillis: Long = 120_000L,
    val detectionCount: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
