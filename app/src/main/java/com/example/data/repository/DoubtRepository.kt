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

/**
 * Hook notified when local Room data is mutated by the user/app so CloudSyncManager
 * can push changes without creating remote->local->remote feedback loops.
 */
interface CloudSyncHook {
    fun onSessionSaved(session: ConversationSession)
    fun onSessionDeleted(cloudId: String)
    fun onDoubtSaved(doubt: DoubtItem)
    fun onDoubtDeleted(cloudId: String)
}

data class CloudMergeStats(
    val sessionsInserted: Int = 0,
    val sessionsUpdated: Int = 0,
    val doubtsInserted: Int = 0,
    val doubtsUpdated: Int = 0
) {
    val totalChanges: Int
        get() = sessionsInserted + sessionsUpdated + doubtsInserted + doubtsUpdated
}

class DoubtRepository(
    private val doubtDao: DoubtDao,
    private val sessionDao: ConversationSessionDao
) {
    @Volatile
    private var cloudSyncHook: CloudSyncHook? = null

    fun setCloudSyncHook(hook: CloudSyncHook?) {
        cloudSyncHook = hook
    }

    val allDoubts: Flow<List<DoubtItem>> = doubtDao.getAllDoubts()
    val allSessions: Flow<List<ConversationSession>> = sessionDao.getAllSessions()

    suspend fun getDoubtById(id: Long): DoubtItem? = doubtDao.getDoubtById(id)

    suspend fun getDoubtByCloudId(cloudId: String): DoubtItem? = doubtDao.getDoubtByCloudId(cloudId)

    fun getDoubtsForSession(sessionId: Long): Flow<List<DoubtItem>> =
        doubtDao.getDoubtsForSession(sessionId)

    suspend fun getDoubtsForSessionOnce(sessionId: Long): List<DoubtItem> =
        doubtDao.getDoubtsForSessionOnce(sessionId)

    suspend fun saveDoubt(doubt: DoubtItem): Long {
        val now = System.currentTimeMillis()
        val resolvedCloudId = ConversationSession.sanitizeCloudId(doubt.cloudId, "doubt")
        val resolvedSessionCloudId = when {
            doubt.sessionCloudId.isNotBlank() && ConversationSession.isValidCloudId(doubt.sessionCloudId) ->
                doubt.sessionCloudId
            doubt.sessionId > 0L ->
                sessionDao.getSessionById(doubt.sessionId)?.cloudId.orEmpty()
            else -> ""
        }
        val normalized = doubt.copy(
            cloudId = resolvedCloudId,
            sessionCloudId = resolvedSessionCloudId,
            updatedAtMillis = now
        )
        val rowId = doubtDao.insertDoubt(normalized)
        cloudSyncHook?.onDoubtSaved(normalized.copy(id = rowId))
        return rowId
    }

    suspend fun updateDoubt(doubt: DoubtItem) {
        val now = System.currentTimeMillis()
        val existing = if (doubt.id > 0L) doubtDao.getDoubtById(doubt.id) else null
        val resolvedCloudId = when {
            doubt.cloudId.isNotBlank() && ConversationSession.isValidCloudId(doubt.cloudId) -> doubt.cloudId
            existing?.cloudId?.isNotBlank() == true -> existing.cloudId
            else -> DoubtItem.newCloudId()
        }
        val resolvedSessionCloudId = when {
            doubt.sessionCloudId.isNotBlank() && ConversationSession.isValidCloudId(doubt.sessionCloudId) ->
                doubt.sessionCloudId
            existing?.sessionCloudId?.isNotBlank() == true -> existing.sessionCloudId
            doubt.sessionId > 0L -> sessionDao.getSessionById(doubt.sessionId)?.cloudId.orEmpty()
            else -> ""
        }
        val normalized = doubt.copy(
            cloudId = resolvedCloudId,
            sessionCloudId = resolvedSessionCloudId,
            updatedAtMillis = now
        )
        doubtDao.updateDoubt(normalized)
        cloudSyncHook?.onDoubtSaved(normalized)
    }

    suspend fun deleteDoubt(id: Long) {
        val existing = doubtDao.getDoubtById(id)
        doubtDao.deleteById(id)
        if (existing != null && existing.cloudId.isNotBlank()) {
            cloudSyncHook?.onDoubtDeleted(existing.cloudId)
        }
    }

    suspend fun clearAll() {
        doubtDao.clearAll()
        sessionDao.clearAll()
    }

    suspend fun getSessionById(id: Long): ConversationSession? = sessionDao.getSessionById(id)

    suspend fun getSessionByCloudId(cloudId: String): ConversationSession? =
        sessionDao.getSessionByCloudId(cloudId)

    suspend fun getAllDoubtsOnce(): List<DoubtItem> = doubtDao.getAllDoubtsOnce()

    suspend fun getAllSessionsOnce(): List<ConversationSession> = sessionDao.getAllSessionsOnce()

    suspend fun getHistoryStats(): Triple<Int, Int, Long> {
        val sessionCount = sessionDao.getSessionCount()
        val doubtCount = doubtDao.getDoubtCount()
        val latestSessionTs = sessionDao.getLatestSessionTimestamp() ?: 0L
        val latestDoubtTs = doubtDao.getLatestDoubtTimestamp() ?: 0L
        return Triple(sessionCount, doubtCount, maxOf(latestSessionTs, latestDoubtTs))
    }

    suspend fun saveSession(session: ConversationSession): Long {
        val now = System.currentTimeMillis()
        val resolvedCloudId = ConversationSession.sanitizeCloudId(session.cloudId, "session")
        val normalized = session.copy(
            cloudId = resolvedCloudId,
            updatedAtMillis = now
        )
        val rowId = sessionDao.insertSession(normalized)
        doubtDao.relinkDoubtsToSession(resolvedCloudId, rowId)
        cloudSyncHook?.onSessionSaved(normalized.copy(id = rowId))
        return rowId
    }

    suspend fun updateSession(session: ConversationSession) {
        val now = System.currentTimeMillis()
        val existing = if (session.id > 0L) sessionDao.getSessionById(session.id) else null
        val resolvedCloudId = when {
            session.cloudId.isNotBlank() && ConversationSession.isValidCloudId(session.cloudId) -> session.cloudId
            existing?.cloudId?.isNotBlank() == true -> existing.cloudId
            else -> ConversationSession.newCloudId("session")
        }
        val normalized = session.copy(
            cloudId = resolvedCloudId,
            updatedAtMillis = now
        )
        sessionDao.updateSession(normalized)
        cloudSyncHook?.onSessionSaved(normalized)
    }

    suspend fun deleteSession(id: Long) {
        val existing = sessionDao.getSessionById(id)
        sessionDao.deleteById(id)
        if (existing != null && existing.cloudId.isNotBlank()) {
            cloudSyncHook?.onSessionDeleted(existing.cloudId)
        }
    }

    suspend fun clearAllSessions() = sessionDao.clearAll()

    suspend fun deleteOlderThanDays(days: Int = 30, nowMillis: Long = System.currentTimeMillis()): Int {
        val cutoff = nowMillis - (days.coerceAtLeast(1).toLong() * 86_400_000L)
        val deletedDoubts = doubtDao.deleteOlderThan(cutoff)
        sessionDao.deleteOlderThan(cutoff)
        return deletedDoubts
    }

    /**
     * Merges sessions and doubts retrieved from Firestore into Room without re-triggering
     * [cloudSyncHook], preventing circular sync loops and Room PK collisions across devices.
     */
    suspend fun mergeRemoteSnapshot(
        remoteSessions: List<ConversationSession>,
        remoteDoubts: List<DoubtItem>
    ): CloudMergeStats {
        var sessionsInserted = 0
        var sessionsUpdated = 0
        var doubtsInserted = 0
        var doubtsUpdated = 0

        val cloudToLocalSessionId = mutableMapOf<String, Long>()
        sessionDao.getAllSessionsOnce().forEach { local ->
            if (local.cloudId.isNotBlank()) {
                cloudToLocalSessionId[local.cloudId] = local.id
            }
        }

        remoteSessions.forEach { rawRemote ->
            val remoteCloudId = ConversationSession.sanitizeCloudId(rawRemote.cloudId, "session")
            val remote = rawRemote.copy(cloudId = remoteCloudId)

            val byCloud = sessionDao.getSessionByCloudId(remoteCloudId)
            val legacyMatch = if (byCloud == null && remote.id > 0L) {
                sessionDao.getSessionById(remote.id)?.takeIf {
                    it.cloudId.isBlank() || it.cloudId == remoteCloudId || remoteCloudId == "session_${it.id}"
                }
            } else null
            val existing = byCloud ?: legacyMatch

            if (existing == null) {
                val insertedId = sessionDao.insertSession(remote.copy(id = 0L))
                cloudToLocalSessionId[remoteCloudId] = insertedId
                doubtDao.relinkDoubtsToSession(remoteCloudId, insertedId)
                sessionsInserted++
            } else {
                cloudToLocalSessionId[remoteCloudId] = existing.id
                val remoteUpdated = remote.updatedAtMillis.coerceAtLeast(remote.endTime)
                val localUpdated = existing.updatedAtMillis.coerceAtLeast(existing.endTime)
                val shouldUpdate = remoteUpdated > localUpdated ||
                    (remoteUpdated == localUpdated && shouldPreferRemoteSession(existing, remote))
                if (shouldUpdate) {
                    sessionDao.updateSession(
                        remote.copy(
                            id = existing.id,
                            cloudId = existing.cloudId.ifBlank { remoteCloudId },
                            updatedAtMillis = maxOf(remoteUpdated, localUpdated)
                        )
                    )
                    sessionsUpdated++
                }
                doubtDao.relinkDoubtsToSession(remoteCloudId, existing.id)
            }
        }

        remoteDoubts.forEach { rawRemote ->
            val remoteCloudId = ConversationSession.sanitizeCloudId(rawRemote.cloudId, "doubt")
            val remoteSessionCloudId = rawRemote.sessionCloudId.takeIf {
                it.isNotBlank() && ConversationSession.isValidCloudId(it)
            }.orEmpty()
            val resolvedSessionId = when {
                remoteSessionCloudId.isNotBlank() ->
                    cloudToLocalSessionId[remoteSessionCloudId]
                        ?: sessionDao.getSessionByCloudId(remoteSessionCloudId)?.id
                        ?: 0L
                else -> 0L
            }
            val remote = rawRemote.copy(
                cloudId = remoteCloudId,
                sessionCloudId = remoteSessionCloudId,
                sessionId = resolvedSessionId
            )

            val byCloud = doubtDao.getDoubtByCloudId(remoteCloudId)
            val legacyMatch = if (byCloud == null && rawRemote.id > 0L) {
                doubtDao.getDoubtById(rawRemote.id)?.takeIf {
                    it.cloudId.isBlank() || it.cloudId == remoteCloudId || remoteCloudId == "doubt_${it.id}"
                }
            } else null
            val existing = byCloud ?: legacyMatch

            if (existing == null) {
                doubtDao.insertDoubt(remote.copy(id = 0L))
                doubtsInserted++
            } else {
                val finalSessionId = if (resolvedSessionId > 0L) resolvedSessionId else existing.sessionId
                val finalSessionCloudId = remoteSessionCloudId.ifBlank { existing.sessionCloudId }
                val remoteUpdated = remote.updatedAtMillis.coerceAtLeast(remote.timestamp)
                val localUpdated = existing.updatedAtMillis.coerceAtLeast(existing.timestamp)
                val shouldUpdate = remoteUpdated > localUpdated ||
                    (remoteUpdated == localUpdated && shouldPreferRemoteDoubt(existing, remote)) ||
                    (finalSessionId > 0L && existing.sessionId != finalSessionId)
                if (shouldUpdate) {
                    val base = if (remoteUpdated >= localUpdated) remote else existing
                    doubtDao.updateDoubt(
                        base.copy(
                            id = existing.id,
                            sessionId = finalSessionId,
                            cloudId = existing.cloudId.ifBlank { remoteCloudId },
                            sessionCloudId = finalSessionCloudId,
                            updatedAtMillis = maxOf(remoteUpdated, localUpdated)
                        )
                    )
                    doubtsUpdated++
                }
            }
        }

        return CloudMergeStats(
            sessionsInserted = sessionsInserted,
            sessionsUpdated = sessionsUpdated,
            doubtsInserted = doubtsInserted,
            doubtsUpdated = doubtsUpdated
        )
    }

    suspend fun deleteSessionByCloudId(cloudId: String) {
        if (cloudId.isNotBlank()) {
            sessionDao.deleteByCloudId(cloudId)
        }
    }

    suspend fun deleteDoubtByCloudId(cloudId: String) {
        if (cloudId.isNotBlank()) {
            doubtDao.deleteByCloudId(cloudId)
        }
    }

    private fun shouldPreferRemoteSession(local: ConversationSession, remote: ConversationSession): Boolean {
        return remote.transcriptJson.length > local.transcriptJson.length ||
            remote.summary.length > local.summary.length ||
            remote.customTag != local.customTag ||
            remote.isPinned != local.isPinned
    }

    private fun shouldPreferRemoteDoubt(local: DoubtItem, remote: DoubtItem): Boolean {
        return remote.detailedAnswer.length > local.detailedAnswer.length ||
            remote.customTag != local.customTag ||
            remote.isStarred != local.isStarred ||
            remote.verificationSourcesJson.length > local.verificationSourcesJson.length
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
