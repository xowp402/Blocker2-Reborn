package com.blocker2.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.blocker2.app.data.local.entity.WordRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordRuleDao {
    @Query("SELECT * FROM word_rules ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WordRuleEntity>>

    @Query("SELECT * FROM word_rules WHERE enabled = 1 ORDER BY id ASC")
    fun observeEnabled(): Flow<List<WordRuleEntity>>

    @Query("SELECT * FROM word_rules WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): WordRuleEntity?

    @Upsert
    suspend fun upsert(rule: WordRuleEntity): Long

    @Query("UPDATE word_rules SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long): Int

    @Query("UPDATE word_rules SET detectionCount = detectionCount + 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun incrementDetectionCount(id: Long, timestamp: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM word_rules WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT COUNT(*) FROM word_rules")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM word_rules WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>
}
