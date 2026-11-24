package com.example.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.ai.AnswerEngineResult
import com.example.data.model.VerificationSource
import com.example.util.SemanticSearchHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local bounded cache persisting up to the 10 most recent AI responses for instant
 * offline retrieval whenever Offline Mode is enabled or internet connectivity is unavailable.
 */
object OfflineResponseCache {
    private const val PREFS_NAME = "doubtsense_offline_cache"
    private const val KEY_CACHED_RESULTS = "cached_results_json"
    const val MAX_CACHED_ITEMS = 10

    fun cacheResult(context: Context, result: AnswerEngineResult) {
        val current = getCachedResults(context).toMutableList()
        current.removeAll { it.query.equals(result.query, ignoreCase = true) }
        current.add(0, result)
        val trimmed = current.take(MAX_CACHED_ITEMS)
        saveList(context, trimmed)
    }

    fun getCachedResults(context: Context): List<AnswerEngineResult> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CACHED_RESULTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(fromJson(obj))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun findBestCachedMatch(context: Context, query: String): AnswerEngineResult? {
        val cached = getCachedResults(context)
        if (cached.isEmpty()) return null
        val exact = cached.firstOrNull { it.query.equals(query.trim(), ignoreCase = true) }
        if (exact != null) return exact
        val ranked = SemanticSearchHelper.rankBySemanticAndKeyword(
            items = cached,
            query = query,
            textSelector = { "${it.query} ${it.topic} ${it.subject} ${it.quickAnswer} ${it.keyConcepts.joinToString(" ")}" }
        )
        return ranked.firstOrNull()
    }

    fun clearCache(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CACHED_RESULTS)
            .apply()
    }

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun saveList(context: Context, items: List<AnswerEngineResult>) {
        val arr = JSONArray()
        items.take(MAX_CACHED_ITEMS).forEach { arr.put(toJson(it)) }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CACHED_RESULTS, arr.toString())
            .apply()
    }

    private fun toJson(item: AnswerEngineResult): JSONObject {
        val conceptsArr = JSONArray()
        item.keyConcepts.forEach { conceptsArr.put(it) }

        val sourcesArr = JSONArray()
        item.sources.forEach { src ->
            sourcesArr.put(
                JSONObject().apply {
                    put("title", src.title)
                    put("url", src.url)
                    put("excerpt", src.excerpt)
                }
            )
        }

        val repliesArr = JSONArray()
        item.smartReplies.forEach { repliesArr.put(it) }

        return JSONObject().apply {
            put("doubtId", item.doubtId)
            put("query", item.query)
            put("subject", item.subject)
            put("topic", item.topic)
            put("quickAnswer", item.quickAnswer)
            put("detailedAnswer", item.detailedAnswer)
            put("keyConcepts", conceptsArr)
            put("confidence", item.confidence.toDouble())
            put("groundingLabel", item.groundingLabel)
            put("isFactChecked", item.isFactChecked)
            put("verificationStatus", item.verificationStatus)
            put("sources", sourcesArr)
            put("capturedViaVoice", item.capturedViaVoice)
            put("timestamp", item.timestamp)
            put("customTag", item.customTag)
            put("isStarred", item.isStarred)
            put("oneSentenceSummary", item.oneSentenceSummary)
            put("responseLanguage", item.responseLanguage)
            put("smartReplies", repliesArr)
        }
    }

    private fun fromJson(obj: JSONObject): AnswerEngineResult {
        val concepts = buildList {
            val arr = obj.optJSONArray("keyConcepts")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i)
                    if (v.isNotBlank()) add(v)
                }
            }
        }
        val sources = buildList {
            val arr = obj.optJSONArray("sources")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    add(
                        VerificationSource(
                            title = s.optString("title"),
                            url = s.optString("url"),
                            excerpt = s.optString("excerpt")
                        )
                    )
                }
            }
        }
        val smartReplies = buildList {
            val arr = obj.optJSONArray("smartReplies")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i)
                    if (v.isNotBlank()) add(v)
                }
            }
        }
        return AnswerEngineResult(
            doubtId = obj.optLong("doubtId", 0L),
            query = obj.optString("query"),
            subject = obj.optString("subject", "General Knowledge"),
            topic = obj.optString("topic", "Cached Answer"),
            quickAnswer = obj.optString("quickAnswer"),
            detailedAnswer = obj.optString("detailedAnswer"),
            keyConcepts = concepts,
            confidence = obj.optDouble("confidence", 0.85).toFloat(),
            groundingLabel = obj.optString("groundingLabel", "Offline Cache"),
            isFactChecked = obj.optBoolean("isFactChecked", false),
            verificationStatus = obj.optString("verificationStatus", "Offline Cached Response"),
            sources = sources,
            capturedViaVoice = obj.optBoolean("capturedViaVoice", false),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            customTag = obj.optString("customTag", "Education"),
            isStarred = obj.optBoolean("isStarred", false),
            oneSentenceSummary = obj.optString("oneSentenceSummary", ""),
            responseLanguage = obj.optString("responseLanguage", "English"),
            smartReplies = smartReplies
        )
    }
}
