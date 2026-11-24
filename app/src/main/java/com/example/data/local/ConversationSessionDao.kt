package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ConversationSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationSessionDao {
    @Query("SELECT * FROM conversation_sessions ORDER BY endTime DESC")
    fun getAllSessions(): Flow<List<ConversationSession>>

    @Query("SELECT * FROM conversation_sessions ORDER BY endTime DESC")
    suspend fun getAllSessionsOnce(): List<ConversationSession>

    @Query("SELECT COUNT(*) FROM conversation_sessions")
    suspend fun getSessionCount(): Int

    @Query("SELECT MAX(endTime) FROM conversation_sessions")
    suspend fun getLatestSessionTimestamp(): Long?

    @Query("SELECT * FROM conversation_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): ConversationSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ConversationSession): Long

    @Update
    suspend fun updateSession(session: ConversationSession)

    @Query("DELETE FROM conversation_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM conversation_sessions WHERE endTime < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM conversation_sessions")
    suspend fun clearAll()
}
