package com.blocker2.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.blocker2.app.data.local.entity.MonitoredAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps ORDER BY appName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MonitoredAppEntity>>

    @Query("SELECT * FROM monitored_apps WHERE monitoringEnabled = 1 ORDER BY appName COLLATE NOCASE ASC")
    fun observeMonitored(): Flow<List<MonitoredAppEntity>>

    @Upsert
    suspend fun upsert(app: MonitoredAppEntity)

    @Query("UPDATE monitored_apps SET monitoringEnabled = :enabled, updatedAt = :timestamp WHERE packageName = :packageName")
    suspend fun setMonitoring(packageName: String, enabled: Boolean, timestamp: Long): Int

    @Query("UPDATE monitored_apps SET detectionCount = detectionCount + 1, updatedAt = :timestamp WHERE packageName = :packageName")
    suspend fun incrementDetectionCount(packageName: String, timestamp: Long): Int

    @Query("DELETE FROM monitored_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String): Int
}
