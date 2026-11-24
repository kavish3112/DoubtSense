package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ai.AnswerEngine
import com.example.ai.DoubtAnalysisEngine
import com.example.audio.LiveVoiceCaptureManager
import com.example.audio.VoiceCaptureListener
import com.example.data.local.DoubtDatabase
import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import com.example.data.remote.CloudSyncManager
import com.example.data.repository.DoubtRepository
import com.example.util.NotificationHelper
import com.example.util.TtsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "ListeningStateManager"

enum class ListeningState {
    IDLE,
    LISTENING,
    PAUSED,
    PROCESSING
}

enum class ChatTurnHighlightState {
    NEUTRAL,
    UNCERTAIN_LIVE,
    DOUBT_CONFIRMED,
    AMBIGUOUS_DOUBT
}

data class LiveChatTurn(
    val id: Long,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val uncertaintyScore: Float = 0.12f,
    val highlightState: ChatTurnHighlightState = ChatTurnHighlightState.NEUTRAL,
    val doubtId: Long? = null,
    val doubtTopic: String? = null,
    val subject: String? = null
)

data class LiveDoubtVisualAlert(
    val turnId: Long,
    val doubtId: Long,
    val topic: String,
    val subject: String,
    val snippet: String,
    val isAmbiguous: Boolean,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface ChatVoiceInputState {
    data object Idle : ChatVoiceInputState
    data class Recording(
        val modeLabel: String,
        val partialTranscript: String,
        val audioLevel: Float,
        val elapsedSeconds: Int,
        val handsFreeContinuous: Boolean
    ) : ChatVoiceInputState
    data object Transcribing : ChatVoiceInputState
    data class PermissionDenied(val permanentlyDenied: Boolean) : ChatVoiceInputState
    data class Error(val message: String) : ChatVoiceInputState
}

object ListeningStateManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val aiEngine = DoubtAnalysisEngine()
    private val contextAnalysisService = com.example.ai.ContextAnalysisService()
    private val chatAnswerEngine = AnswerEngine()

    private var repository: DoubtRepository? = null

    private val _listeningState = MutableStateFlow(ListeningState.IDLE)
    val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<String>>(emptyList())
    val conversationHistory: StateFlow<List<String>> = _conversationHistory.asStateFlow()

    private val turnIdGenerator = AtomicLong(1L)

    private val _liveChatTurns = MutableStateFlow<List<LiveChatTurn>>(emptyList())
    val liveChatTurns: StateFlow<List<LiveChatTurn>> = _liveChatTurns.asStateFlow()

    private val _liveTranscriptHighlight = MutableStateFlow(ChatTurnHighlightState.NEUTRAL)
    val liveTranscriptHighlight: StateFlow<ChatTurnHighlightState> = _liveTranscriptHighlight.asStateFlow()

    private val _liveDoubtAlert = MutableStateFlow<LiveDoubtVisualAlert?>(null)
    val liveDoubtAlert: StateFlow<LiveDoubtVisualAlert?> = _liveDoubtAlert.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    private val _latestDetectedDoubt = MutableStateFlow<DoubtItem?>(null)
    val latestDetectedDoubt: StateFlow<DoubtItem?> = _latestDetectedDoubt.asStateFlow()

    private val _selectedDoubtForDetail = MutableStateFlow<DoubtItem?>(null)
    val selectedDoubtForDetail: StateFlow<DoubtItem?> = _selectedDoubtForDetail.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isRefiningAnswer = MutableStateFlow(false)
    val isRefiningAnswer: StateFlow<Boolean> = _isRefiningAnswer.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Battery Optimization low-power listening mode setting
    private val _lowPowerMode = MutableStateFlow(false)
    val lowPowerMode: StateFlow<Boolean> = _lowPowerMode.asStateFlow()

    // Clarification Mode: "PRIVATE" (on-device) vs "CONVERSATION" (suggest questions to ask aloud)
    private val _clarificationMode = MutableStateFlow("PRIVATE")
    val clarificationMode: StateFlow<String> = _clarificationMode.asStateFlow()

    // Real-time speech uncertainty intensity (0.0 to 1.0) and descriptive label
    private val _uncertaintyIntensity = MutableStateFlow(0f)
    val uncertaintyIntensity: StateFlow<Float> = _uncertaintyIntensity.asStateFlow()

    private val _uncertaintyLabel = MutableStateFlow("Calm / Neutral")
    val uncertaintyLabel: StateFlow<String> = _uncertaintyLabel.asStateFlow()

    // Automatic Text-to-Speech readback after doubt detection
    private val _autoReadAloud = MutableStateFlow(true)
    val autoReadAloud: StateFlow<Boolean> = _autoReadAloud.asStateFlow()

    // Fact-Checking Mode: requires AI to attach verified external sources to all generated answers
    private val _factCheckingMode = MutableStateFlow(false)
    val factCheckingMode: StateFlow<Boolean> = _factCheckingMode.asStateFlow()

    // Inline Chat Real-Time Voice-to-Text & Hands-Free Query State
    private val _chatVoiceInputState = MutableStateFlow<ChatVoiceInputState>(ChatVoiceInputState.Idle)
    val chatVoiceInputState: StateFlow<ChatVoiceInputState> = _chatVoiceInputState.asStateFlow()

    private val _handsFreeVoiceMode = MutableStateFlow(false)
    val handsFreeVoiceMode: StateFlow<Boolean> = _handsFreeVoiceMode.asStateFlow()

    private var chatVoiceCaptureManager: LiveVoiceCaptureManager? = null
    private var chatCaptureAppContext: Context? = null
    private var chatTranscriptionJob: Job? = null
    private var handsFreeRearmJob: Job? = null

    internal val chatCaptureListener = object : VoiceCaptureListener {
        override fun onCaptureStarted(modeLabel: String) {
            _chatVoiceInputState.value = ChatVoiceInputState.Recording(
                modeLabel = modeLabel,
                partialTranscript = "",
                audioLevel = 0.08f,
                elapsedSeconds = 0,
                handsFreeContinuous = _handsFreeVoiceMode.value
            )
            _statusMessage.value = "Listening for hands-free chat question ($modeLabel)..."
        }

        override fun onAudioLevelChanged(level: Float) {
            val clamped = level.coerceIn(0f, 1f)
            _audioRms.value = (clamped * 10f).coerceIn(0f, 10f)
            val current = _chatVoiceInputState.value
            if (current is ChatVoiceInputState.Recording) {
                _chatVoiceInputState.value = current.copy(audioLevel = clamped)
            }
        }

        override fun onPartialTranscript(text: String) {
            val current = _chatVoiceInputState.value
            if (current is ChatVoiceInputState.Recording) {
                _chatVoiceInputState.value = current.copy(partialTranscript = text)
            }
            updateLiveTranscript(text)
        }

        override fun onElapsedSecondsChanged(seconds: Int) {
            val current = _chatVoiceInputState.value
            if (current is ChatVoiceInputState.Recording) {
                _chatVoiceInputState.value = current.copy(elapsedSeconds = seconds)
            }
        }

        override fun onFinalTranscriptReady(transcript: String) {
            _audioRms.value = 0f
            val clean = transcript.trim()
            if (clean.isBlank()) {
                _chatVoiceInputState.value = ChatVoiceInputState.Error("Didn't catch any speech. Tap the mic and speak clearly.")
                return
            }
            _chatVoiceInputState.value = ChatVoiceInputState.Idle
            val ctx = chatCaptureAppContext
            if (ctx != null) {
                onUtteranceFinalized(clean, ctx, isExplicitQuery = true)
            } else {
                updateLiveTranscript(clean)
            }
        }

        override fun onWavAudioReady(wavBytes: ByteArray) {
            _audioRms.value = 0f
            val ctx = chatCaptureAppContext
            if (ctx == null) {
                _chatVoiceInputState.value = ChatVoiceInputState.Error("Unable to transcribe voice audio without application context.")
                return
            }
            chatTranscriptionJob?.cancel()
            chatTranscriptionJob = scope.launch {
                _chatVoiceInputState.value = ChatVoiceInputState.Transcribing
                _statusMessage.value = "Transcribing hands-free voice question..."
                try {
                    val transcript = chatAnswerEngine.transcribeAudio(wavBytes).trim()
                    if (transcript.isBlank()) {
                        _chatVoiceInputState.value = ChatVoiceInputState.Error("No speech recognized. Please try again.")
                    } else {
                        _chatVoiceInputState.value = ChatVoiceInputState.Idle
                        onUtteranceFinalized(transcript, ctx, isExplicitQuery = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Chat voice WAV transcription failed", e)
                    _chatVoiceInputState.value = ChatVoiceInputState.Error(
                        e.localizedMessage ?: "Could not transcribe voice audio."
                    )
                }
            }
        }

        override fun onCaptureError(message: String) {
            _audioRms.value = 0f
            if (message.contains("permission", ignoreCase = true)) {
                _handsFreeVoiceMode.value = false
                _chatVoiceInputState.value = ChatVoiceInputState.PermissionDenied(permanentlyDenied = false)
            } else {
                _chatVoiceInputState.value = ChatVoiceInputState.Error(message)
            }
            _statusMessage.value = message
        }
    }

    private const val PREFS_NAME = "doubtsense_prefs"
    private const val KEY_FACT_CHECKING_MODE = "fact_checking_mode"

    // Active Room ConversationSession tracking
    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    private var activeSessionStartTime: Long = 0L
    private val sessionIntensitySamples = mutableListOf<Float>()
    private var sessionDoubtCount: Int = 0
    private var sessionCategory: String = "General"
    private var sessionTitle: String = "Live Conversation Session"

    fun initRepository(context: Context) {
        if (repository == null) {
            val db = DoubtDatabase.getInstance(context)
            val created = DoubtRepository(db.doubtDao(), db.conversationSessionDao())
            repository = created
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _factCheckingMode.value = prefs.getBoolean(KEY_FACT_CHECKING_MODE, false)
        }
        repository?.let { CloudSyncManager.attachRepository(context, it) }
    }

    fun getRepository(context: Context): DoubtRepository {
        initRepository(context)
        return repository!!
    }

    fun setLowPowerMode(enabled: Boolean) {
        _lowPowerMode.value = enabled
    }

    fun setClarificationMode(mode: String) {
        _clarificationMode.value = mode
    }

    fun setAutoReadAloud(enabled: Boolean) {
        _autoReadAloud.value = enabled
        if (!enabled) {
            TtsManager.stop()
        }
    }

    fun setFactCheckingMode(enabled: Boolean, context: Context? = null) {
        _factCheckingMode.value = enabled
        context?.applicationContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(KEY_FACT_CHECKING_MODE, enabled)
            ?.apply()
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        return true
    }

    fun updateListeningState(state: ListeningState) {
        _listeningState.value = state
        if (state == ListeningState.IDLE) {
            _audioRms.value = 0f
            _uncertaintyIntensity.value = 0f
            _uncertaintyLabel.value = "Microphone Idle"
        }
    }

    fun updateUncertainty(score: Float, label: String? = null) {
        val clamped = score.coerceIn(0f, 1f)
        _uncertaintyIntensity.value = clamped
        _uncertaintyLabel.value = label ?: when {
            clamped >= 0.75f -> "High Uncertainty"
            clamped >= 0.45f -> "Moderate Doubt"
            clamped >= 0.20f -> "Inquisitive Speech"
            clamped > 0.05f -> "Clear Statement"
            else -> "Calm / Neutral"
        }
    }

    fun updateLiveTranscript(text: String) {
        _liveTranscript.value = text
        if (text.isNotBlank()) {
            val liveScore = calculateRealtimeUncertainty(text)
            updateUncertainty(liveScore)
            _liveTranscriptHighlight.value = if (liveScore >= 0.45f) {
                ChatTurnHighlightState.UNCERTAIN_LIVE
            } else {
                ChatTurnHighlightState.NEUTRAL
            }
        } else {
            _liveTranscriptHighlight.value = ChatTurnHighlightState.NEUTRAL
        }
    }

    fun classifyTextForChatHighlight(text: String): ChatTurnHighlightState {
        if (text.isBlank()) return ChatTurnHighlightState.NEUTRAL
        return if (calculateRealtimeUncertainty(text) >= 0.45f) {
            ChatTurnHighlightState.UNCERTAIN_LIVE
        } else {
            ChatTurnHighlightState.NEUTRAL
        }
    }

    fun dismissLiveDoubtAlert() {
        _liveDoubtAlert.value = null
    }

    private fun calculateRealtimeUncertainty(text: String): Float {
        val lower = text.lowercase()
        var score = 0.12f
        val strongDoubtMarkers = listOf(
            "don't understand", "do not understand", "confused", "how does", "how do",
            "why does", "why do", "what does this mean", "explain this again",
            "not sure", "lost here", "doesn't make sense", "what is the difference",
            "how do they actually", "could you explain"
        )
        val moderateDoubtMarkers = listOf(
            "how", "why", "what", "which", "where", "can you", "is it possible",
            "does it", "is that", "?", "maybe", "i think", "wondering", "suppose", "wait"
        )

        for (marker in strongDoubtMarkers) {
            if (lower.contains(marker)) {
                score += 0.55f
                break
            }
        }
        for (marker in moderateDoubtMarkers) {
            if (lower.contains(marker)) {
                score += 0.18f
            }
        }
        return score.coerceIn(0.08f, 0.95f)
    }

    fun updateAudioRms(rms: Float) {
        _audioRms.value = rms.coerceIn(0f, 10f)
    }

    fun setStatusMessage(msg: String?) {
        _statusMessage.value = msg
    }

    fun selectDoubtForDetail(doubt: DoubtItem?) {
        _selectedDoubtForDetail.value = doubt
    }

    fun selectDoubtById(doubtId: Long, context: Context) {
        scope.launch(Dispatchers.IO) {
            val repo = getRepository(context)
            val item = repo.getDoubtById(doubtId)
            if (item != null) {
                _selectedDoubtForDetail.value = item
            }
        }
    }

    fun startListening(context: Context) {
        initRepository(context)
        if (_activeSessionId.value == null) {
            resetSessionMetrics()
        }
        val serviceIntent = Intent(context, DoubtSenseListeningService::class.java).apply {
            action = DoubtSenseListeningService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun resetSessionMetrics() {
        activeSessionStartTime = System.currentTimeMillis()
        sessionIntensitySamples.clear()
        sessionDoubtCount = 0
        sessionCategory = "General"
        sessionTitle = "Live Conversation Session"
    }

    fun pauseListening(context: Context) {
        val serviceIntent = Intent(context, DoubtSenseListeningService::class.java).apply {
            action = NotificationHelper.ACTION_PAUSE
        }
        context.startService(serviceIntent)
    }

    fun resumeListening(context: Context) {
        val serviceIntent = Intent(context, DoubtSenseListeningService::class.java).apply {
            action = NotificationHelper.ACTION_RESUME
        }
        context.startService(serviceIntent)
    }

    fun stopListening(context: Context) {
        val serviceIntent = Intent(context, DoubtSenseListeningService::class.java).apply {
            action = NotificationHelper.ACTION_STOP
        }
        context.startService(serviceIntent)
        _audioRms.value = 0f
        updateUncertainty(0f, "Microphone Idle")
        finalizeCurrentSession(context)
    }

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun refreshChatMicPermissionState(context: Context) {
        if (hasRecordAudioPermission(context) && _chatVoiceInputState.value is ChatVoiceInputState.PermissionDenied) {
            _chatVoiceInputState.value = ChatVoiceInputState.Idle
        }
    }

    fun onChatMicPermissionResult(
        granted: Boolean,
        shouldShowRationale: Boolean,
        context: Context,
        startImmediately: Boolean = true
    ) {
        chatCaptureAppContext = context.applicationContext
        if (granted) {
            _chatVoiceInputState.value = ChatVoiceInputState.Idle
            if (startImmediately) {
                startChatVoiceInput(context)
            }
        } else {
            _handsFreeVoiceMode.value = false
            val permanentlyDenied = !shouldShowRationale
            _chatVoiceInputState.value = ChatVoiceInputState.PermissionDenied(permanentlyDenied = permanentlyDenied)
            _statusMessage.value = if (permanentlyDenied) {
                "Microphone permission is turned off. Open App Settings to enable hands-free voice questions."
            } else {
                "Microphone permission is needed to record voice-to-text questions in chat."
            }
        }
    }

    fun dismissChatMicBanner() {
        val current = _chatVoiceInputState.value
        if (current is ChatVoiceInputState.PermissionDenied || current is ChatVoiceInputState.Error) {
            _chatVoiceInputState.value = ChatVoiceInputState.Idle
        }
    }

    fun setHandsFreeVoiceMode(enabled: Boolean, context: Context? = null) {
        _handsFreeVoiceMode.value = enabled
        val current = _chatVoiceInputState.value
        if (!enabled) {
            handsFreeRearmJob?.cancel()
            if (current is ChatVoiceInputState.Recording) {
                _chatVoiceInputState.value = current.copy(handsFreeContinuous = false)
            }
        } else {
            if (current is ChatVoiceInputState.Recording) {
                _chatVoiceInputState.value = current.copy(handsFreeContinuous = true)
            } else if (context != null && current !is ChatVoiceInputState.Transcribing) {
                startChatVoiceInput(context)
            }
        }
    }

    private fun getOrCreateChatVoiceCaptureManager(context: Context): LiveVoiceCaptureManager {
        val existing = chatVoiceCaptureManager
        if (existing != null) return existing
        return LiveVoiceCaptureManager(context.applicationContext, chatCaptureListener).also {
            chatVoiceCaptureManager = it
        }
    }

    fun startChatVoiceInput(context: Context) {
        val appCtx = context.applicationContext
        chatCaptureAppContext = appCtx
        initRepository(appCtx)
        handsFreeRearmJob?.cancel()

        if (!hasRecordAudioPermission(appCtx)) {
            _handsFreeVoiceMode.value = false
            _chatVoiceInputState.value = ChatVoiceInputState.PermissionDenied(permanentlyDenied = false)
            return
        }

        if (_listeningState.value == ListeningState.LISTENING) {
            pauseListening(appCtx)
        }

        getOrCreateChatVoiceCaptureManager(appCtx).startCapture(preferDirectAudio = false)
    }

    fun stopChatVoiceInput() {
        handsFreeRearmJob?.cancel()
        val current = _chatVoiceInputState.value
        if (current is ChatVoiceInputState.Recording) {
            val manager = chatVoiceCaptureManager
            if (manager != null) {
                manager.stopCaptureAndSubmit()
            } else if (current.partialTranscript.isNotBlank()) {
                chatCaptureListener.onFinalTranscriptReady(current.partialTranscript)
            } else {
                _chatVoiceInputState.value = ChatVoiceInputState.Idle
            }
        }
    }

    fun cancelChatVoiceInput() {
        handsFreeRearmJob?.cancel()
        chatTranscriptionJob?.cancel()
        chatVoiceCaptureManager?.cancelCapture()
        _chatVoiceInputState.value = ChatVoiceInputState.Idle
        _liveTranscript.value = ""
        _liveTranscriptHighlight.value = ChatTurnHighlightState.NEUTRAL
        _audioRms.value = 0f
    }

    fun clearTranscript() {
        handsFreeRearmJob?.cancel()
        chatTranscriptionJob?.cancel()
        chatVoiceCaptureManager?.cancelCapture()
        _handsFreeVoiceMode.value = false
        _chatVoiceInputState.value = ChatVoiceInputState.Idle
        _liveTranscript.value = ""
        _conversationHistory.value = emptyList()
        _liveChatTurns.value = emptyList()
        _liveTranscriptHighlight.value = ChatTurnHighlightState.NEUTRAL
        _liveDoubtAlert.value = null
        _activeSessionId.value = null
        resetSessionMetrics()
        updateUncertainty(0f, "Idle / Clear")
    }

    /**
     * Loads a synced/saved ConversationSession and its linked doubts back into the Live Sense
     * chat timeline so users can continue conversations seamlessly across devices.
     */
    fun restoreSessionIntoLiveChat(
        session: ConversationSession,
        sessionDoubts: List<DoubtItem>
    ) {
        val turns = session.getTranscriptTurns()
        _activeSessionId.value = session.id.takeIf { it > 0L }
        activeSessionStartTime = session.startTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        sessionTitle = session.title.ifBlank { "Conversation Session" }
        sessionCategory = session.category.ifBlank { "General" }
        sessionDoubtCount = session.doubtCount.coerceAtLeast(sessionDoubts.size)
        sessionIntensitySamples.clear()
        if (session.averageDoubtIntensity > 0f) {
            sessionIntensitySamples.add(session.averageDoubtIntensity.coerceIn(0f, 1f))
        }
        if (session.peakDoubtIntensity > 0f) {
            sessionIntensitySamples.add(session.peakDoubtIntensity.coerceIn(0f, 1f))
        }

        _conversationHistory.value = turns.takeLast(25)
        _liveChatTurns.value = turns.takeLast(25).map { text ->
            val matchedDoubt = sessionDoubts.firstOrNull { doubt ->
                doubt.doubtSnippet.equals(text, ignoreCase = true) ||
                    text.contains(doubt.doubtSnippet, ignoreCase = true)
            }
            val score = matchedDoubt?.confidenceScore ?: calculateRealtimeUncertainty(text)
            val highlight = when {
                matchedDoubt != null && matchedDoubt.isAmbiguous -> ChatTurnHighlightState.AMBIGUOUS_DOUBT
                matchedDoubt != null -> ChatTurnHighlightState.DOUBT_CONFIRMED
                score >= 0.45f -> ChatTurnHighlightState.UNCERTAIN_LIVE
                else -> ChatTurnHighlightState.NEUTRAL
            }
            LiveChatTurn(
                id = turnIdGenerator.getAndIncrement(),
                text = text,
                timestamp = matchedDoubt?.timestamp ?: session.endTime,
                uncertaintyScore = score,
                highlightState = highlight,
                doubtId = matchedDoubt?.id,
                doubtTopic = matchedDoubt?.topic,
                subject = matchedDoubt?.subject
            )
        }
        val latest = sessionDoubts.maxByOrNull { it.timestamp }
        _latestDetectedDoubt.value = latest
        _liveDoubtAlert.value = null
        _statusMessage.value = "Loaded session \"${sessionTitle}\" (${turns.size} turns)"
    }

    /**
     * Builds a snapshot ConversationSession representing the active/recent live conversation
     * so live exports include full session analysis metrics (avg/peak intensity, duration, takeaway).
     */
    fun getLiveSessionAnalysisSnapshot(): ConversationSession? {
        val turns = _conversationHistory.value
        val latest = _latestDetectedDoubt.value
        if (turns.isEmpty() && latest == null && _activeSessionId.value == null) {
            return null
        }
        val now = System.currentTimeMillis()
        val avg = if (sessionIntensitySamples.isNotEmpty()) {
            sessionIntensitySamples.average().toFloat().coerceIn(0f, 1f)
        } else {
            latest?.confidenceScore ?: _uncertaintyIntensity.value.coerceIn(0f, 1f)
        }
        val peak = sessionIntensitySamples.maxOrNull()?.coerceIn(0f, 1f)
            ?: maxOf(avg, latest?.confidenceScore ?: 0f)
        return ConversationSession(
            id = _activeSessionId.value ?: 0L,
            startTime = activeSessionStartTime.takeIf { it > 0L } ?: (now - 60_000L),
            endTime = now,
            title = sessionTitle.ifBlank { latest?.topic ?: "Live Conversation Session" },
            category = sessionCategory.ifBlank { latest?.subject ?: "General" },
            customTag = latest?.customTag?.ifBlank { "Study" } ?: "Study",
            transcriptJson = ConversationSession.encodeTranscriptTurns(turns),
            transcriptPreview = turns.lastOrNull() ?: latest?.doubtSnippet.orEmpty(),
            averageDoubtIntensity = avg,
            peakDoubtIntensity = peak,
            doubtCount = sessionDoubtCount.coerceAtLeast(if (latest != null) 1 else 0),
            summary = latest?.notificationSummary.orEmpty()
        )
    }

    private fun finalizeCurrentSession(context: Context) {
        val currentId = _activeSessionId.value ?: return
        val turns = _conversationHistory.value
        scope.launch(Dispatchers.IO) {
            try {
                val repo = getRepository(context)
                val existing = repo.getSessionById(currentId)
                if (existing != null) {
                    val avg = if (sessionIntensitySamples.isNotEmpty()) {
                        sessionIntensitySamples.average().toFloat().coerceIn(0f, 1f)
                    } else existing.averageDoubtIntensity
                    val peak = sessionIntensitySamples.maxOrNull()?.coerceIn(0f, 1f) ?: existing.peakDoubtIntensity
                    repo.updateSession(
                        existing.copy(
                            endTime = System.currentTimeMillis(),
                            title = sessionTitle,
                            category = sessionCategory,
                            transcriptJson = ConversationSession.encodeTranscriptTurns(turns),
                            transcriptPreview = turns.lastOrNull() ?: existing.transcriptPreview,
                            averageDoubtIntensity = avg,
                            peakDoubtIntensity = peak,
                            doubtCount = sessionDoubtCount
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to finalize session", e)
            } finally {
                _activeSessionId.value = null
            }
        }
    }

    /**
     * Called when a speech utterance is finalized by the SpeechRecognizer or manual input.
     */
    fun onUtteranceFinalized(
        utterance: String,
        context: Context,
        isExplicitQuery: Boolean = false
    ) {
        val cleanText = utterance.trim()
        if (cleanText.isBlank()) return

        if (activeSessionStartTime == 0L) {
            resetSessionMetrics()
        }

        val currentHistory = _conversationHistory.value.toMutableList()
        currentHistory.add(cleanText)
        if (currentHistory.size > 25) {
            currentHistory.removeAt(0)
        }
        _conversationHistory.value = currentHistory
        _liveTranscript.value = ""
        _liveTranscriptHighlight.value = ChatTurnHighlightState.NEUTRAL

        val utteranceIntensity = if (isExplicitQuery) {
            calculateRealtimeUncertainty(cleanText).coerceAtLeast(0.65f)
        } else {
            calculateRealtimeUncertainty(cleanText)
        }
        sessionIntensitySamples.add(utteranceIntensity)
        updateUncertainty(utteranceIntensity)

        val turnId = turnIdGenerator.getAndIncrement()
        val preliminaryState = if (isExplicitQuery || utteranceIntensity >= 0.45f) {
            ChatTurnHighlightState.UNCERTAIN_LIVE
        } else {
            ChatTurnHighlightState.NEUTRAL
        }
        val updatedTurns = _liveChatTurns.value.toMutableList()
        updatedTurns.add(
            LiveChatTurn(
                id = turnId,
                text = cleanText,
                uncertaintyScore = utteranceIntensity,
                highlightState = preliminaryState
            )
        )
        if (updatedTurns.size > 25) {
            updatedTurns.removeAt(0)
        }
        _liveChatTurns.value = updatedTurns

        processSpeechForDoubt(cleanText, currentHistory, context, isExplicitQuery, turnId)
    }

    private fun updateChatTurnHighlight(
        turnId: Long,
        highlightState: ChatTurnHighlightState,
        uncertaintyScore: Float,
        doubtId: Long? = null,
        doubtTopic: String? = null,
        subject: String? = null
    ) {
        _liveChatTurns.value = _liveChatTurns.value.map { turn ->
            if (turn.id == turnId) {
                turn.copy(
                    highlightState = highlightState,
                    uncertaintyScore = uncertaintyScore,
                    doubtId = doubtId,
                    doubtTopic = doubtTopic,
                    subject = subject
                )
            } else {
                turn
            }
        }
    }

    private fun processSpeechForDoubt(
        latestSpeech: String,
        contextTurns: List<String>,
        context: Context,
        isExplicitQuery: Boolean = false,
        turnId: Long = -1L
    ) {
        scope.launch {
            _isAnalyzing.value = true
            val prevState = _listeningState.value
            _listeningState.value = ListeningState.PROCESSING
            _statusMessage.value = "Finding verified answer with Gemini AI & Google Search..."
            updateUncertainty(0.82f, "AI Analyzing Uncertainty...")

            try {
                val repo = getRepository(context)

                // Ensure an active Room ConversationSession exists for this live transcript
                var currentSessionId = _activeSessionId.value
                if (currentSessionId == null) {
                    val avg = if (sessionIntensitySamples.isNotEmpty()) {
                        sessionIntensitySamples.average().toFloat().coerceIn(0f, 1f)
                    } else 0.15f
                    val initialSession = ConversationSession(
                        startTime = activeSessionStartTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        endTime = System.currentTimeMillis(),
                        title = sessionTitle,
                        category = sessionCategory,
                        transcriptJson = ConversationSession.encodeTranscriptTurns(contextTurns),
                        transcriptPreview = latestSpeech,
                        averageDoubtIntensity = avg,
                        peakDoubtIntensity = sessionIntensitySamples.maxOrNull() ?: avg,
                        doubtCount = sessionDoubtCount
                    )
                    currentSessionId = repo.saveSession(initialSession)
                    _activeSessionId.value = currentSessionId
                }

                // Trigger ContextAnalysisService to analyze transcript segments for subjects, topics, and verified answer
                val result = contextAnalysisService.analyzeTranscriptSegment(
                    transcriptSegment = latestSpeech,
                    surroundingContext = contextTurns,
                    factCheckingMode = _factCheckingMode.value,
                    isExplicitDoubtQuery = isExplicitQuery
                )
                if (result.isDoubt) {
                    val intensity = result.confidence.coerceIn(0.65f, 0.98f)
                    sessionIntensitySamples.add(intensity)
                    sessionDoubtCount++
                    if (result.subject.isNotBlank()) {
                        sessionCategory = result.subject
                    }
                    if (result.topic.isNotBlank()) {
                        sessionTitle = result.topic
                    }

                    val doubtItem = result.toDoubtItem(contextTurns.takeLast(6).joinToString(" | "))
                        .copy(sessionId = currentSessionId)
                    val id = repo.saveDoubt(doubtItem)
                    val savedItem = doubtItem.copy(id = id)

                    // Update active ConversationSession metrics in Room
                    val avgIntensity = sessionIntensitySamples.average().toFloat().coerceIn(0f, 1f)
                    val peakIntensity = sessionIntensitySamples.maxOrNull()?.coerceIn(0f, 1f) ?: intensity
                    repo.getSessionById(currentSessionId)?.let { existingSession ->
                        repo.updateSession(
                            existingSession.copy(
                                endTime = System.currentTimeMillis(),
                                title = sessionTitle,
                                category = sessionCategory,
                                transcriptJson = ConversationSession.encodeTranscriptTurns(contextTurns),
                                transcriptPreview = latestSpeech,
                                averageDoubtIntensity = avgIntensity,
                                peakDoubtIntensity = peakIntensity,
                                doubtCount = sessionDoubtCount,
                                summary = savedItem.notificationSummary
                            )
                        )
                    }

                    _latestDetectedDoubt.value = savedItem
                    if (turnId > 0L) {
                        updateChatTurnHighlight(
                            turnId = turnId,
                            highlightState = if (result.isAmbiguous) {
                                ChatTurnHighlightState.AMBIGUOUS_DOUBT
                            } else {
                                ChatTurnHighlightState.DOUBT_CONFIRMED
                            },
                            uncertaintyScore = intensity,
                            doubtId = id,
                            doubtTopic = savedItem.topic,
                            subject = savedItem.subject
                        )
                        _liveDoubtAlert.value = LiveDoubtVisualAlert(
                            turnId = turnId,
                            doubtId = id,
                            topic = savedItem.topic,
                            subject = savedItem.subject,
                            snippet = savedItem.doubtSnippet,
                            isAmbiguous = result.isAmbiguous,
                            confidence = intensity
                        )
                    }
                    _statusMessage.value = "💡 Doubt identified: ${savedItem.topic} (${savedItem.doubtType}) [${savedItem.subject}]"
                    updateUncertainty(
                        intensity,
                        if (result.isAmbiguous) "High Ambiguity & Doubt" else "Significant Doubt Detected"
                    )

                    NotificationHelper.showDoubtAnswerNotification(
                        context = context,
                        doubtId = id,
                        topic = savedItem.topic,
                        quickAnswer = savedItem.quickAnswer,
                        doubtSnippet = savedItem.doubtSnippet
                    )

                    // Audibly read back the AI's analyzed response via TTS engine after doubt detection
                    if (_autoReadAloud.value) {
                        val spokenResponse = "${savedItem.topic}. ${savedItem.quickAnswer}"
                        TtsManager.speakAnswer(context, spokenResponse, id)
                    }
                } else {
                    if (turnId > 0L) {
                        updateChatTurnHighlight(
                            turnId = turnId,
                            highlightState = ChatTurnHighlightState.NEUTRAL,
                            uncertaintyScore = 0.12f
                        )
                    }
                    sessionIntensitySamples.add(0.12f)
                    val avgIntensity = sessionIntensitySamples.average().toFloat().coerceIn(0f, 1f)
                    val peakIntensity = sessionIntensitySamples.maxOrNull()?.coerceIn(0f, 1f) ?: 0.12f
                    repo.getSessionById(currentSessionId)?.let { existingSession ->
                        repo.updateSession(
                            existingSession.copy(
                                endTime = System.currentTimeMillis(),
                                transcriptJson = ConversationSession.encodeTranscriptTurns(contextTurns),
                                transcriptPreview = latestSpeech,
                                averageDoubtIntensity = avgIntensity,
                                peakDoubtIntensity = peakIntensity
                            )
                        )
                    }
                    _statusMessage.value = "Context analyzed. No doubt detected."
                    updateUncertainty(0.12f, "Clear Speech / No Doubt")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed during context analysis pipeline", e)
                _statusMessage.value = "Analysis error: ${e.message}"
                updateUncertainty(0.35f, "Analysis Inconclusive")
            } finally {
                _isAnalyzing.value = false
                if (_listeningState.value == ListeningState.PROCESSING) {
                    _listeningState.value = if (prevState == ListeningState.PROCESSING) ListeningState.IDLE else prevState
                }
                if (isExplicitQuery && _handsFreeVoiceMode.value && hasRecordAudioPermission(context)) {
                    scheduleHandsFreeRearm(context)
                }
            }
        }
    }

    private fun scheduleHandsFreeRearm(context: Context) {
        val appCtx = context.applicationContext
        handsFreeRearmJob?.cancel()
        handsFreeRearmJob = scope.launch {
            delay(500L)
            var waits = 0
            while ((TtsManager.isSpeaking.value || TtsManager.isPaused.value) && waits < 120) {
                delay(350L)
                waits++
            }
            if (
                _handsFreeVoiceMode.value &&
                _chatVoiceInputState.value is ChatVoiceInputState.Idle &&
                !_isAnalyzing.value &&
                hasRecordAudioPermission(appCtx)
            ) {
                startChatVoiceInput(appCtx)
            }
        }
    }

    /**
     * Populates representative study conversation sessions in Room when requested by the user on an empty database.
     */
    fun seedSampleSessions(context: Context) {
        scope.launch(Dispatchers.IO) {
            val repo = getRepository(context)
            val now = System.currentTimeMillis()
            val day = 86_400_000L

            val sessionsData = listOf(
                Triple(
                    ConversationSession(
                        startTime = now - (3 * day),
                        endTime = now - (3 * day) + 600_000L,
                        title = "Random Forest Ensembles",
                        category = "Machine Learning",
                        customTag = "Work, Study",
                        transcriptJson = ConversationSession.encodeTranscriptTurns(
                            listOf(
                                "Person A: Random Forest trains multiple decision trees on bootstrapped subsets.",
                                "Person B: But how do they actually work together during prediction?"
                            )
                        ),
                        transcriptPreview = "Person B: But how do they actually work together during prediction?",
                        averageDoubtIntensity = 0.86f,
                        peakDoubtIntensity = 0.92f,
                        doubtCount = 1,
                        summary = "Explained bagging, majority voting, and feature randomness in Random Forests."
                    ),
                    DoubtItem(
                        timestamp = now - (3 * day) + 300_000L,
                        doubtSnippet = "But how do they actually work together during prediction?",
                        subject = "Machine Learning",
                        topic = "Random Forest: Ensemble Voting",
                        importantConcepts = "Decision Trees, Bagging, Majority Voting",
                        doubtType = "Working principle",
                        intent = "Understand how independent decision trees aggregate predictions",
                        quickAnswer = "Each decision tree makes an independent prediction, and the final output is chosen by majority vote (classification) or average (regression).",
                        detailedAnswer = "Random Forest combines hundreds of decorrelated decision trees trained via bootstrap aggregating (bagging). During inference, every tree evaluates the input sample and casts a vote; taking the majority or mean sharply reduces overfitting compared to a single tree.",
                        notificationSummary = "Random Forest: Trees vote independently and majority wins.",
                        conversationalContext = "Person A: Random Forest trains multiple decision trees | Person B: But how do they work together?",
                        confidenceScore = 0.86f,
                        customTag = "Work, Study"
                    ),
                    Unit
                ),
                Triple(
                    ConversationSession(
                        startTime = now - (2 * day),
                        endTime = now - (2 * day) + 450_000L,
                        title = "ATP Synthase & Proton Gradient",
                        category = "Biology",
                        customTag = "Study",
                        transcriptJson = ConversationSession.encodeTranscriptTurns(
                            listOf(
                                "Lecturer: The electron transport chain pumps protons into the intermembrane space.",
                                "Student: I don't understand how ATP synthase actually creates energy from the proton gradient."
                            )
                        ),
                        transcriptPreview = "Student: I don't understand how ATP synthase actually creates energy...",
                        averageDoubtIntensity = 0.78f,
                        peakDoubtIntensity = 0.85f,
                        doubtCount = 1,
                        summary = "Chemiosmosis drives rotational catalysis in ATP synthase."
                    ),
                    DoubtItem(
                        timestamp = now - (2 * day) + 250_000L,
                        doubtSnippet = "I don't understand how ATP synthase actually creates energy from the proton gradient.",
                        subject = "Biology",
                        topic = "Cellular Respiration: ATP Synthase",
                        importantConcepts = "Chemiosmosis, Proton Motive Force, F0F1 Rotor",
                        doubtType = "Mechanism",
                        intent = "Understand how proton flow powers mechanical phosphorylation of ADP",
                        quickAnswer = "Protons flow down their electrochemical gradient through ATP synthase like water through a turbine, spinning its rotor to bond ADP and phosphate into ATP.",
                        detailedAnswer = "In oxidative phosphorylation, protons accumulate in the intermembrane space, creating a proton-motive force. As H+ ions pass back into the mitochondrial matrix through the F0 channel of ATP synthase, they rotate the c-ring and central stalk, driving conformational changes in the F1 catalytic subunits that synthesize ATP from ADP and Pi.",
                        notificationSummary = "ATP Synthase: Proton flow spins a molecular rotor to form ATP.",
                        conversationalContext = "Proton gradient -> Chemiosmosis -> ATP Synthase",
                        confidenceScore = 0.78f,
                        customTag = "Study"
                    ),
                    Unit
                ),
                Triple(
                    ConversationSession(
                        startTime = now - day,
                        endTime = now - day + 500_000L,
                        title = "Quicksort vs Merge Sort Complexity",
                        category = "Computer Science",
                        customTag = "Work",
                        transcriptJson = ConversationSession.encodeTranscriptTurns(
                            listOf(
                                "Peer: Merge sort always takes O(n log n).",
                                "Me: Why can quicksort degrade to O(n squared) if both divide and conquer?"
                            )
                        ),
                        transcriptPreview = "Me: Why can quicksort degrade to O(n squared)?",
                        averageDoubtIntensity = 0.54f,
                        peakDoubtIntensity = 0.66f,
                        doubtCount = 1,
                        summary = "Worst-case unbalanced pivot selection causes quadratic partitions in Quicksort."
                    ),
                    DoubtItem(
                        timestamp = now - day + 220_000L,
                        doubtSnippet = "Why can quicksort degrade to O(n squared) if both divide and conquer?",
                        subject = "Computer Science",
                        topic = "Algorithms: Quicksort Worst-Case",
                        importantConcepts = "Pivot Selection, Partition Balance, Recursion Tree",
                        doubtType = "Comparison",
                        intent = "Understand why unbalanced pivots lead to O(n^2) depth",
                        quickAnswer = "Merge sort always splits the array in half, whereas a poor pivot in quicksort can split into 1 and n-1 elements, causing n recursive levels.",
                        detailedAnswer = "Merge sort guarantees balanced O(log n) recursion depth regardless of data order. Quicksort relies on its pivot; if the smallest or largest element is repeatedly picked (e.g., on already-sorted input with naive pivot choice), partitions become maximally skewed (0 and n-1), producing O(n^2) comparisons.",
                        notificationSummary = "Quicksort O(n^2): Skewed pivots create n-deep recursion.",
                        conversationalContext = "Comparing Merge Sort and Quicksort recursion depth",
                        confidenceScore = 0.54f,
                        customTag = "Work"
                    ),
                    Unit
                ),
                Triple(
                    ConversationSession(
                        startTime = now - 3_600_000L,
                        endTime = now - 3_200_000L,
                        title = "Thermodynamics & Buoyancy",
                        category = "Physics",
                        customTag = "Personal, Study",
                        transcriptJson = ConversationSession.encodeTranscriptTurns(
                            listOf(
                                "Speaker: Hot air balloons rely on heated air inside the envelope.",
                                "Listener: Wait, why does warm air actually rise?"
                            )
                        ),
                        transcriptPreview = "Listener: Wait, why does warm air actually rise?",
                        averageDoubtIntensity = 0.34f,
                        peakDoubtIntensity = 0.42f,
                        doubtCount = 1,
                        summary = "Thermal expansion decreases density, generating upward buoyant force."
                    ),
                    DoubtItem(
                        timestamp = now - 3_400_000L,
                        doubtSnippet = "Wait, why does warm air actually rise?",
                        subject = "Physics",
                        topic = "Thermodynamics: Convection",
                        importantConcepts = "Thermal Expansion, Density, Buoyancy",
                        doubtType = "Cause & Effect",
                        intent = "Understand why heated gas experiences upward lift",
                        quickAnswer = "Heating air increases molecular kinetic energy, expanding its volume and lowering its density so denser cooler air pushes it upward.",
                        detailedAnswer = "By the ideal gas law at constant atmospheric pressure, higher temperature lowers gas density. Surrounding cooler, denser air exerts greater upward buoyant pressure than the weight of the warm air parcel, lifting it via convection.",
                        notificationSummary = "Physics: Warm air is less dense and rises via buoyancy.",
                        conversationalContext = "Hot air balloon convection discussion",
                        confidenceScore = 0.34f
                    ),
                    Unit
                )
            )

            sessionsData.forEach { (session, doubt, _) ->
                val sessionId = repo.saveSession(session)
                repo.saveDoubt(doubt.copy(sessionId = sessionId))
            }
        }
    }

    /**
     * Submits a clarification response from the user and synthesizes a tailored answer.
     */
    fun submitClarification(
        doubt: DoubtItem,
        userClarification: String,
        style: String,
        context: Context
    ) {
        scope.launch {
            _isRefiningAnswer.value = true
            _statusMessage.value = "Generating personalized explanation for your clarification..."
            try {
                val isFactChecking = _factCheckingMode.value
                val personalizedAnswer = aiEngine.generatePersonalizedAnswer(
                    doubt = doubt,
                    userClarification = userClarification,
                    style = style,
                    factCheckingMode = isFactChecking
                )

                val existingSources = doubt.getVerificationSources()
                val finalSources = if (isFactChecking && existingSources.isEmpty()) {
                    com.example.ai.ContextAnalysisService.defaultVerificationSourcesForTopic(
                        subject = doubt.subject,
                        topic = doubt.topic,
                        queryText = doubt.doubtSnippet
                    )
                } else {
                    existingSources
                }

                val updatedItem = doubt.copy(
                    detailedAnswer = personalizedAnswer,
                    userClarificationResponse = userClarification,
                    preferredStyle = style,
                    isResolvedWithClarification = true,
                    isAmbiguous = false,
                    isFactChecked = doubt.isFactChecked || isFactChecking,
                    verificationStatus = if (doubt.isFactChecked || isFactChecking) {
                        doubt.verificationStatus.ifBlank { "Verified via ${finalSources.size} External Source(s)" }
                    } else "",
                    verificationSourcesJson = DoubtItem.encodeVerificationSources(finalSources)
                )

                val repo = getRepository(context)
                repo.updateDoubt(updatedItem)

                _latestDetectedDoubt.value = updatedItem
                if (_selectedDoubtForDetail.value?.id == updatedItem.id) {
                    _selectedDoubtForDetail.value = updatedItem
                }
                _statusMessage.value = "✨ Answer tailored to: '$userClarification'"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit clarification", e)
                _statusMessage.value = "Failed to refine answer: ${e.message}"
            } finally {
                _isRefiningAnswer.value = false
            }
        }
    }

    /**
     * Re-analyzes an existing doubt using Google Search grounding and Gemini AI
     * to upgrade or verify its quickAnswer, detailedAnswer, and external citations.
     */
    fun deepenAndVerifyDoubt(doubt: DoubtItem, context: Context) {
        scope.launch {
            _isRefiningAnswer.value = true
            _statusMessage.value = "Verifying & deepening answer for '${doubt.topic}' with Gemini AI & Google Search..."
            try {
                val contextTurns = doubt.conversationalContext
                    .split(" | ")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val freshResult = contextAnalysisService.analyzeTranscriptSegment(
                    transcriptSegment = doubt.doubtSnippet,
                    surroundingContext = contextTurns,
                    factCheckingMode = true,
                    isExplicitDoubtQuery = true
                )
                val updated = doubt.copy(
                    quickAnswer = freshResult.quickAnswer.ifBlank { doubt.quickAnswer },
                    detailedAnswer = freshResult.detailedAnswer.ifBlank { doubt.detailedAnswer },
                    notificationSummary = freshResult.notificationSummary.ifBlank { doubt.notificationSummary },
                    referenceMaterials = freshResult.referenceMaterials.ifBlank { doubt.referenceMaterials },
                    isFactChecked = freshResult.isFactChecked || doubt.isFactChecked,
                    verificationStatus = freshResult.verificationStatus.ifBlank { doubt.verificationStatus },
                    verificationSourcesJson = if (freshResult.verificationSources.isNotEmpty()) {
                        DoubtItem.encodeVerificationSources(freshResult.verificationSources)
                    } else {
                        doubt.verificationSourcesJson
                    }
                )
                val repo = getRepository(context)
                repo.updateDoubt(updated)
                _latestDetectedDoubt.value = updated
                if (_selectedDoubtForDetail.value?.id == updated.id) {
                    _selectedDoubtForDetail.value = updated
                }
                _statusMessage.value = "✅ Verified & deepened answer for '${updated.topic}'"
                if (_autoReadAloud.value) {
                    TtsManager.speakAnswer(context, "${updated.topic}. ${updated.quickAnswer}", updated.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deepen and verify doubt", e)
                _statusMessage.value = "Could not deepen answer: ${e.message}"
            } finally {
                _isRefiningAnswer.value = false
            }
        }
    }
}
