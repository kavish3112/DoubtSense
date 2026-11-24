package com.example.util

enum class VoiceCommand(val description: String) {
    EXPORT_HISTORY("Export history"),
    CLEAR_LIST("Clear saved history list"),
    SWITCH_TO_DARK_MODE("Switch to dark mode"),
    SWITCH_TO_LIGHT_MODE("Switch to light mode"),
    SWITCH_TO_HIGH_CONTRAST("Switch to high contrast mode")
}

/**
 * Lightweight voice command recognizer allowing spoken shortcuts such as
 * "Export history", "Clear list", or "Switch to dark mode".
 */
object VoiceCommandProcessor {

    fun parseCommand(transcript: String): VoiceCommand? {
        val normalized = transcript.trim().lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return null

        return when {
            normalized.contains("export history") ||
                normalized.contains("export doubts") ||
                normalized.contains("export my history") ||
                normalized.contains("download history") -> VoiceCommand.EXPORT_HISTORY

            normalized.contains("clear list") ||
                normalized.contains("clear history") ||
                normalized.contains("clear saved list") ||
                normalized.contains("delete history") -> VoiceCommand.CLEAR_LIST

            normalized.contains("switch to dark mode") ||
                normalized.contains("enable dark mode") ||
                normalized == "dark mode" -> VoiceCommand.SWITCH_TO_DARK_MODE

            normalized.contains("switch to light mode") ||
                normalized.contains("enable light mode") ||
                normalized == "light mode" -> VoiceCommand.SWITCH_TO_LIGHT_MODE

            normalized.contains("high contrast") -> VoiceCommand.SWITCH_TO_HIGH_CONTRAST

            else -> null
        }
    }
}
