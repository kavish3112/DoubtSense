package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.example.R

/**
 * Copies AI-generated responses safely to the device's system clipboard
 * and surfaces immediate user feedback.
 */
object ClipboardHelper {

    fun formatAnswerForClipboard(
        quickAnswer: String,
        detailedAnswer: String = "",
        includeDetailed: Boolean = true
    ): String {
        val cleanQuick = quickAnswer.trim()
        val cleanDetailed = detailedAnswer.trim()
        return when {
            !includeDetailed && cleanQuick.isNotBlank() -> cleanQuick
            cleanDetailed.isBlank() || cleanDetailed == cleanQuick -> cleanQuick
            cleanQuick.isBlank() -> cleanDetailed
            else -> "$cleanQuick\n\n$cleanDetailed"
        }
    }

    fun copyAnswer(
        context: Context,
        text: String,
        label: String = "DoubtSense Answer",
        showToast: Boolean = true
    ): Boolean {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(label, cleanText))
        if (showToast) {
            try {
                Toast.makeText(
                    context,
                    context.getString(R.string.answer_copied_toast),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
                // Ignore Toast exceptions in headless test environments
            }
        }
        return true
    }
}
