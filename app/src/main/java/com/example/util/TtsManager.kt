package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "TtsManager"

object TtsManager : TextToSpeech.OnInitListener {

    internal var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null
    private var pendingDoubtId: Long? = null
    private var pendingSliceStart: Int = 0

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _speechPitch = MutableStateFlow(1.0f)
    val speechPitch: StateFlow<Float> = _speechPitch.asStateFlow()

    private var languageTag: String = Locale.getDefault().toLanguageTag()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _currentlySpeakingDoubtId = MutableStateFlow<Long?>(null)
    val currentlySpeakingDoubtId: StateFlow<Long?> = _currentlySpeakingDoubtId.asStateFlow()

    private val _spokenText = MutableStateFlow("")
    val spokenText: StateFlow<String> = _spokenText.asStateFlow()

    private val _spokenRange = MutableStateFlow<IntRange?>(null)
    val spokenRange: StateFlow<IntRange?> = _spokenRange.asStateFlow()

    private var activeOffset: Int = 0
    private var lastSpokenCharIndex: Int = 0
    private var pausedOffset: Int = 0
    internal var currentUtteranceId: String? = null
    internal var utteranceProgressListener: UtteranceProgressListener? = null
    private var utteranceCounter: Long = 0L

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        _speechRate.value = clamped
        try {
            tts?.setSpeechRate(clamped)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update speech rate: ${e.message}")
        }
        restartActiveSpeechWithUpdatedVoiceParams()
    }

    fun getSpeechRate(): Float = _speechRate.value

    fun setSpeechPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _speechPitch.value = clamped
        try {
            tts?.setPitch(clamped)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update speech pitch: ${e.message}")
        }
        restartActiveSpeechWithUpdatedVoiceParams()
    }

    fun getSpeechPitch(): Float = _speechPitch.value

    fun setLanguageTag(tag: String) {
        languageTag = tag.trim().ifBlank { Locale.getDefault().toLanguageTag() }
        if (isInitialized && tts != null) {
            applyLanguage(languageTag)
        }
    }

    private fun restartActiveSpeechWithUpdatedVoiceParams() {
        val full = _spokenText.value
        val activeId = _currentlySpeakingDoubtId.value
        if (_isSpeaking.value && !_isPaused.value && isInitialized && tts != null && full.isNotBlank()) {
            val resumeAt = skipLeadingSpaces(full, snapToWordStart(full, lastSpokenCharIndex))
                .let { if (it >= full.length) 0 else it }
            speakSliceInternal(full, resumeAt, activeId)
        }
    }

    private fun applyLanguage(tag: String) {
        try {
            val locale = Locale.forLanguageTag(tag)
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
        } catch (_: Exception) {
            tts?.setLanguage(Locale.US)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyLanguage(languageTag)
            tts?.setSpeechRate(_speechRate.value)
            tts?.setPitch(_speechPitch.value)
            isInitialized = true

            val progressListener = object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId != currentUtteranceId) return
                    _isPaused.value = false
                    _isSpeaking.value = true
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    if (utteranceId != currentUtteranceId) return
                    if (start >= 0 && end > start) {
                        val globalStart = activeOffset + start
                        val globalEnd = activeOffset + end
                        lastSpokenCharIndex = globalStart
                        _spokenRange.value = globalStart until globalEnd
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId != currentUtteranceId) return
                    clearPlaybackState()
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId != currentUtteranceId) return
                    clearPlaybackState()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId != currentUtteranceId) return
                    clearPlaybackState()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId != currentUtteranceId) return
                    clearPlaybackState()
                }
            }
            utteranceProgressListener = progressListener
            tts?.setOnUtteranceProgressListener(progressListener)
            Log.d(TAG, "TextToSpeech initialized successfully")

            val queued = pendingText
            val queuedId = pendingDoubtId
            val queuedSliceStart = pendingSliceStart
            pendingText = null
            pendingDoubtId = null
            pendingSliceStart = 0
            if (!queued.isNullOrBlank()) {
                speakSliceInternal(queued, queuedSliceStart, queuedId)
            }
        } else {
            pendingText = null
            pendingDoubtId = null
            pendingSliceStart = 0
            clearPlaybackState()
            Log.e(TAG, "Failed to initialize TextToSpeech, status: $status")
        }
    }

    fun speakAnswer(context: Context, text: String, doubtId: Long? = null) {
        val cleanText = sanitizeForSpeech(text)
        if (cleanText.isBlank()) return

        // If currently active (speaking or paused) for this doubt, tapping Listen/Stop stops it
        if ((_isSpeaking.value || _isPaused.value) && _currentlySpeakingDoubtId.value == doubtId) {
            stop()
            return
        }

        init(context)
        _isPaused.value = false
        pausedOffset = 0
        activeOffset = 0
        lastSpokenCharIndex = 0

        if (!isInitialized || tts == null) {
            pendingText = cleanText
            pendingDoubtId = doubtId
            pendingSliceStart = 0
            _currentlySpeakingDoubtId.value = doubtId
            _spokenText.value = cleanText
            _spokenRange.value = null
            _isSpeaking.value = true
            Log.d(TAG, "TTS warming up; queued utterance for doubtId=$doubtId")
            return
        }

        speakSliceInternal(cleanText, 0, doubtId)
    }

    fun pause() {
        if (!_isSpeaking.value || _isPaused.value) return
        val full = _spokenText.value
        pausedOffset = snapToWordStart(full, lastSpokenCharIndex)
        pendingText = null
        pendingDoubtId = null
        pendingSliceStart = 0
        // Clear utterance id before tts.stop() so UtteranceProgressListener.onStop does not wipe paused state
        currentUtteranceId = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing TTS", e)
        }
        _isSpeaking.value = false
        _isPaused.value = true
    }

    fun resume(context: Context) {
        if (!_isPaused.value) return
        val full = _spokenText.value
        if (full.isBlank()) {
            stop()
            return
        }
        val doubtId = _currentlySpeakingDoubtId.value
        val startIdx = skipLeadingSpaces(full, pausedOffset).let {
            if (it >= full.length) 0 else it
        }
        _isPaused.value = false
        init(context)

        if (!isInitialized || tts == null) {
            pendingText = full
            pendingDoubtId = doubtId
            pendingSliceStart = startIdx
            activeOffset = startIdx
            lastSpokenCharIndex = startIdx
            _isSpeaking.value = true
            return
        }

        speakSliceInternal(full, startIdx, doubtId)
    }

    fun togglePauseResume(context: Context) {
        when {
            _isPaused.value -> resume(context)
            _isSpeaking.value -> pause()
        }
    }

    internal fun sanitizeForSpeech(rawText: String): String {
        val maxLen = try {
            TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1000)
        } catch (_: Throwable) {
            3900
        }
        return rawText
            .replace(Regex("[*#`]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLen)
    }

    internal fun snapToWordStart(text: String, index: Int): Int {
        if (text.isEmpty() || index <= 0) return 0
        var pos = index.coerceIn(0, text.length - 1)
        while (pos > 0 && !text[pos - 1].isWhitespace()) {
            pos--
        }
        return pos
    }

    internal fun skipLeadingSpaces(text: String, index: Int): Int {
        if (text.isEmpty()) return 0
        var pos = index.coerceIn(0, text.length)
        while (pos < text.length && text[pos].isWhitespace()) {
            pos++
        }
        return pos
    }

    private fun speakSliceInternal(fullText: String, sliceStart: Int, doubtId: Long?) {
        val safeStart = skipLeadingSpaces(fullText, sliceStart).let {
            if (it >= fullText.length) 0 else it
        }
        val slice = fullText.substring(safeStart)
        if (slice.isBlank()) {
            clearPlaybackState()
            return
        }

        currentUtteranceId = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }

        activeOffset = safeStart
        lastSpokenCharIndex = safeStart
        pausedOffset = safeStart
        _currentlySpeakingDoubtId.value = doubtId
        _spokenText.value = fullText
        if (safeStart == 0) {
            _spokenRange.value = null
        }
        _isPaused.value = false
        _isSpeaking.value = true

        tts?.setSpeechRate(_speechRate.value)
        tts?.setPitch(_speechPitch.value)

        val utteranceId = "doubtsense_answer_${doubtId ?: 0L}_${++utteranceCounter}"
        currentUtteranceId = utteranceId
        val status = tts?.speak(slice, TextToSpeech.QUEUE_FLUSH, null, utteranceId) ?: TextToSpeech.ERROR
        if (status == TextToSpeech.ERROR) {
            Log.w(TAG, "TextToSpeech.speak returned ERROR for doubtId=$doubtId")
            clearPlaybackState()
        }
    }

    private fun clearPlaybackState() {
        currentUtteranceId = null
        _isSpeaking.value = false
        _isPaused.value = false
        _currentlySpeakingDoubtId.value = null
        _spokenRange.value = null
        _spokenText.value = ""
        activeOffset = 0
        lastSpokenCharIndex = 0
        pausedOffset = 0
    }

    fun stop() {
        pendingText = null
        pendingDoubtId = null
        pendingSliceStart = 0
        currentUtteranceId = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
        clearPlaybackState()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
