package com.blocker2.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.blocker2.app.data.local.entity.ActiveAppBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveAppBlockDao {
    @Query("SELECT * FROM active_app_blocks WHERE packageName = :packageName AND expiresAt > :now LIMIT 1")
    suspend fun findActive(packageName: String, now: Long): ActiveAppBlockEntity?

    @Query("SELECT * FROM active_app_blocks WHERE expiresAt > :now ORDER BY expiresAt ASC")
    fun observeActive(now: Long): Flow<List<ActiveAppBlockEntity>>

    @Upsert
    suspend fun upsert(block: ActiveAppBlockEntity)

    @Query("DELETE FROM active_app_blocks WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("UPDATE active_app_blocks SET expiresAt = :newExpiresAt WHERE packageName = :packageName AND expiresAt > :now")
    suspend fun extendActive(packageName: String, newExpiresAt: Long, now: Long): Int

    @Query("DELETE FROM active_app_blocks WHERE packageName = :packageName")
    suspend fun clearForPackage(packageName: String): Int

    @Query("DELETE FROM active_app_blocks")
    suspend fun clearAll(): Int
}
