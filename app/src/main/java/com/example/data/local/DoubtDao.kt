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

    @Query("SELECT * FROM doubts WHERE id = :id LIMIT 1")
    suspend fun getDoubtById(id: Long): DoubtItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoubt(doubt: DoubtItem): Long

    @Update
    suspend fun updateDoubt(doubt: DoubtItem)

    @Delete
    suspend fun deleteDoubt(doubt: DoubtItem)

    @Query("DELETE FROM doubts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM doubts")
    suspend fun clearAll()
}
