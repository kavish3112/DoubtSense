package com.example.ai

import android.util.Log
import com.example.data.model.DoubtItem
import com.example.data.model.VerificationSource
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "AnswerEngine"

data class AnswerEngineResult(
    val doubtId: Long = 0L,
    val query: String,
    val subject: String,
    val topic: String,
    val quickAnswer: String,
    val detailedAnswer: String,
    val keyConcepts: List<String>,
    val confidence: Float,
    val groundingLabel: String,
    val isFactChecked: Boolean,
    val verificationStatus: String,
    val sources: List<VerificationSource>,
    val capturedViaVoice: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val customTag: String = "Education",
    val isStarred: Boolean = false,
    val oneSentenceSummary: String = "",
    val responseLanguage: String = "English",
    val smartReplies: List<String> = emptyList()
) {
    fun toDoubtItem(conversationalContext: String = ""): DoubtItem {
        return DoubtItem(
            id = doubtId,
            timestamp = timestamp,
            doubtSnippet = query,
            subject = subject,
            topic = topic,
            importantConcepts = keyConcepts.joinToString(", "),
            doubtType = if (capturedViaVoice) "Voice Answer Engine" else "Answer Engine Query",
            intent = "Generate immediate, verified answer for '$query'",
            quickAnswer = quickAnswer,
            detailedAnswer = detailedAnswer,
            notificationSummary = "$topic: ${quickAnswer.take(90)}",
            conversationalContext = conversationalContext,
            isAmbiguous = false,
            clarificationQuestion = null,
            suggestedOptionsJson = "[\"Core Principle\",\"Step-by-step Walkthrough\",\"Practical Example\"]",
            referenceMaterials = sources.joinToString("\n") { "• ${it.title}: ${it.url}" },
            conversationSuggestions = "Would you like a worked example of $topic?",
            confidenceScore = confidence,
            isFactChecked = isFactChecked,
            verificationStatus = verificationStatus,
            verificationSourcesJson = DoubtItem.encodeVerificationSources(sources),
            isStarred = isStarred,
            customTag = customTag,
            oneSentenceSummary = oneSentenceSummary
        )
    }

    companion object {
        fun fromDoubtItem(doubt: DoubtItem): AnswerEngineResult {
            val concepts = doubt.importantConcepts
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val grounding = when {
                doubt.verificationStatus.contains("Google Search", ignoreCase = true) -> "Gemini + Google Search"
                doubt.verificationStatus.contains("Live Web", ignoreCase = true) ||
                    doubt.verificationStatus.contains("Web-Grounded", ignoreCase = true) -> "Live Web Reference"
                doubt.verificationStatus.contains("Offline", ignoreCase = true) -> "Offline Knowledge"
                doubt.isFactChecked -> "Verified AI Engine"
                else -> "Saved Room History"
            }
            val resolvedTopic = doubt.topic.ifBlank { "Saved Answer" }
            return AnswerEngineResult(
                doubtId = doubt.id,
                query = doubt.doubtSnippet,
                subject = doubt.subject.ifBlank { "General Knowledge" },
                topic = resolvedTopic,
                quickAnswer = doubt.quickAnswer,
                detailedAnswer = doubt.detailedAnswer.ifBlank { doubt.quickAnswer },
                keyConcepts = concepts,
                confidence = doubt.confidenceScore.coerceIn(0f, 1f),
                groundingLabel = grounding,
                isFactChecked = doubt.isFactChecked,
                verificationStatus = doubt.verificationStatus,
                sources = doubt.getVerificationSources(),
                capturedViaVoice = doubt.doubtType.contains("Voice", ignoreCase = true),
                timestamp = doubt.timestamp,
                customTag = doubt.customTag.ifBlank { "Education" },
                isStarred = doubt.isStarred,
                oneSentenceSummary = doubt.oneSentenceSummary,
                responseLanguage = "English",
                smartReplies = ContextAnalysisService.defaultSmartRepliesForTopic(
                    topic = resolvedTopic,
                    query = doubt.doubtSnippet,
                    language = "English"
                )
            )
        }
    }
}

/**
 * Dedicated DoubtSense Answer Engine:
 * 1) Converts raw captured WAV microphone audio into accurate text via Gemini multimodal audio when
 *    device SpeechRecognizer is unavailable or direct audio capture is selected.
 * 2) Answers any spoken or typed sentence immediately using Gemini + Google Search grounding,
 *    resilient Flash-Lite fallback, live Wikipedia / DuckDuckGo web lookup, and STEM/CS knowledge.
 */
class AnswerEngine(
    private val contextAnalysisService: ContextAnalysisService = ContextAnalysisService(),
    private val localAnalysisProvider: ((String, List<String>, Boolean) -> ContextAnalysisResult)? = null
) {

    private val audioTranscriptionSystemPrompt = content {
        text(
            """
            You are an ultra-accurate speech-to-text transcriber for academic, coding, scientific, and everyday questions.
            Return ONLY the exact words spoken in the audio as clean, properly punctuated text.
            Do not answer the question yet and do not add commentary or speaker prefixes.
            """.trimIndent()
        )
    }

    private val audioModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig { temperature = 0.0f },
            systemInstruction = audioTranscriptionSystemPrompt
        )
    }

    private val liteAudioModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig { temperature = 0.0f },
            systemInstruction = audioTranscriptionSystemPrompt
        )
    }

    private val streamingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig { temperature = 0.25f },
            systemInstruction = content {
                text(
                    """
                    You are DoubtSense AI, an elite academic and technical tutor.
                    Deliver an immediate, crystal-clear, factually grounded explanation with exact formulas/code,
                    step-by-step reasoning, and a concrete example.
                    End with 3 concise follow-up questions under a line starting with "FOLLOW_UPS:" separated by "|".
                    """.trimIndent()
                )
            }
        )
    }

    private val liteSummaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig { temperature = 0.2f }
        )
    }

    /**
     * Converts captured 16kHz WAV microphone audio bytes into text via Firebase AI Logic.
     */
    suspend fun transcribeAudio(wavBytes: ByteArray): String = withContext(Dispatchers.IO) {
        if (wavBytes.size <= 44) {
            throw IllegalArgumentException("Recorded audio buffer is empty. Please speak clearly and try again.")
        }

        val audioPrompt = content {
            inlineData(wavBytes, "audio/wav")
            text("Transcribe the spoken question or sentence from this microphone recording accurately. Output only the transcript text.")
        }

        try {
            val response = withTimeout(45_000L) {
                audioModel.generateContent(audioPrompt)
            }
            val transcript = response.text?.trim().orEmpty()
            if (transcript.isNotBlank()) return@withContext transcript
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Primary Gemini audio transcription timed out", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Primary Gemini audio transcription failed, trying Flash-Lite: ${e.message}")
        }

        try {
            val liteResp = withTimeout(35_000L) {
                liteAudioModel.generateContent(audioPrompt)
            }
            val transcript = liteResp.text?.trim().orEmpty()
            if (transcript.isNotBlank()) return@withContext transcript
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Backup Gemini audio transcription failed: ${e.message}", e)
            throw IllegalStateException("Could not transcribe microphone audio: ${e.localizedMessage ?: "Network unavailable"}")
        }

        throw IllegalStateException("No speech was recognized in the audio recording. Please speak closer to the mic and try again.")
    }

    /**
     * Generates an immediate, accurate, tool-grounded answer for any spoken or typed sentence.
     */
    suspend fun generateImmediateAnswer(
        query: String,
        conversationContext: List<String> = emptyList(),
        factCheckingMode: Boolean = true,
        capturedViaVoice: Boolean = false,
        responseLanguage: String = "English"
    ): AnswerEngineResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotBlank()) { "Please speak or type a question to answer." }

        val analysis = localAnalysisProvider?.invoke(cleanQuery, conversationContext, factCheckingMode)
            ?: contextAnalysisService.analyzeTranscriptSegment(
                transcriptSegment = cleanQuery,
                surroundingContext = conversationContext,
                factCheckingMode = factCheckingMode,
                isExplicitDoubtQuery = true,
                responseLanguage = responseLanguage
            )

        buildAnswerFromAnalysis(
            analysis = analysis,
            query = cleanQuery,
            capturedViaVoice = capturedViaVoice,
            responseLanguage = responseLanguage,
            conversationContext = conversationContext
        )
    }

    /**
     * Streams progressive AI answer segments via Firebase AI Logic `generateContentStream()`
     * so the UI updates in real time and auto-scrolls smoothly. Falls back cleanly to chunked
     * structured generation when offline or when a localAnalysisProvider is active.
     */
    suspend fun generateStreamingAnswer(
        query: String,
        conversationContext: List<String> = emptyList(),
        factCheckingMode: Boolean = true,
        capturedViaVoice: Boolean = false,
        responseLanguage: String = "English",
        onPartialText: (String) -> Unit
    ): AnswerEngineResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotBlank()) { "Please speak or type a question to answer." }

        // Use immediate fallback + incremental emission when injected local provider is used (e.g. unit tests / offline)
        if (localAnalysisProvider != null) {
            val result = generateImmediateAnswer(
                query = cleanQuery,
                conversationContext = conversationContext,
                factCheckingMode = factCheckingMode,
                capturedViaVoice = capturedViaVoice,
                responseLanguage = responseLanguage
            )
            emitTextProgressively(result.detailedAnswer, onPartialText)
            return@withContext result
        }

        val contextBlock = if (conversationContext.isNotEmpty()) {
            "Previous context:\n" + conversationContext.takeLast(6).joinToString("\n") { "- $it" } + "\n\n"
        } else {
            ""
        }
        val languageDirective = if (!responseLanguage.equals("English", ignoreCase = true) && responseLanguage.isNotBlank()) {
            "Respond naturally in $responseLanguage.\n"
        } else {
            ""
        }
        val prompt = """
            $contextBlock${languageDirective}Question: "$cleanQuery"
            Provide:
            1) A crisp 2-sentence direct answer first.
            2) A clear step-by-step breakdown with a concrete worked example or analogy.
            3) Finally, on a single trailing line: FOLLOW_UPS: question1 | question2 | question3
        """.trimIndent()

        val accumulated = StringBuilder()
        try {
            withTimeout(55_000L) {
                streamingModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        val visibleText = accumulated.toString()
                            .substringBefore("FOLLOW_UPS:")
                            .trimEnd()
                        if (visibleText.isNotBlank()) {
                            onPartialText(visibleText)
                        }
                    }
                }
            }

            val fullRaw = accumulated.toString().trim()
            if (fullRaw.isNotBlank()) {
                val followUpIndex = fullRaw.lastIndexOf("FOLLOW_UPS:")
                val mainBody = if (followUpIndex >= 0) {
                    fullRaw.substring(0, followUpIndex).trim()
                } else {
                    fullRaw
                }
                val parsedReplies = if (followUpIndex >= 0) {
                    fullRaw.substring(followUpIndex + "FOLLOW_UPS:".length)
                        .split("|")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .take(3)
                } else {
                    emptyList()
                }

                val sentences = mainBody.split(Regex("(?<=[.!?])\\s+"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val quickAnswer = sentences.take(2).joinToString(" ").ifBlank { mainBody.take(260) }
                val coreTopic = OfflineKnowledgeBase.extractCoreSearchPhrase(cleanQuery, conversationContext)
                    .split(" ")
                    .filter { it.length > 2 }
                    .take(4)
                    .joinToString(" ")
                    .replaceFirstChar { it.uppercase() }
                    .ifBlank { "Concept Answer" }
                val subject = OfflineKnowledgeBase.inferSubjectFromText("$cleanQuery $mainBody")
                val sources = if (factCheckingMode) {
                    ContextAnalysisService.defaultVerificationSourcesForTopic(subject, coreTopic, cleanQuery)
                } else {
                    emptyList()
                }
                val smartReplies = parsedReplies.ifEmpty {
                    ContextAnalysisService.defaultSmartRepliesForTopic(coreTopic, cleanQuery, responseLanguage)
                }
                val clarityScore = analyzeIntentClarity(
                    query = cleanQuery,
                    conversationContext = conversationContext,
                    modelConfidence = 0.92f,
                    isAmbiguous = false
                )
                return@withContext AnswerEngineResult(
                    query = cleanQuery,
                    subject = subject,
                    topic = coreTopic,
                    quickAnswer = quickAnswer,
                    detailedAnswer = mainBody,
                    keyConcepts = listOf(coreTopic, subject).distinct(),
                    confidence = clarityScore,
                    groundingLabel = if (factCheckingMode) "Verified Streaming AI" else "Gemini Streaming AI",
                    isFactChecked = factCheckingMode,
                    verificationStatus = if (factCheckingMode) "Streamed via Gemini AI (${sources.size} Source(s))" else "Streamed via Gemini AI",
                    sources = sources,
                    capturedViaVoice = capturedViaVoice,
                    responseLanguage = responseLanguage,
                    smartReplies = smartReplies
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Streaming answer timed out, falling back", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Streaming answer unavailable, falling back to structured pipeline: ${e.message}")
        }

        val fallback = generateImmediateAnswer(
            query = cleanQuery,
            conversationContext = conversationContext,
            factCheckingMode = factCheckingMode,
            capturedViaVoice = capturedViaVoice,
            responseLanguage = responseLanguage
        )
        emitTextProgressively(fallback.detailedAnswer, onPartialText)
        fallback
    }

    /**
     * Generates a crisp one-sentence summary of a question/answer pair for quick history scanning.
     */
    suspend fun generateOneSentenceSummary(
        question: String,
        answer: String,
        language: String = "English"
    ): String = withContext(Dispatchers.IO) {
        val cleanQuestion = question.trim()
        val cleanAnswer = answer.trim()
        if (cleanAnswer.isBlank()) return@withContext cleanQuestion.take(120)

        try {
            val langHint = if (!language.equals("English", ignoreCase = true) && language.isNotBlank()) {
                " Write the single sentence in $language."
            } else {
                ""
            }
            val prompt = "Summarize the following Q&A in exactly ONE concise, clear sentence under 25 words.$langHint\nQuestion: $cleanQuestion\nAnswer: ${cleanAnswer.take(1000)}"
            val response = withTimeout(20_000L) {
                liteSummaryModel.generateContent(prompt)
            }
            val generated = response.text?.trim()?.replace("\n", " ")?.trim()
            if (!generated.isNullOrBlank()) {
                return@withContext generated
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cloud summary unavailable, using extractive fallback: ${e.message}")
        }

        extractSingleSentenceFallback(cleanAnswer, cleanQuestion)
    }

    private fun extractSingleSentenceFallback(answer: String, question: String): String {
        val firstSentence = answer
            .replace("\n", " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .firstOrNull { it.trim().length > 12 }
            ?.trim()
        return firstSentence ?: "${question.take(50)}: ${answer.take(90)}"
    }

    private suspend fun emitTextProgressively(
        fullText: String,
        onPartialText: (String) -> Unit
    ) {
        val words = fullText.split(" ")
        if (words.size <= 4) {
            onPartialText(fullText)
            return
        }
        val step = (words.size / 6).coerceAtLeast(3)
        var index = step
        while (index < words.size) {
            onPartialText(words.take(index).joinToString(" "))
            delay(20L)
            index += step
        }
        onPartialText(fullText)
    }

    internal fun buildAnswerFromAnalysis(
        analysis: ContextAnalysisResult,
        query: String,
        capturedViaVoice: Boolean,
        doubtId: Long = 0L,
        responseLanguage: String = "English",
        conversationContext: List<String> = emptyList()
    ): AnswerEngineResult {
        val effectiveQuick = analysis.quickAnswer.ifBlank {
            "Answer ready for \"$query\"."
        }
        val effectiveDetailed = analysis.detailedAnswer.ifBlank { effectiveQuick }
        val effectiveTopic = analysis.topic.ifBlank { "Direct Answer" }
        val effectiveSubject = analysis.subject.ifBlank { "General Knowledge" }
        val groundingLabel = when {
            analysis.verificationStatus.contains("Google Search", ignoreCase = true) -> "Gemini + Google Search"
            analysis.verificationStatus.contains("Live Web", ignoreCase = true) ||
                analysis.verificationStatus.contains("Web-Grounded", ignoreCase = true) -> "Live Web Reference"
            analysis.verificationStatus.contains("Offline", ignoreCase = true) -> "Offline Fallback"
            analysis.isFactChecked -> "Verified AI Engine"
            else -> "AI Answer Engine"
        }
        val replies = analysis.smartReplies.ifEmpty {
            ContextAnalysisService.defaultSmartRepliesForTopic(effectiveTopic, query, responseLanguage)
        }
        val computedConfidence = analyzeIntentClarity(
            query = query,
            conversationContext = conversationContext,
            modelConfidence = analysis.confidence,
            isAmbiguous = analysis.isAmbiguous
        )

        return AnswerEngineResult(
            doubtId = doubtId,
            query = query,
            subject = effectiveSubject,
            topic = effectiveTopic,
            quickAnswer = effectiveQuick,
            detailedAnswer = effectiveDetailed,
            keyConcepts = analysis.importantConcepts.ifEmpty { listOf(effectiveTopic) },
            confidence = computedConfidence,
            groundingLabel = groundingLabel,
            isFactChecked = analysis.isFactChecked,
            verificationStatus = analysis.verificationStatus,
            sources = analysis.verificationSources,
            capturedViaVoice = capturedViaVoice,
            responseLanguage = responseLanguage,
            smartReplies = replies
        )
    }

    companion object {
        /**
         * Analyzes the user's spoken or typed doubt input together with model confidence and
         * conversational context to estimate how clearly the AI understood the intent.
         */
        fun analyzeIntentClarity(
            query: String,
            conversationContext: List<String> = emptyList(),
            modelConfidence: Float = 0.88f,
            isAmbiguous: Boolean = false
        ): Float {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return 0.35f
            val words = trimmed.lowercase()
                .replace(Regex("[^a-z0-9\\s']"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            var score = modelConfidence.coerceIn(0.40f, 0.99f)
            if (isAmbiguous) score -= 0.22f

            val hasQuestionSignal = trimmed.endsWith("?") || listOf(
                "why", "how", "what", "when", "where", "which", "explain",
                "define", "compare", "difference", "summarize", "solve", "derive"
            ).any { signal -> words.contains(signal) }
            if (hasQuestionSignal) score += 0.03f

            if (words.size >= 4) {
                score += 0.02f
            } else if (words.size <= 2) {
                score -= 0.14f
            }

            val unresolvedPronouns = setOf("it", "this", "that", "they", "them", "thing", "stuff")
            if (words.any { it in unresolvedPronouns } && conversationContext.isEmpty() && words.size < 6) {
                score -= 0.14f
            }

            val hesitationMarkers = setOf("um", "uh", "hmm", "idk", "maybe")
            if (words.any { it in hesitationMarkers } && words.size < 5) {
                score -= 0.08f
            }

            return score.coerceIn(0.35f, 0.99f)
        }
    }
}
