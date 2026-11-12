package com.blocker2.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.blocker2.app.data.local.entity.CommitmentSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommitmentSessionDao {
    @Query("SELECT * FROM commitment_session WHERE id = 1 LIMIT 1")
    fun observeSession(): Flow<CommitmentSessionEntity?>

    @Query("SELECT * FROM commitment_session WHERE id = 1 LIMIT 1")
    suspend fun getSession(): CommitmentSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: CommitmentSessionEntity)

    @Query("DELETE FROM commitment_session WHERE id = 1")
    suspend fun endSession(): Int
    @Query("SELECT * FROM commitment_session")
    suspend fun exportAll(): List<CommitmentSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun importAll(items: List<CommitmentSessionEntity>)

    @Query("DELETE FROM commitment_session")
    suspend fun clearForImport()

}
