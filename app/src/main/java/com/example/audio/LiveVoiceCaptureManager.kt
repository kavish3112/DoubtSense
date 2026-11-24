package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale

private const val TAG = "LiveVoiceCapture"

interface VoiceCaptureListener {
    fun onCaptureStarted(modeLabel: String)
    fun onAudioLevelChanged(level: Float)
    fun onPartialTranscript(text: String)
    fun onElapsedSecondsChanged(seconds: Int)
    fun onFinalTranscriptReady(transcript: String)
    fun onWavAudioReady(wavBytes: ByteArray)
    fun onCaptureError(message: String)
}

/**
 * Dual-mode live microphone capture controller:
 * 1) Real-time Android [SpeechRecognizer] streaming partial + final transcripts.
 * 2) Direct [AudioRecord] 16kHz PCM-to-WAV capture (auto-engaged whenever SpeechRecognizer is
 *    unavailable on the device/emulator or when direct audio capture is preferred).
 */
class LiveVoiceCaptureManager(
    context: Context,
    private val listener: VoiceCaptureListener
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var speechRecognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var audioRecordJob: Job? = null
    private var timerJob: Job? = null
    private val pcmStream = ByteArrayOutputStream()

    @Volatile
    private var isCapturing = false

    @Volatile
    private var activeMode: CaptureMode = CaptureMode.SPEECH_RECOGNIZER

    enum class CaptureMode(val displayLabel: String) {
        SPEECH_RECOGNIZER("Live Speech-to-Text"),
        AUDIO_RECORD_WAV("Direct Mic Audio (WAV)")
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startCapture(preferDirectAudio: Boolean = false) {
        if (!hasRecordAudioPermission()) {
            listener.onCaptureError("Microphone permission is required to capture live audio.")
            return
        }

        stopAllInternal()
        isCapturing = true

        val recognizerAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        if (!preferDirectAudio && recognizerAvailable) {
            startSpeechRecognizerCapture()
        } else {
            startDirectAudioRecordCapture()
        }
    }

    /**
     * Stops live capture. For SpeechRecognizer, triggers final recognition result if any;
     * for AudioRecord, encodes the recorded PCM audio into WAV and delivers it for AI transcription.
     */
    fun stopCaptureAndSubmit() {
        if (!isCapturing) return
        when (activeMode) {
            CaptureMode.SPEECH_RECOGNIZER -> {
                mainHandler.post {
                    try {
                        speechRecognizer?.stopListening()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping SpeechRecognizer cleanly: ${e.message}")
                        cancelCapture()
                    }
                }
            }
            CaptureMode.AUDIO_RECORD_WAV -> {
                finishDirectAudioRecordAndDeliver()
            }
        }
    }

    fun cancelCapture() {
        isCapturing = false
        stopAllInternal()
        listener.onAudioLevelChanged(0f)
    }

    private fun startSpeechRecognizerCapture() {
        activeMode = CaptureMode.SPEECH_RECOGNIZER
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            if (isCapturing) {
                                listener.onCaptureStarted(CaptureMode.SPEECH_RECOGNIZER.displayLabel)
                                startElapsedTimer()
                            }
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {
                            if (!isCapturing) return
                            // Android RMS dB typically spans -2..10+
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                            listener.onAudioLevelChanged(normalized)
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            listener.onAudioLevelChanged(0f)
                        }

                        override fun onError(error: Int) {
                            if (!isCapturing) return
                            listener.onAudioLevelChanged(0f)
                            when (error) {
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                    isCapturing = false
                                    stopAllInternal()
                                    listener.onCaptureError("Microphone permission denied.")
                                }
                                SpeechRecognizer.ERROR_AUDIO,
                                SpeechRecognizer.ERROR_CLIENT,
                                SpeechRecognizer.ERROR_SERVER,
                                SpeechRecognizer.ERROR_NETWORK,
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                                    Log.w(TAG, "SpeechRecognizer error=$error; switching to AudioRecord WAV capture")
                                    stopSpeechRecognizerOnly()
                                    startDirectAudioRecordCapture()
                                }
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                    isCapturing = false
                                    stopAllInternal()
                                    listener.onCaptureError("No speech detected. Tap the microphone and speak clearly.")
                                }
                                else -> {
                                    isCapturing = false
                                    stopAllInternal()
                                    listener.onCaptureError("Speech capture interrupted (code $error). Try again.")
                                }
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val wasCapturing = isCapturing
                            isCapturing = false
                            timerJob?.cancel()
                            listener.onAudioLevelChanged(0f)
                            if (!wasCapturing) return

                            val text = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            if (text.isNotBlank()) {
                                listener.onFinalTranscriptReady(text)
                            } else {
                                listener.onCaptureError("Didn't catch that. Please tap the mic and speak clearly.")
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            if (!isCapturing) return
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            if (partial.isNotBlank()) {
                                listener.onPartialTranscript(partial)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val sttLocaleTag = com.example.util.AppPreferencesManager.sttLanguageTag.value
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLocaleTag.ifBlank { Locale.getDefault().toLanguageTag() })
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, sttLocaleTag.ifBlank { Locale.getDefault().toLanguageTag() })
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_800L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
                }
                speechRecognizer?.startListening(intent)
                listener.onCaptureStarted(CaptureMode.SPEECH_RECOGNIZER.displayLabel)
                startElapsedTimer()
            } catch (e: Exception) {
                Log.w(TAG, "Falling back to direct AudioRecord capture: ${e.message}")
                stopSpeechRecognizerOnly()
                startDirectAudioRecordCapture()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDirectAudioRecordCapture() {
        activeMode = CaptureMode.AUDIO_RECORD_WAV
        pcmStream.reset()

        val sampleRate = AudioWavEncoder.DEFAULT_SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        val bufferSize = (minBuf * 2).coerceAtLeast(8192)

        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                isCapturing = false
                listener.onCaptureError("Unable to initialize microphone AudioRecord hardware.")
                return
            }

            audioRecord = recorder
            recorder.startRecording()
            listener.onCaptureStarted(CaptureMode.AUDIO_RECORD_WAV.displayLabel)
            startElapsedTimer()

            audioRecordJob = ioScope.launch {
                val readBuffer = ShortArray(2048)
                val maxRecordingMs = 25_000L
                val silenceAutoStopMs = 2_000L
                val speechStartThreshold = 0.09f
                val startTimeMs = System.currentTimeMillis()
                var heardSpeech = false
                var lastSpeechAtMs = startTimeMs

                while (isActive && isCapturing) {
                    val readCount = recorder.read(readBuffer, 0, readBuffer.size)
                    if (readCount > 0) {
                        val pcmBytes = AudioWavEncoder.shortsToLittleEndianBytes(readBuffer, readCount)
                        synchronized(pcmStream) {
                            pcmStream.write(pcmBytes)
                        }
                        val level = AudioWavEncoder.computeNormalizedRms(readBuffer, readCount)
                        mainHandler.post {
                            if (isCapturing) {
                                listener.onAudioLevelChanged(level)
                            }
                        }
                        val now = System.currentTimeMillis()
                        if (level >= speechStartThreshold) {
                            heardSpeech = true
                            lastSpeechAtMs = now
                        }
                        if (now - startTimeMs >= maxRecordingMs) {
                            mainHandler.post { finishDirectAudioRecordAndDeliver() }
                            break
                        }
                        if (heardSpeech && (now - lastSpeechAtMs >= silenceAutoStopMs) && (now - startTimeMs >= 1_600L)) {
                            mainHandler.post { finishDirectAudioRecordAndDeliver() }
                            break
                        }
                    } else if (readCount < 0) {
                        Log.w(TAG, "AudioRecord read returned error code: $readCount")
                        break
                    }
                }
            }
        } catch (e: SecurityException) {
            isCapturing = false
            listener.onCaptureError("Microphone permission denied.")
        } catch (e: Exception) {
            isCapturing = false
            Log.e(TAG, "Direct AudioRecord startup failed", e)
            listener.onCaptureError("Microphone capture failed: ${e.localizedMessage ?: "Hardware error"}")
        }
    }

    private fun finishDirectAudioRecordAndDeliver() {
        if (!isCapturing) return
        isCapturing = false
        timerJob?.cancel()
        audioRecordJob?.cancel()
        audioRecordJob = null
        stopAudioRecordOnly()
        listener.onAudioLevelChanged(0f)

        val pcmBytes = synchronized(pcmStream) {
            val bytes = pcmStream.toByteArray()
            pcmStream.reset()
            bytes
        }
        if (pcmBytes.size < 3_200) {
            listener.onCaptureError("Recording too short. Hold or speak clearly into the microphone.")
            return
        }
        val wavBytes = AudioWavEncoder.encodePcm16ToWav(pcmBytes, AudioWavEncoder.DEFAULT_SAMPLE_RATE)
        listener.onWavAudioReady(wavBytes)
    }

    private fun startElapsedTimer() {
        timerJob?.cancel()
        timerJob = ioScope.launch {
            var secs = 0
            while (isActive && isCapturing) {
                mainHandler.post {
                    if (isCapturing) listener.onElapsedSecondsChanged(secs)
                }
                delay(1_000L)
                secs++
            }
        }
    }

    private fun stopSpeechRecognizerOnly() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            } finally {
                speechRecognizer = null
            }
        }
    }

    private fun stopAudioRecordOnly() {
        try {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
                rec.release()
            }
        } catch (_: Exception) {
        } finally {
            audioRecord = null
        }
    }

    private fun stopAllInternal() {
        timerJob?.cancel()
        timerJob = null
        audioRecordJob?.cancel()
        audioRecordJob = null
        stopSpeechRecognizerOnly()
        stopAudioRecordOnly()
    }

    fun release() {
        isCapturing = false
        stopAllInternal()
        ioScope.cancel()
    }
}
