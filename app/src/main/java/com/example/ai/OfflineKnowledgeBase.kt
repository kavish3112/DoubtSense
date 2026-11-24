package com.example.ai

import android.util.Log
import com.example.data.model.VerificationSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "OfflineKnowledgeBase"

data class WebLookupResult(
    val title: String,
    val subject: String,
    val quickSummary: String,
    val detailedExtract: String,
    val sourceUrl: String,
    val sourceTitle: String
)

/**
 * Provides accurate doubt-signal classification, live Wikipedia knowledge lookup
 * (over HTTPS when Gemini cloud inference is unreachable), and a high-precision
 * curated STEM & CS fallback knowledge base so users never receive empty filler answers.
 */
object OfflineKnowledgeBase {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Determines whether an utterance is an actual question, confusion, or learning prompt
     * rather than casual chit-chat.
     */
    fun isLikelyDoubtOrQuestion(
        segment: String,
        forceTreatAsDoubt: Boolean = false
    ): Boolean {
        val lower = segment.trim().lowercase()
        if (lower.length < 3) return false
        if (forceTreatAsDoubt) return true

        // Ignore obvious social/scheduling remarks with no academic/conceptual cue
        val nonDoubtSocialPhrases = listOf(
            "grab coffee", "grab lunch", "see you later", "good morning",
            "good night", "thank you", "thanks everyone", "sounds good",
            "let's take a break", "hello everyone"
        )
        if (nonDoubtSocialPhrases.any { lower.contains(it) } && !lower.contains("?")) {
            return false
        }

        if (lower.endsWith("?")) return true

        val questionSignals = listOf(
            "why", "how", "what", "when", "where", "which", "who",
            "confused", "don't understand", "do not understand", "dont understand",
            "explain", "tell me about", "define", "meaning of", "difference between",
            "compare", "versus", "vs ", "work together", "mean", "clarify",
            "not sure", "lost here", "doesn't make sense", "does not make sense",
            "can you", "could you", "derive", "calculate", "solve", "prove",
            "example of", "why does", "how does", "how do", "what is", "what are"
        )
        return questionSignals.any { lower.contains(it) }
    }

    /**
     * Queries Wikipedia's open search & summary REST API and falls back to DuckDuckGo
     * Instant Answer API when Gemini AI is temporarily unreachable, providing real
     * web-sourced facts and a genuine HTTPS citation.
     */
    fun lookupWebEncyclopedia(
        query: String,
        surroundingContext: List<String> = emptyList()
    ): WebLookupResult? {
        val cleaned = extractCoreSearchPhrase(query, surroundingContext)
        if (cleaned.length < 2) return null

        lookupWikipedia(cleaned)?.let { return it }
        return lookupDuckDuckGoInstantAnswer(cleaned)
    }

    private fun lookupWikipedia(cleanedQuery: String): WebLookupResult? {
        return try {
            val encodedQuery = URLEncoder.encode(cleanedQuery, "UTF-8")
            val searchUrl =
                "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&srlimit=1&utf8=&format=json"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "DoubtSenseAndroid/1.0")
                .build()

            val pageTitle: String = httpClient.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = JSONObject(body)
                val searchArr = root.optJSONObject("query")?.optJSONArray("search") ?: return null
                if (searchArr.length() == 0) return null
                searchArr.getJSONObject(0).optString("title", "").trim()
            }
            if (pageTitle.isBlank()) return null

            val encodedTitle = URLEncoder.encode(pageTitle.replace(" ", "_"), "UTF-8")
            val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"
            val summaryReq = Request.Builder()
                .url(summaryUrl)
                .header("User-Agent", "DoubtSenseAndroid/1.0")
                .build()

            httpClient.newCall(summaryReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val summaryJson = JSONObject(body)
                val extract = summaryJson.optString("extract", "").trim()
                if (extract.length < 25) return null

                val description = summaryJson.optString("description", "").trim()
                val canonicalUrl = summaryJson.optJSONObject("content_urls")
                    ?.optJSONObject("desktop")
                    ?.optString("page", "")
                    ?.takeIf { it.startsWith("https://") }
                    ?: "https://en.wikipedia.org/wiki/$encodedTitle"

                val sentences = extract.split(Regex("(?<=[.!?])\\s+"))
                val quick = sentences.take(2).joinToString(" ").ifBlank { extract }
                val subject = inferSubjectFromText("$pageTitle $description $extract")

                WebLookupResult(
                    title = pageTitle,
                    subject = subject,
                    quickSummary = quick,
                    detailedExtract = if (description.isNotBlank()) {
                        "$pageTitle ($description)\n\n$extract"
                    } else {
                        extract
                    },
                    sourceUrl = canonicalUrl,
                    sourceTitle = "Wikipedia: $pageTitle"
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Wikipedia lookup unavailable: ${e.message}")
            null
        }
    }

    private fun lookupDuckDuckGoInstantAnswer(cleanedQuery: String): WebLookupResult? {
        return try {
            val encodedQuery = URLEncoder.encode(cleanedQuery, "UTF-8")
            val url =
                "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "DoubtSenseAndroid/1.0")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                parseDuckDuckGoJson(body, cleanedQuery)
            }
        } catch (e: Exception) {
            Log.d(TAG, "DuckDuckGo lookup unavailable: ${e.message}")
            null
        }
    }

    internal fun parseDuckDuckGoJson(rawJson: String, cleanedQuery: String): WebLookupResult? {
        return try {
            val json = JSONObject(rawJson)
            val heading = json.optString("Heading", "").trim().ifBlank {
                cleanedQuery.replaceFirstChar { it.uppercase() }
            }
            val directAnswer = json.optString("Answer", "").trim()
            val abstractText = json.optString("AbstractText", "").trim()
            val definition = json.optString("Definition", "").trim()
            val abstractUrl = json.optString("AbstractURL", "").trim()
            val definitionUrl = json.optString("DefinitionURL", "").trim()
            val abstractSource = json.optString("AbstractSource", "DuckDuckGo").trim().ifBlank { "DuckDuckGo" }

            var relatedText = ""
            var relatedUrl = ""
            val relatedTopics = json.optJSONArray("RelatedTopics")
            if (relatedTopics != null && relatedTopics.length() > 0) {
                for (i in 0 until relatedTopics.length()) {
                    val item = relatedTopics.optJSONObject(i) ?: continue
                    val candidateText = item.optString("Text", "").trim()
                    val candidateUrl = item.optString("FirstURL", "").trim()
                    if (candidateText.length >= 25) {
                        relatedText = candidateText
                        relatedUrl = candidateUrl
                        break
                    }
                }
            }

            val bestText = when {
                abstractText.length >= 25 -> abstractText
                directAnswer.isNotBlank() -> directAnswer
                definition.length >= 20 -> definition
                relatedText.length >= 25 -> relatedText
                else -> return null
            }

            val bestUrl = listOf(abstractUrl, definitionUrl, relatedUrl)
                .firstOrNull { it.startsWith("https://") }
                ?: "https://duckduckgo.com/?q=${URLEncoder.encode(cleanedQuery, "UTF-8")}"

            val sentences = bestText.split(Regex("(?<=[.!?])\\s+"))
            val quick = sentences.take(2).joinToString(" ").ifBlank { bestText }
            val subject = inferSubjectFromText("$heading $bestText")

            WebLookupResult(
                title = heading,
                subject = subject,
                quickSummary = quick,
                detailedExtract = bestText,
                sourceUrl = bestUrl,
                sourceTitle = "$abstractSource: $heading"
            )
        } catch (e: Exception) {
            Log.d(TAG, "Failed parsing DuckDuckGo JSON: ${e.message}")
            null
        }
    }

    internal fun extractCoreSearchPhrase(query: String, surroundingContext: List<String>): String {
        val stopPrefixes = listOf(
            "please explain", "can you explain", "could you explain", "tell me about",
            "i don't understand", "i do not understand", "what is the difference between",
            "what is a", "what is an", "what is the", "what is", "what are the", "what are",
            "how does a", "how does an", "how does the", "how does", "how do",
            "why does", "why do", "why is", "explain", "define", "clarify"
        )
        var cleaned = query.trim()
            .replace(Regex("^(person [a-z]|speaker [a-z0-9]+|student|lecturer|me)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            .replace("?", "")
            .trim()
        val lower = cleaned.lowercase()
        for (prefix in stopPrefixes) {
            if (lower.startsWith("$prefix ")) {
                cleaned = cleaned.substring(prefix.length).trim()
                break
            }
        }
        // If utterance uses pronouns ("how do they work together?"), combine with last context turn
        val isPronounHeavy = cleaned.lowercase().split(" ").any {
            it in setOf("they", "it", "this", "that", "those", "these")
        } && cleaned.split(" ").size <= 7
        if (isPronounHeavy && surroundingContext.isNotEmpty()) {
            val prev = surroundingContext.last()
                .replace(Regex("^(person [a-z]|speaker [a-z0-9]+)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            cleaned = "$prev $cleaned".take(100)
        }
        return cleaned.trim()
    }

    internal fun inferSubjectFromText(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.containsAny("algorithm", "sorting", "complexity", "database", "sql", "tcp", "http", "hash", "recursion", "tree", "graph", "programming", "compiler") -> "Computer Science"
            lower.containsAny("neural", "machine learning", "gradient", "regression", "classification", "forest", "transformer", "bayes", "backpropagation") -> "Machine Learning"
            lower.containsAny("cell", "biology", "protein", "dna", "rna", "mitosis", "osmosis", "photosynthesis", "atp", "enzyme", "evolution") -> "Biology"
            lower.containsAny("thermodynamics", "entropy", "force", "velocity", "gravity", "quantum", "energy", "momentum", "wave", "optics", "physics") -> "Physics"
            lower.containsAny("chemistry", "molecule", "equilibrium", "reaction", "acid", "atom", "electron", "bond", "stoichiometry") -> "Chemistry"
            lower.containsAny("calculus", "derivative", "integral", "matrix", "eigenvalue", "probability", "theorem", "equation", "math") -> "Mathematics"
            lower.containsAny("economics", "inflation", "supply", "demand", "gdp", "monetary", "market") -> "Economics"
            else -> "General Studies"
        }
    }

    fun matchBuiltInKnowledge(
        segment: String,
        surroundingContext: List<String>,
        factCheckingMode: Boolean
    ): ContextAnalysisResult? {
        val combined = (surroundingContext.takeLast(5) + segment).joinToString(" ").lowercase()

        val result: ContextAnalysisResult? = when {
            combined.containsAny("random forest", "decision tree", "bagging") -> ContextAnalysisResult(
                isDoubt = true,
                subject = "Machine Learning",
                topic = "Random Forest: Ensemble Voting",
                intent = "Understand how multiple decision trees combine their predictions",
                importantConcepts = listOf("Decision Trees", "Bagging", "Majority Voting", "Feature Randomness"),
                doubtType = "Working principle",
                doubtSnippet = segment,
                quickAnswer = "In a Random Forest, each decision tree is trained on a random bootstrap sample of data and random feature subsets, makes an independent prediction, and the ensemble outputs the majority vote (classification) or mean (regression).",
                detailedAnswer = "1. Bootstrap Aggregating (Bagging): Each tree trains on a random sample drawn with replacement from the dataset.\n2. Feature Randomness: At every split, a tree evaluates only a random subset of features, keeping individual trees decorrelated.\n3. Aggregation: Classification uses majority voting across all trees; regression averages their outputs.\n4. Why It Excels: Combining many diverse, low-bias trees sharply lowers variance and prevents overfitting.",
                notificationSummary = "Random Forest: Independent trees vote; majority or mean wins.",
                isAmbiguous = false,
                clarificationQuestion = "Would you like to focus on majority voting, bootstrap sampling, or feature randomness?",
                suggestedOptions = listOf("Majority Voting vs Averaging", "Bootstrap Sampling", "Feature Randomness"),
                referenceMaterials = "Breiman, L. (2001). 'Random Forests', Machine Learning; Scikit-learn Ensemble Methods.",
                conversationSuggestions = "Are the trees weighted equally or by out-of-bag accuracy?",
                confidence = 0.90f
            )

            combined.containsAny("gradient descent", "backpropagation", "loss function") -> ContextAnalysisResult(
                isDoubt = true,
                subject = "Machine Learning",
                topic = "Gradient Descent & Backpropagation",
                intent = "Understand how neural networks iteratively minimize error",
                importantConcepts = listOf("Loss Function", "Partial Derivatives", "Chain Rule", "Learning Rate"),
                doubtType = "Mathematical derivation",
                doubtSnippet = segment,
                quickAnswer = "Gradient descent updates model weights in the opposite direction of the loss function's gradient (slope), while backpropagation uses the calculus chain rule to compute those weight gradients layer by layer from output back to input.",
                detailedAnswer = "1. Forward Pass: Inputs propagate through layers to yield a prediction and compute scalar loss L.\n2. Backpropagation (Chain Rule): Starting at the output, compute ∂L/∂w for each weight recursively.\n3. Parameter Update: Move each weight downhill: w_new = w_old - η · (∂L/∂w), where η is the learning rate.\n4. Key Balance: Too large η overshoots minima; too small η converges slowly.",
                notificationSummary = "Backprop computes loss gradients via chain rule; gradient descent steps weights downhill.",
                isAmbiguous = false,
                clarificationQuestion = null,
                suggestedOptions = listOf("Chain Rule Derivation", "Learning Rate Tuning", "SGD vs Adam"),
                referenceMaterials = "Goodfellow, Bengio & Courville — Deep Learning, Chapter 6.",
                conversationSuggestions = "What learning rate schedule or optimizer is being used here?",
                confidence = 0.91f
            )

            combined.containsAny("bayes", "conditional probability", "posterior") -> ContextAnalysisResult(
                isDoubt = true,
                subject = "Mathematics",
                topic = "Bayes' Theorem",
                intent = "Understand how prior probability updates when new evidence arrives",
                importantConcepts = listOf("Prior Probability", "Likelihood", "Posterior", "Evidence"),
                doubtType = "Mathematical derivation",
                doubtSnippet = segment,
                quickAnswer = "Bayes' Theorem calculates the updated probability of a hypothesis given new evidence: P(A|B) = [P(B|A) · P(A)] / P(B), multiplying the prior belief by the likelihood of observing the evidence.",
                detailedAnswer = "Formula: P(H|E) = [P(E|H) · P(H)] / P(E)\n• P(H) — Prior: initial probability of hypothesis before seeing evidence.\n• P(E|H) — Likelihood: how probable the evidence is if H is true.\n• P(E) — Marginal Evidence: total probability of seeing E across all scenarios.\n• Example: Even a 99% accurate medical test yields many false positives if the disease prior P(H) is very rare (1 in 1,000).",
                notificationSummary = "Bayes' Theorem: P(A|B) = P(B|A)·P(A) / P(B) updates belief with evidence.",
                isAmbiguous = false,
                clarificationQuestion = null,
                suggestedOptions = listOf("Medical Test Example", "Prior vs Likelihood", "Naive Bayes Classifier"),
                referenceMaterials = "Think Bayes — Allen B. Downey; MIT 18.05 Probability.",
                conversationSuggestions = "What base rate or prior probability should we assume for this case?",
                confidence = 0.92f
            )

            combined.containsAny("atp synthase", "proton gradient", "chemiosmosis") -> ContextAnalysisResult(
                isDoubt = true,
                subject = "Biology",
                topic = "Cellular Respiration: ATP Synthase",
                intent = "Understand how a proton gradient powers ATP synthesis",
                importantConcepts = listOf("Chemiosmosis", "Proton-Motive Force", "F0-F1 Rotor", "Oxidative Phosphorylation"),
                doubtType = "Process flow",
                doubtSnippet = segment,
                quickAnswer = "ATP synthase acts like a molecular turbine: H⁺ protons flow down their electrochemical gradient through the F0 membrane channel, spinning an internal rotor that mechanically presses ADP and inorganic phosphate together to synthesize ATP.",
                detailedAnswer = "1. Gradient Creation: The electron transport chain pumps H⁺ into the intermembrane space, storing potential energy.\n2. Chemiosmosis: Protons diffuse back into the matrix through ATP synthase's F0 ring.\n3. Rotational Catalysis: Ion flow rotates the central gamma stalk inside the F1 catalytic head.\n4. Bond Formation: Shape shifts in F1 force ADP + Pi -> ATP (~3-4 protons per ATP).",
                notificationSummary = "ATP Synthase: H⁺ flow spins a molecular rotor to phosphorylate ADP into ATP.",
                isAmbiguous = false,
                clarificationQuestion = null,
                suggestedOptions = listOf("F0 Rotor Mechanics", "Proton-Motive Force", "Comparison with Chloroplasts"),
                referenceMaterials = "Campbell Biology: Cellular Respiration & Oxidative Phosphorylation.",
                conversationSuggestions = "How many protons are needed per ATP molecule in mitochondria?",
                confidence = 0.92f
            )

            combined.containsAny("quicksort", "merge sort") -> ContextAnalysisResult(
                isDoubt = true,
                subject = "Computer Science",
                topic = "Algorithms: Merge Sort vs Quicksort",
                intent = "Understand why Merge Sort stays O(n log n) while Quicksort can hit O(n²)",
                importantConcepts = listOf("Divide and Conquer", "Pivot Selection", "Recursion Tree Depth", "Worst-Case Complexity"),
                doubtType = "Conceptual difference",
                doubtSnippet = segment,
                quickAnswer = "Merge Sort always divides arrays evenly at n/2, guaranteeing log₂(n) recursion depth and O(n log n) time; Quicksort partitions around a pivot, and picking extreme elements creates skewed 0 vs (n-1) splits of depth n, yielding O(n²).",
                detailedAnswer = "1. Merge Sort: Strict midpoint splits keep tree depth at log₂(n) with O(n) merge work per level -> Θ(n log n) always, using O(n) extra space.\n2. Quicksort: Partitions in-place (O(log n) stack space) around a pivot. Naive first/last pivot on sorted data creates n recursive levels doing n+(n-1)+...+1 = O(n²) work.\n3. Practical Fix: Randomized pivot or Median-of-Three keeps expected runtime O(n log n).",
                notificationSummary = "Merge Sort always splits evenly O(n log n); skewed pivots make Quicksort O(n²).",
                isAmbiguous = false,
                clarificationQuestion = null,
                suggestedOptions = listOf("Worst-Case Trace", "Randomized Pivot Fix", "Space Complexity"),
                referenceMaterials = "CLRS Introduction to Algorithms: Chapters 2 & 7.",
                conversationSuggestions = "Are we assuming a deterministic first-element pivot or randomized pivoting?",
                confidence = 0.93f
            )

            combined.containsAny("hash table", "hash map", "hashmap", "collision") -> ContextAnalysisResult(
                isDoubt = true,
                subject = "Computer Science",
                topic = "Hash Tables & O(1) Lookup",
                intent = "Understand how hash tables achieve constant-time lookup and handle collisions",
                importantConcepts = listOf("Hash Function", "Bucket Array", "Collision Resolution", "Load Factor"),
                doubtType = "Working principle",
                doubtSnippet = segment,
                quickAnswer = "A hash map passes a key through a deterministic hash function to compute an array index directly, giving O(1) average lookup; collisions (two keys mapping to the same bucket) are resolved via chaining (linked lists/trees) or open addressing.",
                detailedAnswer = "1. Hashing: hash(key) % capacity maps arbitrary keys straight to an array slot in constant time.\n2. Collisions: When two keys share an index, Separate Chaining stores entries in a bucket list/balanced tree, while Open Addressing probes nearby slots.\n3. Resizing: Keeping load factor (entries/buckets) below ~0.75 preserves O(1) amortized operations; worst case degrades to O(n) only under pathological hash collisions.",
                notificationSummary = "Hash Maps compute array indices via hash(key) for O(1) average lookup.",
                isAmbiguous = false,
                clarificationQuestion = null,
                suggestedOptions = listOf("Chaining vs Open Addressing", "Load Factor & Resizing", "Worst-Case O(n)"),
                referenceMaterials = "CLRS Chapter 11: Hash Tables.",
                conversationSuggestions = "Does this implementation use separate chaining or linear probing?",
                confidence = 0.91f
            )

            combined.containsAny("tcp", "udp") -> ContextAnalysisResult(
                isDoubt = true,
                subject = "Computer Science",
                topic = "Networking: TCP vs UDP",
                intent = "Compare reliable connection-oriented TCP with low-latency UDP",
                importantConcepts = listOf("3-Way Handshake", "Flow Control", "Packet Loss", "Datagrams"),
                doubtType = "Conceptual difference",
                doubtSnippet = segment,
                quickAnswer = "TCP is connection-oriented and guarantees ordered, lossless delivery via a 3-way handshake, ACKs, and retransmissions; UDP is connectionless and sends independent packets with minimal overhead for low latency, without delivery guarantees.",
                detailedAnswer = "1. TCP (Transmission Control Protocol): Establishes connections (SYN, SYN-ACK, ACK), numbers bytes, retransmits dropped packets, and throttles speed via congestion control. Ideal for HTTP, file transfers, and databases.\n2. UDP (User Datagram Protocol): Fires datagrams immediately with an 8-byte header and no handshake or retransmission. Ideal for live video/voice, gaming, and DNS where speed beats retrying old frames.",
                notificationSummary = "TCP guarantees ordered reliable delivery; UDP prioritizes raw speed over reliability.",
                isAmbiguous = false,
                clarificationQuestion = null,
                suggestedOptions = listOf("3-Way Handshake", "Congestion Control", "When to Use UDP"),
                referenceMaterials = "Kurose & Ross — Computer Networking: Transport Layer.",
                conversationSuggestions = "Is strict packet ordering or sub-50ms latency more critical for this workload?",
                confidence = 0.92f
            )

            combined.containsAny("warm air", "convection", "buoyancy") -> ContextAnalysisResult(
                isDoubt = true,
                subject = "Physics",
                topic = "Thermodynamics: Convection & Buoyancy",
                intent = "Understand why warm fluids/gases rise above cooler ones",
                importantConcepts = listOf("Thermal Expansion", "Density", "Buoyant Force", "Convection"),
                doubtType = "Working principle",
                doubtSnippet = segment,
                quickAnswer = "Heating air increases molecular kinetic energy so molecules spread farther apart (lower density at equal pressure); gravity pulls denser cool air downward beneath it, generating an upward buoyant force that lifts the warm air.",
                detailedAnswer = "1. Thermal Expansion: Higher temperature increases molecular speed and spacing (V ∝ T at constant pressure).\n2. Lower Density: ρ = m/V decreases when volume expands.\n3. Buoyant Lift: Denser surrounding cool air sinks under the warm pocket, pushing it upward (Archimedes' principle) and establishing convection currents.",
                notificationSummary = "Physics: Warm air expands, drops in density, and rises via buoyancy.",
                isAmbiguous = false,
                clarificationQuestion = null,
                suggestedOptions = listOf("Ideal Gas Law", "Atmospheric Convection", "Buoyancy Equation"),
                referenceMaterials = "Halliday & Resnick: Fluid Mechanics & Thermodynamics.",
                conversationSuggestions = "How does adiabatic cooling affect the rising parcel at higher altitude?",
                confidence = 0.90f
            )

            combined.containsAny("entropy", "second law") -> ContextAnalysisResult(
                isDoubt = true,
                subject = "Physics",
                topic = "Thermodynamics: Entropy & Second Law",
                intent = "Understand what entropy measures and why it increases in isolated systems",
                importantConcepts = listOf("Microstates", "Boltzmann Formula", "Irreversibility", "Thermal Equilibrium"),
                doubtType = "Working principle",
                doubtSnippet = segment,
                quickAnswer = "Entropy (S = k_B ln W) measures the number of microscopic arrangements (microstates) corresponding to a macroscopic state; isolated systems naturally evolve toward equilibrium simply because disordered equilibrium macrostates have overwhelmingly more microstates.",
                detailedAnswer = "1. Statistical Meaning: W is the count of ways particles and energy can be arranged. Dispersed energy has vastly larger W than concentrated energy.\n2. Second Law: Total entropy of an isolated system never decreases (ΔS ≥ 0) because transitioning to equilibrium is statistically near-certain.\n3. Everyday Example: Heat flows from a hot cup to a cool room—never spontaneously the reverse—because shared thermal energy maximizes accessible microstates.",
                notificationSummary = "Entropy counts accessible microstates; systems evolve toward higher-probability equilibrium.",
                isAmbiguous = false,
                clarificationQuestion = null,
                suggestedOptions = listOf("Boltzmann Equation", "Heat Flow Example", "Entropy vs Disorder"),
                referenceMaterials = "Schroeder — An Introduction to Thermal Physics.",
                conversationSuggestions = "Are we defining entropy thermodynamically (dQ/T) or statistically (k ln W)?",
                confidence = 0.92f
            )

            combined.containsAny("photosynthesis", "chloroplast", "calvin cycle") -> ContextAnalysisResult(
                isDoubt = true,
                subject = "Biology",
                topic = "Photosynthesis: Light & Calvin Cycles",
                intent = "Understand how plants turn sunlight, water, and CO₂ into glucose",
                importantConcepts = listOf("Chlorophyll", "Light-Dependent Reactions", "Calvin Cycle", "RuBisCO"),
                doubtType = "Process flow",
                doubtSnippet = segment,
                quickAnswer = "Photosynthesis has two coupled stages: light-dependent reactions in thylakoids split water (releasing O₂) to charge ATP and NADPH, and the Calvin cycle in the stroma spends that ATP/NADPH to fix CO₂ into glucose.",
                detailedAnswer = "1. Light-Dependent Reactions (Thylakoid): Photons excite chlorophyll electrons, splitting H₂O into O₂ + H⁺ and generating ATP + NADPH.\n2. Calvin Cycle (Stroma): RuBisCO attaches CO₂ to RuBP, and ATP + NADPH reduce the intermediates into G3P sugars, which build glucose.\n3. Balanced Equation: 6 CO₂ + 6 H₂O + light -> C₆H₁₂O₆ + 6 O₂.",
                notificationSummary = "Photosynthesis: Light reactions charge ATP/NADPH; Calvin cycle fixes CO₂ into glucose.",
                isAmbiguous = false,
                clarificationQuestion = null,
                suggestedOptions = listOf("Light-Dependent Reactions", "Calvin Cycle", "Chemical Equation"),
                referenceMaterials = "Campbell Biology: Photosynthesis.",
                conversationSuggestions = "Which step splits water to release oxygen gas?",
                confidence = 0.90f
            )

            combined.containsAny("osmosis", "semipermeable", "tonicity") -> ContextAnalysisResult(
                isDoubt = true,
                subject = "Biology",
                topic = "Cell Transport: Osmosis",
                intent = "Understand the direction of water movement across cell membranes",
                importantConcepts = listOf("Semipermeable Membrane", "Solute Concentration", "Hypotonic vs Hypertonic"),
                doubtType = "Working principle",
                doubtSnippet = segment,
                quickAnswer = "Osmosis is the passive net diffusion of water molecules across a semipermeable membrane from an area of lower solute concentration (higher free water potential) to an area of higher solute concentration until osmotic pressure balances.",
                detailedAnswer = "1. Why Water Moves: Dissolved solutes bind water molecules, lowering free water concentration. More water molecules hit the membrane from the dilute side, creating net flow toward the concentrated side.\n2. Tonicity Effects: In a hypotonic solution, cells take in water and swell; in a hypertonic solution, water leaves and cells shrink; isotonic solutions yield zero net volume change.",
                notificationSummary = "Osmosis moves water passively toward higher solute concentration across membranes.",
                isAmbiguous = false,
                clarificationQuestion = null,
                suggestedOptions = listOf("Hypotonic vs Hypertonic", "Aquaporins", "Osmotic Pressure"),
                referenceMaterials = "Campbell Biology: Membrane Structure and Function.",
                conversationSuggestions = "Is the surrounding solution hypotonic or hypertonic relative to the cell?",
                confidence = 0.90f
            )

            combined.containsAny("equilibrium", "le chatelier", "balance") && combined.containsAny("react", "chem", "concentration") -> ContextAnalysisResult(
                isDoubt = true,
                subject = "Chemistry",
                topic = "Chemical Equilibrium & Le Chatelier's Principle",
                intent = "Understand how reversible chemical systems respond to stress",
                importantConcepts = listOf("Le Chatelier's Principle", "Dynamic Equilibrium", "Reaction Quotient Q"),
                doubtType = "Conceptual difference",
                doubtSnippet = segment,
                quickAnswer = "At chemical equilibrium, forward and reverse reaction rates are equal; if concentration, pressure, or temperature is disturbed, Le Chatelier's Principle states the equilibrium shifts in the direction that counteracts the disturbance.",
                detailedAnswer = "1. Dynamic Equilibrium: Both forward and reverse reactions keep occurring at equal speeds, keeping concentrations constant.\n2. Disturbances: Adding reactant shifts right (makes more product); increasing pressure favors fewer gas moles; raising temperature favors the endothermic direction.\n3. Distinction: Note that stoichiometry balances atom counts in equations, whereas equilibrium governs reaction extent.",
                notificationSummary = "Le Chatelier's Principle: Equilibrium shifts to oppose added concentration, heat, or pressure.",
                isAmbiguous = combined.contains("balance") && !combined.contains("chatelier"),
                clarificationQuestion = "Are you asking about shifting chemical equilibrium (Le Chatelier) or balancing stoichiometric equations?",
                suggestedOptions = listOf("Equilibrium Shift", "Stoichiometric Balancing", "Rate Constants"),
                referenceMaterials = "Zumdahl Chemistry: Chemical Equilibrium.",
                conversationSuggestions = "Are we altering reactant concentration, temperature, or gas pressure?",
                confidence = 0.85f
            )

            else -> null
        }

        return if (result != null && factCheckingMode) {
            val sources = ContextAnalysisService.defaultVerificationSourcesForTopic(
                result.subject,
                result.topic,
                segment
            )
            result.copy(
                isFactChecked = true,
                verificationStatus = "Verified Reference (${sources.size} Sources)",
                verificationSources = sources,
                referenceMaterials = sources.joinToString("\n") { "• ${it.title}: ${it.url}" }
            )
        } else {
            result
        }
    }

    fun WebLookupResult.toContextAnalysisResult(
        originalSegment: String,
        factCheckingMode: Boolean
    ): ContextAnalysisResult {
        val sources = listOf(
            VerificationSource(
                title = sourceTitle,
                url = sourceUrl,
                excerpt = quickSummary
            )
        )
        return ContextAnalysisResult(
            isDoubt = true,
            subject = subject,
            topic = title,
            intent = "Understand core definition, mechanism, and context for $title",
            importantConcepts = listOf(title, subject),
            doubtType = "Verified Reference",
            doubtSnippet = originalSegment,
            quickAnswer = quickSummary,
            detailedAnswer = detailedExtract,
            notificationSummary = "$title: ${quickSummary.take(90)}",
            isAmbiguous = false,
            clarificationQuestion = null,
            suggestedOptions = listOf("Core Principle", "Step-by-step Walkthrough", "Practical Example"),
            referenceMaterials = "$sourceTitle ($sourceUrl)",
            conversationSuggestions = "Could we walk through a concrete example of $title?",
            confidence = 0.88f,
            isFactChecked = true,
            verificationStatus = if (factCheckingMode) {
                "Verified via Live Web Reference"
            } else {
                "Web-Grounded Reference"
            },
            verificationSources = sources
        )
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
