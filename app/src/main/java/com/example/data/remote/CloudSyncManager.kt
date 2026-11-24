package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import com.example.data.repository.CloudMergeStats
import com.example.data.repository.CloudSyncHook
import com.example.data.repository.DoubtRepository
import com.example.util.AppPreferencesManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

private const val TAG = "CloudSyncManager"

sealed interface CloudSyncUiState {
    data object SignedOut : CloudSyncUiState
    data class Syncing(val message: String = "Syncing sessions across devices...") : CloudSyncUiState
    data class Synced(
        val userEmail: String?,
        val userName: String?,
        val lastSyncedMillis: Long,
        val sessionCount: Int,
        val doubtCount: Int
    ) : CloudSyncUiState
    data class OfflinePaused(val userEmail: String?) : CloudSyncUiState
    data class Error(val message: String) : CloudSyncUiState
}

/**
 * Coordinates auth-gated bidirectional synchronization between local Room persistence and
 * per-user Firestore collections (`/users/{userId}/sessions` & `/users/{userId}/doubts`).
 */
object CloudSyncManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncState = MutableStateFlow<CloudSyncUiState>(CloudSyncUiState.SignedOut)
    val syncState: StateFlow<CloudSyncUiState> = _syncState.asStateFlow()

    private val _activeUser = MutableStateFlow<FirebaseUser?>(null)
    val activeUser: StateFlow<FirebaseUser?> = _activeUser.asStateFlow()

    @Volatile
    private var attachedRepository: DoubtRepository? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var syncRepository: CloudSyncRepository? = null

    @Volatile
    private var activeUserId: String? = null

    private var listenerParentJob: Job? = null
    private val pendingSyncMutex = Any()

    // Track cloudIds known to exist remotely so genuine remote deletions remove local copies,
    // while newly created offline local sessions are never wiped before initial upload.
    private val knownRemoteSessionIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val knownRemoteDoubtIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val inFlightSessionDeletes: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val inFlightDoubtDeletes: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private val syncHook = object : CloudSyncHook {
        override fun onSessionSaved(session: ConversationSession) {
            val uid = activeUserId ?: return
            val remote = syncRepository ?: return
            if (!shouldAutoSyncNow()) return
            val cloudId = CloudSyncRepository.resolveSessionCloudId(session)
            scope.launch {
                val res = remote.upsertSession(uid, session.copy(cloudId = cloudId), cloudId)
                if (res.isSuccess) {
                    knownRemoteSessionIds.add(cloudId)
                    markSyncedSnapshot()
                }
            }
        }

        override fun onSessionDeleted(cloudId: String) {
            val uid = activeUserId ?: return
            val remote = syncRepository ?: return
            if (!shouldAutoSyncNow() || cloudId.isBlank()) return
            inFlightSessionDeletes.add(cloudId)
            knownRemoteSessionIds.remove(cloudId)
            scope.launch {
                try {
                    remote.deleteSession(uid, cloudId)
                    markSyncedSnapshot()
                } finally {
                    inFlightSessionDeletes.remove(cloudId)
                }
            }
        }

        override fun onDoubtSaved(doubt: DoubtItem) {
            val uid = activeUserId ?: return
            val remote = syncRepository ?: return
            if (!shouldAutoSyncNow()) return
            val cloudId = CloudSyncRepository.resolveDoubtCloudId(doubt)
            scope.launch {
                val res = remote.upsertDoubt(uid, doubt.copy(cloudId = cloudId), cloudId)
                if (res.isSuccess) {
                    knownRemoteDoubtIds.add(cloudId)
                    markSyncedSnapshot()
                }
            }
        }

        override fun onDoubtDeleted(cloudId: String) {
            val uid = activeUserId ?: return
            val remote = syncRepository ?: return
            if (!shouldAutoSyncNow() || cloudId.isBlank()) return
            inFlightDoubtDeletes.add(cloudId)
            knownRemoteDoubtIds.remove(cloudId)
            scope.launch {
                try {
                    remote.deleteDoubt(uid, cloudId)
                    markSyncedSnapshot()
                } finally {
                    inFlightDoubtDeletes.remove(cloudId)
                }
            }
        }
    }

    fun resolveFirebaseAuthOrNull(): FirebaseAuth? =
        runCatching { FirebaseAuth.getInstance() }.getOrNull()

    fun attachRepository(context: Context, repo: DoubtRepository) {
        appContext = context.applicationContext
        if (attachedRepository !== repo) {
            attachedRepository = repo
            repo.setCloudSyncHook(syncHook)
        }
    }

    private fun ensureSyncRepository(context: Context): CloudSyncRepository? {
        syncRepository?.let { return it }
        return runCatching { CloudSyncRepository(context.applicationContext) }
            .onFailure { Log.w(TAG, "CloudSyncRepository unavailable in this context: ${it.message}") }
            .getOrNull()
            ?.also { syncRepository = it }
    }

    private fun shouldAutoSyncNow(): Boolean {
        return !AppPreferencesManager.offlineMode.value && AppPreferencesManager.autoCloudSyncEnabled.value
    }

    /**
     * Binds or tears down real-time Firestore cross-device sync for the current Firebase user.
     */
    fun bindAuthenticatedSession(
        context: Context,
        repo: DoubtRepository,
        user: FirebaseUser?,
        autoSyncEnabled: Boolean = AppPreferencesManager.autoCloudSyncEnabled.value,
        offlineMode: Boolean = AppPreferencesManager.offlineMode.value
    ) {
        attachRepository(context, repo)
        _activeUser.value = user

        if (user == null) {
            scope.launch {
                stopActiveListeners()
                activeUserId = null
                knownRemoteSessionIds.clear()
                knownRemoteDoubtIds.clear()
                _syncState.value = CloudSyncUiState.SignedOut
            }
            return
        }

        if (offlineMode || !autoSyncEnabled) {
            scope.launch {
                stopActiveListeners()
                activeUserId = user.uid
                _syncState.value = CloudSyncUiState.OfflinePaused(user.email)
            }
            return
        }

        val remote = ensureSyncRepository(context) ?: run {
            _syncState.value = CloudSyncUiState.Error("Firebase Cloud Sync is unavailable on this device.")
            return
        }

        synchronized(pendingSyncMutex) {
            if (activeUserId == user.uid && listenerParentJob?.isActive == true) {
                return
            }
        }

        scope.launch {
            stopActiveListeners()
            activeUserId = user.uid
            startAuthenticatedSyncPipeline(context.applicationContext, repo, remote, user)
        }
    }

    private fun startAuthenticatedSyncPipeline(
        context: Context,
        repo: DoubtRepository,
        remote: CloudSyncRepository,
        user: FirebaseUser
    ) {
        val uid = user.uid
        val job = SupervisorJob()
        listenerParentJob = job
        val auth = resolveFirebaseAuthOrNull()

        scope.launch(job) {
            _syncState.value = CloudSyncUiState.Syncing("Syncing chat sessions across devices...")
            try {
                // Step 1: Bidirectional initial reconciliation (upload unpushed/newer local rows BEFORE observing deletions)
                reconcileBidirectional(context, repo, remote, user)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Initial bidirectional sync failed", e)
                if (activeUserId == uid && auth?.currentUser?.uid == uid) {
                    _syncState.value = CloudSyncUiState.Error(
                        e.localizedMessage ?: "Unable to complete initial cloud sync."
                    )
                }
                return@launch
            }

            // Step 2: Continuous real-time Firestore snapshot listener for sessions
            launch {
                try {
                    remote.observeSessions(uid).collectLatest { remoteBackups ->
                        if (activeUserId != uid || auth?.currentUser?.uid != uid) return@collectLatest
                        val incomingIds = remoteBackups.map { it.cloudId }.toSet()
                        val removedIds = knownRemoteSessionIds.filter {
                            it !in incomingIds && it !in inFlightSessionDeletes
                        }
                        removedIds.forEach { repo.deleteSessionByCloudId(it) }

                        val entities = remoteBackups.map { it.toRoomEntity() }
                        repo.mergeRemoteSnapshot(remoteSessions = entities, remoteDoubts = emptyList())

                        knownRemoteSessionIds.clear()
                        knownRemoteSessionIds.addAll(incomingIds)
                        markSyncedSnapshot(user = user)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (activeUserId == uid && auth?.currentUser?.uid == uid) {
                        handleFirestoreError(e, OperationType.LIST, "users/$uid/sessions", auth)
                        _syncState.value = CloudSyncUiState.Error(
                            e.localizedMessage ?: "Real-time session listener interrupted."
                        )
                    }
                }
            }

            // Step 3: Continuous real-time Firestore snapshot listener for doubts
            launch {
                try {
                    remote.observeDoubts(uid).collectLatest { remoteBackups ->
                        if (activeUserId != uid || auth?.currentUser?.uid != uid) return@collectLatest
                        val incomingIds = remoteBackups.map { it.cloudId }.toSet()
                        val removedIds = knownRemoteDoubtIds.filter {
                            it !in incomingIds && it !in inFlightDoubtDeletes
                        }
                        removedIds.forEach { repo.deleteDoubtByCloudId(it) }

                        val entities = remoteBackups.map { it.toRoomEntity() }
                        repo.mergeRemoteSnapshot(remoteSessions = emptyList(), remoteDoubts = entities)

                        knownRemoteDoubtIds.clear()
                        knownRemoteDoubtIds.addAll(incomingIds)
                        markSyncedSnapshot(user = user)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (activeUserId == uid && auth?.currentUser?.uid == uid) {
                        handleFirestoreError(e, OperationType.LIST, "users/$uid/doubts", auth)
                        _syncState.value = CloudSyncUiState.Error(
                            e.localizedMessage ?: "Real-time doubt listener interrupted."
                        )
                    }
                }
            }
        }
    }

    /**
     * Performs a manual or initial two-way sync:
     * 1. Fetches current cloud backups
     * 2. Uploads any local sessions/doubts missing in cloud or newer locally
     * 3. Merges cloud items missing locally or newer in cloud back into Room
     */
    suspend fun syncNow(context: Context): Result<CloudMergeStats> {
        val appCtx = context.applicationContext
        val auth = resolveFirebaseAuthOrNull()
        val user = auth?.currentUser
            ?: return Result.failure(IllegalStateException("Sign in with Google first to sync across devices."))
        if (AppPreferencesManager.offlineMode.value) {
            return Result.failure(IllegalStateException("Offline Mode is enabled. Disable Offline Mode to sync."))
        }
        val repo = attachedRepository
            ?: return Result.failure(IllegalStateException("Local database repository is not attached."))
        val remote = ensureSyncRepository(appCtx)
            ?: return Result.failure(IllegalStateException("Cloud database is unavailable."))

        _syncState.value = CloudSyncUiState.Syncing("Syncing chat sessions & doubts...")
        return try {
            val stats = reconcileBidirectional(appCtx, repo, remote, user)
            Result.success(stats)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _syncState.value = CloudSyncUiState.Error(e.localizedMessage ?: "Cloud sync failed.")
            Result.failure(e)
        }
    }

    private suspend fun reconcileBidirectional(
        context: Context,
        repo: DoubtRepository,
        remote: CloudSyncRepository,
        user: FirebaseUser
    ): CloudMergeStats {
        val uid = user.uid
        val remoteResult = remote.fetchRawBackups(uid).getOrThrow()
        val (remoteSessionBackups, remoteDoubtBackups) = remoteResult

        val remoteSessionByCloudId = remoteSessionBackups.associateBy { it.cloudId }
        val remoteDoubtByCloudId = remoteDoubtBackups.associateBy { it.cloudId }

        val localSessions = repo.getAllSessionsOnce()
        val localDoubts = repo.getAllDoubtsOnce()

        // Ensure every local session and doubt has a valid persisted cloudId
        val normalizedLocalSessions = localSessions.map { session ->
            val resolvedCloudId = CloudSyncRepository.resolveSessionCloudId(session)
            if (resolvedCloudId != session.cloudId) {
                val fixed = session.copy(cloudId = resolvedCloudId)
                repo.updateSession(fixed)
                fixed
            } else {
                session
            }
        }
        val sessionCloudIdByLocalId = normalizedLocalSessions.associate { it.id to it.cloudId }
        val normalizedLocalDoubts = localDoubts.map { doubt ->
            val resolvedCloudId = CloudSyncRepository.resolveDoubtCloudId(doubt)
            val resolvedSessionCloud = doubt.sessionCloudId
                .takeIf { it.isNotBlank() && ConversationSession.isValidCloudId(it) }
                ?: sessionCloudIdByLocalId[doubt.sessionId].orEmpty()
            if (resolvedCloudId != doubt.cloudId || resolvedSessionCloud != doubt.sessionCloudId) {
                val fixed = doubt.copy(
                    cloudId = resolvedCloudId,
                    sessionCloudId = resolvedSessionCloud
                )
                repo.updateDoubt(fixed)
                fixed
            } else {
                doubt
            }
        }

        // Upload any local sessions missing on remote OR strictly newer locally
        val sessionsToPush = normalizedLocalSessions.filter { local ->
            val match = remoteSessionByCloudId[local.cloudId]
            if (match == null) {
                true
            } else {
                val localTs = local.updatedAtMillis.coerceAtLeast(local.endTime)
                val remoteTs = match.updatedAtMillis.coerceAtLeast(match.endTime)
                localTs > remoteTs
            }
        }

        val doubtsToPush = normalizedLocalDoubts.filter { local ->
            val match = remoteDoubtByCloudId[local.cloudId]
            if (match == null) {
                true
            } else {
                val localTs = local.updatedAtMillis.coerceAtLeast(local.timestamp)
                val remoteTs = match.updatedAtMillis.coerceAtLeast(match.timestamp)
                localTs > remoteTs
            }
        }

        if (sessionsToPush.isNotEmpty() || doubtsToPush.isNotEmpty()) {
            remote.backupAll(uid, doubtsToPush, sessionsToPush).getOrThrow()
        }

        // Merge all remote sessions and doubts down into Room
        val stats = repo.mergeRemoteSnapshot(
            remoteSessions = remoteSessionBackups.map { it.toRoomEntity() },
            remoteDoubts = remoteDoubtBackups.map { it.toRoomEntity() }
        )

        knownRemoteSessionIds.clear()
        knownRemoteSessionIds.addAll(remoteSessionByCloudId.keys)
        knownRemoteSessionIds.addAll(normalizedLocalSessions.map { it.cloudId })

        knownRemoteDoubtIds.clear()
        knownRemoteDoubtIds.addAll(remoteDoubtByCloudId.keys)
        knownRemoteDoubtIds.addAll(normalizedLocalDoubts.map { it.cloudId })

        markSyncedSnapshot(context = context, user = user)
        return stats
    }

    private suspend fun markSyncedSnapshot(
        context: Context? = appContext,
        user: FirebaseUser? = _activeUser.value
    ) {
        val now = System.currentTimeMillis()
        context?.let { AppPreferencesManager.recordCloudSync(it, now) }
        val repo = attachedRepository
        val sessionCount = runCatching { repo?.getAllSessionsOnce()?.size ?: 0 }.getOrDefault(0)
        val doubtCount = runCatching { repo?.getAllDoubtsOnce()?.size ?: 0 }.getOrDefault(0)
        _syncState.value = CloudSyncUiState.Synced(
            userEmail = user?.email,
            userName = user?.displayName,
            lastSyncedMillis = now,
            sessionCount = sessionCount,
            doubtCount = doubtCount
        )
    }

    private suspend fun stopActiveListeners() {
        val existing = listenerParentJob
        listenerParentJob = null
        existing?.cancelAndJoin()
    }

    /**
     * Safely tears down active Firestore snapshot listeners BEFORE signing out from Firebase Auth.
     */
    suspend fun signOut(context: Context) = withContext(NonCancellable) {
        val auth = resolveFirebaseAuthOrNull()
        activeUserId = null
        knownRemoteSessionIds.clear()
        knownRemoteDoubtIds.clear()
        if (auth != null) {
            GoogleAuthHelper.signOut(
                context = context,
                auth = auth,
                onBeforeFirebaseSignOut = { stopActiveListeners() }
            )
        } else {
            stopActiveListeners()
        }
        _activeUser.value = null
        _syncState.value = CloudSyncUiState.SignedOut
    }
}
