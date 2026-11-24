package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single external verification source attached when Fact-Checking Mode is enabled.
 */
data class VerificationSource(
    val title: String,
    val url: String,
    val excerpt: String = ""
)

/**
 * Represents a detected doubt with deep contextual understanding,
 * classification, adaptive clarification exchanges, and tailored answers.
 */
@Entity(tableName = "doubts")
data class DoubtItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val doubtSnippet: String,
    val topic: String,
    val quickAnswer: String,
    val detailedAnswer: String,
    val notificationSummary: String,
    val conversationalContext: String = "",
    val isAmbiguous: Boolean = false,
    val clarificationQuestion: String? = null,
    val confidenceScore: Float = 0.9f,
    val isStarred: Boolean = false,

    // Enhanced Context Understanding & Intent
    val subject: String = "",
    val importantConcepts: String = "",
    val doubtType: String = "",
    val intent: String = "",

    // Interactive Clarification & Adaptive Options
    val suggestedOptionsJson: String = "[]",
    val referenceMaterials: String = "",
    val conversationSuggestions: String = "",
    val userClarificationResponse: String? = null,
    val clarificationHistory: String = "",
    val preferredStyle: String = "Detailed",
    val clarificationMode: String = "PRIVATE", // "PRIVATE" or "CONVERSATION"
    val isResolvedWithClarification: Boolean = false,

    // Fact-Checking Mode & External Source Verification
    val isFactChecked: Boolean = false,
    val verificationStatus: String = "",
    val verificationSourcesJson: String = "[]",

    // Custom Category Tags & One-Sentence Summary
    val customTag: String = "Education",
    val oneSentenceSummary: String = ""
) {
    /**
     * Parses external verification sources in pure Kotlin (safe for JVM unit tests).
     * Encoded format: JSON array of objects [{"title":"...","url":"...","excerpt":"..."}]
     */
    fun getVerificationSources(): List<VerificationSource> {
        val trimmed = verificationSourcesJson.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()

        val sources = mutableListOf<VerificationSource>()
        var idx = 0
        while (idx < trimmed.length) {
            val objStart = trimmed.indexOf('{', idx)
            if (objStart == -1) break
            val objEnd = findMatchingBrace(trimmed, objStart)
            if (objEnd == -1) break

            val objContent = trimmed.substring(objStart + 1, objEnd)
            val title = extractJsonField(objContent, "title")
            val url = extractJsonField(objContent, "url")
            val excerpt = extractJsonField(objContent, "excerpt")
            if (title.isNotBlank() && url.isNotBlank()) {
                sources.add(VerificationSource(title = title, url = url, excerpt = excerpt))
            }
            idx = objEnd + 1
        }
        return sources
    }

    companion object {
        fun encodeVerificationSources(sources: List<VerificationSource>): String {
            if (sources.isEmpty()) return "[]"
            return "[" + sources.joinToString(",") { src ->
                "{\"title\":\"${escapeJson(src.title)}\",\"url\":\"${escapeJson(src.url)}\",\"excerpt\":\"${escapeJson(src.excerpt)}\"}"
            } + "]"
        }

        private fun escapeJson(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t")
        }

        private fun findMatchingBrace(text: String, openIndex: Int): Int {
            var inQuotes = false
            var escaped = false
            for (i in openIndex until text.length) {
                val ch = text[i]
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inQuotes = !inQuotes
                    ch == '}' && !inQuotes -> return i
                }
            }
            return -1
        }

        private fun extractJsonField(objBody: String, fieldName: String): String {
            val keyPattern = "\"$fieldName\""
            var searchFrom = 0
            while (searchFrom < objBody.length) {
                val keyIdx = objBody.indexOf(keyPattern, searchFrom)
                if (keyIdx == -1) return ""
                val colonIdx = objBody.indexOf(':', keyIdx + keyPattern.length)
                if (colonIdx == -1) return ""
                var valStart = colonIdx + 1
                while (valStart < objBody.length && objBody[valStart].isWhitespace()) {
                    valStart++
                }
                if (valStart >= objBody.length || objBody[valStart] != '"') {
                    searchFrom = keyIdx + keyPattern.length
                    continue
                }
                val sb = StringBuilder()
                var escaped = false
                for (i in (valStart + 1) until objBody.length) {
                    val ch = objBody[i]
                    when {
                        escaped -> {
                            when (ch) {
                                'n' -> sb.append('\n')
                                't' -> sb.append('\t')
                                '"' -> sb.append('"')
                                '\\' -> sb.append('\\')
                                else -> sb.append(ch)
                            }
                            escaped = false
                        }
                        ch == '\\' -> escaped = true
                        ch == '"' -> return sb.toString()
                        else -> sb.append(ch)
                    }
                }
                return sb.toString()
            }
            return ""
        }
    }
}
