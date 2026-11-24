package com.example.data.repository

import com.example.data.local.ConversationSessionDao
import com.example.data.local.DoubtDao
import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import kotlinx.coroutines.flow.Flow

/**
 * Aggregated analytics summary for a specific subject/category derived from stored Room sessions & doubts.
 */
data class CategorySummary(
    val categoryName: String,
    val sessionCount: Int,
    val totalDoubts: Int,
    val averageIntensity: Float,
    val peakIntensity: Float,
    val latestTopic: String,
    val lastUpdatedTimestamp: Long
)

class DoubtRepository(
    private val doubtDao: DoubtDao,
    private val sessionDao: ConversationSessionDao
) {
    val allDoubts: Flow<List<DoubtItem>> = doubtDao.getAllDoubts()
    val allSessions: Flow<List<ConversationSession>> = sessionDao.getAllSessions()

    suspend fun getDoubtById(id: Long): DoubtItem? = doubtDao.getDoubtById(id)

    fun getDoubtsForSession(sessionId: Long): Flow<List<DoubtItem>> =
        doubtDao.getDoubtsForSession(sessionId)

    suspend fun getDoubtsForSessionOnce(sessionId: Long): List<DoubtItem> =
        doubtDao.getDoubtsForSessionOnce(sessionId)

    suspend fun saveDoubt(doubt: DoubtItem): Long = doubtDao.insertDoubt(doubt)

    suspend fun updateDoubt(doubt: DoubtItem) = doubtDao.updateDoubt(doubt)

    suspend fun deleteDoubt(id: Long) = doubtDao.deleteById(id)

    suspend fun clearAll() {
        doubtDao.clearAll()
        sessionDao.clearAll()
    }

    suspend fun getSessionById(id: Long): ConversationSession? = sessionDao.getSessionById(id)

    suspend fun getAllDoubtsOnce(): List<DoubtItem> = doubtDao.getAllDoubtsOnce()

    suspend fun getAllSessionsOnce(): List<ConversationSession> = sessionDao.getAllSessionsOnce()

    suspend fun getHistoryStats(): Triple<Int, Int, Long> {
        val sessionCount = sessionDao.getSessionCount()
        val doubtCount = doubtDao.getDoubtCount()
        val latestSessionTs = sessionDao.getLatestSessionTimestamp() ?: 0L
        val latestDoubtTs = doubtDao.getLatestDoubtTimestamp() ?: 0L
        return Triple(sessionCount, doubtCount, maxOf(latestSessionTs, latestDoubtTs))
    }

    suspend fun saveSession(session: ConversationSession): Long = sessionDao.insertSession(session)

    suspend fun updateSession(session: ConversationSession) = sessionDao.updateSession(session)

    suspend fun deleteSession(id: Long) = sessionDao.deleteById(id)

    suspend fun clearAllSessions() = sessionDao.clearAll()

    suspend fun deleteOlderThanDays(days: Int = 30, nowMillis: Long = System.currentTimeMillis()): Int {
        val cutoff = nowMillis - (days.coerceAtLeast(1).toLong() * 86_400_000L)
        val deletedDoubts = doubtDao.deleteOlderThan(cutoff)
        sessionDao.deleteOlderThan(cutoff)
        return deletedDoubts
    }

    companion object {
        fun sortWithPinnedFirst(doubts: List<DoubtItem>): List<DoubtItem> {
            return doubts.sortedWith(
                compareByDescending<DoubtItem> { it.isStarred }
                    .thenByDescending { it.timestamp }
            )
        }

        fun filterRecentDays(
            doubts: List<DoubtItem>,
            days: Int = 7,
            nowMillis: Long = System.currentTimeMillis()
        ): List<DoubtItem> {
            val cutoff = nowMillis - (days.coerceAtLeast(1).toLong() * 86_400_000L)
            return doubts.filter { it.timestamp >= cutoff }
        }

        /**
         * Computes per-category summary metrics with average doubt intensity from stored sessions and doubts.
         */
        fun computeCategorySummaries(
            sessions: List<ConversationSession>,
            doubts: List<DoubtItem>
        ): List<CategorySummary> {
            val categoryMap = mutableMapOf<String, MutableList<Pair<Float, Long>>>()
            val sessionCountByCat = mutableMapOf<String, Int>()
            val doubtCountByCat = mutableMapOf<String, Int>()
            val latestTopicByCat = mutableMapOf<String, Pair<String, Long>>()

            sessions.forEach { session ->
                val cat = session.category.ifBlank { "General" }
                sessionCountByCat[cat] = (sessionCountByCat[cat] ?: 0) + 1
                categoryMap.getOrPut(cat) { mutableListOf() }
                    .add(session.averageDoubtIntensity.coerceIn(0f, 1f) to session.endTime)
                val existingTopic = latestTopicByCat[cat]
                if (existingTopic == null || session.endTime > existingTopic.second) {
                    latestTopicByCat[cat] = session.title to session.endTime
                }
            }

            doubts.forEach { doubt ->
                val cat = doubt.subject.ifBlank {
                    doubt.topic.substringBefore(":").trim().ifBlank { "General" }
                }
                doubtCountByCat[cat] = (doubtCountByCat[cat] ?: 0) + 1
                // If no session exists for this category yet, include doubt's confidence/intensity score
                if ((sessionCountByCat[cat] ?: 0) == 0) {
                    categoryMap.getOrPut(cat) { mutableListOf() }
                        .add(doubt.confidenceScore.coerceIn(0f, 1f) to doubt.timestamp)
                }
                val existingTopic = latestTopicByCat[cat]
                if (existingTopic == null || doubt.timestamp >= existingTopic.second) {
                    latestTopicByCat[cat] = doubt.topic to doubt.timestamp
                }
            }

            return categoryMap.map { (cat, scores) ->
                val avg = if (scores.isNotEmpty()) scores.map { it.first }.average().toFloat() else 0f
                val peak = scores.maxOfOrNull { it.first } ?: 0f
                val lastTs = scores.maxOfOrNull { it.second } ?: 0L
                CategorySummary(
                    categoryName = cat,
                    sessionCount = sessionCountByCat[cat] ?: 0,
                    totalDoubts = doubtCountByCat[cat] ?: 0,
                    averageIntensity = avg,
                    peakIntensity = peak,
                    latestTopic = latestTopicByCat[cat]?.first ?: cat,
                    lastUpdatedTimestamp = lastTs
                )
            }.sortedByDescending { it.averageIntensity }
        }
    }
}
