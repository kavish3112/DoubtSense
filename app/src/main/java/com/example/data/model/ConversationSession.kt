package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a persisted listening/conversation session stored in the local Room database,
 * enabling users to revisit past transcripts, doubt intensity metrics, and doubt analysis logs.
 */
@Entity(tableName = "conversation_sessions")
data class ConversationSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val title: String = "Conversation Session",
    val category: String = "General",
    val transcriptJson: String = "[]",
    val transcriptPreview: String = "",
    val averageDoubtIntensity: Float = 0f,
    val peakDoubtIntensity: Float = 0f,
    val doubtCount: Int = 0,
    val summary: String = "",
    val customTag: String = "General",
    val isPinned: Boolean = false
) {
    /**
     * Parses the stored JSON-serialized conversation turns in pure Kotlin (safe for JVM unit tests).
     */
    fun getTranscriptTurns(): List<String> {
        val trimmed = transcriptJson.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()
        val inner = trimmed.removePrefix("[").removeSuffix("]").trim()
        if (inner.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var escaped = false

        for (ch in inner) {
            when {
                escaped -> {
                    when (ch) {
                        'n' -> current.append('\n')
                        't' -> current.append('\t')
                        '"' -> current.append('"')
                        '\\' -> current.append('\\')
                        else -> current.append(ch)
                    }
                    escaped = false
                }
                ch == '\\' -> escaped = true
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    val item = current.toString().trim()
                    if (item.isNotEmpty()) result.add(item)
                    current.clear()
                }
                else -> if (inQuotes) current.append(ch)
            }
        }
        val last = current.toString().trim()
        if (last.isNotEmpty()) result.add(last)
        return result
    }

    fun getTagList(): List<String> = parseTagList(customTag)

    fun hasTag(tag: String): Boolean {
        val needle = tag.trim()
        if (needle.isEmpty()) return false
        return getTagList().any { it.equals(needle, ignoreCase = true) }
    }

    companion object {
        fun parseTagList(raw: String): List<String> {
            return raw.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
        }

        fun encodeTags(tags: List<String>): String {
            val cleaned = tags
                .map { it.replace(",", " ").replace(Regex("\\s+"), " ").trim().take(28) }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
            return if (cleaned.isEmpty()) "General" else cleaned.joinToString(", ")
        }

        fun encodeTranscriptTurns(turns: List<String>): String {
            return "[" + turns.joinToString(",") { turn ->
                val escaped = turn
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t")
                "\"$escaped\""
            } + "]"
        }
    }
}
