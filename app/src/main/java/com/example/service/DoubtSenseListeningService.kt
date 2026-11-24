package com.example.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ServiceCompat
import com.example.ai.AnswerEngine
import com.example.ai.OfflineKnowledgeBase
import com.example.audio.LiveVoiceCaptureManager
import com.example.audio.VoiceCaptureListener
import com.example.util.AppPreferencesManager
import com.example.util.BatteryMonitor
import com.example.util.NotificationHelper
import com.example.util.ThemeMode
import com.example.util.TtsManager
import com.example.util.VoiceCommand
import com.example.util.VoiceCommandProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class DoubtSenseListeningService : Service(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var fallbackCaptureManager: LiveVoiceCaptureManager? = null
    private var isPaused = false
    private var isServiceRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val answerEngine = AnswerEngine()

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        TtsManager.init(this)
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(this@DoubtSenseListeningService)
            }
        } else {
            Log.w(TAG, "Android SpeechRecognizer unavailable; using AudioRecord + Gemini speech-to-text fallback")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                isServiceRunning = true
                isPaused = false
                val notification = NotificationHelper.buildListeningNotification(this, isPaused = false)
                val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
                ServiceCompat.startForeground(
                    this,
                    NotificationHelper.NOTIFICATION_LISTENING_ID,
                    notification,
                    fgType
                )
                ListeningStateManager.updateListeningState(ListeningState.LISTENING)
                startListeningInternal()
            }
            NotificationHelper.ACTION_PAUSE -> {
                isPaused = true
                stopListeningInternal()
                val notification = NotificationHelper.buildListeningNotification(this, isPaused = true)
                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(NotificationHelper.NOTIFICATION_LISTENING_ID, notification)
                ListeningStateManager.updateListeningState(ListeningState.PAUSED)
            }
            NotificationHelper.ACTION_RESUME -> {
                isPaused = false
                val notification = NotificationHelper.buildListeningNotification(this, isPaused = false)
                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(NotificationHelper.NOTIFICATION_LISTENING_ID, notification)
                ListeningStateManager.updateListeningState(ListeningState.LISTENING)
                startListeningInternal()
            }
            NotificationHelper.ACTION_STOP -> {
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startListeningInternal() {
        if (isPaused || !isServiceRunning) return
        if (BatteryMonitor.shouldPauseBackgroundListening(this)) {
            isPaused = true
            ListeningStateManager.updateListeningState(ListeningState.PAUSED)
            ListeningStateManager.setStatusMessage("Background listening paused (Battery Saver)")
            return
        }

        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(this)) {
                    if (speechRecognizer == null) {
                        initializeRecognizer()
                    }
                    val sttTag = AppPreferencesManager.sttLanguageTag.value.ifBlank { Locale.getDefault().toLanguageTag() }
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttTag)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sttTag)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    }
                    speechRecognizer?.startListening(intent)
                } else {
                    startAudioRecordFallbackCapture()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech listener, switching to direct AudioRecord fallback", e)
                startAudioRecordFallbackCapture()
            }
        }
    }

    private fun startAudioRecordFallbackCapture() {
        if (isPaused || !isServiceRunning) return
        if (fallbackCaptureManager == null) {
            fallbackCaptureManager = LiveVoiceCaptureManager(
                this,
                object : VoiceCaptureListener {
                    override fun onCaptureStarted(modeLabel: String) {
                        ListeningStateManager.setStatusMessage("Live mic active ($modeLabel)")
                    }

                    override fun onAudioLevelChanged(level: Float) {
                        if (!isPaused && isServiceRunning) {
                            ListeningStateManager.updateAudioRms(level * 10f)
                        }
                    }

                    override fun onPartialTranscript(text: String) {
                        if (!isPaused) ListeningStateManager.updateLiveTranscript(text)
                    }

                    override fun onElapsedSecondsChanged(seconds: Int) {}

                    override fun onFinalTranscriptReady(transcript: String) {
                        if (transcript.isNotBlank()) {
                            val hasDoubtSignal = OfflineKnowledgeBase.isLikelyDoubtOrQuestion(transcript)
                            ListeningStateManager.onUtteranceFinalized(
                                transcript,
                                applicationContext,
                                isExplicitQuery = hasDoubtSignal
                            )
                        }
                        scheduleNextListenCycle()
                    }

                    override fun onWavAudioReady(wavBytes: ByteArray) {
                        serviceScope.launch {
                            try {
                                ListeningStateManager.setStatusMessage("Transcribing captured microphone audio...")
                                val transcript = answerEngine.transcribeAudio(wavBytes)
                                if (transcript.isNotBlank()) {
                                    mainHandler.post {
                                        val hasDoubtSignal = OfflineKnowledgeBase.isLikelyDoubtOrQuestion(transcript)
                                        ListeningStateManager.onUtteranceFinalized(
                                            transcript,
                                            applicationContext,
                                            isExplicitQuery = hasDoubtSignal
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "AudioRecord transcription failed: ${e.message}")
                            } finally {
                                scheduleNextListenCycle()
                            }
                        }
                    }

                    override fun onCaptureError(message: String) {
                        ListeningStateManager.setStatusMessage(message)
                        scheduleNextListenCycle()
                    }
                }
            )
        }
        fallbackCaptureManager?.startCapture(preferDirectAudio = true)
    }

    private fun scheduleNextListenCycle() {
        val restartDelay = if (ListeningStateManager.lowPowerMode.value) 1000L else 450L
        if (isServiceRunning && !isPaused) {
            mainHandler.postDelayed({
                startListeningInternal()
            }, restartDelay)
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            fallbackCaptureManager?.cancelCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech listener", e)
        }
    }

    // RecognitionListener callbacks
    override fun onReadyForSpeech(params: Bundle?) {
        if (!isPaused && isServiceRunning) {
            ListeningStateManager.updateListeningState(ListeningState.LISTENING)
        }
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {
        if (!isPaused && isServiceRunning) {
            ListeningStateManager.updateAudioRms(rmsdB)
        }
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        ListeningStateManager.updateAudioRms(0f)
    }

    override fun onError(error: Int) {
        ListeningStateManager.updateAudioRms(0f)
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                ListeningStateManager.setStatusMessage("Microphone permission required for real-time speech transcription.")
                stopSelf()
                return
            }
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                ListeningStateManager.setStatusMessage("Switching to direct AudioRecord capture...")
                stopListeningInternal()
                if (isServiceRunning && !isPaused) {
                    mainHandler.postDelayed({ startAudioRecordFallbackCapture() }, 300L)
                }
                return
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                ListeningStateManager.setStatusMessage("Speech recognizer busy; reinitializing...")
                stopListeningInternal()
            }
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                ListeningStateManager.setStatusMessage("Speech recognition offline/network delay; retrying...")
            }
        }
        scheduleNextListenCycle()
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim()
        if (!text.isNullOrBlank()) {
            val cmd = VoiceCommandProcessor.parseCommand(text)
            if (cmd != null) {
                when (cmd) {
                    VoiceCommand.SWITCH_TO_DARK_MODE -> AppPreferencesManager.setThemeMode(applicationContext, ThemeMode.DARK)
                    VoiceCommand.SWITCH_TO_LIGHT_MODE -> AppPreferencesManager.setThemeMode(applicationContext, ThemeMode.LIGHT)
                    VoiceCommand.SWITCH_TO_HIGH_CONTRAST -> AppPreferencesManager.setThemeMode(applicationContext, ThemeMode.HIGH_CONTRAST)
                    VoiceCommand.CLEAR_LIST -> ListeningStateManager.clearTranscript()
                    VoiceCommand.EXPORT_HISTORY -> ListeningStateManager.setStatusMessage("Voice command: Export history")
                }
                ListeningStateManager.setStatusMessage("Voice command executed: ${cmd.description}")
            } else {
                val hasDoubtSignal = OfflineKnowledgeBase.isLikelyDoubtOrQuestion(text)
                ListeningStateManager.onUtteranceFinalized(
                    utterance = text,
                    context = applicationContext,
                    isExplicitQuery = hasDoubtSignal
                )
            }
        }

        val restartDelay = if (ListeningStateManager.lowPowerMode.value || AppPreferencesManager.batterySaverMode.value) 1000L else 300L
        if (isServiceRunning && !isPaused) {
            mainHandler.postDelayed({
                startListeningInternal()
            }, restartDelay)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val partialText = matches?.firstOrNull()
        if (!partialText.isNullOrBlank() && !isPaused) {
            ListeningStateManager.updateLiveTranscript(partialText)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        isServiceRunning = false
        isPaused = false
        stopListeningInternal()
        speechRecognizer?.destroy()
        speechRecognizer = null
        fallbackCaptureManager?.release()
        fallbackCaptureManager = null
        serviceScope.cancel()
        ListeningStateManager.updateListeningState(ListeningState.IDLE)
        ListeningStateManager.updateAudioRms(0f)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.doubtsense.ACTION_START"
        private const val TAG = "DoubtSenseService"
    }
}
