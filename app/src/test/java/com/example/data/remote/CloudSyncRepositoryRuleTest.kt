package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAndRestoreDoubt_authenticatedOwner_succeedsAndRoundTrips() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore, auth)
        val docId = "doubt_${UUID.randomUUID().toString().replace("-", "_")}"

        val sampleDoubt = DoubtItem(
            id = 42L,
            timestamp = 1700000001000L,
            doubtSnippet = "What is structured concurrency in Kotlin?",
            topic = "Kotlin Coroutines",
            quickAnswer = "Coroutines are scoped so child jobs cancel with parents.",
            detailedAnswer = "Structured concurrency binds coroutine lifetimes to a CoroutineScope.",
            notificationSummary = "Coroutines share parent scope lifetimes.",
            confidenceScore = 0.94f,
            isStarred = true,
            customTag = "Work",
            oneSentenceSummary = "Structured concurrency scopes child coroutines to their parent."
        )

        val upsertResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertDoubt(uid, sampleDoubt, docId)
        }
        assertTrue("Expected upsert to succeed: ${upsertResult.exceptionOrNull()}", upsertResult.isSuccess)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getDoubtById(uid, docId)
        }
        assertTrue(fetched.isSuccess)
        val backup = fetched.getOrThrow()
        assertNotNull(backup)
        assertEquals("Work", backup?.customTag)
        assertEquals(true, backup?.isStarred)

        // Repeat update on existing doc preserves createdAt and succeeds
        val updatedResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertDoubt(uid, sampleDoubt.copy(customTag = "Education", isStarred = false), docId)
        }
        assertTrue("Expected update to succeed: ${updatedResult.exceptionOrNull()}", updatedResult.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeDoubts(uid).first { list -> list.any { it.id == docId } }
        }
        assertEquals("Education", observed.first { it.id == docId }.customTag)
    }

    @Test
    fun backupAllAndRestoreAll_authenticatedOwner_backsUpDoubtsAndSessions() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore, auth)
        val doubt = DoubtItem(
            id = 9001L,
            doubtSnippet = "Explain SQLite WAL mode",
            topic = "Databases",
            quickAnswer = "Write-Ahead Logging allows concurrent reads and writes.",
            detailedAnswer = "WAL appends changes to a separate log file.",
            notificationSummary = "WAL separates writes for concurrency.",
            customTag = "Education"
        )
        val session = ConversationSession(
            id = 9002L,
            title = "SQLite Tuning",
            category = "Engineering",
            transcriptJson = ConversationSession.encodeTranscriptTurns(listOf("Explain SQLite WAL mode")),
            customTag = "Work",
            isPinned = true
        )

        val summary = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.backupAll(uid, listOf(doubt), listOf(session))
        }
        assertTrue("Backup failed: ${summary.exceptionOrNull()}", summary.isSuccess)
        assertEquals(1, summary.getOrThrow().uploadedDoubts)
        assertEquals(1, summary.getOrThrow().uploadedSessions)

        val restored = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.restoreAll(uid)
        }
        assertTrue(restored.isSuccess)
        assertTrue(restored.getOrThrow().doubts.any { it.doubtSnippet == "Explain SQLite WAL mode" })
        assertTrue(restored.getOrThrow().sessions.any { it.title == "SQLite Tuning" })
    }

    @Test
    fun crossUserRead_differentAuthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore, auth)
        val docId = "doubt_${UUID.randomUUID().toString().replace("-", "_")}"

        val sample = DoubtItem(
            id = 77L,
            doubtSnippet = "Private Alice question",
            topic = "Personal",
            quickAnswer = "Quick note",
            detailedAnswer = "Detailed note",
            notificationSummary = "Summary"
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertDoubt(aliceUid, sample, docId).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudSyncRepository(firestore, auth)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getDoubtById(aliceUid, docId)
        }
        assertTrue(result.isFailure)
        val firestoreException = checkNotNull(result.exceptionOrNull()?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got ${result.exceptionOrNull()}"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun observeDoubts_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = CloudSyncRepository(firestore, auth)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeDoubts("alice_unauth_check").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun rawWrite_withGhostField_failsWithPermissionDenied() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val docId = "doubt_${UUID.randomUUID().toString().replace("-", "_")}"
        val backup = FirestoreDoubtBackup.fromRoomEntity(
            DoubtItem(
                id = 88L,
                doubtSnippet = "Test ghost field",
                topic = "Security",
                quickAnswer = "Quick",
                detailedAnswer = "Detailed",
                notificationSummary = "Summary"
            )
        )
        val payloadWithGhost = backup.toCreateMap().toMutableMap().apply {
            put("isAdminOverride", true)
        }

        var caught: Exception? = null
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(uid)
                    .collection("doubts").document(docId)
                    .set(payloadWithGhost)
                    .await()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for ghost field, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
