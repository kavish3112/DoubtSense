package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.example.ai.DoubtAnalysisEngine
import com.example.data.local.DoubtDatabase
import com.example.data.model.DoubtItem
import com.example.data.repository.DoubtRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ListeningStateManager"

enum class ListeningState {
    IDLE,
    LISTENING,
    PAUSED,
    PROCESSING
}

object ListeningStateManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val aiEngine = DoubtAnalysisEngine()
    private val contextAnalysisService = com.example.ai.ContextAnalysisService()

    private var repository: DoubtRepository? = null

    private val _listeningState = MutableStateFlow(ListeningState.IDLE)
    val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<String>>(emptyList())
    val conversationHistory: StateFlow<List<String>> = _conversationHistory.asStateFlow()

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

    fun initRepository(context: Context) {
        if (repository == null) {
            val db = DoubtDatabase.getInstance(context)
            repository = DoubtRepository(db.doubtDao())
        }
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

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        }
        return true
    }

    fun updateListeningState(state: ListeningState) {
        _listeningState.value = state
    }

    fun updateLiveTranscript(text: String) {
        _liveTranscript.value = text
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
        val serviceIntent = Intent(context, DoubtSenseListeningService::class.java).apply {
            action = DoubtSenseListeningService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
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
    }

    fun clearTranscript() {
        _liveTranscript.value = ""
        _conversationHistory.value = emptyList()
    }

    /**
     * Called when a speech utterance is finalized by the SpeechRecognizer or manual tester.
     */
    fun onUtteranceFinalized(utterance: String, context: Context) {
        val cleanText = utterance.trim()
        if (cleanText.isBlank()) return

        val currentHistory = _conversationHistory.value.toMutableList()
        currentHistory.add(cleanText)
        if (currentHistory.size > 25) {
            currentHistory.removeAt(0)
        }
        _conversationHistory.value = currentHistory
        _liveTranscript.value = ""

        processSpeechForDoubt(cleanText, currentHistory, context)
    }

    private fun processSpeechForDoubt(
        latestSpeech: String,
        contextTurns: List<String>,
        context: Context
    ) {
        scope.launch {
            _isAnalyzing.value = true
            val prevState = _listeningState.value
            _listeningState.value = ListeningState.PROCESSING
            _statusMessage.value = "Analyzing context, intent & topic with ContextAnalysisService..."

            try {
                // Trigger ContextAnalysisService to analyze transcript segments for subjects, topics, and intent
                val result = contextAnalysisService.analyzeTranscriptSegment(latestSpeech, contextTurns)
                if (result.isDoubt) {
                    val doubtItem = result.toDoubtItem(contextTurns.takeLast(6).joinToString(" | "))
                    val repo = getRepository(context)
                    val id = repo.saveDoubt(doubtItem)
                    val savedItem = doubtItem.copy(id = id)

                    _latestDetectedDoubt.value = savedItem
                    _statusMessage.value = "💡 Doubt identified: ${savedItem.topic} (${savedItem.doubtType}) [${savedItem.subject}]"

                    NotificationHelper.showDoubtAnswerNotification(
                        context = context,
                        doubtId = id,
                        topic = savedItem.topic,
                        quickAnswer = savedItem.quickAnswer,
                        doubtSnippet = savedItem.doubtSnippet
                    )
                } else {
                    _statusMessage.value = "Context analyzed. No doubt detected."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed during context analysis pipeline", e)
                _statusMessage.value = "Analysis error: ${e.message}"
            } finally {
                _isAnalyzing.value = false
                if (_listeningState.value == ListeningState.PROCESSING) {
                    _listeningState.value = if (prevState == ListeningState.PROCESSING) ListeningState.IDLE else prevState
                }
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
                val personalizedAnswer = aiEngine.generatePersonalizedAnswer(
                    doubt = doubt,
                    userClarification = userClarification,
                    style = style
                )

                val updatedItem = doubt.copy(
                    detailedAnswer = personalizedAnswer,
                    userClarificationResponse = userClarification,
                    preferredStyle = style,
                    isResolvedWithClarification = true,
                    isAmbiguous = false
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
}
