package com.blocker2.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.blocker2.app.data.local.entity.TelegramFilterRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TelegramFilterRuleDao {
    @Query("SELECT * FROM telegram_filter_rules ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TelegramFilterRuleEntity>>

    @Query("SELECT * FROM telegram_filter_rules WHERE enabled = 1 ORDER BY id ASC")
    fun observeEnabled(): Flow<List<TelegramFilterRuleEntity>>

    @Upsert
    suspend fun upsert(rule: TelegramFilterRuleEntity): Long

    @Query("UPDATE telegram_filter_rules SET enabled = :enabled, updatedAt = :timestamp WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, timestamp: Long): Int

    @Query("UPDATE telegram_filter_rules SET detectionCount = detectionCount + 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun incrementDetectionCount(id: Long, timestamp: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM telegram_filter_rules WHERE id = :id")
    suspend fun deleteById(id: Long): Int
    @Query("SELECT * FROM telegram_filter_rules")
    suspend fun exportAll(): List<TelegramFilterRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun importAll(items: List<TelegramFilterRuleEntity>)

    @Query("DELETE FROM telegram_filter_rules")
    suspend fun clearForImport()

}
