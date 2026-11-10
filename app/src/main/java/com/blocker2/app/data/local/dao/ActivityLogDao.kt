package com.blocker2.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.blocker2.app.data.local.entity.ActivityLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: ActivityLogEntity): Long

    @Query("SELECT * FROM activity_logs ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityLogEntity>>

    @Query("SELECT COUNT(*) FROM activity_logs WHERE occurredAt >= :startOfDay")
    fun observeCountSince(startOfDay: Long): Flow<Int>

    @Query("DELETE FROM activity_logs WHERE occurredAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long): Int

    @Query("DELETE FROM activity_logs")
    suspend fun clearAll(): Int
}
