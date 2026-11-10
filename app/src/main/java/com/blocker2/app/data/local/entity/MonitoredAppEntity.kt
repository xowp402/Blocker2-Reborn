package com.blocker2.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_apps")
data class MonitoredAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String = "OTHER",
    val monitoringEnabled: Boolean = false,
    val detectionCount: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
