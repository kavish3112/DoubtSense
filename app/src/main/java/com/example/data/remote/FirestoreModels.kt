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
    val cloudId: String = "",
    val sessionCloudId: String = "",
    val query: String = "",
    val summary: String = "",
    val oneSentenceSummary: String = "",
    val category: String = "General",
    val subject: String = "General",
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
    val quickAnswer: String = "",
    val notificationSummary: String = "",
    val conversationalContext: String = "",
    val importantConcepts: String = "",
    val doubtType: String = "",
    val intent: String = "",
    @get:PropertyName("isAmbiguous")
    @set:PropertyName("isAmbiguous")
    var isAmbiguous: Boolean = false,
    val clarificationQuestion: String = "",
    val suggestedOptionsJson: String = "[]",
    val referenceMaterials: String = "",
    val conversationSuggestions: String = "",
    @get:PropertyName("isFactChecked")
    @set:PropertyName("isFactChecked")
    var isFactChecked: Boolean = false,
    val verificationStatus: String = "",
    val verificationSourcesJson: String = "[]",
    val updatedAtMillis: Long = 0L,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    private fun baseFieldMap(): MutableMap<String, Any?> {
        val resolvedCloudId = ConversationSession.sanitizeCloudId(
            cloudId.ifBlank { id },
            fallbackPrefix = "doubt"
        )
        val resolvedSessionCloudId = sessionCloudId
            .takeIf { it.isNotBlank() && ConversationSession.isValidCloudId(it) }
            .orEmpty()
        return mutableMapOf(
            "localId" to localId.coerceAtLeast(0L),
            "cloudId" to resolvedCloudId,
            "sessionCloudId" to resolvedSessionCloudId,
            "query" to query.trim().take(2000).ifBlank { "Doubt" },
            "summary" to summary.take(10000),
            "oneSentenceSummary" to oneSentenceSummary.take(500).ifBlank { null },
            "category" to category.trim().take(60).ifBlank { "General" },
            "subject" to subject.trim().take(60).ifBlank { null },
            "customTag" to customTag.trim().take(120).ifBlank { null },
            "timestamp" to timestamp.coerceAtLeast(0L),
            "isResolved" to isResolved,
            "isStarred" to isStarred,
            "confidenceScore" to confidenceScore.coerceIn(0.0, 1.0),
            "followUpCount" to followUpCount.coerceAtLeast(0L),
            "quickAnswer" to quickAnswer.take(4000).ifBlank { null },
            "notificationSummary" to notificationSummary.take(1000).ifBlank { null },
            "conversationalContext" to conversationalContext.take(4000).ifBlank { null },
            "importantConcepts" to importantConcepts.take(1000).ifBlank { null },
            "doubtType" to doubtType.take(120).ifBlank { null },
            "intent" to intent.take(1000).ifBlank { null },
            "isAmbiguous" to isAmbiguous,
            "clarificationQuestion" to clarificationQuestion.take(1000).ifBlank { null },
            "suggestedOptionsJson" to suggestedOptionsJson.take(2000).ifBlank { null },
            "referenceMaterials" to referenceMaterials.take(2000).ifBlank { null },
            "conversationSuggestions" to conversationSuggestions.take(2000).ifBlank { null },
            "isFactChecked" to isFactChecked,
            "verificationStatus" to verificationStatus.take(500).ifBlank { null },
            "verificationSourcesJson" to verificationSourcesJson.take(10000).ifBlank { null },
            "updatedAtMillis" to updatedAtMillis.coerceAtLeast(timestamp.coerceAtLeast(0L))
        )
    }

    fun toCreateMap(): Map<String, Any> {
        val raw = baseFieldMap().apply {
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(existingCreatedAt: Timestamp): Map<String, Any> {
        val raw = baseFieldMap().apply {
            put("createdAt", existingCreatedAt)
            put("updatedAt", FieldValue.serverTimestamp())
        }
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toRoomEntity(): DoubtItem {
        val resolvedCloudId = ConversationSession.sanitizeCloudId(
            cloudId.ifBlank { id },
            fallbackPrefix = "doubt"
        )
        val resolvedSessionCloudId = sessionCloudId
            .takeIf { it.isNotBlank() && ConversationSession.isValidCloudId(it) }
            .orEmpty()
        val cleanSummary = summary.trim()
        val resolvedQuick = quickAnswer.ifBlank {
            oneSentenceSummary.ifBlank {
                cleanSummary.lineSequence().firstOrNull { it.isNotBlank() }?.take(220) ?: ""
            }
        }
        val resolvedTs = if (timestamp > 0L) timestamp else System.currentTimeMillis()
        val resolvedUpdatedAt = when {
            updatedAtMillis > 0L -> updatedAtMillis
            updatedAt != null -> updatedAt.toDate().time
            else -> resolvedTs
        }
        return DoubtItem(
            id = if (localId > 0L) localId else 0L,
            sessionId = 0L,
            timestamp = resolvedTs,
            doubtSnippet = query,
            topic = category.ifBlank { "General" },
            quickAnswer = resolvedQuick,
            detailedAnswer = cleanSummary,
            notificationSummary = notificationSummary.ifBlank { oneSentenceSummary.ifBlank { resolvedQuick } },
            conversationalContext = conversationalContext,
            isAmbiguous = isAmbiguous,
            clarificationQuestion = clarificationQuestion.ifBlank { null },
            confidenceScore = confidenceScore.toFloat().coerceIn(0f, 1f),
            isStarred = isStarred,
            subject = subject.ifBlank { category.ifBlank { "General" } },
            importantConcepts = importantConcepts,
            doubtType = doubtType,
            intent = intent,
            suggestedOptionsJson = suggestedOptionsJson.ifBlank { "[]" },
            referenceMaterials = referenceMaterials,
            conversationSuggestions = conversationSuggestions,
            isResolvedWithClarification = isResolved,
            isFactChecked = isFactChecked,
            verificationStatus = verificationStatus,
            verificationSourcesJson = verificationSourcesJson.ifBlank { "[]" },
            customTag = customTag.ifBlank { "Education" },
            oneSentenceSummary = oneSentenceSummary,
            cloudId = resolvedCloudId,
            sessionCloudId = resolvedSessionCloudId,
            updatedAtMillis = resolvedUpdatedAt
        )
    }

    companion object {
        fun fromRoomEntity(item: DoubtItem): FirestoreDoubtBackup {
            val resolvedCloudId = ConversationSession.sanitizeCloudId(
                item.cloudId.ifBlank {
                    if (item.id > 0L) "doubt_${item.id}" else DoubtItem.newCloudId()
                },
                fallbackPrefix = "doubt"
            )
            val resolvedSessionCloudId = item.sessionCloudId
                .takeIf { it.isNotBlank() && ConversationSession.isValidCloudId(it) }
                .orEmpty()
            val resolvedQuery = item.doubtSnippet.trim().take(2000).ifBlank {
                item.topic.trim().take(2000).ifBlank { "Doubt" }
            }
            val resolvedCategory = item.topic.trim().take(60).ifBlank {
                item.subject.trim().take(60).ifBlank { "General" }
            }
            val resolvedSubject = item.subject.trim().take(60).ifBlank { resolvedCategory }
            val resolvedSummary = item.detailedAnswer.ifBlank { item.quickAnswer }.take(10000)
            val resolvedOneSentence = item.oneSentenceSummary.ifBlank { item.notificationSummary }.take(500)
            return FirestoreDoubtBackup(
                id = resolvedCloudId,
                localId = item.id.coerceAtLeast(0L),
                cloudId = resolvedCloudId,
                sessionCloudId = resolvedSessionCloudId,
                query = resolvedQuery,
                summary = resolvedSummary,
                oneSentenceSummary = resolvedOneSentence,
                category = resolvedCategory,
                subject = resolvedSubject,
                customTag = item.customTag.trim().take(120).ifBlank { "Education" },
                timestamp = item.timestamp.coerceAtLeast(0L),
                isResolved = resolvedSummary.isNotBlank() || item.isResolvedWithClarification,
                isStarred = item.isStarred,
                confidenceScore = item.confidenceScore.toDouble().coerceIn(0.0, 1.0),
                followUpCount = if (item.clarificationHistory.isBlank()) 0L else 1L,
                quickAnswer = item.quickAnswer.take(4000),
                notificationSummary = item.notificationSummary.take(1000),
                conversationalContext = item.conversationalContext.take(4000),
                importantConcepts = item.importantConcepts.take(1000),
                doubtType = item.doubtType.take(120),
                intent = item.intent.take(1000),
                isAmbiguous = item.isAmbiguous,
                clarificationQuestion = item.clarificationQuestion.orEmpty().take(1000),
                suggestedOptionsJson = item.suggestedOptionsJson.take(2000).ifBlank { "[]" },
                referenceMaterials = item.referenceMaterials.take(2000),
                conversationSuggestions = item.conversationSuggestions.take(2000),
                isFactChecked = item.isFactChecked,
                verificationStatus = item.verificationStatus.take(500),
                verificationSourcesJson = item.verificationSourcesJson.take(10000).ifBlank { "[]" },
                updatedAtMillis = item.updatedAtMillis.coerceAtLeast(item.timestamp.coerceAtLeast(0L))
            )
        }

        fun fromSnapshot(doc: DocumentSnapshot): FirestoreDoubtBackup? {
            if (!doc.exists()) return null
            val resolvedCloudId = doc.getString("cloudId")
                ?.takeIf { ConversationSession.isValidCloudId(it) }
                ?: ConversationSession.sanitizeCloudId(doc.id, "doubt")
            val ts = (doc.get("timestamp") as? Number)?.toLong() ?: 0L
            val estimatedUpdated = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val updatedMs = (doc.get("updatedAtMillis") as? Number)?.toLong()
                ?: estimatedUpdated?.toDate()?.time
                ?: ts
            return FirestoreDoubtBackup(
                id = doc.id,
                localId = (doc.get("localId") as? Number)?.toLong() ?: 0L,
                cloudId = resolvedCloudId,
                sessionCloudId = doc.getString("sessionCloudId").orEmpty(),
                query = doc.getString("query").orEmpty(),
                summary = doc.getString("summary").orEmpty(),
                oneSentenceSummary = doc.getString("oneSentenceSummary").orEmpty(),
                category = doc.getString("category").orEmpty().ifBlank { "General" },
                subject = doc.getString("subject").orEmpty().ifBlank {
                    doc.getString("category").orEmpty().ifBlank { "General" }
                },
                customTag = doc.getString("customTag").orEmpty().ifBlank { "Education" },
                timestamp = ts,
                isResolved = doc.getBoolean("isResolved") ?: false,
                isStarred = doc.getBoolean("isStarred") ?: false,
                confidenceScore = (doc.get("confidenceScore") as? Number)?.toDouble()?.coerceIn(0.0, 1.0) ?: 0.9,
                followUpCount = (doc.get("followUpCount") as? Number)?.toLong() ?: 0L,
                quickAnswer = doc.getString("quickAnswer").orEmpty(),
                notificationSummary = doc.getString("notificationSummary").orEmpty(),
                conversationalContext = doc.getString("conversationalContext").orEmpty(),
                importantConcepts = doc.getString("importantConcepts").orEmpty(),
                doubtType = doc.getString("doubtType").orEmpty(),
                intent = doc.getString("intent").orEmpty(),
                isAmbiguous = doc.getBoolean("isAmbiguous") ?: false,
                clarificationQuestion = doc.getString("clarificationQuestion").orEmpty(),
                suggestedOptionsJson = doc.getString("suggestedOptionsJson").orEmpty().ifBlank { "[]" },
                referenceMaterials = doc.getString("referenceMaterials").orEmpty(),
                conversationSuggestions = doc.getString("conversationSuggestions").orEmpty(),
                isFactChecked = doc.getBoolean("isFactChecked") ?: false,
                verificationStatus = doc.getString("verificationStatus").orEmpty(),
                verificationSourcesJson = doc.getString("verificationSourcesJson").orEmpty().ifBlank { "[]" },
                updatedAtMillis = updatedMs,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = estimatedUpdated
            )
        }
    }
}

@IgnoreExtraProperties
data class FirestoreSessionBackup(
    @DocumentId
    val id: String = "",
    val localId: Long = 0L,
    val cloudId: String = "",
    val title: String = "Conversation Session",
    val answerJson: String = "[]",
    val customTag: String = "General",
    val category: String = "General",
    @get:PropertyName("isPinned")
    @set:PropertyName("isPinned")
    var isPinned: Boolean = false,
    val timestamp: Long = 0L,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val transcriptPreview: String = "",
    val averageDoubtIntensity: Double = 0.0,
    val peakDoubtIntensity: Double = 0.0,
    val doubtCount: Long = 0L,
    val summary: String = "",
    val updatedAtMillis: Long = 0L,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    private fun baseFieldMap(): MutableMap<String, Any?> {
        val resolvedCloudId = ConversationSession.sanitizeCloudId(
            cloudId.ifBlank { id },
            fallbackPrefix = "session"
        )
        val safeEnd = (if (endTime > 0L) endTime else timestamp).coerceAtLeast(0L)
        val safeStart = (if (startTime > 0L) startTime else safeEnd).coerceAtLeast(0L)
        return mutableMapOf(
            "localId" to localId.coerceAtLeast(0L),
            "cloudId" to resolvedCloudId,
            "title" to title.trim().take(300).ifBlank { "Conversation Session" },
            "answerJson" to answerJson.take(40000).ifBlank { "[]" },
            "customTag" to customTag.trim().take(120).ifBlank { null },
            "category" to category.trim().take(60).ifBlank { null },
            "isPinned" to isPinned,
            "timestamp" to safeEnd,
            "startTime" to safeStart,
            "endTime" to safeEnd,
            "transcriptPreview" to transcriptPreview.take(1000).ifBlank { null },
            "averageDoubtIntensity" to averageDoubtIntensity.coerceIn(0.0, 1.0),
            "peakDoubtIntensity" to peakDoubtIntensity.coerceIn(0.0, 1.0),
            "doubtCount" to doubtCount.coerceAtLeast(0L),
            "summary" to summary.take(2000).ifBlank { null },
            "updatedAtMillis" to updatedAtMillis.coerceAtLeast(safeEnd)
        )
    }

    fun toCreateMap(): Map<String, Any> {
        val raw = baseFieldMap().apply {
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(existingCreatedAt: Timestamp): Map<String, Any> {
        val raw = baseFieldMap().apply {
            put("createdAt", existingCreatedAt)
            put("updatedAt", FieldValue.serverTimestamp())
        }
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toRoomEntity(): ConversationSession {
        val resolvedCloudId = ConversationSession.sanitizeCloudId(
            cloudId.ifBlank { id },
            fallbackPrefix = "session"
        )
        val fallbackTs = if (timestamp > 0L) timestamp else System.currentTimeMillis()
        val resolvedEnd = if (endTime > 0L) endTime else fallbackTs
        val resolvedStart = if (startTime > 0L) startTime else resolvedEnd
        val dummy = ConversationSession(transcriptJson = answerJson.ifBlank { "[]" })
        val resolvedPreview = transcriptPreview.ifBlank {
            dummy.getTranscriptTurns().lastOrNull().orEmpty().take(160)
        }
        val resolvedUpdatedAt = when {
            updatedAtMillis > 0L -> updatedAtMillis
            updatedAt != null -> updatedAt.toDate().time
            else -> resolvedEnd
        }
        return ConversationSession(
            id = if (localId > 0L) localId else 0L,
            startTime = resolvedStart,
            endTime = resolvedEnd,
            title = title.ifBlank { "Conversation Session" },
            category = category.ifBlank { "General" },
            transcriptJson = answerJson.ifBlank { "[]" },
            transcriptPreview = resolvedPreview,
            averageDoubtIntensity = averageDoubtIntensity.toFloat().coerceIn(0f, 1f),
            peakDoubtIntensity = peakDoubtIntensity.toFloat().coerceIn(0f, 1f),
            doubtCount = doubtCount.coerceAtLeast(0L).toInt(),
            summary = summary,
            customTag = customTag.ifBlank { "General" },
            isPinned = isPinned,
            cloudId = resolvedCloudId,
            updatedAtMillis = resolvedUpdatedAt
        )
    }

    companion object {
        fun fromRoomEntity(session: ConversationSession): FirestoreSessionBackup {
            val resolvedCloudId = ConversationSession.sanitizeCloudId(
                session.cloudId.ifBlank {
                    if (session.id > 0L) "session_${session.id}" else ConversationSession.newCloudId("session")
                },
                fallbackPrefix = "session"
            )
            val safeEnd = session.endTime.coerceAtLeast(0L)
            val safeStart = session.startTime.coerceAtLeast(0L)
            return FirestoreSessionBackup(
                id = resolvedCloudId,
                localId = session.id.coerceAtLeast(0L),
                cloudId = resolvedCloudId,
                title = session.title.trim().take(300).ifBlank { "Conversation Session" },
                answerJson = session.transcriptJson.take(40000).ifBlank { "[]" },
                customTag = session.customTag.trim().take(120).ifBlank { "General" },
                category = session.category.trim().take(60).ifBlank { "General" },
                isPinned = session.isPinned,
                timestamp = safeEnd,
                startTime = safeStart,
                endTime = safeEnd,
                transcriptPreview = session.transcriptPreview.take(1000),
                averageDoubtIntensity = session.averageDoubtIntensity.toDouble().coerceIn(0.0, 1.0),
                peakDoubtIntensity = session.peakDoubtIntensity.toDouble().coerceIn(0.0, 1.0),
                doubtCount = session.doubtCount.coerceAtLeast(0).toLong(),
                summary = session.summary.take(2000),
                updatedAtMillis = session.updatedAtMillis.coerceAtLeast(safeEnd)
            )
        }

        fun fromSnapshot(doc: DocumentSnapshot): FirestoreSessionBackup? {
            if (!doc.exists()) return null
            val resolvedCloudId = doc.getString("cloudId")
                ?.takeIf { ConversationSession.isValidCloudId(it) }
                ?: ConversationSession.sanitizeCloudId(doc.id, "session")
            val ts = (doc.get("timestamp") as? Number)?.toLong() ?: 0L
            val end = ((doc.get("endTime") as? Number)?.toLong() ?: ts).coerceAtLeast(0L)
            val start = ((doc.get("startTime") as? Number)?.toLong() ?: end).coerceAtLeast(0L)
            val estimatedUpdated = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val updatedMs = (doc.get("updatedAtMillis") as? Number)?.toLong()
                ?: estimatedUpdated?.toDate()?.time
                ?: end
            return FirestoreSessionBackup(
                id = doc.id,
                localId = (doc.get("localId") as? Number)?.toLong() ?: 0L,
                cloudId = resolvedCloudId,
                title = doc.getString("title").orEmpty().ifBlank { "Conversation Session" },
                answerJson = doc.getString("answerJson").orEmpty().ifBlank { "[]" },
                customTag = doc.getString("customTag").orEmpty().ifBlank { "General" },
                category = doc.getString("category").orEmpty().ifBlank { "General" },
                isPinned = doc.getBoolean("isPinned") ?: false,
                timestamp = ts,
                startTime = start,
                endTime = end,
                transcriptPreview = doc.getString("transcriptPreview").orEmpty(),
                averageDoubtIntensity = (doc.get("averageDoubtIntensity") as? Number)?.toDouble()?.coerceIn(0.0, 1.0) ?: 0.0,
                peakDoubtIntensity = (doc.get("peakDoubtIntensity") as? Number)?.toDouble()?.coerceIn(0.0, 1.0) ?: 0.0,
                doubtCount = (doc.get("doubtCount") as? Number)?.toLong() ?: 0L,
                summary = doc.getString("summary").orEmpty(),
                updatedAtMillis = updatedMs,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = estimatedUpdated
            )
        }
    }
}
