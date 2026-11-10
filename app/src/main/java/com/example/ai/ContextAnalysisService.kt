package com.example.ai

import android.util.Log
import com.example.data.model.DoubtItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ContextAnalysisService"

/**
 * Data structure representing the semantic context analysis of a transcript segment.
 */
data class ContextAnalysisResult(
    val isDoubt: Boolean,
    val subject: String,
    val topic: String,
    val intent: String,
    val importantConcepts: List<String>,
    val doubtType: String,
    val doubtSnippet: String,
    val quickAnswer: String,
    val detailedAnswer: String,
    val notificationSummary: String,
    val isAmbiguous: Boolean,
    val clarificationQuestion: String?,
    val suggestedOptions: List<String>,
    val referenceMaterials: String,
    val conversationSuggestions: String,
    val confidence: Float
) {
    fun toDoubtItem(conversationalContext: String): DoubtItem {
        return DoubtItem(
            doubtSnippet = doubtSnippet,
            subject = subject,
            topic = topic,
            importantConcepts = importantConcepts.joinToString(", "),
            doubtType = doubtType,
            intent = intent,
            quickAnswer = quickAnswer,
            detailedAnswer = detailedAnswer,
            notificationSummary = notificationSummary,
            conversationalContext = conversationalContext,
            isAmbiguous = isAmbiguous,
            clarificationQuestion = clarificationQuestion,
            suggestedOptionsJson = "[" + suggestedOptions.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" } + "]",
            referenceMaterials = referenceMaterials,
            conversationSuggestions = conversationSuggestions,
            confidenceScore = confidence
        )
    }
}

/**
 * Dedicated ContextAnalysisService powered by the Gemini API (via Firebase AI Logic)
 * to analyze transcript segments for subjects, topics, and underlying intent.
 */
class ContextAnalysisService {

    private val contextSchema = Schema.obj(
        properties = mapOf(
            "isDoubt" to Schema.boolean(
                description = "True if the transcript segment contains an expression of confusion, question, uncertainty, or request for clarification."
            ),
            "subject" to Schema.string(
                description = "The overarching discipline or domain (e.g. 'Machine Learning', 'Cellular Biology', 'Algorithms', 'Physics')."
            ),
            "topic" to Schema.string(
                description = "The specific topic being discussed deduced from transcript flow (e.g. 'Random Forest', 'ATP Synthase', 'Quicksort')."
            ),
            "intent" to Schema.string(
                description = "The speaker's specific intention or what they are confused about (e.g. 'Wants to understand how individual tree outputs are combined')."
            ),
            "importantConcepts" to Schema.array(
                items = Schema.string(),
                description = "Key concepts mentioned in the surrounding transcript."
            ),
            "doubtType" to Schema.string(
                description = "Type of doubt: 'Working principle', 'Mathematical derivation', 'Process flow', 'Implementation syntax', or 'Conceptual difference'."
            ),
            "doubtSnippet" to Schema.string(
                description = "The exact or condensed doubt statement from the transcript."
            ),
            "quickAnswer" to Schema.string(
                description = "Concise 2-3 sentence answer directly addressing the intent."
            ),
            "detailedAnswer" to Schema.string(
                description = "Full explanation with analogies, step-by-step mechanism, and practical example."
            ),
            "notificationSummary" to Schema.string(
                description = "Short notification text (under 15 words) previewing the answer."
            ),
            "isAmbiguous" to Schema.boolean(
                description = "True if context is insufficient or requires user clarification."
            ),
            "clarificationQuestion" to Schema.string(
                description = "Tailored clarification question if ambiguous or incomplete."
            ),
            "suggestedOptions" to Schema.array(
                items = Schema.string(),
                description = "Selectable multiple choice options for user clarification."
            ),
            "referenceMaterials" to Schema.string(
                description = "Key references, docs, or textbook chapters."
            ),
            "conversationSuggestions" to Schema.string(
                description = "Suggested questions the user can ask aloud in ongoing discussion."
            ),
            "confidence" to Schema.double(
                description = "Confidence in the context analysis from 0.0 to 1.0."
            )
        )
    )

    private val genConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = contextSchema
        temperature = 0.2f
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = genConfig
        )
    }

    /**
     * Analyzes a transcript segment in the context of preceding dialogue turns.
     */
    suspend fun analyzeTranscriptSegment(
        transcriptSegment: String,
        surroundingContext: List<String>
    ): ContextAnalysisResult = withContext(Dispatchers.IO) {
        val trimmedSegment = transcriptSegment.trim()
        if (trimmedSegment.length < 3) {
            return@withContext createNonDoubtResult(trimmedSegment)
        }

        val contextBuilder = StringBuilder()
        if (surroundingContext.isNotEmpty()) {
            contextBuilder.append("--- PRECEDING TRANSCRIPT FLOW ---\n")
            surroundingContext.takeLast(10).forEachIndexed { index, turn ->
                contextBuilder.append("Turn [${index + 1}]: $turn\n")
            }
            contextBuilder.append("---------------------------------\n")
        }

        val prompt = """
            You are DoubtSense's ContextAnalysisService.
            
            $contextBuilder
            CURRENT TRANSCRIPT SEGMENT: "$trimmedSegment"
            
            YOUR ANALYSIS GOALS:
            1. Analyze the transcript segment within the surrounding dialogue flow.
            2. Identify:
               - Subject: Broad academic or professional field.
               - Topic: Specific subject of conversation.
               - Intent: What the speaker actually wants to know or is confused about.
               - Important Concepts: Key terminology mentioned.
               - Doubt Type: Working principle, math derivation, syntax, process, etc.
            3. If this is a question, confusion, or doubt, set isDoubt=true. If it is casual chit-chat or agreement, set isDoubt=false.
            4. Provide both quickAnswer and detailedAnswer directly targeting the identified intent.
            5. If context is ambiguous, generate a targeted clarificationQuestion and suggestedOptions.
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }

            val jsonText = response.text
            if (jsonText.isNullOrBlank()) {
                return@withContext fallbackContextAnalysis(trimmedSegment, surroundingContext)
            }

            val json = JSONObject(jsonText)
            val isDoubt = json.optBoolean("isDoubt", false)
            if (!isDoubt) {
                return@withContext createNonDoubtResult(trimmedSegment)
            }

            val subject = json.optString("subject", "General Academic").ifBlank { "General Academic" }
            val topic = json.optString("topic", "General Concept").ifBlank { "General Concept" }
            val intent = json.optString("intent", "Understand $topic").ifBlank { "Understand $topic" }

            val conceptsList = mutableListOf<String>()
            val conceptsArray = json.optJSONArray("importantConcepts")
            if (conceptsArray != null) {
                for (i in 0 until conceptsArray.length()) {
                    conceptsList.add(conceptsArray.getString(i))
                }
            }
            if (conceptsList.isEmpty()) {
                conceptsList.add(topic)
            }

            val doubtType = json.optString("doubtType", "Working principle").ifBlank { "Working principle" }
            val doubtSnippet = json.optString("doubtSnippet", trimmedSegment).ifBlank { trimmedSegment }
            val quickAnswer = json.optString("quickAnswer", "").ifBlank {
                "Quick analysis for $topic: $intent."
            }
            val detailedAnswer = json.optString("detailedAnswer", quickAnswer).ifBlank { quickAnswer }
            val notificationSummary = json.optString("notificationSummary", "$topic: $quickAnswer").ifBlank {
                "$topic: $quickAnswer"
            }
            val isAmbiguous = json.optBoolean("isAmbiguous", false)
            val clarificationQuestion = json.optString("clarificationQuestion", "").ifBlank { null }

            val optionsList = mutableListOf<String>()
            val optionsArray = json.optJSONArray("suggestedOptions")
            if (optionsArray != null) {
                for (i in 0 until optionsArray.length()) {
                    optionsList.add(optionsArray.getString(i))
                }
            }
            if (optionsList.isEmpty()) {
                optionsList.addAll(listOf("Core Principle", "Practical Example", "Step-by-step Walkthrough"))
            }

            val referenceMaterials = json.optString("referenceMaterials", "Recommended study references on $topic.")
            val conversationSuggestions = json.optString("conversationSuggestions", "Could you elaborate on how $topic behaves here?")
            val confidence = json.optDouble("confidence", 0.9).toFloat()

            ContextAnalysisResult(
                isDoubt = true,
                subject = subject,
                topic = topic,
                intent = intent,
                importantConcepts = conceptsList,
                doubtType = doubtType,
                doubtSnippet = doubtSnippet,
                quickAnswer = quickAnswer,
                detailedAnswer = detailedAnswer,
                notificationSummary = notificationSummary,
                isAmbiguous = isAmbiguous,
                clarificationQuestion = clarificationQuestion,
                suggestedOptions = optionsList,
                referenceMaterials = referenceMaterials,
                conversationSuggestions = conversationSuggestions,
                confidence = confidence
            )

        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Context analysis timed out", e)
            fallbackContextAnalysis(trimmedSegment, surroundingContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Context analysis failed", e)
            fallbackContextAnalysis(trimmedSegment, surroundingContext)
        }
    }

    private fun createNonDoubtResult(snippet: String): ContextAnalysisResult {
        return ContextAnalysisResult(
            isDoubt = false,
            subject = "",
            topic = "",
            intent = "",
            importantConcepts = emptyList(),
            doubtType = "",
            doubtSnippet = snippet,
            quickAnswer = "",
            detailedAnswer = "",
            notificationSummary = "",
            isAmbiguous = false,
            clarificationQuestion = null,
            suggestedOptions = emptyList(),
            referenceMaterials = "",
            conversationSuggestions = "",
            confidence = 1.0f
        )
    }

    private fun fallbackContextAnalysis(
        segment: String,
        surroundingContext: List<String>
    ): ContextAnalysisResult {
        val lower = segment.lowercase()
        val questionSignals = listOf("why", "how", "what", "confused", "don't understand", "explain", "work together", "?")
        val isQuestion = questionSignals.any { lower.contains(it) }

        if (!isQuestion) {
            return createNonDoubtResult(segment)
        }

        val subject = if (lower.contains("tree") || lower.contains("forest") || lower.contains("model")) "Machine Learning"
        else if (lower.contains("photo") || lower.contains("cell")) "Biology"
        else if (lower.contains("sort") || lower.contains("algo")) "Computer Science"
        else "General Science"

        val topic = if (lower.contains("forest")) "Random Forest"
        else if (lower.contains("photo")) "Photosynthesis"
        else "Concept Clarification"

        val concepts = listOf(topic, "Core Mechanism")
        val intent = "Understand how $topic functions"
        val doubtType = "Working principle"

        return ContextAnalysisResult(
            isDoubt = true,
            subject = subject,
            topic = topic,
            intent = intent,
            importantConcepts = concepts,
            doubtType = doubtType,
            doubtSnippet = segment,
            quickAnswer = "In $subject, $topic resolves '$segment'.",
            detailedAnswer = "Detailed analysis for '$segment':\n\n1. Concept Overview: $topic\n2. Intent: $intent.\n3. Context: ${surroundingContext.takeLast(2).joinToString(" ")}.",
            notificationSummary = "$topic: $intent.",
            isAmbiguous = segment.split(" ").size < 5,
            clarificationQuestion = "Would you like more details on the core mechanism or a practical example?",
            suggestedOptions = listOf("Core Mechanism", "Practical Example", "Step-by-step"),
            referenceMaterials = "Standard references for $topic.",
            conversationSuggestions = "Could you clarify how $topic applies in this case?",
            confidence = 0.8f
        )
    }
}
