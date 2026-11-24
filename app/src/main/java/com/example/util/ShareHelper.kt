package com.example.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.R
import com.example.ai.AnswerEngineResult
import com.example.data.model.ConversationSession
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
        title: String = "DoubtSense Conversation Digest",
        sessions: List<ConversationSession> = emptyList()
    ) {
        DocumentExportHelper.exportConversation(
            context = context,
            conversationTurns = conversationTurns,
            doubts = doubts,
            format = format,
            documentTitle = title,
            sessions = sessions
        )
    }

    /**
     * Exports consolidated doubt history alongside complete session analysis metrics
     * to a shareable PDF or Structured Text file.
     */
    fun exportHistoryWithSessionAnalysis(
        context: Context,
        sessions: List<ConversationSession>,
        doubts: List<DoubtItem>,
        format: ExportDocumentFormat,
        title: String = "DoubtSense Doubt History & Session Analysis",
        onProgress: ((Float) -> Unit)? = null
    ) {
        if (sessions.isEmpty() && doubts.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.export_history_empty_toast),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        DocumentExportHelper.exportHistoryWithSessionAnalysis(
            context = context,
            sessions = sessions,
            doubts = doubts,
            format = format,
            documentTitle = title,
            onProgress = onProgress
        )
    }

    /**
     * Exports a stored Room ConversationSession and its linked doubts to PDF or Structured Text.
     */
    fun exportSession(
        context: Context,
        session: ConversationSession,
        doubts: List<DoubtItem>,
        format: ExportDocumentFormat
    ) {
        DocumentExportHelper.exportSession(context, session, doubts, format)
    }

    /**
     * Exports a list of doubts as a consolidated study digest / revision sheet.
     */
    fun exportDoubtsDigest(
        context: Context,
        doubts: List<DoubtItem>,
        format: ExportDocumentFormat = ExportDocumentFormat.STRUCTURED_TEXT,
        title: String = "DoubtSense Study Digest (${doubts.size} Doubts)",
        onProgress: ((Float) -> Unit)? = null
    ) {
        if (doubts.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.export_history_empty_toast),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        DocumentExportHelper.exportConversation(
            context = context,
            conversationTurns = emptyList(),
            doubts = doubts,
            format = format,
            documentTitle = title,
            onProgress = onProgress
        )
    }

    /**
     * Exports the Room-backed Answer Engine Q&A history list as a shareable Text (.txt), PDF, or CSV file.
     */
    fun exportAnswerEngineHistory(
        context: Context,
        results: List<AnswerEngineResult>,
        format: ExportDocumentFormat,
        onProgress: ((Float) -> Unit)? = null
    ) {
        val doubts = results.map { item ->
            item.toDoubtItem(
                conversationalContext = if (item.capturedViaVoice) {
                    "Captured via Voice Microphone"
                } else {
                    "Answer Engine Query"
                }
            )
        }
        exportDoubtsDigest(
            context = context,
            doubts = doubts,
            format = format,
            title = "DoubtSense Saved Q&A History (${results.size} Items)",
            onProgress = onProgress
        )
    }

    fun shareAnswerEngineResult(
        context: Context,
        result: AnswerEngineResult
    ) {
        val doubt = result.toDoubtItem(
            conversationalContext = if (result.capturedViaVoice) {
                "Captured via Voice Microphone"
            } else {
                "Answer Engine Query"
            }
        )
        shareDoubt(context, doubt, ShareFormat.FULL_SUMMARY)
    }
}
