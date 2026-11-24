package com.example.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    HIGH_CONTRAST("High Contrast")
}

enum class ListeningCaptureMode(val label: String) {
    PUSH_TO_TALK("Push-to-Talk"),
    CONTINUOUS_LISTENING("Continuous Listening")
}

data class LanguageOption(
    val displayName: String,
    val aiLanguageName: String,
    val sttLocaleTag: String,
    val shortCode: String
)

/**
 * Centralized persisted preferences manager for DoubtSense themes, speech/AI languages,
 * listening mode, TTS speech rate, Battery Saver, Offline Mode, 30-day cleanup, and custom tags.
 */
object AppPreferencesManager {
    private const val PREFS_NAME = "doubtsense_prefs"

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_AI_LANGUAGE = "ai_response_language"
    private const val KEY_STT_LANGUAGE_TAG = "stt_language_tag"
    private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
    private const val KEY_TTS_SPEECH_PITCH = "tts_speech_pitch"
    private const val KEY_LISTENING_MODE = "listening_capture_mode"
    private const val KEY_BATTERY_SAVER = "battery_saver_mode"
    private const val KEY_OFFLINE_MODE = "offline_mode"
    private const val KEY_AUTO_CLEANUP_30_DAYS = "auto_cleanup_30_days"
    private const val KEY_CUSTOM_TAGS = "custom_tags_json"
    private const val KEY_LAST_CLOUD_SYNC = "last_cloud_sync_timestamp"
    private const val KEY_AUTO_CLOUD_SYNC = "auto_cloud_sync_enabled"

    val SUPPORTED_LANGUAGES = listOf(
        LanguageOption("English (US)", "English", "en-US", "EN"),
        LanguageOption("English (UK)", "English", "en-GB", "EN-GB"),
        LanguageOption("Spanish", "Spanish", "es-ES", "ES"),
        LanguageOption("French", "French", "fr-FR", "FR"),
        LanguageOption("German", "German", "de-DE", "DE"),
        LanguageOption("Hindi", "Hindi", "hi-IN", "HI"),
        LanguageOption("Japanese", "Japanese", "ja-JP", "JA"),
        LanguageOption("Chinese (Simplified)", "Chinese", "zh-CN", "ZH")
    )

    val DEFAULT_TAGS = listOf("Work", "Personal", "Study", "Education")

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _aiResponseLanguage = MutableStateFlow("English")
    val aiResponseLanguage: StateFlow<String> = _aiResponseLanguage.asStateFlow()

    private val _sttLanguageTag = MutableStateFlow("en-US")
    val sttLanguageTag: StateFlow<String> = _sttLanguageTag.asStateFlow()

    private val _ttsSpeechRate = MutableStateFlow(1.0f)
    val ttsSpeechRate: StateFlow<Float> = _ttsSpeechRate.asStateFlow()

    private val _ttsSpeechPitch = MutableStateFlow(1.0f)
    val ttsSpeechPitch: StateFlow<Float> = _ttsSpeechPitch.asStateFlow()

    private val _listeningCaptureMode = MutableStateFlow(ListeningCaptureMode.PUSH_TO_TALK)
    val listeningCaptureMode: StateFlow<ListeningCaptureMode> = _listeningCaptureMode.asStateFlow()

    private val _batterySaverMode = MutableStateFlow(false)
    val batterySaverMode: StateFlow<Boolean> = _batterySaverMode.asStateFlow()

    private val _offlineMode = MutableStateFlow(false)
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()

    private val _autoCleanup30Days = MutableStateFlow(false)
    val autoCleanup30Days: StateFlow<Boolean> = _autoCleanup30Days.asStateFlow()

    private val _customTags = MutableStateFlow(DEFAULT_TAGS)
    val customTags: StateFlow<List<String>> = _customTags.asStateFlow()

    private val _lastCloudSyncTimestamp = MutableStateFlow(0L)
    val lastCloudSyncTimestamp: StateFlow<Long> = _lastCloudSyncTimestamp.asStateFlow()

    private val _autoCloudSyncEnabled = MutableStateFlow(true)
    val autoCloudSyncEnabled: StateFlow<Boolean> = _autoCloudSyncEnabled.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        _themeMode.value = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.DARK)

        _aiResponseLanguage.value = prefs.getString(KEY_AI_LANGUAGE, "English")?.ifBlank { "English" } ?: "English"
        _sttLanguageTag.value = prefs.getString(KEY_STT_LANGUAGE_TAG, "en-US")?.ifBlank { "en-US" } ?: "en-US"
        _ttsSpeechRate.value = prefs.getFloat(KEY_TTS_SPEECH_RATE, 1.0f).coerceIn(0.5f, 2.0f)
        _ttsSpeechPitch.value = prefs.getFloat(KEY_TTS_SPEECH_PITCH, 1.0f).coerceIn(0.5f, 2.0f)

        val captureStr = prefs.getString(KEY_LISTENING_MODE, ListeningCaptureMode.PUSH_TO_TALK.name)
            ?: ListeningCaptureMode.PUSH_TO_TALK.name
        _listeningCaptureMode.value = runCatching { ListeningCaptureMode.valueOf(captureStr) }
            .getOrDefault(ListeningCaptureMode.PUSH_TO_TALK)

        _batterySaverMode.value = prefs.getBoolean(KEY_BATTERY_SAVER, false)
        _offlineMode.value = prefs.getBoolean(KEY_OFFLINE_MODE, false)
        _autoCleanup30Days.value = prefs.getBoolean(KEY_AUTO_CLEANUP_30_DAYS, false)
        _lastCloudSyncTimestamp.value = prefs.getLong(KEY_LAST_CLOUD_SYNC, 0L)
        _autoCloudSyncEnabled.value = prefs.getBoolean(KEY_AUTO_CLOUD_SYNC, true)

        val tagsJson = prefs.getString(KEY_CUSTOM_TAGS, null)
        if (!tagsJson.isNullOrBlank()) {
            val parsed = parseTagsJson(tagsJson)
            if (parsed.isNotEmpty()) {
                _customTags.value = parsed
            }
        }
        TtsManager.setSpeechRate(_ttsSpeechRate.value)
        TtsManager.setSpeechPitch(_ttsSpeechPitch.value)
        TtsManager.setLanguageTag(_sttLanguageTag.value)
        initialized = true
    }

    fun setThemeMode(context: Context?, mode: ThemeMode) {
        _themeMode.value = mode
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    fun setAiResponseLanguage(context: Context?, language: String) {
        val clean = language.trim().ifBlank { "English" }
        _aiResponseLanguage.value = clean
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_AI_LANGUAGE, clean)?.apply()
    }

    fun cycleAiResponseLanguage(context: Context?): String {
        val options = SUPPORTED_LANGUAGES.map { it.aiLanguageName }.distinct()
        val currentIdx = options.indexOfFirst { it.equals(_aiResponseLanguage.value, ignoreCase = true) }
        val next = options[(currentIdx + 1) % options.size]
        setAiResponseLanguage(context, next)
        return next
    }

    fun setSttLanguageTag(context: Context?, tag: String) {
        val clean = tag.trim().ifBlank { "en-US" }
        _sttLanguageTag.value = clean
        TtsManager.setLanguageTag(clean)
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_STT_LANGUAGE_TAG, clean)?.apply()
    }

    fun setTtsSpeechRate(context: Context?, rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        _ttsSpeechRate.value = clamped
        TtsManager.setSpeechRate(clamped)
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putFloat(KEY_TTS_SPEECH_RATE, clamped)?.apply()
    }

    fun setTtsSpeechPitch(context: Context?, pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _ttsSpeechPitch.value = clamped
        TtsManager.setSpeechPitch(clamped)
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putFloat(KEY_TTS_SPEECH_PITCH, clamped)?.apply()
    }

    fun setListeningCaptureMode(context: Context?, mode: ListeningCaptureMode) {
        _listeningCaptureMode.value = mode
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_LISTENING_MODE, mode.name)?.apply()
    }

    fun setBatterySaverMode(context: Context?, enabled: Boolean) {
        _batterySaverMode.value = enabled
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_BATTERY_SAVER, enabled)?.apply()
    }

    fun setOfflineMode(context: Context?, enabled: Boolean) {
        _offlineMode.value = enabled
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_OFFLINE_MODE, enabled)?.apply()
    }

    fun setAutoCleanup30Days(context: Context?, enabled: Boolean) {
        _autoCleanup30Days.value = enabled
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_AUTO_CLEANUP_30_DAYS, enabled)?.apply()
    }

    fun isDefaultTag(tag: String): Boolean {
        return DEFAULT_TAGS.any { it.equals(tag.trim(), ignoreCase = true) }
    }

    fun addCustomTag(context: Context?, tag: String): String? {
        val clean = tag.replace(",", " ").replace(Regex("\\s+"), " ").trim().take(28)
        if (clean.isBlank()) return null
        val existing = _customTags.value.firstOrNull { it.equals(clean, ignoreCase = true) }
        if (existing != null) return existing
        val updated = (_customTags.value + clean).distinctBy { it.lowercase() }
        _customTags.value = updated
        persistCustomTags(context, updated)
        return clean
    }

    fun removeCustomTag(context: Context?, tag: String): Boolean {
        val clean = tag.trim()
        if (clean.isBlank() || isDefaultTag(clean)) return false
        val updated = _customTags.value.filterNot { it.equals(clean, ignoreCase = true) }
        if (updated.size == _customTags.value.size) return false
        _customTags.value = updated
        persistCustomTags(context, updated)
        return true
    }

    private fun persistCustomTags(context: Context?, tags: List<String>) {
        val arr = JSONArray()
        tags.forEach { arr.put(it) }
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_CUSTOM_TAGS, arr.toString())?.apply()
    }

    fun recordCloudSync(context: Context?, timestampMillis: Long = System.currentTimeMillis()) {
        _lastCloudSyncTimestamp.value = timestampMillis
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putLong(KEY_LAST_CLOUD_SYNC, timestampMillis)?.apply()
    }

    fun setAutoCloudSyncEnabled(context: Context?, enabled: Boolean) {
        _autoCloudSyncEnabled.value = enabled
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_AUTO_CLOUD_SYNC, enabled)?.apply()
    }

    private fun parseTagsJson(raw: String): List<String> {
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        } catch (_: Exception) {
            DEFAULT_TAGS
        }
    }
}
