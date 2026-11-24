package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.DoubtItem
import java.net.URLEncoder

/**
 * Allows DoubtSense to refer detected doubts and conversational context
 * to external knowledge providers such as Google Search and ChatGPT.
 */
object ExternalReferenceHelper {

    fun buildSearchQuery(doubt: DoubtItem): String {
        val parts = mutableListOf<String>()
        if (doubt.topic.isNotBlank() && !doubt.topic.equals("Concept Clarification", ignoreCase = true)) {
            parts.add(doubt.topic)
        }
        parts.add(doubt.doubtSnippet)
        return parts.joinToString(" ").trim()
    }

    fun buildChatGptPrompt(doubt: DoubtItem): String = buildString {
        appendLine("I have a doubt during a study/work conversation. Please provide a clear, accurate answer with a brief example.")
        if (doubt.subject.isNotBlank()) {
            appendLine("Subject: ${doubt.subject}")
        }
        if (doubt.topic.isNotBlank()) {
            appendLine("Topic: ${doubt.topic}")
        }
        if (doubt.conversationalContext.isNotBlank()) {
            appendLine("Conversation Context: ${doubt.conversationalContext}")
        }
        append("Question/Doubt: \"${doubt.doubtSnippet}\"")
    }

    fun openInGoogle(context: Context, doubt: DoubtItem) {
        val query = buildSearchQuery(doubt)
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.google.com/search?q=$encoded"
        launchBrowser(context, url, "Opening Google Search for ${doubt.topic}...")
    }

    fun openInChatGpt(context: Context, doubt: DoubtItem) {
        val prompt = buildChatGptPrompt(doubt)
        // Copy prompt to clipboard as backup so the user can also paste immediately if needed
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("DoubtSense ChatGPT Prompt", prompt))
        } catch (_: Exception) {
            // Safe ignore on restricted contexts
        }

        val encoded = URLEncoder.encode(prompt, "UTF-8")
        val url = "https://chatgpt.com/?q=$encoded"
        launchBrowser(context, url, "Prompt copied! Referring doubt to ChatGPT...")
    }

    fun openUrl(context: Context, url: String, sourceTitle: String = "External Source") {
        val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://www.google.com/search?q=${URLEncoder.encode(url, "UTF-8")}"
        }
        launchBrowser(context, normalizedUrl, "Opening verified source: $sourceTitle...")
    }

    private fun launchBrowser(context: Context, url: String, toastMessage: String) {
        try {
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No web browser available to open external reference.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
