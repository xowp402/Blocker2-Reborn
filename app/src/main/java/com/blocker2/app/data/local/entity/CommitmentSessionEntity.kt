package com.blocker2.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A persistent commitment session that survives process death and device restarts. */
@Entity(tableName = "commitment_session")
data class CommitmentSessionEntity(
    @PrimaryKey val id: Int = 1,
    val startedAt: Long,
    val expiresAt: Long
)
