package com.example.util

import android.content.Context
import android.content.Intent
import com.example.data.model.DoubtItem

enum class ShareFormat {
    FULL_SUMMARY, // Includes both quick and detailed answers
    QUICK_ONLY,   // Concise version for fast messaging
    DETAILED_ONLY, // Deep explanation for notes
    STRUCTURED_TEXT_DOCUMENT, // Full structured text export
    PDF_DOCUMENT // Styled PDF document export
}

object ShareHelper {

    /**
     * Shares a single doubt answer to messaging apps, email, or note-taking apps.
     */
    fun shareDoubt(
        context: Context,
        doubt: DoubtItem,
        format: ShareFormat = ShareFormat.FULL_SUMMARY
    ) {
        when (format) {
            ShareFormat.PDF_DOCUMENT -> {
                DocumentExportHelper.exportSingleDoubt(context, doubt, ExportDocumentFormat.PDF)
                return
            }
            ShareFormat.STRUCTURED_TEXT_DOCUMENT -> {
                DocumentExportHelper.exportSingleDoubt(context, doubt, ExportDocumentFormat.STRUCTURED_TEXT)
                return
            }
            else -> {
                // Inline text sharing for standard quick messaging
                val shareText = when (format) {
                    ShareFormat.FULL_SUMMARY -> buildString {
                        appendLine("💡 DoubtSense: ${doubt.topic}")
                        appendLine("❓ Doubt: \"${doubt.doubtSnippet}\"")
                        if (doubt.intent.isNotBlank()) {
                            appendLine("🎯 Intent: ${doubt.intent}")
                        }
                        appendLine()
                        appendLine("⚡ Quick Answer:")
                        appendLine(doubt.quickAnswer)
                        appendLine()
                        appendLine("📖 Detailed Explanation:")
                        appendLine(doubt.detailedAnswer)
                        if (doubt.isAmbiguous && !doubt.clarificationQuestion.isNullOrBlank()) {
                            appendLine()
                            appendLine("🔍 Clarification Note:")
                            appendLine(doubt.clarificationQuestion)
                        }
                        appendLine()
                        append("Shared via DoubtSense AI")
                    }

                    ShareFormat.QUICK_ONLY -> buildString {
                        appendLine("💡 DoubtSense: ${doubt.topic}")
                        appendLine("❓ \"${doubt.doubtSnippet}\"")
                        appendLine()
                        appendLine("⚡ ${doubt.quickAnswer}")
                        appendLine()
                        append("Shared via DoubtSense AI")
                    }

                    ShareFormat.DETAILED_ONLY -> buildString {
                        appendLine("📚 Study Note: ${doubt.topic}")
                        appendLine("Question: \"${doubt.doubtSnippet}\"")
                        if (doubt.importantConcepts.isNotBlank()) {
                            appendLine("Key Concepts: ${doubt.importantConcepts}")
                        }
                        appendLine()
                        appendLine(doubt.detailedAnswer)
                        appendLine()
                        append("Exported from DoubtSense")
                    }

                    else -> ""
                }

                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_SUBJECT, "DoubtSense: ${doubt.topic}")
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }

                val shareIntent = Intent.createChooser(sendIntent, "Share Doubt Answer to...")
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(shareIntent)
            }
        }
    }

    /**
     * Exports full conversation turns and detected doubts as either a structured text or PDF document.
     */
    fun exportFullConversation(
        context: Context,
        conversationTurns: List<String>,
        doubts: List<DoubtItem>,
        format: ExportDocumentFormat,
        title: String = "DoubtSense Conversation Digest"
    ) {
        DocumentExportHelper.exportConversation(
            context = context,
            conversationTurns = conversationTurns,
            doubts = doubts,
            format = format,
            documentTitle = title
        )
    }

    /**
     * Exports a list of doubts as a consolidated study digest / revision sheet (defaults to structured text).
     */
    fun exportDoubtsDigest(
        context: Context,
        doubts: List<DoubtItem>,
        format: ExportDocumentFormat = ExportDocumentFormat.STRUCTURED_TEXT
    ) {
        if (doubts.isEmpty()) return
        DocumentExportHelper.exportConversation(
            context = context,
            conversationTurns = emptyList(),
            doubts = doubts,
            format = format,
            documentTitle = "DoubtSense Study Digest (${doubts.size} Doubts)"
        )
    }
}
