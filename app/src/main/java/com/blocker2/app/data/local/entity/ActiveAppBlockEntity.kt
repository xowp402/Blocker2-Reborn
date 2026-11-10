package com.blocker2.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A temporary, local-only restriction for one package.
 * The row is retained until its expiry so a process restart does not silently
 * cancel a still-active timed block.
 */
@Entity(
    tableName = "active_app_blocks",
    indices = [Index(value = ["expiresAt"])]
)
data class ActiveAppBlockEntity(
    @PrimaryKey val packageName: String,
    val sourceRuleId: Long?,
    val reason: String,
    val createdAt: Long,
    val expiresAt: Long
)
