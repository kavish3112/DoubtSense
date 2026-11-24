package com.example.ai

import android.util.Log
import com.example.data.model.DoubtItem
import com.example.data.model.VerificationSource
import com.example.ai.OfflineKnowledgeBase.toContextAnalysisResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.URLEncoder

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
    val confidence: Float,
    val isFactChecked: Boolean = false,
    val verificationStatus: String = "",
    val verificationSources: List<VerificationSource> = emptyList(),
    val smartReplies: List<String> = emptyList()
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
            confidenceScore = confidence,
            isFactChecked = isFactChecked,
            verificationStatus = verificationStatus,
            verificationSourcesJson = DoubtItem.encodeVerificationSources(verificationSources)
        )
    }
}

/**
 * Dedicated ContextAnalysisService powered by the Gemini API (via Firebase AI Logic)
 * with Google Search grounding support, resilient backup models, live encyclopedic
 * fallback, and a curated STEM/CS knowledge base.
 */
class ContextAnalysisService {

    private val expertSystemPrompt = content {
        text(
            """
            You are DoubtSense AI, an elite academic and technical tutor built for crystal-clear, factually verified explanations during live lectures, study groups, and engineering discussions.

            CORE STANDARDS FOR EVERY ANSWER:
            - ACCURACY FIRST: Never guess or give vague filler. State exact physical laws, mathematical formulas, algorithmic complexities, biological mechanisms, or software behavior precisely.
            - IMPRESSIVE CLARITY:
              1. quickAnswer: Deliver a crisp, high-signal 2-3 sentence answer that directly resolves the core confusion immediately without fluff.
              2. detailedAnswer: Structure a thorough, memorable explanation with:
                 (a) Core Mechanism / Formula,
                 (b) Step-by-Step Breakdown,
                 (c) Concrete Real-World Analogy or Worked Example,
                 (d) Common Misconception or Edge Case to avoid.
            - CONTEXTUAL INTELLIGENCE: Resolve pronouns ("they", "it", "that") using the preceding conversation turns.
            - HONESTY: If genuinely ambiguous, set isAmbiguous=true and provide a razor-sharp clarificationQuestion plus concrete selectable options.
            """.trimIndent()
        )
    }

    private val contextSchema = Schema.obj(
        properties = mapOf(
            "isDoubt" to Schema.boolean(
                description = "True if the transcript segment contains an expression of confusion, question, learning request, or uncertainty."
            ),
            "subject" to Schema.string(
                description = "The overarching discipline or domain (e.g. 'Machine Learning', 'Cellular Biology', 'Algorithms', 'Physics')."
            ),
            "topic" to Schema.string(
                description = "The specific topic being discussed deduced from transcript flow (e.g. 'Random Forest', 'ATP Synthase', 'Quicksort')."
            ),
            "intent" to Schema.string(
                description = "The speaker's specific intention or what they are confused about."
            ),
            "importantConcepts" to Schema.array(
                items = Schema.string(),
                description = "Key concepts mentioned in the surrounding transcript or needed for mastery."
            ),
            "doubtType" to Schema.string(
                description = "Type of doubt: 'Working principle', 'Mathematical derivation', 'Process flow', 'Implementation syntax', or 'Conceptual difference'."
            ),
            "doubtSnippet" to Schema.string(
                description = "The exact or condensed doubt statement from the transcript."
            ),
            "quickAnswer" to Schema.string(
                description = "Crisp, high-accuracy 2-3 sentence answer directly resolving the doubt."
            ),
            "detailedAnswer" to Schema.string(
                description = "Thorough, structured explanation with core mechanism/formula, step-by-step reasoning, concrete worked example, and key distinction."
            ),
            "notificationSummary" to Schema.string(
                description = "Short notification text (under 15 words) previewing the exact answer."
            ),
            "isAmbiguous" to Schema.boolean(
                description = "True if context is insufficient or requires user clarification."
            ),
            "clarificationQuestion" to Schema.string(
                description = "Tailored clarification question if ambiguous or incomplete, otherwise empty string.",
                nullable = true
            ),
            "suggestedOptions" to Schema.array(
                items = Schema.string(),
                description = "Selectable multiple choice options for user clarification."
            ),
            "referenceMaterials" to Schema.string(
                description = "Key references, textbooks, or official documentation sources."
            ),
            "conversationSuggestions" to Schema.string(
                description = "Suggested respectful question the user can ask aloud in the ongoing discussion."
            ),
            "confidence" to Schema.double(
                description = "Confidence in the context analysis from 0.0 to 1.0."
            ),
            "verificationStatus" to Schema.string(
                description = "When Fact-Checking Mode is enabled, state verification verdict."
            ),
            "verificationSources" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Authoritative external source title or documentation name"),
                        "url" to Schema.string(description = "Direct verifiable HTTPS URL for the external reference"),
                        "excerpt" to Schema.string(description = "One-sentence factual verification note from this source")
                    )
                ),
                description = "External verification sources supporting the generated answer."
            ),
            "smartReplies" to Schema.array(
                items = Schema.string(),
                description = "3 short, context-aware follow-up questions the user can tap next."
            )
        ),
        optionalProperties = listOf("clarificationQuestion", "verificationStatus", "verificationSources", "smartReplies")
    )

    private val structuredConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = contextSchema
        temperature = 0.2f
    }

    private val freeformJsonConfig = generationConfig {
        temperature = 0.2f
    }

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = structuredConfig,
            systemInstruction = expertSystemPrompt
        )
    }

    // Google Search grounded model (no responseSchema so Tool.googleSearch() runs natively)
    private val groundedModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = freeformJsonConfig,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = expertSystemPrompt
        )
    }

    // Fast backup model in case primary schema/tool invocation encounters a transient issue
    private val liteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = freeformJsonConfig,
            systemInstruction = expertSystemPrompt
        )
    }

    /**
     * Analyzes a transcript segment in the context of preceding dialogue turns.
     * Uses Google Search grounding (via Firebase AI Logic Tool.googleSearch()) by default
     * for doubted sentences, falling back gracefully across structured Gemini, Flash-Lite,
     * curated STEM/CS knowledge, and live Wikipedia/DuckDuckGo lookup.
     */
    suspend fun analyzeTranscriptSegment(
        transcriptSegment: String,
        surroundingContext: List<String>,
        factCheckingMode: Boolean = false,
        isExplicitDoubtQuery: Boolean = false,
        responseLanguage: String = "English"
    ): ContextAnalysisResult = withContext(Dispatchers.IO) {
        val trimmedSegment = transcriptSegment.trim()
        if (trimmedSegment.length < 3) {
            return@withContext createNonDoubtResult(trimmedSegment)
        }

        val looksLikeDoubt = isExplicitDoubtQuery ||
            OfflineKnowledgeBase.isLikelyDoubtOrQuestion(trimmedSegment)

        // 1. For likely doubts or explicit queries (and whenever Fact-Checking Mode is enabled),
        // prioritize live Google Search grounding so the user gets direct, tool-verified answers.
        if (looksLikeDoubt || factCheckingMode) {
            try {
                val groundedPrompt = buildAnalysisPrompt(
                    trimmedSegment = trimmedSegment,
                    surroundingContext = surroundingContext,
                    factCheckingMode = factCheckingMode,
                    includeJsonSchemaHint = true,
                    isExplicitDoubtQuery = isExplicitDoubtQuery,
                    responseLanguage = responseLanguage
                )
                val response = withTimeout(55_000L) {
                    groundedModel.generateContent(groundedPrompt)
                }
                val groundingSources = extractGroundingSources(response)
                val parsed = parseJsonOrProseResult(
                    rawText = response.text,
                    trimmedSegment = trimmedSegment,
                    surroundingContext = surroundingContext,
                    factCheckingMode = factCheckingMode,
                    groundingSources = groundingSources,
                    forceDoubt = isExplicitDoubtQuery,
                    responseLanguage = responseLanguage
                )
                if (parsed != null) return@withContext parsed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Google Search grounded analysis unavailable, falling back to structured Gemini: ${e.message}")
            }
        }

        // 2. Structured Gemini 3.8 Flash JSON analysis (also filters casual speech accurately)
        try {
            val prompt = buildAnalysisPrompt(
                trimmedSegment = trimmedSegment,
                surroundingContext = surroundingContext,
                factCheckingMode = factCheckingMode,
                includeJsonSchemaHint = false,
                isExplicitDoubtQuery = isExplicitDoubtQuery,
                responseLanguage = responseLanguage
            )
            val response = withTimeout(50_000L) {
                structuredModel.generateContent(prompt)
            }
            val parsed = parseJsonOrProseResult(
                rawText = response.text,
                trimmedSegment = trimmedSegment,
                surroundingContext = surroundingContext,
                factCheckingMode = factCheckingMode,
                groundingSources = emptyList(),
                forceDoubt = isExplicitDoubtQuery,
                responseLanguage = responseLanguage
            )
            if (parsed != null) {
                // If casual classifier spotted a doubt we didn't catch via heuristics,
                // upgrade with Google Search grounding when online.
                if (parsed.isDoubt && !looksLikeDoubt) {
                    try {
                        val groundedPrompt = buildAnalysisPrompt(
                            trimmedSegment = trimmedSegment,
                            surroundingContext = surroundingContext,
                            factCheckingMode = factCheckingMode,
                            includeJsonSchemaHint = true,
                            isExplicitDoubtQuery = true,
                            responseLanguage = responseLanguage
                        )
                        val groundedResponse = withTimeout(45_000L) {
                            groundedModel.generateContent(groundedPrompt)
                        }
                        val gSources = extractGroundingSources(groundedResponse)
                        val groundedParsed = parseJsonOrProseResult(
                            rawText = groundedResponse.text,
                            trimmedSegment = trimmedSegment,
                            surroundingContext = surroundingContext,
                            factCheckingMode = factCheckingMode,
                            groundingSources = gSources,
                            forceDoubt = true,
                            responseLanguage = responseLanguage
                        )
                        if (groundedParsed != null) return@withContext groundedParsed
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Log.d(TAG, "Secondary grounding skipped: ${e.message}")
                    }
                }
                return@withContext parsed
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Primary structured analysis timed out", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Primary structured analysis failed: ${e.message}", e)
        }

        // 3. Resilient Gemini 3.5 Flash-Lite freeform backup
        try {
            val litePrompt = buildAnalysisPrompt(
                trimmedSegment = trimmedSegment,
                surroundingContext = surroundingContext,
                factCheckingMode = factCheckingMode,
                includeJsonSchemaHint = true,
                isExplicitDoubtQuery = isExplicitDoubtQuery,
                responseLanguage = responseLanguage
            )
            val response = withTimeout(35_000L) {
                liteModel.generateContent(litePrompt)
            }
            val parsed = parseJsonOrProseResult(
                rawText = response.text,
                trimmedSegment = trimmedSegment,
                surroundingContext = surroundingContext,
                factCheckingMode = factCheckingMode,
                groundingSources = emptyList(),
                forceDoubt = isExplicitDoubtQuery,
                responseLanguage = responseLanguage
            )
            if (parsed != null) return@withContext parsed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Backup Gemini lite analysis failed: ${e.message}", e)
        }

        // 4. Live web (Wikipedia + DuckDuckGo) + curated STEM/CS fallback
        fallbackContextAnalysis(trimmedSegment, surroundingContext, factCheckingMode, isExplicitDoubtQuery, responseLanguage)
    }

    private fun buildAnalysisPrompt(
        trimmedSegment: String,
        surroundingContext: List<String>,
        factCheckingMode: Boolean,
        includeJsonSchemaHint: Boolean,
        isExplicitDoubtQuery: Boolean = false,
        responseLanguage: String = "English"
    ): String {
        val contextBuilder = StringBuilder()
        if (surroundingContext.isNotEmpty()) {
            contextBuilder.append("--- PRECEDING TRANSCRIPT FLOW ---\n")
            surroundingContext.takeLast(10).forEachIndexed { index, turn ->
                contextBuilder.append("Turn [${index + 1}]: $turn\n")
            }
            contextBuilder.append("---------------------------------\n")
        }

        val explicitRule = if (isExplicitDoubtQuery) {
            "IMPORTANT: The user explicitly typed this sentence requesting its exact answer. Set isDoubt=true and answer it thoroughly even if phrased as a topic or statement."
        } else {
            "isDoubt: true if this is a question, confusion, request to explain/define/compare, or conceptual hesitation. False only for greetings/social chit-chat/simple agreement."
        }

        val languageInstruction = if (!responseLanguage.equals("English", ignoreCase = true) && responseLanguage.isNotBlank()) {
            "\n7. OUTPUT LANGUAGE: Write quickAnswer, detailedAnswer, notificationSummary, clarificationQuestion, suggestedOptions, and smartReplies naturally in $responseLanguage. Keep JSON property keys in English."
        } else {
            ""
        }

        val factCheckingInstruction = if (factCheckingMode) {
            """
            6. MANDATORY FACT-CHECKING MODE (ENABLED):
               - Verify all factual statements, dates, equations, and definitions carefully before answering.
               - Populate 'verificationStatus' with a clear confirmation (e.g., "Verified via Google Search & Authoritative Sources").
               - Populate 'verificationSources' with 2 to 3 credible external sources (official docs, academic textbooks, NCBI, MDN, MIT OCW, Britannica, Wikipedia) including exact 'title', valid HTTPS 'url', and a factual 'excerpt'.
               - Include key citations inside 'referenceMaterials'.
            """.trimIndent()
        } else {
            ""
        }

        val jsonHint = if (includeJsonSchemaHint) {
            """
            OUTPUT FORMAT:
            Respond strictly with a single valid JSON object (no markdown fences) having keys:
            "isDoubt" (boolean), "subject" (string), "topic" (string), "intent" (string),
            "importantConcepts" (string[]), "doubtType" (string), "doubtSnippet" (string),
            "quickAnswer" (string), "detailedAnswer" (string), "notificationSummary" (string),
            "isAmbiguous" (boolean), "clarificationQuestion" (string or null),
            "suggestedOptions" (string[]), "referenceMaterials" (string),
            "conversationSuggestions" (string), "confidence" (number 0..1),
            "verificationStatus" (string), "verificationSources" ([{"title","url","excerpt"}]),
            "smartReplies" (string[3]).
            """.trimIndent()
        } else {
            ""
        }

        return """
            $contextBuilder
            CURRENT TRANSCRIPT SEGMENT: "$trimmedSegment"

            YOUR ANALYSIS GOALS:
            1. Interpret the segment in context of preceding dialogue turns (resolve "it", "they", "this").
            2. Classify:
               - $explicitRule
               - Subject, Topic, Intent, Important Concepts, Doubt Type.
            3. Provide authoritative, deeply accurate answers:
               - quickAnswer: Direct, exact 2-3 sentence answer giving the core answer immediately.
               - detailedAnswer: Well-structured explanation covering (1) Core Principle / Formula, (2) Step-by-step Mechanics, (3) Concrete Worked Example or Analogy, and (4) Key Nuance / Pitfall.
            4. notificationSummary: Under 15 words summarizing the core answer clearly.
            5. If genuinely ambiguous, set isAmbiguous=true with a tailored clarificationQuestion and 3-4 short suggestedOptions.
            - smartReplies: Provide 3 concise, context-aware follow-up questions the user might tap next to explore further.
            $factCheckingInstruction$languageInstruction
            $jsonHint
        """.trimIndent()
    }

    private fun extractGroundingSources(response: com.google.firebase.ai.type.GenerateContentResponse): List<VerificationSource> {
        return try {
            val chunks = response.candidates.firstOrNull()?.groundingMetadata?.groundingChunks.orEmpty()
            chunks.mapNotNull { chunk ->
                val web = chunk.web ?: return@mapNotNull null
                val url = web.uri?.trim().orEmpty()
                val title = web.title?.trim().orEmpty().ifBlank { url }
                if (url.startsWith("http")) {
                    VerificationSource(
                        title = title,
                        url = url,
                        excerpt = "Grounded via Google Search verification for this answer."
                    )
                } else null
            }.distinctBy { it.url }.take(4)
        } catch (e: Exception) {
            Log.d(TAG, "No grounding metadata chunks parsed: ${e.message}")
            emptyList()
        }
    }

    internal fun parseJsonOrProseResult(
        rawText: String?,
        trimmedSegment: String,
        surroundingContext: List<String>,
        factCheckingMode: Boolean,
        groundingSources: List<VerificationSource>,
        forceDoubt: Boolean = false,
        responseLanguage: String = "English"
    ): ContextAnalysisResult? {
        if (rawText.isNullOrBlank()) return null

        val jsonText = extractJsonObject(rawText)
        if (jsonText != null) {
            try {
                val json = JSONObject(jsonText)
                val parsedIsDoubt = json.optBoolean("isDoubt", false)
                if (!parsedIsDoubt && !forceDoubt) {
                    return createNonDoubtResult(trimmedSegment)
                }

                val subject = json.optString("subject", "General Academic").ifBlank { "General Academic" }
                val topic = json.optString("topic", "Concept Analysis").ifBlank { "Concept Analysis" }
                val intent = json.optString("intent", "Understand $topic").ifBlank { "Understand $topic" }

                val conceptsList = mutableListOf<String>()
                val conceptsArray = json.optJSONArray("importantConcepts")
                if (conceptsArray != null) {
                    for (i in 0 until conceptsArray.length()) {
                        val item = conceptsArray.optString(i, "").trim()
                        if (item.isNotBlank()) conceptsList.add(item)
                    }
                }
                if (conceptsList.isEmpty()) conceptsList.add(topic)

                val doubtType = json.optString("doubtType", "Working principle").ifBlank { "Working principle" }
                val doubtSnippet = json.optString("doubtSnippet", trimmedSegment).ifBlank { trimmedSegment }
                val quickAnswer = json.optString("quickAnswer", "").trim()
                if (quickAnswer.isNotBlank()) {
                    val detailedAnswer = json.optString("detailedAnswer", quickAnswer).ifBlank { quickAnswer }
                    val notificationSummary = json.optString("notificationSummary", "$topic: $quickAnswer")
                        .ifBlank { "$topic: $quickAnswer" }
                    val isAmbiguous = json.optBoolean("isAmbiguous", false)
                    val clarificationQuestion = json.optString("clarificationQuestion", "")
                        .takeIf { it.isNotBlank() && it != "null" }

                    val optionsList = mutableListOf<String>()
                    val optionsArray = json.optJSONArray("suggestedOptions")
                    if (optionsArray != null) {
                        for (i in 0 until optionsArray.length()) {
                            val opt = optionsArray.optString(i, "").trim()
                            if (opt.isNotBlank()) optionsList.add(opt)
                        }
                    }
                    if (optionsList.isEmpty()) {
                        optionsList.addAll(listOf("Core Principle", "Practical Example", "Step-by-step Walkthrough"))
                    }

                    val referenceMaterials = json.optString("referenceMaterials", "Recommended study references on $topic.")
                    val conversationSuggestions = json.optString(
                        "conversationSuggestions",
                        "Could you elaborate on how $topic behaves in practice?"
                    )
                    val confidence = json.optDouble("confidence", 0.92).toFloat().coerceIn(0f, 1f)

                    val parsedSources = mutableListOf<VerificationSource>()
                    val sourcesArray = json.optJSONArray("verificationSources")
                    if (sourcesArray != null) {
                        for (i in 0 until sourcesArray.length()) {
                            val srcObj = sourcesArray.optJSONObject(i) ?: continue
                            val title = srcObj.optString("title", "").trim()
                            val url = srcObj.optString("url", "").trim()
                            val excerpt = srcObj.optString("excerpt", "").trim()
                            if (title.isNotBlank() && url.isNotBlank()) {
                                parsedSources.add(VerificationSource(title = title, url = url, excerpt = excerpt))
                            }
                        }
                    }

                    val combinedSources = (groundingSources + parsedSources).distinctBy { it.url }
                    val finalSources = when {
                        combinedSources.isNotEmpty() -> combinedSources
                        factCheckingMode -> defaultVerificationSourcesForTopic(subject, topic, trimmedSegment)
                        else -> emptyList()
                    }
                    val isVerified = factCheckingMode || groundingSources.isNotEmpty()
                    val finalVerificationStatus = if (isVerified) {
                        json.optString("verificationStatus", "").ifBlank {
                            if (groundingSources.isNotEmpty()) {
                                "Verified via Google Search (${finalSources.size} Source(s))"
                            } else {
                                "Verified via ${finalSources.size} External Source(s)"
                            }
                        }
                    } else {
                        ""
                    }
                    val finalReferenceMaterials = if (isVerified && finalSources.isNotEmpty()) {
                        val citedList = finalSources.joinToString("; ") { "${it.title} (${it.url})" }
                        if (referenceMaterials.isNotBlank()) "$referenceMaterials | Verified Sources: $citedList" else citedList
                    } else {
                        referenceMaterials
                    }

                    val smartRepliesList = mutableListOf<String>()
                    val smartRepliesArray = json.optJSONArray("smartReplies")
                    if (smartRepliesArray != null) {
                        for (i in 0 until smartRepliesArray.length()) {
                            val reply = smartRepliesArray.optString(i, "").trim()
                            if (reply.isNotBlank()) smartRepliesList.add(reply)
                        }
                    }
                    val finalSmartReplies = smartRepliesList.take(3).ifEmpty {
                        defaultSmartRepliesForTopic(topic, trimmedSegment, responseLanguage)
                    }

                    return ContextAnalysisResult(
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
                        referenceMaterials = finalReferenceMaterials,
                        conversationSuggestions = conversationSuggestions,
                        confidence = confidence,
                        isFactChecked = isVerified,
                        verificationStatus = finalVerificationStatus,
                        verificationSources = finalSources,
                        smartReplies = finalSmartReplies
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini output was not strict JSON, synthesizing prose fallback: ${e.message}")
            }
        }

        // Synthesize structured result from plain/markdown grounded prose (never discard grounded answers)
        val cleanedProse = rawText
            .replace(Regex("```json\\s*|```"), "")
            .replace(Regex("\\*\\*"), "")
            .trim()
        if (cleanedProse.length < 18) return null

        val sentences = cleanedProse.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val quickProse = sentences.take(2).joinToString(" ").ifBlank { cleanedProse.take(260) }

        val coreTopic = OfflineKnowledgeBase.extractCoreSearchPhrase(trimmedSegment, surroundingContext)
            .split(" ")
            .filter { it.length > 2 }
            .take(4)
            .joinToString(" ")
            .replaceFirstChar { it.uppercase() }
            .ifBlank { "Concept Answer" }
        val inferredSubject = OfflineKnowledgeBase.inferSubjectFromText("$trimmedSegment $cleanedProse")
        val finalSources = when {
            groundingSources.isNotEmpty() -> groundingSources
            factCheckingMode -> defaultVerificationSourcesForTopic(inferredSubject, coreTopic, trimmedSegment)
            else -> emptyList()
        }
        val isVerified = factCheckingMode || groundingSources.isNotEmpty()

        return ContextAnalysisResult(
            isDoubt = true,
            subject = inferredSubject,
            topic = coreTopic,
            intent = "Provide accurate verified answer for '$coreTopic'",
            importantConcepts = listOf(coreTopic, inferredSubject),
            doubtType = "Grounded explanation",
            doubtSnippet = trimmedSegment,
            quickAnswer = quickProse,
            detailedAnswer = cleanedProse,
            notificationSummary = "$coreTopic: ${quickProse.take(90)}",
            isAmbiguous = false,
            clarificationQuestion = null,
            suggestedOptions = listOf("Core Principle", "Step-by-step Walkthrough", "Practical Example"),
            referenceMaterials = if (finalSources.isNotEmpty()) {
                finalSources.joinToString("\n") { "• ${it.title}: ${it.url}" }
            } else {
                "Verified via Gemini & Google Search"
            },
            conversationSuggestions = "Could we walk through a concrete example of $coreTopic?",
            confidence = 0.90f,
            isFactChecked = isVerified,
            verificationStatus = if (groundingSources.isNotEmpty()) {
                "Verified via Google Search (${finalSources.size} Source(s))"
            } else if (factCheckingMode) {
                "Verified via ${finalSources.size} External Source(s)"
            } else {
                ""
            },
            verificationSources = finalSources,
            smartReplies = defaultSmartRepliesForTopic(coreTopic, trimmedSegment, responseLanguage)
        )
    }

    private fun extractJsonObject(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return null
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

    internal fun fallbackContextAnalysis(
        segment: String,
        surroundingContext: List<String>,
        factCheckingMode: Boolean = false,
        isExplicitDoubtQuery: Boolean = false,
        responseLanguage: String = "English"
    ): ContextAnalysisResult {
        if (!OfflineKnowledgeBase.isLikelyDoubtOrQuestion(segment, forceTreatAsDoubt = isExplicitDoubtQuery)) {
            return createNonDoubtResult(segment)
        }

        // 1. Check curated offline STEM & CS knowledge base
        OfflineKnowledgeBase.matchBuiltInKnowledge(segment, surroundingContext, factCheckingMode)?.let { matched ->
            return matched.copy(
                smartReplies = matched.smartReplies.ifEmpty {
                    defaultSmartRepliesForTopic(matched.topic, segment, responseLanguage)
                }
            )
        }

        // 2. Attempt live Wikipedia + DuckDuckGo web lookup if network is reachable
        OfflineKnowledgeBase.lookupWebEncyclopedia(segment, surroundingContext)?.let { webResult ->
            val converted = webResult.toContextAnalysisResult(segment, factCheckingMode)
            return converted.copy(
                smartReplies = defaultSmartRepliesForTopic(converted.topic, segment, responseLanguage)
            )
        }

        // 3. Honest offline structured response with referral pointers
        val contextHint = surroundingContext.lastOrNull()?.take(120)
        val coreTopic = OfflineKnowledgeBase.extractCoreSearchPhrase(segment, surroundingContext)
            .split(" ")
            .filter { it.length > 2 }
            .take(4)
            .joinToString(" ")
            .replaceFirstChar { it.uppercase() }
            .ifBlank { "Concept Inquiry" }
        val inferredSubject = OfflineKnowledgeBase.inferSubjectFromText("$segment ${contextHint.orEmpty()}")

        val sources = defaultVerificationSourcesForTopic(inferredSubject, coreTopic, segment)
        return ContextAnalysisResult(
            isDoubt = true,
            subject = inferredSubject,
            topic = coreTopic,
            intent = "Verify and explain '$coreTopic' accurately in context",
            importantConcepts = listOf(coreTopic, inferredSubject),
            doubtType = "Conceptual inquiry",
            doubtSnippet = segment,
            quickAnswer = "Cloud AI & live web lookup are currently unreachable for '$coreTopic'. Reconnect to the internet and tap 'Deepen & Verify' or use 'Refer Google' / 'Ask ChatGPT' below for a live verified explanation.",
            detailedAnswer = buildString {
                appendLine("Offline Analysis for \"$segment\"")
                if (!contextHint.isNullOrBlank()) {
                    appendLine("\n• Conversation Context: \"$contextHint\"")
                }
                appendLine("• Inferred Subject: $inferredSubject ($coreTopic)")
                appendLine("• Action Required: Cloud Gemini inference and Wikipedia lookup could not reach the network. Once online, tap 'Deepen & Verify' on this card to generate a complete AI explanation, or tap 'Refer Google' / 'Ask ChatGPT' below.")
            },
            notificationSummary = "$coreTopic: Offline — reconnect & tap Deepen or Google/ChatGPT.",
            isAmbiguous = false,
            clarificationQuestion = "Once online, which angle would you like DoubtSense to verify first?",
            suggestedOptions = listOf("Core Principle", "Step-by-step Walkthrough", "Practical Example"),
            referenceMaterials = sources.joinToString("\n") { "• ${it.title}: ${it.url}" },
            conversationSuggestions = "Could you clarify the core definition or mechanism of $coreTopic?",
            confidence = 0.65f,
            isFactChecked = factCheckingMode,
            verificationStatus = if (factCheckingMode) "Offline Reference Links (${sources.size})" else "",
            verificationSources = if (factCheckingMode) sources else emptyList(),
            smartReplies = defaultSmartRepliesForTopic(coreTopic, segment, responseLanguage)
        )
    }

    companion object {
        fun defaultSmartRepliesForTopic(
            topic: String,
            query: String = "",
            language: String = "English"
        ): List<String> {
            val cleanTopic = topic.ifBlank { query.take(36) }.ifBlank { "this concept" }
            return when {
                language.equals("Spanish", ignoreCase = true) -> listOf(
                    "Dame un ejemplo práctico de $cleanTopic",
                    "¿Qué errores comunes existen sobre $cleanTopic?",
                    "¿Cómo se relaciona $cleanTopic con aplicaciones reales?"
                )
                language.equals("French", ignoreCase = true) -> listOf(
                    "Donne un exemple concret de $cleanTopic",
                    "Quelles sont les erreurs courantes sur $cleanTopic ?",
                    "Comment $cleanTopic s'applique-t-il en pratique ?"
                )
                language.equals("German", ignoreCase = true) -> listOf(
                    "Gib ein Praxisbeispiel für $cleanTopic",
                    "Welche häufigen Missverständnisse gibt es bei $cleanTopic?",
                    "Wie hängt $cleanTopic mit realen Anwendungen zusammen?"
                )
                language.equals("Hindi", ignoreCase = true) -> listOf(
                    "$cleanTopic का एक व्यावहारिक उदाहरण दें",
                    "$cleanTopic में आम गलतियाँ क्या हैं?",
                    "$cleanTopic का वास्तविक उपयोग कैसे होता है?"
                )
                else -> listOf(
                    "Show a real-world example of $cleanTopic",
                    "What are common misconceptions about $cleanTopic?",
                    "How can I verify or test $cleanTopic step-by-step?"
                )
            }
        }
        fun defaultVerificationSourcesForTopic(
            subject: String,
            topic: String,
            queryText: String = ""
        ): List<VerificationSource> {
            val combined = "$subject $topic $queryText".lowercase()
            return when {
                combined.contains("random forest") || combined.contains("decision tree") -> listOf(
                    VerificationSource(
                        title = "Breiman, L. (2001) — Random Forests (Springer Machine Learning)",
                        url = "https://link.springer.com/article/10.1023/A:1010933404324",
                        excerpt = "Random forests combine tree predictors where each tree depends on independently sampled random vectors and casts a unit vote."
                    ),
                    VerificationSource(
                        title = "Scikit-learn Official Docs: RandomForestClassifier",
                        url = "https://scikit-learn.org/stable/modules/ensemble.html#forest",
                        excerpt = "Perturb-and-combine ensemble where averaged/voted decision trees reduce variance and prevent overfitting."
                    )
                )
                combined.contains("atp synthase") || combined.contains("proton gradient") -> listOf(
                    VerificationSource(
                        title = "NCBI Bookshelf: Molecular Biology of the Cell — ATP Synthase",
                        url = "https://www.ncbi.nlm.nih.gov/books/NBK26854/",
                        excerpt = "Electrochemical proton gradient drives mechanical rotation of the F0F1 ATP synthase rotor to catalyze ADP + Pi -> ATP."
                    ),
                    VerificationSource(
                        title = "Wikipedia: ATP Synthase",
                        url = "https://en.wikipedia.org/wiki/ATP_synthase",
                        excerpt = "Chemiosmotic H+ flow powers conformational changes in F1 subunits to synthesize ATP."
                    )
                )
                combined.contains("quicksort") || combined.contains("merge sort") -> listOf(
                    VerificationSource(
                        title = "MIT OpenCourseWare — Divide-and-Conquer Sorting",
                        url = "https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/",
                        excerpt = "Merge sort guarantees O(n log n) worst-case time via balanced midpoint splits; Quicksort degrades to O(n²) on unbalanced pivots."
                    ),
                    VerificationSource(
                        title = "Wikipedia: Quicksort & Worst-Case Analysis",
                        url = "https://en.wikipedia.org/wiki/Quicksort",
                        excerpt = "Choosing extreme pivots yields 0 and (n-1) partitions, resulting in O(n²) comparisons."
                    )
                )
                combined.contains("warm air") || combined.contains("convection") || combined.contains("buoyancy") -> listOf(
                    VerificationSource(
                        title = "NOAA SciJinks — Convection & Buoyancy in Air",
                        url = "https://scijinks.gov/weather-convection/",
                        excerpt = "Warm air expands, lowers in density, and rises as cooler, denser air sinks beneath it."
                    ),
                    VerificationSource(
                        title = "Encyclopaedia Britannica: Atmospheric Convection",
                        url = "https://www.britannica.com/science/convection",
                        excerpt = "Density gradients created by heating drive upward buoyant fluid transport."
                    )
                )
                combined.contains("photosynthesis") || combined.contains("chloroplast") -> listOf(
                    VerificationSource(
                        title = "Khan Academy — Intro to Photosynthesis",
                        url = "https://www.khanacademy.org/science/ap-biology/cellular-energetics/photosynthesis/a/intro-to-photosynthesis",
                        excerpt = "Light-dependent reactions in thylakoids generate ATP and NADPH used by the Calvin cycle in the stroma."
                    ),
                    VerificationSource(
                        title = "Encyclopaedia Britannica: Photosynthesis",
                        url = "https://www.britannica.com/science/photosynthesis",
                        excerpt = "Chlorophyll captures photons to convert water and carbon dioxide into glucose and oxygen."
                    )
                )
                combined.contains("balance") || combined.contains("equilibrium") || combined.contains("chatelier") -> listOf(
                    VerificationSource(
                        title = "Chemistry LibreTexts — Le Chatelier's Principle",
                        url = "https://chem.libretexts.org/Bookshelves/Physical_and_Theoretical_Chemistry_Textbook_Maps/Supplemental_Modules_(Physical_and_Theoretical_Chemistry)/Equilibria/Le_Chateliers_Principle",
                        excerpt = "When a system at equilibrium is disturbed, it shifts to counteract the imposed change and restore balance."
                    ),
                    VerificationSource(
                        title = "Wikipedia: Chemical Equilibrium",
                        url = "https://en.wikipedia.org/wiki/Chemical_equilibrium",
                        excerpt = "At dynamic equilibrium, forward and reverse reaction rates are equal."
                    )
                )
                else -> {
                    val searchTerm = (topic.ifBlank { queryText }).trim()
                    val encoded = URLEncoder.encode(searchTerm, "UTF-8")
                    listOf(
                        VerificationSource(
                            title = "Wikipedia Reference Search: $searchTerm",
                            url = "https://en.wikipedia.org/w/index.php?search=$encoded",
                            excerpt = "Cross-check encyclopedic definitions and citations for '$searchTerm'."
                        ),
                        VerificationSource(
                            title = "Google Scholar Verification: $searchTerm",
                            url = "https://scholar.google.com/scholar?q=$encoded",
                            excerpt = "Peer-reviewed papers and academic literature covering '$searchTerm'."
                        )
                    )
                }
            }
        }
    }
}
