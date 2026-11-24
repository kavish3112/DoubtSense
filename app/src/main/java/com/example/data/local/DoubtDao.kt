package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DoubtItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DoubtDao {
    @Query("SELECT * FROM doubts ORDER BY timestamp DESC")
    fun getAllDoubts(): Flow<List<DoubtItem>>

    @Query("SELECT * FROM doubts ORDER BY timestamp DESC")
    suspend fun getAllDoubtsOnce(): List<DoubtItem>

    @Query("SELECT COUNT(*) FROM doubts")
    suspend fun getDoubtCount(): Int

    @Query("SELECT MAX(timestamp) FROM doubts")
    suspend fun getLatestDoubtTimestamp(): Long?

    @Query("SELECT * FROM doubts WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getDoubtsForSession(sessionId: Long): Flow<List<DoubtItem>>

    @Query("SELECT * FROM doubts WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getDoubtsForSessionOnce(sessionId: Long): List<DoubtItem>

    @Query("SELECT * FROM doubts WHERE id = :id LIMIT 1")
    suspend fun getDoubtById(id: Long): DoubtItem?

    @Query("SELECT * FROM doubts WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getDoubtByCloudId(cloudId: String): DoubtItem?

    @Query("UPDATE doubts SET sessionId = :localSessionId WHERE sessionCloudId = :sessionCloudId AND :sessionCloudId != ''")
    suspend fun relinkDoubtsToSession(sessionCloudId: String, localSessionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoubt(doubt: DoubtItem): Long

    @Update
    suspend fun updateDoubt(doubt: DoubtItem)

    @Delete
    suspend fun deleteDoubt(doubt: DoubtItem)

    @Query("DELETE FROM doubts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM doubts WHERE cloudId = :cloudId")
    suspend fun deleteByCloudId(cloudId: String)

    @Query("DELETE FROM doubts WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM doubts")
    suspend fun clearAll()
}
