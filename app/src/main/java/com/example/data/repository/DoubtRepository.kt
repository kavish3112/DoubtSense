package com.example.data.repository

import com.example.data.local.DoubtDao
import com.example.data.model.DoubtItem
import kotlinx.coroutines.flow.Flow

class DoubtRepository(private val doubtDao: DoubtDao) {
    val allDoubts: Flow<List<DoubtItem>> = doubtDao.getAllDoubts()

    suspend fun getDoubtById(id: Long): DoubtItem? = doubtDao.getDoubtById(id)

    suspend fun saveDoubt(doubt: DoubtItem): Long = doubtDao.insertDoubt(doubt)

    suspend fun updateDoubt(doubt: DoubtItem) = doubtDao.updateDoubt(doubt)

    suspend fun deleteDoubt(id: Long) = doubtDao.deleteById(id)

    suspend fun clearAll() = doubtDao.clearAll()
}
