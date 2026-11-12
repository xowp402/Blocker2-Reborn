package com.blocker2.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.blocker2.app.data.local.entity.AllowedSiteEntity
import com.blocker2.app.data.local.entity.SiteRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteRuleDao {
    @Query("SELECT * FROM site_rules ORDER BY updatedAt DESC")
    fun observeBlockedSites(): Flow<List<SiteRuleEntity>>

    @Query("SELECT * FROM site_rules WHERE enabled = 1 ORDER BY id ASC")
    fun observeEnabledBlockedSites(): Flow<List<SiteRuleEntity>>

    @Upsert
    suspend fun upsertBlockedSite(rule: SiteRuleEntity): Long

    @Query("UPDATE site_rules SET enabled = :enabled, updatedAt = :timestamp WHERE id = :id")
    suspend fun setBlockedSiteEnabled(id: Long, enabled: Boolean, timestamp: Long): Int

    @Query("DELETE FROM site_rules WHERE id = :id")
    suspend fun deleteBlockedSite(id: Long): Int

    @Query("SELECT * FROM allowed_sites")
    suspend fun exportAllowedSites(): List<AllowedSiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun importAllowedSites(items: List<AllowedSiteEntity>)

    @Query("DELETE FROM allowed_sites")
    suspend fun clearAllowedSitesForImport()

    @Query("SELECT * FROM allowed_sites ORDER BY createdAt DESC")
    fun observeAllowedSites(): Flow<List<AllowedSiteEntity>>

    @Upsert
    suspend fun upsertAllowedSite(rule: AllowedSiteEntity): Long

    @Query("DELETE FROM allowed_sites WHERE id = :id")
    suspend fun deleteAllowedSite(id: Long): Int
    @Query("SELECT * FROM site_rules")
    suspend fun exportAll(): List<SiteRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun importAll(items: List<SiteRuleEntity>)

    @Query("DELETE FROM site_rules")
    suspend fun clearForImport()

}
