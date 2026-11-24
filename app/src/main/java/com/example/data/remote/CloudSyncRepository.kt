package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class CloudBackupSummary(
    val uploadedDoubts: Int,
    val uploadedSessions: Int,
    val syncedAtMillis: Long = System.currentTimeMillis()
)

data class CloudRestoreResult(
    val doubts: List<DoubtItem>,
    val sessions: List<ConversationSession>
)

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth? = resolveDefaultAuth()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        resolveDefaultAuth()
    )

    companion object {
        fun isValidCloudId(raw: String): Boolean = ConversationSession.isValidCloudId(raw)

        fun resolveDoubtCloudId(item: DoubtItem): String =
            ConversationSession.sanitizeCloudId(
                item.cloudId.ifBlank {
                    if (item.id > 0L) "doubt_${item.id}" else DoubtItem.newCloudId()
                },
                fallbackPrefix = "doubt"
            )

        fun resolveSessionCloudId(session: ConversationSession): String =
            ConversationSession.sanitizeCloudId(
                session.cloudId.ifBlank {
                    if (session.id > 0L) "session_${session.id}" else ConversationSession.newCloudId("session")
                },
                fallbackPrefix = "session"
            )

        private fun resolveDefaultAuth(): FirebaseAuth? =
            try {
                FirebaseAuth.getInstance()
            } catch (_: Exception) {
                null
            }
    }

    private fun doubtsCollection(userId: String) =
        db.collection("users").document(userId).collection("doubts")

    private fun sessionsCollection(userId: String) =
        db.collection("users").document(userId).collection("sessions")

    fun observeDoubts(userId: String): Flow<List<FirestoreDoubtBackup>> {
        return doubtsCollection(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { FirestoreDoubtBackup.fromSnapshot(it) }
                    .sortedByDescending { it.timestamp }
            }
    }

    fun observeSessions(userId: String): Flow<List<FirestoreSessionBackup>> {
        return sessionsCollection(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { FirestoreSessionBackup.fromSnapshot(it) }
                    .sortedByDescending { it.endTime.coerceAtLeast(it.timestamp) }
            }
    }

    suspend fun getDoubtById(userId: String, docId: String): Result<FirestoreDoubtBackup?> {
        val safeDocId = ConversationSession.sanitizeCloudId(docId, "doubt")
        val docRef = doubtsCollection(userId).document(safeDocId)
        return try {
            val snap = docRef.get().await()
            Result.success(FirestoreDoubtBackup.fromSnapshot(snap))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun upsertDoubt(
        userId: String,
        item: DoubtItem,
        docId: String = resolveDoubtCloudId(item)
    ): Result<String> {
        val safeDocId = ConversationSession.sanitizeCloudId(docId, "doubt")
        val docRef = doubtsCollection(userId).document(safeDocId)
        return try {
            val existingSnap = docRef.get().await()
            val backup = FirestoreDoubtBackup.fromRoomEntity(item).copy(
                id = safeDocId,
                cloudId = safeDocId
            )
            val existingCreatedAt = existingSnap.getTimestamp("createdAt")
            if (existingSnap.exists() && existingCreatedAt != null) {
                docRef.set(backup.toUpdateMap(existingCreatedAt)).await()
            } else {
                docRef.set(backup.toCreateMap()).await()
            }
            Result.success(safeDocId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteDoubt(userId: String, docId: String): Result<Unit> {
        val safeDocId = ConversationSession.sanitizeCloudId(docId, "doubt")
        val docRef = doubtsCollection(userId).document(safeDocId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun upsertSession(
        userId: String,
        session: ConversationSession,
        docId: String = resolveSessionCloudId(session)
    ): Result<String> {
        val safeDocId = ConversationSession.sanitizeCloudId(docId, "session")
        val docRef = sessionsCollection(userId).document(safeDocId)
        return try {
            val existingSnap = docRef.get().await()
            val backup = FirestoreSessionBackup.fromRoomEntity(session).copy(
                id = safeDocId,
                cloudId = safeDocId
            )
            val existingCreatedAt = existingSnap.getTimestamp("createdAt")
            if (existingSnap.exists() && existingCreatedAt != null) {
                docRef.set(backup.toUpdateMap(existingCreatedAt)).await()
            } else {
                docRef.set(backup.toCreateMap()).await()
            }
            Result.success(safeDocId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(userId: String, docId: String): Result<Unit> {
        val safeDocId = ConversationSession.sanitizeCloudId(docId, "session")
        val docRef = sessionsCollection(userId).document(safeDocId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun fetchRawBackups(
        userId: String
    ): Result<Pair<List<FirestoreSessionBackup>, List<FirestoreDoubtBackup>>> {
        val sessionsRef = sessionsCollection(userId)
        val doubtsRef = doubtsCollection(userId)
        return try {
            val sessionBackups = sessionsRef.get().await().documents
                .mapNotNull { FirestoreSessionBackup.fromSnapshot(it) }
                .sortedByDescending { it.endTime.coerceAtLeast(it.timestamp) }
            val doubtBackups = doubtsRef.get().await().documents
                .mapNotNull { FirestoreDoubtBackup.fromSnapshot(it) }
                .sortedByDescending { it.timestamp }
            Result.success(sessionBackups to doubtBackups)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$userId", auth)
            Result.failure(e)
        }
    }

    suspend fun backupAll(
        userId: String,
        doubts: List<DoubtItem>,
        sessions: List<ConversationSession>
    ): Result<CloudBackupSummary> {
        val doubtsRef = doubtsCollection(userId)
        val sessionsRef = sessionsCollection(userId)
        return try {
            val existingDoubts = doubtsRef.get().await().documents.associate {
                it.id to it.getTimestamp("createdAt")
            }
            val existingSessions = sessionsRef.get().await().documents.associate {
                it.id to it.getTimestamp("createdAt")
            }

            val sessionCloudIdByLocalId = sessions.associate { session ->
                session.id to resolveSessionCloudId(session)
            }

            var sessionCount = 0
            sessions.forEach { session ->
                val docId = resolveSessionCloudId(session)
                val backup = FirestoreSessionBackup.fromRoomEntity(session).copy(
                    id = docId,
                    cloudId = docId
                )
                val existingCreatedAt = existingSessions[docId]
                val payload = if (existingCreatedAt != null) {
                    backup.toUpdateMap(existingCreatedAt)
                } else {
                    backup.toCreateMap()
                }
                sessionsRef.document(docId).set(payload).await()
                sessionCount++
            }

            var doubtCount = 0
            doubts.forEach { item ->
                val docId = resolveDoubtCloudId(item)
                val resolvedSessionCloudId = item.sessionCloudId
                    .takeIf { it.isNotBlank() && isValidCloudId(it) }
                    ?: sessionCloudIdByLocalId[item.sessionId]
                    ?: ""
                val backup = FirestoreDoubtBackup.fromRoomEntity(
                    item.copy(sessionCloudId = resolvedSessionCloudId)
                ).copy(
                    id = docId,
                    cloudId = docId,
                    sessionCloudId = resolvedSessionCloudId
                )
                val existingCreatedAt = existingDoubts[docId]
                val payload = if (existingCreatedAt != null) {
                    backup.toUpdateMap(existingCreatedAt)
                } else {
                    backup.toCreateMap()
                }
                doubtsRef.document(docId).set(payload).await()
                doubtCount++
            }

            Result.success(CloudBackupSummary(uploadedDoubts = doubtCount, uploadedSessions = sessionCount))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId", auth)
            Result.failure(e)
        }
    }

    suspend fun restoreAll(userId: String): Result<CloudRestoreResult> {
        return fetchRawBackups(userId).map { (sessionBackups, doubtBackups) ->
            CloudRestoreResult(
                doubts = doubtBackups.map { it.toRoomEntity() },
                sessions = sessionBackups.map { it.toRoomEntity() }
            )
        }
    }
}
