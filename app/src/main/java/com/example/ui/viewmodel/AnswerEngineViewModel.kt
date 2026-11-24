package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AnswerEngine
import com.example.ai.AnswerEngineResult
import com.example.audio.LiveVoiceCaptureManager
import com.example.audio.VoiceCaptureListener
import com.example.data.local.OfflineResponseCache
import com.example.data.model.ConversationSession
import com.example.data.repository.DoubtRepository
import com.example.service.ListeningStateManager
import com.example.util.AppPreferencesManager
import com.example.util.BatteryMonitor
import com.example.util.ExportDocumentFormat
import com.example.util.ListeningCaptureMode
import com.example.util.ShareHelper
import com.example.util.ThemeMode
import com.example.util.TtsManager
import com.example.util.VoiceCommand
import com.example.util.VoiceCommandProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AnswerEngineViewModel"

sealed interface AnswerEngineUiState {
    data object Idle : AnswerEngineUiState
    data class Listening(
        val modeLabel: String,
        val partialTranscript: String,
        val audioLevel: Float,
        val elapsedSeconds: Int
    ) : AnswerEngineUiState
    data object TranscribingAudio : AnswerEngineUiState
    data class GeneratingAnswer(
        val query: String,
        val streamingAnswer: String = ""
    ) : AnswerEngineUiState
    data class Success(val result: AnswerEngineResult) : AnswerEngineUiState
    data class Error(
        val message: String,
        val lastQuery: String = "",
        val isPermissionError: Boolean = false,
        val permanentlyDenied: Boolean = false
    ) : AnswerEngineUiState
}

/**
 * Coordinates live microphone audio capture -> speech-to-text conversion ->
 * AnswerEngine streaming answer generation -> Room & Offline 10-item cache -> TTS readback.
 */
class AnswerEngineViewModel(
    application: Application,
    private val answerEngine: AnswerEngine
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, AnswerEngine())

    private val repository by lazy {
        ListeningStateManager.getRepository(getApplication<Application>())
    }

    private val _uiState = MutableStateFlow<AnswerEngineUiState>(AnswerEngineUiState.Idle)
    val uiState: StateFlow<AnswerEngineUiState> = _uiState.asStateFlow()

    private val _queryInput = MutableStateFlow("")
    val queryInput: StateFlow<String> = _queryInput.asStateFlow()

    private val _preferDirectAudioCapture = MutableStateFlow(false)
    val preferDirectAudioCapture: StateFlow<Boolean> = _preferDirectAudioCapture.asStateFlow()

    private val _followUpParentContext = MutableStateFlow<List<String>>(emptyList())
    val followUpParentContext: StateFlow<List<String>> = _followUpParentContext.asStateFlow()

    private val _voiceCommandFeedback = MutableStateFlow<String?>(null)
    val voiceCommandFeedback: StateFlow<String?> = _voiceCommandFeedback.asStateFlow()

    private val _summarizingItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val summarizingItemIds: StateFlow<Set<Long>> = _summarizingItemIds.asStateFlow()

    private val _cachedOfflineResults = MutableStateFlow(
        OfflineResponseCache.getCachedResults(application)
    )
    val cachedOfflineResults: StateFlow<List<AnswerEngineResult>> = _cachedOfflineResults.asStateFlow()

    val aiResponseLanguage: StateFlow<String> = AppPreferencesManager.aiResponseLanguage
    val listeningCaptureMode: StateFlow<ListeningCaptureMode> = AppPreferencesManager.listeningCaptureMode
    val batterySaverMode: StateFlow<Boolean> = AppPreferencesManager.batterySaverMode
    val offlineMode: StateFlow<Boolean> = AppPreferencesManager.offlineMode

    /**
     * Reactive Q&A history backed by Room (`doubts` table), pinned favorites first.
     */
    val recentAnswers: StateFlow<List<AnswerEngineResult>> = repository.allDoubts
        .map { doubts ->
            DoubtRepository.sortWithPinnedFirst(doubts)
                .take(40)
                .map { AnswerEngineResult.fromDoubtItem(it) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private var activeJob: Job? = null

    internal val captureListener = object : VoiceCaptureListener {
        override fun onCaptureStarted(modeLabel: String) {
            _uiState.value = AnswerEngineUiState.Listening(
                modeLabel = modeLabel,
                partialTranscript = "",
                audioLevel = 0.08f,
                elapsedSeconds = 0
            )
        }

        override fun onAudioLevelChanged(level: Float) {
            val current = _uiState.value
            if (current is AnswerEngineUiState.Listening) {
                _uiState.value = current.copy(audioLevel = level)
            }
        }

        override fun onPartialTranscript(text: String) {
            val current = _uiState.value
            if (current is AnswerEngineUiState.Listening) {
                _uiState.value = current.copy(partialTranscript = text)
            }
            _queryInput.value = text
        }

        override fun onElapsedSecondsChanged(seconds: Int) {
            val current = _uiState.value
            if (current is AnswerEngineUiState.Listening) {
                _uiState.value = current.copy(elapsedSeconds = seconds)
            }
        }

        override fun onFinalTranscriptReady(transcript: String) {
            _queryInput.value = transcript
            if (tryExecuteVoiceCommand(transcript)) {
                _uiState.value = AnswerEngineUiState.Idle
                return
            }
            submitQueryToAnswerEngine(
                query = transcript,
                capturedViaVoice = true
            )
        }

        override fun onWavAudioReady(wavBytes: ByteArray) {
            transcribeAndAnswerAudio(wavBytes)
        }

        override fun onCaptureError(message: String) {
            _uiState.value = AnswerEngineUiState.Error(message = message, lastQuery = _queryInput.value)
        }
    }

    private val voiceCaptureManager by lazy {
        LiveVoiceCaptureManager(getApplication(), captureListener)
    }

    fun updateQueryInput(newText: String) {
        _queryInput.value = newText
    }

    fun setPreferDirectAudioCapture(preferDirect: Boolean) {
        _preferDirectAudioCapture.value = preferDirect
    }

    fun setAiResponseLanguage(language: String) {
        AppPreferencesManager.setAiResponseLanguage(getApplication(), language)
    }

    fun cycleAiResponseLanguage(): String {
        return AppPreferencesManager.cycleAiResponseLanguage(getApplication())
    }

    fun setListeningCaptureMode(mode: ListeningCaptureMode) {
        AppPreferencesManager.setListeningCaptureMode(getApplication(), mode)
    }

    fun clearVoiceCommandFeedback() {
        _voiceCommandFeedback.value = null
    }

    fun hasRecordPermission(): Boolean = voiceCaptureManager.hasRecordAudioPermission()

    fun startMicrophoneCapture() {
        activeJob?.cancel()
        val app = getApplication<Application>()
        if (BatteryMonitor.shouldPauseBackgroundListening(app)) {
            _voiceCommandFeedback.value = "Battery Saver active: paused background listening at low battery"
        }
        voiceCaptureManager.startCapture(preferDirectAudio = _preferDirectAudioCapture.value)
    }

    fun startFollowUpCapture(existingResult: AnswerEngineResult) {
        _followUpParentContext.value = listOf(
            "Previous question: ${existingResult.query}",
            "Previous answer: ${existingResult.quickAnswer}"
        )
        startMicrophoneCapture()
    }

    fun stopMicrophoneAndAnswer() {
        voiceCaptureManager.stopCaptureAndSubmit()
    }

    fun cancelMicrophoneCapture() {
        voiceCaptureManager.cancelCapture()
        _uiState.value = AnswerEngineUiState.Idle
    }

    fun onPermissionDenied(permanentlyDenied: Boolean = false) {
        _uiState.value = AnswerEngineUiState.Error(
            message = if (permanentlyDenied) {
                "Microphone permission is turned off. Open App Settings to allow microphone access for voice questions."
            } else {
                "Microphone permission is needed to capture live audio. Grant permission or type your question below."
            },
            isPermissionError = true,
            permanentlyDenied = permanentlyDenied
        )
    }

    fun clearPermissionErrorIfGranted() {
        val current = _uiState.value
        if (current is AnswerEngineUiState.Error && current.isPermissionError && hasRecordPermission()) {
            _uiState.value = AnswerEngineUiState.Idle
        }
    }

    fun selectRecentAnswer(result: AnswerEngineResult) {
        _queryInput.value = result.query
        _uiState.value = AnswerEngineUiState.Success(result)
    }

    fun clearFollowUpContext() {
        _followUpParentContext.value = emptyList()
    }

    fun submitCurrentTextQuery() {
        val clean = _queryInput.value.trim()
        if (clean.isBlank()) {
            _uiState.value = AnswerEngineUiState.Error("Please enter or speak a question first.")
            return
        }
        if (tryExecuteVoiceCommand(clean)) {
            _queryInput.value = ""
            return
        }
        submitQueryToAnswerEngine(query = clean, capturedViaVoice = false)
    }

    fun submitFollowUpQuery(followUpQuestion: String, parentResult: AnswerEngineResult) {
        _followUpParentContext.value = listOf(
            "Previous question: ${parentResult.query}",
            "Previous answer: ${parentResult.quickAnswer}"
        )
        submitQueryToAnswerEngine(query = followUpQuestion, capturedViaVoice = false)
    }

    fun submitQueryToAnswerEngine(query: String, capturedViaVoice: Boolean) {
        val clean = query.trim()
        if (clean.isBlank()) {
            _uiState.value = AnswerEngineUiState.Error("No spoken or typed text found to answer.")
            return
        }
        _queryInput.value = clean
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = AnswerEngineUiState.GeneratingAnswer(query = clean, streamingAnswer = "")
            try {
                val app = getApplication<Application>()
                val mergedContext = (_followUpParentContext.value + ListeningStateManager.conversationHistory.value.takeLast(4))
                    .distinct()
                    .takeLast(6)
                val factCheckEnabled = ListeningStateManager.factCheckingMode.value
                val targetLanguage = AppPreferencesManager.aiResponseLanguage.value

                // Check Offline Mode / network availability -> serve from bounded 10-item cache if matched
                val useOffline = AppPreferencesManager.offlineMode.value || !OfflineResponseCache.isNetworkAvailable(app)
                val cachedMatch = if (useOffline) OfflineResponseCache.findBestCachedMatch(app, clean) else null

                val rawResult = if (cachedMatch != null && cachedMatch.query.equals(clean, ignoreCase = true)) {
                    cachedMatch.copy(
                        groundingLabel = "Offline Cache (10-Recent)",
                        verificationStatus = "Served from Offline Response Cache",
                        timestamp = System.currentTimeMillis()
                    )
                } else {
                    answerEngine.generateStreamingAnswer(
                        query = clean,
                        conversationContext = mergedContext,
                        factCheckingMode = factCheckEnabled,
                        capturedViaVoice = capturedViaVoice,
                        responseLanguage = targetLanguage,
                        onPartialText = { partial ->
                            _uiState.value = AnswerEngineUiState.GeneratingAnswer(
                                query = clean,
                                streamingAnswer = partial
                            )
                        }
                    )
                }

                val savedResult = persistAnswerResult(rawResult)
                OfflineResponseCache.cacheResult(app, savedResult)
                _cachedOfflineResults.value = OfflineResponseCache.getCachedResults(app)
                _followUpParentContext.value = emptyList()
                _uiState.value = AnswerEngineUiState.Success(savedResult)

                if (ListeningStateManager.autoReadAloud.value) {
                    TtsManager.speakAnswer(
                        app,
                        "${savedResult.topic}. ${savedResult.quickAnswer}",
                        savedResult.doubtId
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AnswerEngine failed to generate answer", e)
                val app = getApplication<Application>()
                val offlineFallback = OfflineResponseCache.findBestCachedMatch(app, clean)
                if (offlineFallback != null) {
                    _uiState.value = AnswerEngineUiState.Success(
                        offlineFallback.copy(
                            groundingLabel = "Offline Cache Fallback",
                            verificationStatus = "Retrieved from Offline Cache"
                        )
                    )
                } else {
                    _uiState.value = AnswerEngineUiState.Error(
                        message = e.localizedMessage ?: "Failed to generate answer. Please try again.",
                        lastQuery = clean
                    )
                }
            }
        }
    }

    fun toggleFavorite(item: AnswerEngineResult) {
        if (item.doubtId <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = repository.getDoubtById(item.doubtId) ?: item.toDoubtItem()
                val updated = existing.copy(isStarred = !existing.isStarred)
                repository.updateDoubt(updated)
                val current = _uiState.value
                if (current is AnswerEngineUiState.Success && current.result.doubtId == item.doubtId) {
                    _uiState.value = AnswerEngineUiState.Success(current.result.copy(isStarred = updated.isStarred))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not toggle favorite for id=${item.doubtId}: ${e.message}")
            }
        }
    }

    fun updateCustomTag(item: AnswerEngineResult, newTag: String) {
        val cleanTag = newTag.trim().ifBlank { return }
        AppPreferencesManager.addCustomTag(getApplication(), cleanTag)
        if (item.doubtId <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = repository.getDoubtById(item.doubtId) ?: item.toDoubtItem()
                repository.updateDoubt(existing.copy(customTag = cleanTag))
                val current = _uiState.value
                if (current is AnswerEngineUiState.Success && current.result.doubtId == item.doubtId) {
                    _uiState.value = AnswerEngineUiState.Success(current.result.copy(customTag = cleanTag))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not update custom tag for id=${item.doubtId}: ${e.message}")
            }
        }
    }

    fun generateOneSentenceSummary(item: AnswerEngineResult) {
        if (item.doubtId <= 0L) return
        if (_summarizingItemIds.value.contains(item.doubtId)) return
        viewModelScope.launch {
            _summarizingItemIds.value = _summarizingItemIds.value + item.doubtId
            try {
                val summary = answerEngine.generateOneSentenceSummary(
                    question = item.query,
                    answer = item.detailedAnswer,
                    language = AppPreferencesManager.aiResponseLanguage.value
                )
                withContext(Dispatchers.IO) {
                    val existing = repository.getDoubtById(item.doubtId) ?: item.toDoubtItem()
                    repository.updateDoubt(existing.copy(oneSentenceSummary = summary))
                }
                val current = _uiState.value
                if (current is AnswerEngineUiState.Success && current.result.doubtId == item.doubtId) {
                    _uiState.value = AnswerEngineUiState.Success(current.result.copy(oneSentenceSummary = summary))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed generating 1-sentence summary: ${e.message}")
            } finally {
                _summarizingItemIds.value = _summarizingItemIds.value - item.doubtId
            }
        }
    }

    fun deleteHistoryItem(doubtId: Long) {
        if (doubtId <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteDoubt(doubtId)
                val current = _uiState.value
                if (current is AnswerEngineUiState.Success && current.result.doubtId == doubtId) {
                    _uiState.value = AnswerEngineUiState.Idle
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete history item id=$doubtId: ${e.message}")
            }
        }
    }

    internal fun tryExecuteVoiceCommand(transcript: String): Boolean {
        val command = VoiceCommandProcessor.parseCommand(transcript) ?: return false
        val app = getApplication<Application>()
        when (command) {
            VoiceCommand.EXPORT_HISTORY -> {
                val items = recentAnswers.value
                if (items.isNotEmpty()) {
                    ShareHelper.exportAnswerEngineHistory(app, items, ExportDocumentFormat.PDF)
                    _voiceCommandFeedback.value = "Voice command: Exporting history PDF"
                } else {
                    _voiceCommandFeedback.value = "Voice command recognized: No saved history to export yet"
                }
            }
            VoiceCommand.CLEAR_LIST -> {
                viewModelScope.launch(Dispatchers.IO) {
                    repository.clearAll()
                    OfflineResponseCache.clearCache(app)
                    _cachedOfflineResults.value = emptyList()
                }
                _uiState.value = AnswerEngineUiState.Idle
                _voiceCommandFeedback.value = "Voice command: Cleared history list"
            }
            VoiceCommand.SWITCH_TO_DARK_MODE -> {
                AppPreferencesManager.setThemeMode(app, ThemeMode.DARK)
                _voiceCommandFeedback.value = "Voice command: Switched to Dark mode"
            }
            VoiceCommand.SWITCH_TO_LIGHT_MODE -> {
                AppPreferencesManager.setThemeMode(app, ThemeMode.LIGHT)
                _voiceCommandFeedback.value = "Voice command: Switched to Light mode"
            }
            VoiceCommand.SWITCH_TO_HIGH_CONTRAST -> {
                AppPreferencesManager.setThemeMode(app, ThemeMode.HIGH_CONTRAST)
                _voiceCommandFeedback.value = "Voice command: Switched to High Contrast mode"
            }
        }
        runCatching {
            Toast.makeText(app, _voiceCommandFeedback.value.orEmpty(), Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun transcribeAndAnswerAudio(wavBytes: ByteArray) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = AnswerEngineUiState.TranscribingAudio
            try {
                val transcript = answerEngine.transcribeAudio(wavBytes)
                _queryInput.value = transcript
                if (tryExecuteVoiceCommand(transcript)) {
                    _uiState.value = AnswerEngineUiState.Idle
                    return@launch
                }
                _uiState.value = AnswerEngineUiState.GeneratingAnswer(query = transcript, streamingAnswer = "")

                val app = getApplication<Application>()
                val mergedContext = (_followUpParentContext.value + ListeningStateManager.conversationHistory.value.takeLast(4))
                    .distinct()
                    .takeLast(6)
                val factCheckEnabled = ListeningStateManager.factCheckingMode.value
                val targetLanguage = AppPreferencesManager.aiResponseLanguage.value
                val rawResult = answerEngine.generateStreamingAnswer(
                    query = transcript,
                    conversationContext = mergedContext,
                    factCheckingMode = factCheckEnabled,
                    capturedViaVoice = true,
                    responseLanguage = targetLanguage,
                    onPartialText = { partial ->
                        _uiState.value = AnswerEngineUiState.GeneratingAnswer(
                            query = transcript,
                            streamingAnswer = partial
                        )
                    }
                )
                val savedResult = persistAnswerResult(rawResult)
                OfflineResponseCache.cacheResult(app, savedResult)
                _cachedOfflineResults.value = OfflineResponseCache.getCachedResults(app)
                _followUpParentContext.value = emptyList()
                _uiState.value = AnswerEngineUiState.Success(savedResult)

                if (ListeningStateManager.autoReadAloud.value) {
                    TtsManager.speakAnswer(
                        app,
                        "${savedResult.topic}. ${savedResult.quickAnswer}",
                        savedResult.doubtId
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice audio transcription/answer failed", e)
                _uiState.value = AnswerEngineUiState.Error(
                    message = e.localizedMessage ?: "Could not process voice audio.",
                    lastQuery = _queryInput.value
                )
            }
        }
    }

    private suspend fun persistAnswerResult(result: AnswerEngineResult): AnswerEngineResult {
        return withContext(Dispatchers.IO) {
            try {
                val appContext = getApplication<Application>()
                val repo = ListeningStateManager.getRepository(appContext)
                val sessionId = repo.saveSession(
                    ConversationSession(
                        startTime = result.timestamp,
                        endTime = System.currentTimeMillis(),
                        title = result.topic,
                        category = result.subject,
                        customTag = result.customTag,
                        transcriptJson = ConversationSession.encodeTranscriptTurns(
                            listOf("Question: ${result.query}", "Answer: ${result.quickAnswer}")
                        ),
                        transcriptPreview = result.query,
                        averageDoubtIntensity = result.confidence,
                        peakDoubtIntensity = result.confidence,
                        doubtCount = 1,
                        summary = result.quickAnswer.take(120)
                    )
                )
                val savedId = repo.saveDoubt(
                    result.toDoubtItem(conversationalContext = "Answer Engine (${if (result.capturedViaVoice) "Voice Mic" else "Direct Input"})")
                        .copy(sessionId = sessionId)
                )
                result.copy(doubtId = savedId)
            } catch (e: Exception) {
                Log.w(TAG, "Could not persist AnswerEngine result to Room: ${e.message}")
                result
            }
        }
    }

    override fun onCleared() {
        voiceCaptureManager.release()
        super.onCleared()
    }
}
