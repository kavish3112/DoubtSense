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

private const val TAG = "DoubtAnalysisEngine"

data class AnalysisResult(
    val isDoubt: Boolean,
    val doubtItem: DoubtItem?,
    val rawReason: String? = null
)

class DoubtAnalysisEngine {

    // Structured JSON schema for comprehensive context understanding
    private val doubtDetectionSchema = Schema.obj(
        properties = mapOf(
            "isDoubt" to Schema.boolean(
                description = "True if the dialogue or context expresses a genuine doubt, question, confusion, or request for explanation. False for casual remarks, acknowledgements, greetings, or statements without confusion."
            ),
            "doubtSnippet" to Schema.string(
                description = "The specific doubt statement or question expressed by the speaker."
            ),
            "subject" to Schema.string(
                description = "Main overarching subject of the conversation (e.g., 'Machine Learning', 'Cellular Biology', 'Calculus', 'Operating Systems', 'Economics')."
            ),
            "topic" to Schema.string(
                description = "Specific topic being discussed deduced from conversational flow (e.g., 'Random Forest', 'ATP Synthase Mechanism', 'Integration by Parts')."
            ),
            "importantConcepts" to Schema.string(
                description = "Important concepts mentioned in previous and current conversation (e.g., 'Decision Trees, Bagging, Majority Voting')."
            ),
            "doubtType" to Schema.string(
                description = "Classification of doubt type: 'Working principle', 'Mathematical derivation', 'Conceptual difference', 'Process flow', 'Implementation syntax', or 'Definition'."
            ),
            "intent" to Schema.string(
                description = "The speaker's actual intention and what they are confused about (e.g., 'Wants to understand how individual tree predictions aggregate into the final outcome')."
            ),
            "isAmbiguous" to Schema.boolean(
                description = "True if the doubt lacks sufficient context, has multiple conflicting interpretations, or requires clarification before answering with certainty."
            ),
            "clarificationQuestion" to Schema.string(
                description = "A dynamic, relevant question to ask the user to clarify their specific confusion. Avoid generic questions; tailor strictly to subject and context."
            ),
            "suggestedOptions" to Schema.array(
                items = Schema.string(),
                description = "3 to 4 short selectable options the user can tap to answer the clarification question."
            ),
            "quickAnswer" to Schema.string(
                description = "Crisp, direct 2-3 sentence explanation directly addressing the doubt."
            ),
            "detailedAnswer" to Schema.string(
                description = "Comprehensive explanation with concepts, step-by-step breakdown, and practical example."
            ),
            "notificationSummary" to Schema.string(
                description = "Concise 1-sentence notification summary (under 15 words) giving the direct answer."
            ),
            "referenceMaterials" to Schema.string(
                description = "Brief suggested documentation, textbook topics, or official references relevant to this doubt."
            ),
            "conversationSuggestions" to Schema.string(
                description = "Suggested questions the user could ask aloud in the ongoing discussion to clarify concepts without interrupting."
            ),
            "confidenceScore" to Schema.double(
                description = "Confidence in the detection and context understanding from 0.0 to 1.0."
            )
        )
    )

    private val genConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = doubtDetectionSchema
        temperature = 0.2f
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = genConfig
        )
    }

    // Freeform model for generating adaptive personalized answers based on user clarification
    private val adaptiveModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    /**
     * Analyzes conversational flow, extracts deep context (subject, topic, concepts, doubtType, intent),
     * and sets up the interactive clarification system.
     */
    suspend fun analyzeConversation(
        conversationTurns: List<String>,
        latestUtterance: String,
        userPreferencesDepth: String = "Balanced"
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val trimmedUtterance = latestUtterance.trim()
        if (trimmedUtterance.length < 3) {
            return@withContext AnalysisResult(isDoubt = false, doubtItem = null, rawReason = "Utterance too short")
        }

        val contextBuilder = StringBuilder()
        if (conversationTurns.isNotEmpty()) {
            contextBuilder.append("--- PRECEDING CONVERSATION FLOW (Earliest to Most Recent) ---\n")
            conversationTurns.takeLast(10).forEachIndexed { index, turn ->
                contextBuilder.append("Turn [${index + 1}]: $turn\n")
            }
            contextBuilder.append("-----------------------------------------------------------\n")
        }

        val prompt = """
            You are DoubtSense's Context-Aware Doubt Understanding and Interactive Clarification Intelligence.
            
            $contextBuilder
            LATEST UTTERANCE: "$trimmedUtterance"
            TARGET EXPLANATION DEPTH: $userPreferencesDepth
            
            YOUR ANALYSIS CRITERIA:
            1. SURROUNDING CONTEXT & FLOW:
               - Do not evaluate the sentence in isolation. Look at the preceding statements to understand what is being discussed.
               - Identify:
                 * Subject: Main academic or practical discipline (e.g. Machine Learning, Biology, Distributed Systems).
                 * Topic: Specific topic under discussion (e.g. Random Forest, Photosynthesis, Two-Phase Commit).
                 * Important Concepts: Key terms and concepts mentioned in the surrounding context.
                 * Intent: The speaker's underlying intention and confusion.
                 * Doubt Type: e.g. Working principle, Formula derivation, Conceptual difference, Syntax, etc.
               - Resolve anaphora (e.g. "how do they work together?" -> "they" refers to multiple decision trees).
            
            2. DOUBT IDENTIFICATION:
               - Check if this is a genuine doubt, uncertainty, or confusion.
               - If it is not a doubt (casual statement, greeting, agreement), set isDoubt=false.
            
            3. AMBIGUITY & CLARIFICATION:
               - If context is insufficient or open to multiple interpretations, acknowledge uncertainty (isAmbiguous=true).
               - Generate a highly tailored clarificationQuestion and 3-4 suggestedOptions.
                 Example for ML: "Do you want to understand the training process, the tree voting mechanism, or feature subset randomness?"
                 Options: ["Voting Mechanism", "Bootstrapping / Bagging", "Feature Randomness"]
                 Example for Biology: "Are you asking about light-dependent reactions or the Calvin cycle?"
                 Options: ["Light Reactions", "Calvin Cycle", "Chloroplast Structure"]
            
            4. CONVERSATION SUGGESTIONS (Mode B):
               - Suggest a respectful question the user could ask aloud in the conversation (e.g., "Could we clarify whether the decision trees vote with equal weight or weighted probability?").
            
            5. DUAL-TIER ANSWERS:
               - quickAnswer: 2-3 clear sentences.
               - detailedAnswer: Full conceptual explanation with an analogy and step-by-step mechanism.
               - referenceMaterials: 1-2 reliable references or study topics.
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }

            val jsonText = response.text
            if (jsonText.isNullOrBlank()) {
                return@withContext fallbackAnalysis(trimmedUtterance, conversationTurns)
            }

            val json = JSONObject(jsonText)
            val isDoubt = json.optBoolean("isDoubt", false)
            if (!isDoubt) {
                return@withContext AnalysisResult(
                    isDoubt = false,
                    doubtItem = null,
                    rawReason = "Analyzed as non-doubt statement"
                )
            }

            val doubtSnippet = json.optString("doubtSnippet", trimmedUtterance).ifBlank { trimmedUtterance }
            val subject = json.optString("subject", "General Academic").ifBlank { "General Academic" }
            val topic = json.optString("topic", "General Concept").ifBlank { "General Concept" }
            val importantConcepts = json.optString("importantConcepts", "").ifBlank { topic }
            val doubtType = json.optString("doubtType", "Conceptual").ifBlank { "Conceptual" }
            val intent = json.optString("intent", "Understand the underlying principle").ifBlank {
                "Understand $topic"
            }
            val quickAnswer = json.optString("quickAnswer", "").ifBlank {
                "Here is a quick overview of $topic based on your conversation context."
            }
            val detailedAnswer = json.optString("detailedAnswer", quickAnswer).ifBlank { quickAnswer }
            val notificationSummary = json.optString("notificationSummary", "$topic: $quickAnswer").ifBlank {
                "$topic: $quickAnswer"
            }
            val isAmbiguous = json.optBoolean("isAmbiguous", false)
            val clarificationQuestion = json.optString("clarificationQuestion", "").ifBlank { null }

            val optionsArray = json.optJSONArray("suggestedOptions")
            val optionsList = mutableListOf<String>()
            if (optionsArray != null) {
                for (i in 0 until optionsArray.length()) {
                    optionsList.add(optionsArray.getString(i))
                }
            }
            if (optionsList.isEmpty()) {
                optionsList.addAll(listOf("Core Principle", "Practical Example", "Step-by-step Walkthrough"))
            }
            val suggestedOptionsJson = JSONArray(optionsList).toString()

            val referenceMaterials = json.optString("referenceMaterials", "Recommended textbooks and standard documentation on $topic.")
            val conversationSuggestions = json.optString("conversationSuggestions", "Could you elaborate on how $topic applies in this scenario?")

            val confidence = json.optDouble("confidenceScore", 0.90).toFloat()

            val doubtItem = DoubtItem(
                doubtSnippet = doubtSnippet,
                subject = subject,
                topic = topic,
                importantConcepts = importantConcepts,
                doubtType = doubtType,
                intent = intent,
                quickAnswer = quickAnswer,
                detailedAnswer = detailedAnswer,
                notificationSummary = notificationSummary,
                conversationalContext = conversationTurns.takeLast(6).joinToString(" | "),
                isAmbiguous = isAmbiguous,
                clarificationQuestion = clarificationQuestion,
                suggestedOptionsJson = suggestedOptionsJson,
                referenceMaterials = referenceMaterials,
                conversationSuggestions = conversationSuggestions,
                confidenceScore = confidence
            )

            return@withContext AnalysisResult(isDoubt = true, doubtItem = doubtItem)

        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Doubt analysis timed out", e)
            return@withContext fallbackAnalysis(trimmedUtterance, conversationTurns)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI analysis error: ${e.message}", e)
            return@withContext fallbackAnalysis(trimmedUtterance, conversationTurns)
        }
    }

    /**
     * Generates a personalized answer after the user provides clarification responses
     * and selects their preferred explanation style.
     */
    suspend fun generatePersonalizedAnswer(
        doubt: DoubtItem,
        userClarification: String,
        style: String = "Detailed technical explanation"
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are DoubtSense's Personalised Answer Intelligence.
            
            ORIGINAL DETECTED DOUBT: "${doubt.doubtSnippet}"
            SUBJECT: ${doubt.subject}
            TOPIC: ${doubt.topic}
            IMPORTANT CONCEPTS: ${doubt.importantConcepts}
            DOUBT TYPE: ${doubt.doubtType}
            INTENDED GOAL: ${doubt.intent}
            SURROUNDING CONVERSATION CONTEXT: "${doubt.conversationalContext}"
            
            USER'S CLARIFICATION RESPONSE:
            "$userClarification"
            
            REQUESTED EXPLANATION STYLE:
            "$style"
            (Supported styles: Simple explanation, Detailed technical explanation, Explanation with practical examples, Mathematical explanation, Step-by-step solution, Python code or implementation)
            
            INSTRUCTIONS:
            1. Directly resolve the user's specific point of confusion revealed by their clarification response.
            2. Do not give a generic topic summary; tailor the explanation directly to what they answered.
            3. Follow the requested explanation style strictly (e.g. if Python code was requested, provide clear Python implementation with comments; if Mathematical, provide formulas and variables).
            4. Conclude with a 1-sentence Key Takeaway.
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                adaptiveModel.generateContent(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                return@withContext text
            }
            return@withContext "Based on your clarification ('$userClarification'), here is the targeted explanation for ${doubt.topic}:\n\n${doubt.detailedAnswer}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate personalized answer", e)
            return@withContext "Based on your clarification ('$userClarification'):\n\n${doubt.detailedAnswer}"
        }
    }

    /**
     * Fallback heuristic analysis if offline or backend error occurs.
     */
    private fun fallbackAnalysis(utterance: String, conversationTurns: List<String>): AnalysisResult {
        val lower = utterance.lowercase()
        val questionSignals = listOf(
            "why", "how", "what", "confused", "don't understand", "dont understand",
            "explain", "difference between", "what is", "can you", "what does",
            "not clear", "doesn't make sense", "why does", "how does", "work together"
        )

        val hasSignal = questionSignals.any { lower.contains(it) } || lower.endsWith("?")
        if (!hasSignal) {
            return AnalysisResult(isDoubt = false, doubtItem = null, rawReason = "Heuristic non-doubt")
        }

        val subject = if (lower.contains("tree") || lower.contains("forest") || lower.contains("model") || lower.contains("learning")) "Machine Learning"
        else if (lower.contains("photo") || lower.contains("cell") || lower.contains("biology")) "Cellular Biology"
        else if (lower.contains("sort") || lower.contains("search") || lower.contains("algorithm")) "Computer Science"
        else if (lower.contains("gravity") || lower.contains("force") || lower.contains("physics")) "Physics"
        else "General Science"

        val topic = if (lower.contains("forest") || lower.contains("tree")) "Random Forest Ensemble"
        else if (lower.contains("photo")) "Photosynthesis"
        else if (lower.contains("sort")) "Sorting Algorithms"
        else "Concept Understanding"

        val concepts = if (topic.contains("Forest")) "Decision Trees, Bagging, Majority Voting"
        else if (topic.contains("Photosynthesis")) "Chloroplasts, ATP Synthase, Light Reactions"
        else "Core Mechanics, Variables"

        val intent = "Understand how $topic functions in context"
        val doubtType = "Working Principle"

        val options = listOf("Core Mechanism", "Practical Example", "Step-by-step Walkthrough")
        val optionsJson = JSONArray(options).toString()

        val item = DoubtItem(
            doubtSnippet = utterance,
            subject = subject,
            topic = topic,
            importantConcepts = concepts,
            doubtType = doubtType,
            intent = intent,
            quickAnswer = "In $subject, $topic resolves '$utterance' through interconnected principles.",
            detailedAnswer = "Detailed breakdown for '$utterance' in $topic:\n\n1. Concept Overview: $concepts form the structural foundation.\n2. Working Principle: When components interact, they aggregate individual states to produce the final output.\n3. Takeaway: Analyzing the surrounding dialogue ('${conversationTurns.takeLast(2).joinToString(" ")}') confirms this relationship.",
            notificationSummary = "$topic: Working principle explanation.",
            conversationalContext = conversationTurns.takeLast(4).joinToString(" | "),
            isAmbiguous = utterance.split(" ").size < 5,
            clarificationQuestion = "Would you like to focus on the core mechanism or a practical code/math example?",
            suggestedOptionsJson = optionsJson,
            referenceMaterials = "Official reference documentation on $topic.",
            conversationSuggestions = "Could you explain how $topic handles edge cases?",
            confidenceScore = 0.8f
        )

        return AnalysisResult(isDoubt = true, doubtItem = item)
    }
}
