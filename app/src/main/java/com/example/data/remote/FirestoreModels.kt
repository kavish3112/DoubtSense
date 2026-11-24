package com.example.data.remote

import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreDoubtBackup(
    @DocumentId
    val id: String = "",
    val localId: Long = 0L,
    val query: String = "",
    val summary: String = "",
    val oneSentenceSummary: String = "",
    val category: String = "General",
    val customTag: String = "Education",
    val timestamp: Long = 0L,
    @get:PropertyName("isResolved")
    @set:PropertyName("isResolved")
    var isResolved: Boolean = false,
    @get:PropertyName("isStarred")
    @set:PropertyName("isStarred")
    var isStarred: Boolean = false,
    val confidenceScore: Double = 0.9,
    val followUpCount: Long = 0L,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "localId" to localId.coerceAtLeast(0L),
            "query" to query.trim().take(2000).ifBlank { "Doubt" },
            "summary" to summary.take(10000),
            "oneSentenceSummary" to oneSentenceSummary.take(500).ifBlank { null },
            "category" to category.trim().take(60).ifBlank { "General" },
            "customTag" to customTag.trim().take(60).ifBlank { null },
            "timestamp" to timestamp.coerceAtLeast(0L),
            "isResolved" to isResolved,
            "isStarred" to isStarred,
            "confidenceScore" to confidenceScore.coerceIn(0.0, 1.0),
            "followUpCount" to followUpCount.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(existingCreatedAt: Timestamp): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "localId" to localId.coerceAtLeast(0L),
            "query" to query.trim().take(2000).ifBlank { "Doubt" },
            "summary" to summary.take(10000),
            "oneSentenceSummary" to oneSentenceSummary.take(500).ifBlank { null },
            "category" to category.trim().take(60).ifBlank { "General" },
            "customTag" to customTag.trim().take(60).ifBlank { null },
            "timestamp" to timestamp.coerceAtLeast(0L),
            "isResolved" to isResolved,
            "isStarred" to isStarred,
            "confidenceScore" to confidenceScore.coerceIn(0.0, 1.0),
            "followUpCount" to followUpCount.coerceAtLeast(0L),
            "createdAt" to existingCreatedAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toRoomEntity(): DoubtItem {
        val cleanSummary = summary.trim()
        val quick = oneSentenceSummary.ifBlank {
            cleanSummary.lineSequence().firstOrNull { it.isNotBlank() }?.take(220) ?: ""
        }
        return DoubtItem(
            id = if (localId > 0L) localId else 0L,
            sessionId = 0L,
            timestamp = if (timestamp > 0L) timestamp else System.currentTimeMillis(),
            doubtSnippet = query,
            topic = category.ifBlank { "General" },
            quickAnswer = quick,
            detailedAnswer = cleanSummary,
            notificationSummary = oneSentenceSummary.ifBlank { quick },
            confidenceScore = confidenceScore.toFloat().coerceIn(0f, 1f),
            isStarred = isStarred,
            subject = category.ifBlank { "General" },
            customTag = customTag.ifBlank { "Education" },
            oneSentenceSummary = oneSentenceSummary
        )
    }

    companion object {
        fun fromRoomEntity(item: DoubtItem): FirestoreDoubtBackup {
            val resolvedQuery = item.doubtSnippet.trim().take(2000).ifBlank {
                item.topic.trim().take(2000).ifBlank { "Doubt" }
            }
            val resolvedCategory = item.topic.trim().take(60).ifBlank {
                item.subject.trim().take(60).ifBlank { "General" }
            }
            val resolvedSummary = item.detailedAnswer.ifBlank { item.quickAnswer }.take(10000)
            val resolvedOneSentence = item.oneSentenceSummary.ifBlank { item.notificationSummary }.take(500)
            return FirestoreDoubtBackup(
                id = "doubt_${item.id}",
                localId = item.id.coerceAtLeast(0L),
                query = resolvedQuery,
                summary = resolvedSummary,
                oneSentenceSummary = resolvedOneSentence,
                category = resolvedCategory,
                customTag = item.customTag.trim().take(60).ifBlank { "Education" },
                timestamp = item.timestamp.coerceAtLeast(0L),
                isResolved = resolvedSummary.isNotBlank() || item.isResolvedWithClarification,
                isStarred = item.isStarred,
                confidenceScore = item.confidenceScore.toDouble().coerceIn(0.0, 1.0),
                followUpCount = if (item.clarificationHistory.isBlank()) 0L else 1L
            )
        }

        fun fromSnapshot(doc: DocumentSnapshot): FirestoreDoubtBackup? {
            if (!doc.exists()) return null
            return FirestoreDoubtBackup(
                id = doc.id,
                localId = doc.getLong("localId") ?: 0L,
                query = doc.getString("query").orEmpty(),
                summary = doc.getString("summary").orEmpty(),
                oneSentenceSummary = doc.getString("oneSentenceSummary").orEmpty(),
                category = doc.getString("category").orEmpty().ifBlank { "General" },
                customTag = doc.getString("customTag").orEmpty().ifBlank { "Education" },
                timestamp = doc.getLong("timestamp") ?: 0L,
                isResolved = doc.getBoolean("isResolved") ?: false,
                isStarred = doc.getBoolean("isStarred") ?: false,
                confidenceScore = doc.getDouble("confidenceScore")
                    ?: (doc.get("confidenceScore") as? Number)?.toDouble()
                    ?: 0.9,
                followUpCount = doc.getLong("followUpCount") ?: 0L,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

@IgnoreExtraProperties
data class FirestoreSessionBackup(
    @DocumentId
    val id: String = "",
    val localId: Long = 0L,
    val title: String = "Conversation Session",
    val answerJson: String = "[]",
    val customTag: String = "General",
    val category: String = "General",
    @get:PropertyName("isPinned")
    @set:PropertyName("isPinned")
    var isPinned: Boolean = false,
    val timestamp: Long = 0L,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "localId" to localId.coerceAtLeast(0L),
            "title" to title.trim().take(300).ifBlank { "Conversation Session" },
            "answerJson" to answerJson.take(40000).ifBlank { "[]" },
            "customTag" to customTag.trim().take(60).ifBlank { null },
            "category" to category.trim().take(60).ifBlank { null },
            "isPinned" to isPinned,
            "timestamp" to timestamp.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(existingCreatedAt: Timestamp): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "localId" to localId.coerceAtLeast(0L),
            "title" to title.trim().take(300).ifBlank { "Conversation Session" },
            "answerJson" to answerJson.take(40000).ifBlank { "[]" },
            "customTag" to customTag.trim().take(60).ifBlank { null },
            "category" to category.trim().take(60).ifBlank { null },
            "isPinned" to isPinned,
            "timestamp" to timestamp.coerceAtLeast(0L),
            "createdAt" to existingCreatedAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toRoomEntity(): ConversationSession {
        val ts = if (timestamp > 0L) timestamp else System.currentTimeMillis()
        val dummy = ConversationSession(transcriptJson = answerJson)
        val preview = dummy.getTranscriptTurns().lastOrNull().orEmpty().take(160)
        return ConversationSession(
            id = if (localId > 0L) localId else 0L,
            startTime = ts,
            endTime = ts,
            title = title.ifBlank { "Conversation Session" },
            category = category.ifBlank { "General" },
            transcriptJson = answerJson.ifBlank { "[]" },
            transcriptPreview = preview,
            customTag = customTag.ifBlank { "General" },
            isPinned = isPinned
        )
    }

    companion object {
        fun fromRoomEntity(session: ConversationSession): FirestoreSessionBackup {
            return FirestoreSessionBackup(
                id = "session_${session.id}",
                localId = session.id.coerceAtLeast(0L),
                title = session.title.trim().take(300).ifBlank { "Conversation Session" },
                answerJson = session.transcriptJson.take(40000).ifBlank { "[]" },
                customTag = session.customTag.trim().take(60).ifBlank { "General" },
                category = session.category.trim().take(60).ifBlank { "General" },
                isPinned = session.isPinned,
                timestamp = session.endTime.coerceAtLeast(0L)
            )
        }

        fun fromSnapshot(doc: DocumentSnapshot): FirestoreSessionBackup? {
            if (!doc.exists()) return null
            return FirestoreSessionBackup(
                id = doc.id,
                localId = doc.getLong("localId") ?: 0L,
                title = doc.getString("title").orEmpty().ifBlank { "Conversation Session" },
                answerJson = doc.getString("answerJson").orEmpty().ifBlank { "[]" },
                customTag = doc.getString("customTag").orEmpty().ifBlank { "General" },
                category = doc.getString("category").orEmpty().ifBlank { "General" },
                isPinned = doc.getBoolean("isPinned") ?: false,
                timestamp = doc.getLong("timestamp") ?: 0L,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}
