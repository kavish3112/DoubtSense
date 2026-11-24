package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.min

/**
 * Provides hybrid keyword + conceptual semantic search ranking (supporting natural language
 * queries and related terms even without exact substring matches) plus keyword highlighting.
 */
object SemanticSearchHelper {

    private val stopWords = setOf(
        "the", "is", "are", "was", "were", "a", "an", "and", "or", "of", "to", "in",
        "for", "on", "with", "that", "this", "it", "how", "what", "why", "when", "where",
        "can", "do", "does", "about", "from", "by", "my", "me", "find", "show", "past"
    )

    private val conceptClusters: List<Set<String>> = listOf(
        setOf("speed", "fast", "slow", "performance", "latency", "complexity", "big-o", "time", "runtime", "efficient", "optimize"),
        setOf("memory", "ram", "allocation", "heap", "stack", "cache", "buffer", "storage", "leak", "garbage"),
        setOf("async", "asynchronous", "concurrency", "concurrent", "thread", "threads", "coroutine", "coroutines", "parallel", "flow", "backpressure"),
        setOf("database", "sql", "sqlite", "room", "query", "table", "firestore", "cloud", "sync", "persist", "backup"),
        setOf("network", "http", "https", "tcp", "udp", "packet", "api", "rest", "latency", "socket", "dns"),
        setOf("ai", "ml", "machine", "learning", "neural", "model", "gradient", "backpropagation", "forest", "tree", "training", "overfitting"),
        setOf("biology", "cell", "cellular", "atp", "proton", "enzyme", "photosynthesis", "osmosis", "membrane", "mitochondria", "energy"),
        setOf("physics", "heat", "warm", "temperature", "thermodynamics", "entropy", "buoyancy", "force", "energy", "convection", "gravity"),
        setOf("chemistry", "chemical", "molecule", "reaction", "equilibrium", "acid", "atom", "bond", "chatelier"),
        setOf("math", "calculus", "derivative", "integral", "probability", "bayes", "matrix", "equation", "proof", "theorem"),
        setOf("sort", "sorting", "quicksort", "mergesort", "merge", "pivot", "recursion", "algorithm", "array", "hash", "hashmap")
    )

    fun extractQueryTokens(query: String): List<String> {
        return query.lowercase()
            .split(Regex("[^a-z0-9+\\-#]+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }
            .distinct()
    }

    fun expandSemanticTerms(tokens: List<String>): Set<String> {
        val expanded = LinkedHashSet<String>()
        tokens.forEach { token ->
            expanded.add(token)
            val stem = stemToken(token)
            if (stem.length >= 3) expanded.add(stem)
            conceptClusters.forEach { cluster ->
                if (cluster.any { it == token || it.startsWith(stem) || token.startsWith(it) }) {
                    expanded.addAll(cluster)
                }
            }
        }
        return expanded
    }

    fun scoreMatch(text: String, query: String): Double {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isBlank()) return 1.0
        val lowerText = text.lowercase()

        var score = 0.0
        if (lowerText.contains(cleanQuery)) {
            score += 10.0
        }

        val queryTokens = extractQueryTokens(cleanQuery)
        if (queryTokens.isEmpty()) {
            return if (lowerText.contains(cleanQuery)) 10.0 else 0.0
        }

        val textTokens = lowerText.split(Regex("[^a-z0-9+\\-#]+"))
            .filter { it.length >= 2 }
            .toSet()

        // Direct token + stem hits
        queryTokens.forEach { qt ->
            val qStem = stemToken(qt)
            when {
                lowerText.contains(qt) -> score += 3.5
                textTokens.any { stemToken(it) == qStem || it.startsWith(qStem) } -> score += 2.2
            }
        }

        // Conceptual cluster overlap
        val expandedConcepts = expandSemanticTerms(queryTokens)
        var semanticHits = 0
        expandedConcepts.forEach { concept ->
            if (concept !in queryTokens && textTokens.any { it == concept || it.contains(concept) }) {
                semanticHits++
            }
        }
        score += min(semanticHits, 6) * 1.15
        return score
    }

    fun <T> rankBySemanticAndKeyword(
        items: List<T>,
        query: String,
        textSelector: (T) -> String
    ): List<T> {
        val clean = query.trim()
        if (clean.isBlank()) return items
        return items.mapNotNull { item ->
            val s = scoreMatch(textSelector(item), clean)
            if (s > 0.0) item to s else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun buildHighlightedAnnotatedString(
        text: String,
        query: String,
        highlightBackground: Color,
        highlightForeground: Color
    ): AnnotatedString {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank() || text.isBlank()) return AnnotatedString(text)

        val queryTokens = extractQueryTokens(cleanQuery).ifEmpty { listOf(cleanQuery.lowercase()) }
        val expanded = expandSemanticTerms(queryTokens)
        val matchTerms = (listOf(cleanQuery.lowercase()) + queryTokens + expanded)
            .filter { it.length >= 2 }
            .distinct()
            .sortedByDescending { it.length }

        val ranges = mutableListOf<IntRange>()
        val lowerText = text.lowercase()
        for (term in matchTerms) {
            var startIndex = 0
            while (startIndex < lowerText.length) {
                val found = lowerText.indexOf(term, startIndex)
                if (found == -1) break
                val end = found + term.length
                val overlaps = ranges.any { !(end <= it.first || found > it.last) }
                if (!overlaps) {
                    ranges.add(found until end)
                }
                startIndex = end
            }
        }

        return buildAnnotatedString {
            append(text)
            ranges.forEach { range ->
                if (range.first in 0 until text.length && range.last < text.length) {
                    addStyle(
                        style = SpanStyle(
                            background = highlightBackground,
                            color = highlightForeground,
                            fontWeight = FontWeight.Bold
                        ),
                        start = range.first,
                        end = range.last + 1
                    )
                }
            }
        }
    }

    private fun stemToken(token: String): String {
        return when {
            token.length > 5 && token.endsWith("ing") -> token.dropLast(3)
            token.length > 4 && token.endsWith("ed") -> token.dropLast(2)
            token.length > 4 && token.endsWith("es") -> token.dropLast(2)
            token.length > 3 && token.endsWith("s") -> token.dropLast(1)
            else -> token
        }
    }
}
