package com.blocker2.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "allowed_sites",
    indices = [Index(value = ["normalizedPattern"], unique = true)]
)
data class AllowedSiteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val normalizedPattern: String,
    val includeSubdomains: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
