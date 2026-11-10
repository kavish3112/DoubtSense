package com.example.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.util.NotificationHelper
import java.util.Locale

class DoubtSenseListeningService : Service(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isPaused = false
    private var isServiceRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(this@DoubtSenseListeningService)
            }
        } else {
            Log.w(TAG, "Speech recognition is not available on this device")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                isServiceRunning = true
                isPaused = false
                val notification = NotificationHelper.buildListeningNotification(this, isPaused = false)
                startForeground(NotificationHelper.NOTIFICATION_LISTENING_ID, notification)
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

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initializeRecognizer()
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech listener", e)
            }
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
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
        // Adjust duty-cycle interval based on Battery Optimization Low-Power Mode setting
        val restartDelay = if (ListeningStateManager.lowPowerMode.value) 1000L else 400L
        if (isServiceRunning && !isPaused) {
            mainHandler.postDelayed({
                startListeningInternal()
            }, restartDelay)
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim()
        if (!text.isNullOrBlank()) {
            ListeningStateManager.onUtteranceFinalized(text, applicationContext)
        }

        val restartDelay = if (ListeningStateManager.lowPowerMode.value) 800L else 300L
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
