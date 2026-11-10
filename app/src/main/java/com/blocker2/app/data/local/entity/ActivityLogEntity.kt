package com.blocker2.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activity_logs",
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["sourcePackage"]),
        Index(value = ["ruleType"])
    ]
)
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAt: Long = System.currentTimeMillis(),
    val sourcePackage: String,
    val sourceLabel: String? = null,
    val ruleType: String,
    val ruleId: Long? = null,
    val matchedValue: String,
    val reaction: String,
    val durationMillis: Long = 0,
    val outcome: String = "RECORDED"
)
