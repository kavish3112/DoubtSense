package com.example.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "DocumentExportHelper"

enum class ExportDocumentFormat {
    STRUCTURED_TEXT,
    PDF,
    CSV
}

data class ExportArtifact(
    val file: File,
    val uri: Uri,
    val sendIntent: Intent,
    val chooserIntent: Intent
)

/**
 * Helper to export the full conversation transcript and AI-generated doubt answers
 * into structured text (.txt/markdown) or styled multi-page PDF documents.
 */
object DocumentExportHelper {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    private fun formatDurationLabel(startMs: Long, endMs: Long): String {
        val elapsedSec = ((endMs - startMs).coerceAtLeast(0L) / 1000L)
        val mins = elapsedSec / 60L
        val secs = elapsedSec % 60L
        return if (mins > 0L) "${mins}m ${secs}s" else "${secs}s"
    }

    /**
     * Generates a structured plain-text / Markdown document from conversation turns, session analysis, and doubt answers.
     */
    fun buildStructuredText(
        conversationTurns: List<String>,
        doubts: List<DoubtItem>,
        documentTitle: String = "DoubtSense Conversation & Knowledge Digest",
        sessions: List<ConversationSession> = emptyList()
    ): String {
        val now = dateFormat.format(Date())
        return buildString {
            appendLine("=================================================================")
            appendLine("           DOUBTSENSE - AI KNOWLEDGE & Q&A HISTORY DIGEST        ")
            appendLine("=================================================================")
            appendLine("Document: $documentTitle")
            appendLine("Generated: $now")
            if (sessions.isNotEmpty()) {
                val avgIntensity = (sessions.map { it.averageDoubtIntensity }.average() * 100).toInt()
                val peakIntensity = ((sessions.maxOfOrNull { it.peakDoubtIntensity } ?: 0f) * 100).toInt()
                appendLine("Sessions Analyzed: ${sessions.size}")
                appendLine("Overall Avg Doubt Intensity: $avgIntensity% (Peak: $peakIntensity%)")
            }
            if (conversationTurns.isNotEmpty()) {
                appendLine("Total Conversation Turns: ${conversationTurns.size}")
            }
            appendLine("Total Saved Questions / Doubts: ${doubts.size}")
            appendLine("=================================================================")
            appendLine()

            if (sessions.isNotEmpty()) {
                appendLine("📊 SESSION ANALYSIS OVERVIEW")
                appendLine("-----------------------------------------------------------------")
                val tagCounts = sessions
                    .flatMap { it.getTagList().ifEmpty { listOf("General") } }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                if (tagCounts.isNotEmpty()) {
                    appendLine("Tags Covered: ${tagCounts.joinToString(", ") { "${it.key} (${it.value})" }}")
                }
                val categoryCounts = sessions
                    .map { it.category.ifBlank { "General" } }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                if (categoryCounts.isNotEmpty()) {
                    appendLine("Subjects Covered: ${categoryCounts.joinToString(", ") { "${it.key} (${it.value})" }}")
                }
                appendLine()
                sessions.forEachIndexed { sIdx, session ->
                    val tagsLabel = session.getTagList().ifEmpty { listOf("General") }.joinToString(", ")
                    val avgPct = (session.averageDoubtIntensity * 100).toInt()
                    val peakPct = (session.peakDoubtIntensity * 100).toInt()
                    val turnsCount = session.getTranscriptTurns().size
                    val duration = formatDurationLabel(session.startTime, session.endTime)
                    appendLine("Session #${sIdx + 1}: ${session.title.ifBlank { "Conversation Session" }}")
                    appendLine("  Category: ${session.category.ifBlank { "General" }} | Tags: $tagsLabel | Duration: $duration")
                    appendLine("  Recorded: ${dateFormat.format(Date(session.startTime))}")
                    appendLine("  Analysis: Avg Doubt Intensity $avgPct% | Peak $peakPct% | Doubts: ${session.doubtCount} | Turns: $turnsCount")
                    if (session.summary.isNotBlank()) {
                        appendLine("  Session Takeaway: ${session.summary}")
                    }
                    appendLine()
                }
                appendLine("=================================================================")
                appendLine()
            }

            if (conversationTurns.isNotEmpty()) {
                // 1. Full Conversation Transcript
                appendLine("🗣️ PART 1: FULL CONVERSATION TRANSCRIPT")
                appendLine("-----------------------------------------------------------------")
                conversationTurns.forEachIndexed { index, turn ->
                    val lineNum = (index + 1).toString().padStart(2, '0')
                    appendLine("[$lineNum] $turn")
                }
                appendLine()
                appendLine("=================================================================")
                appendLine()
            }

            // 2. Detected Doubts / Saved Q&A History
            val qaHeader = if (conversationTurns.isNotEmpty()) {
                "💡 PART 2: DETECTED DOUBTS & AI-GENERATED ANSWERS"
            } else {
                "💡 SAVED QUESTIONS & AI-GENERATED ANSWERS"
            }
            appendLine(qaHeader)
            appendLine("-----------------------------------------------------------------")
            if (doubts.isEmpty()) {
                appendLine("[No doubts or Q&A entries recorded yet.]")
            } else {
                doubts.forEachIndexed { index, doubt ->
                    val doubtIndex = index + 1
                    val subjectLabel = if (doubt.subject.isNotBlank()) "[${doubt.subject}] " else ""
                    appendLine("--- DOUBT #$doubtIndex: $subjectLabel${doubt.topic} ---")
                    appendLine("Recorded: ${dateFormat.format(Date(doubt.timestamp))}")
                    appendLine("❓ Question: \"${doubt.doubtSnippet}\"")

                    if (doubt.intent.isNotBlank()) {
                        appendLine("🎯 Identified Intent: ${doubt.intent}")
                    }
                    if (doubt.doubtType.isNotBlank()) {
                        appendLine("🏷️ Doubt Type: ${doubt.doubtType}")
                    }
                    if (doubt.importantConcepts.isNotBlank()) {
                        appendLine("🔑 Key Concepts: ${doubt.importantConcepts}")
                    }
                    if (doubt.conversationalContext.isNotBlank()) {
                        appendLine("💬 Surrounding Context: ${doubt.conversationalContext}")
                    }

                    appendLine()
                    appendLine("⚡ QUICK ANSWER:")
                    appendLine(doubt.quickAnswer)

                    appendLine()
                    appendLine("📖 DETAILED EXPLANATION:")
                    appendLine(doubt.detailedAnswer)

                    if (doubt.isAmbiguous && !doubt.clarificationQuestion.isNullOrBlank()) {
                        appendLine()
                        appendLine("🔍 AMBIGUITY & CLARIFICATION:")
                        appendLine("Q: ${doubt.clarificationQuestion}")
                        if (!doubt.userClarificationResponse.isNullOrBlank()) {
                            appendLine("User Clarification: ${doubt.userClarificationResponse}")
                        }
                    }

                    if (doubt.isFactChecked) {
                        val verifiedSources = doubt.getVerificationSources()
                        appendLine()
                        appendLine("✅ FACT-CHECKING VERIFICATION: ${doubt.verificationStatus.ifBlank { "Verified via External Sources" }}")
                        verifiedSources.forEach { src ->
                            appendLine("  • ${src.title} — ${src.url}")
                            if (src.excerpt.isNotBlank()) {
                                appendLine("    Note: ${src.excerpt}")
                            }
                        }
                    }

                    if (doubt.referenceMaterials.isNotBlank()) {
                        appendLine()
                        appendLine("📚 Recommended References:")
                        appendLine(doubt.referenceMaterials)
                    }

                    if (doubt.conversationSuggestions.isNotBlank()) {
                        appendLine()
                        appendLine("💬 Suggested Questions to Continue Dialogue:")
                        appendLine(doubt.conversationSuggestions)
                    }

                    appendLine("-----------------------------------------------------------------")
                    appendLine()
                }
            }

            appendLine("=================================================================")
            appendLine("Generated by DoubtSense AI • Real-time Context Understanding Engine")
            append("=================================================================")
        }
    }

    /**
     * Generates an RFC-4180 CSV spreadsheet containing all doubt Q&A records, tags, and summaries.
     */
    fun buildCsv(
        conversationTurns: List<String>,
        doubts: List<DoubtItem>
    ): String {
        fun csvCell(value: String): String {
            val normalized = value.replace("\r\n", "\n").replace("\r", "\n")
            return "\"" + normalized.replace("\"", "\"\"") + "\""
        }

        return buildString {
            appendLine("ID,RecordedAt,Topic,Subject,CustomTag,Starred,Question,OneSentenceSummary,QuickAnswer,DetailedAnswer,Context")
            doubts.forEachIndexed { idx, doubt ->
                val rowId = if (doubt.id > 0L) doubt.id else (idx + 1).toLong()
                val recorded = dateFormat.format(Date(doubt.timestamp))
                val summary = doubt.oneSentenceSummary.ifBlank { doubt.notificationSummary }
                val contextCol = doubt.conversationalContext.ifBlank {
                    conversationTurns.joinToString(" | ")
                }
                appendLine(
                    listOf(
                        rowId.toString(),
                        csvCell(recorded),
                        csvCell(doubt.topic),
                        csvCell(doubt.subject),
                        csvCell(doubt.customTag.ifBlank { "Education" }),
                        doubt.isStarred.toString(),
                        csvCell(doubt.doubtSnippet),
                        csvCell(summary),
                        csvCell(doubt.quickAnswer),
                        csvCell(doubt.detailedAnswer),
                        csvCell(contextCol)
                    ).joinToString(",")
                )
            }
        }
    }

    /**
     * Builds the export file (.txt, .pdf, or .csv), FileProvider content URI, and ACTION_SEND + chooser Intents.
     */
    fun createExportArtifact(
        context: Context,
        conversationTurns: List<String>,
        doubts: List<DoubtItem>,
        format: ExportDocumentFormat,
        documentTitle: String = "DoubtSense Study Digest",
        onProgress: ((Float) -> Unit)? = null,
        sessions: List<ConversationSession> = emptyList()
    ): ExportArtifact {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = fileTimestampFormat.format(Date())
        onProgress?.invoke(0.2f)

        return when (format) {
            ExportDocumentFormat.STRUCTURED_TEXT -> {
                val content = buildStructuredText(conversationTurns, doubts, documentTitle, sessions)
                onProgress?.invoke(0.65f)
                val file = File(exportsDir, "doubtsense_history_$timestamp.txt")
                file.writeText(content, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "$documentTitle (${doubts.size} Items)")
                    putExtra(Intent.EXTRA_TEXT, content)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, documentTitle, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(sendIntent, "Export Saved History (Text) To...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                onProgress?.invoke(1.0f)
                ExportArtifact(file = file, uri = uri, sendIntent = sendIntent, chooserIntent = chooser)
            }

            ExportDocumentFormat.PDF -> {
                val file = File(exportsDir, "doubtsense_history_$timestamp.pdf")
                onProgress?.invoke(0.45f)
                generatePdfDocument(context, conversationTurns, doubts, documentTitle, file, sessions)
                onProgress?.invoke(0.85f)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_SUBJECT, "$documentTitle (${doubts.size} Items)")
                    putExtra(Intent.EXTRA_TEXT, "Attached: $documentTitle (${doubts.size} saved Q&A items)")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, documentTitle, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(sendIntent, "Export Saved History (PDF) To...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                onProgress?.invoke(1.0f)
                ExportArtifact(file = file, uri = uri, sendIntent = sendIntent, chooserIntent = chooser)
            }

            ExportDocumentFormat.CSV -> {
                val csvContent = buildCsv(conversationTurns, doubts)
                onProgress?.invoke(0.65f)
                val file = File(exportsDir, "doubtsense_history_$timestamp.csv")
                file.writeText(csvContent, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, "$documentTitle (${doubts.size} Items)")
                    putExtra(Intent.EXTRA_TEXT, "Attached CSV history spreadsheet (${doubts.size} items)")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, documentTitle, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(sendIntent, "Export Saved History (CSV) To...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                onProgress?.invoke(1.0f)
                ExportArtifact(file = file, uri = uri, sendIntent = sendIntent, chooserIntent = chooser)
            }
        }
    }

    fun buildSuggestedSessionFileName(
        session: ConversationSession,
        format: ExportDocumentFormat
    ): String {
        val rawTitle = session.title.ifBlank { "DoubtSense_Session" }
        val slug = rawTitle
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "session" }
        val ts = fileTimestampFormat.format(Date(session.endTime.takeIf { it > 0L } ?: System.currentTimeMillis()))
        val ext = when (format) {
            ExportDocumentFormat.STRUCTURED_TEXT -> "txt"
            ExportDocumentFormat.PDF -> "pdf"
            ExportDocumentFormat.CSV -> "csv"
        }
        return "doubtsense_${slug}_$ts.$ext"
    }

    fun writeSessionToUri(
        context: Context,
        targetUri: Uri,
        session: ConversationSession,
        doubts: List<DoubtItem>,
        format: ExportDocumentFormat,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        val baseTurns = session.getTranscriptTurns().ifEmpty {
            if (session.transcriptPreview.isNotBlank()) listOf(session.transcriptPreview) else emptyList()
        }
        val turns = if (session.summary.isNotBlank()) {
            listOf("Session Takeaway: ${session.summary}") + baseTurns
        } else {
            baseTurns
        }
        val categoryLabel = session.category.ifBlank { "General" }
        val tagLabel = session.customTag.ifBlank { "Education" }
        val title = "${session.title.ifBlank { "DoubtSense Session" }} ($categoryLabel • $tagLabel)"
        return writeExportToUri(
            context = context,
            targetUri = targetUri,
            conversationTurns = turns,
            doubts = doubts,
            format = format,
            documentTitle = title,
            onProgress = onProgress,
            sessions = listOf(session)
        )
    }

    fun writeExportToUri(
        context: Context,
        targetUri: Uri,
        conversationTurns: List<String>,
        doubts: List<DoubtItem>,
        format: ExportDocumentFormat,
        documentTitle: String = "DoubtSense Study Digest",
        onProgress: ((Float) -> Unit)? = null,
        sessions: List<ConversationSession> = emptyList()
    ): Boolean {
        return try {
            val artifact = createExportArtifact(
                context = context,
                conversationTurns = conversationTurns,
                doubts = doubts,
                format = format,
                documentTitle = documentTitle,
                onProgress = onProgress,
                sessions = sessions
            )
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                artifact.file.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: return false
            ExportReminderManager.markHistoryExported(context)
            val formatLabel = when (format) {
                ExportDocumentFormat.STRUCTURED_TEXT -> "Text (.txt)"
                ExportDocumentFormat.PDF -> "PDF (.pdf)"
                ExportDocumentFormat.CSV -> "CSV (.csv)"
            }
            Handler(Looper.getMainLooper()).post {
                runCatching {
                    Toast.makeText(
                        context,
                        "Saved $formatLabel export (${doubts.size} doubt(s))",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing export to SAF URI", e)
            false
        }
    }

    /**
     * Opens the Android system Print dialog for clean printable PDF generation or direct Save-as-PDF.
     */
    fun printPdfDocument(
        context: Context,
        conversationTurns: List<String>,
        doubts: List<DoubtItem>,
        documentTitle: String = "DoubtSense Printable Study Digest",
        onProgress: ((Float) -> Unit)? = null,
        sessions: List<ConversationSession> = emptyList()
    ): Boolean {
        return try {
            onProgress?.invoke(0.3f)
            val artifact = createExportArtifact(
                context = context,
                conversationTurns = conversationTurns,
                doubts = doubts,
                format = ExportDocumentFormat.PDF,
                documentTitle = documentTitle,
                onProgress = onProgress,
                sessions = sessions
            )
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                context.startActivity(artifact.chooserIntent)
                return true
            }
            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("DoubtSense_History.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (cancellationSignal?.isCanceled == true || destination == null) {
                        callback?.onWriteCancelled()
                        return
                    }
                    try {
                        FileInputStream(artifact.file).use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }
            printManager.print("DoubtSense_History", adapter, PrintAttributes.Builder().build())
            ExportReminderManager.markHistoryExported(context)
            runCatching {
                Toast.makeText(
                    context,
                    "Printable PDF prepared (${doubts.size} items)",
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open print PDF dialog", e)
            false
        }
    }

    /**
     * Exports the conversation and doubt answers as Structured Text (.txt), PDF, or CSV document,
     * launching the system share sheet with Android FileProvider and showing a completion Toast.
     */
    fun exportConversation(
        context: Context,
        conversationTurns: List<String>,
        doubts: List<DoubtItem>,
        format: ExportDocumentFormat,
        documentTitle: String = "DoubtSense Study Digest",
        onProgress: ((Float) -> Unit)? = null,
        sessions: List<ConversationSession> = emptyList()
    ) {
        try {
            val artifact = createExportArtifact(
                context = context,
                conversationTurns = conversationTurns,
                doubts = doubts,
                format = format,
                documentTitle = documentTitle,
                onProgress = onProgress,
                sessions = sessions
            )
            context.startActivity(artifact.chooserIntent)
            ExportReminderManager.markHistoryExported(context)
            runCatching {
                Toast.makeText(
                    context,
                    "History export ready (${format.name})",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export document", e)
            runCatching {
                Toast.makeText(
                    context,
                    "Export failed: ${e.localizedMessage ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Exports a stored Room ConversationSession and its linked doubts to Structured Text or PDF,
     * including full session analysis metrics (avg/peak intensity, duration, tags, takeaway).
     */
    fun exportSession(
        context: Context,
        session: ConversationSession,
        doubts: List<DoubtItem>,
        format: ExportDocumentFormat
    ) {
        val turns = session.getTranscriptTurns().ifEmpty {
            if (session.transcriptPreview.isNotBlank()) listOf(session.transcriptPreview) else emptyList()
        }
        val title = "${session.title} (${session.category})"
        exportConversation(
            context = context,
            conversationTurns = turns,
            doubts = doubts,
            format = format,
            documentTitle = title,
            sessions = listOf(session)
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
        documentTitle: String = "DoubtSense Doubt History & Session Analysis",
        onProgress: ((Float) -> Unit)? = null
    ) {
        val turns = sessions.flatMap { s ->
            val sTurns = s.getTranscriptTurns()
            if (sTurns.isNotEmpty()) {
                listOf("[${s.title.ifBlank { "Session" }} • ${s.category.ifBlank { "General" }}]") + sTurns
            } else if (s.transcriptPreview.isNotBlank()) {
                listOf("[${s.title.ifBlank { "Session" }}] ${s.transcriptPreview}")
            } else {
                emptyList()
            }
        }
        exportConversation(
            context = context,
            conversationTurns = turns,
            doubts = doubts,
            format = format,
            documentTitle = documentTitle,
            onProgress = onProgress,
            sessions = sessions
        )
    }

    /**
     * Exports a single doubt along with its conversational context to structured text or PDF.
     */
    fun exportSingleDoubt(
        context: Context,
        doubt: DoubtItem,
        format: ExportDocumentFormat
    ) {
        val turns = if (doubt.conversationalContext.isNotBlank()) {
            listOf(doubt.conversationalContext)
        } else {
            emptyList()
        }
        exportConversation(
            context = context,
            conversationTurns = turns,
            doubts = listOf(doubt),
            format = format,
            documentTitle = "DoubtSense: ${doubt.topic}"
        )
    }

    // =========================================================================
    // Native Multi-Page PDF Generation Engine
    // =========================================================================

    private fun generatePdfDocument(
        context: Context,
        conversationTurns: List<String>,
        doubts: List<DoubtItem>,
        documentTitle: String,
        outputFile: File,
        sessions: List<ConversationSession> = emptyList()
    ) {
        val pdfDocument = PdfDocument()
        try {

        // Standard A4 dimensions at 72 dpi (points)
        val pageWidth = 595
        val pageHeight = 842

        val marginHorizontal = 40f
        val marginTop = 45f
        val marginBottom = 45f
        val contentWidth = pageWidth - (marginHorizontal * 2)

        // Paints
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A") // Deep slate
            textSize = 18f
            isFakeBoldText = true
        }

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7") // Cyan/Blue accent
            textSize = 10f
            isFakeBoldText = true
        }

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B") // Muted slate
            textSize = 9f
        }

        val sectionHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 12f
            isFakeBoldText = true
        }

        val topicTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0369A1") // Deep ocean cyan
            textSize = 13f
            isFakeBoldText = true
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 10f
            isFakeBoldText = true
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 9.5f
        }

        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 10f
            isFakeBoldText = true
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.8f
        }

        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }

        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val quickAnswerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0FDFA") // Soft mint/cyan
            style = Paint.Style.FILL
        }

        val quickAnswerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#99F6E4")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas
        var currentY = marginTop

        fun fitSingleLine(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            val ellipsis = "…"
            val sb = StringBuilder()
            for (ch in text) {
                if (paint.measureText(sb.toString() + ch + ellipsis) > maxWidth) break
                sb.append(ch)
            }
            return if (sb.isEmpty()) ellipsis else "$sb$ellipsis"
        }

        fun drawHeaderAndFooter(c: Canvas, pNum: Int) {
            // Running header line (on pages > 1)
            if (pNum > 1) {
                val runningTitle = fitSingleLine("DoubtSense • $documentTitle", metaPaint, contentWidth)
                c.drawText(runningTitle, marginHorizontal, 30f, metaPaint)
                c.drawLine(marginHorizontal, 35f, pageWidth - marginHorizontal, 35f, linePaint)
            }
            // Running footer line on all pages
            val footerY = pageHeight - 25f
            c.drawLine(marginHorizontal, footerY - 10f, pageWidth - marginHorizontal, footerY - 10f, linePaint)
            c.drawText("Generated by DoubtSense AI • Real-Time Doubt Detection", marginHorizontal, footerY, metaPaint)
            val pageStr = "Page $pNum"
            val pWidth = metaPaint.measureText(pageStr)
            c.drawText(pageStr, pageWidth - marginHorizontal - pWidth, footerY, metaPaint)
        }

        fun ensureSpace(neededHeight: Float) {
            if (currentY + neededHeight > pageHeight - marginBottom) {
                drawHeaderAndFooter(canvas, pageNumber)
                pdfDocument.finishPage(currentPage)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                currentY = marginTop + 10f
            }
        }

        fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
            val lines = mutableListOf<String>()
            val paragraphs = text.split("\n")
            for (paragraph in paragraphs) {
                if (paragraph.isBlank()) {
                    lines.add("")
                    continue
                }
                val words = paragraph.split(" ")
                var currentLine = StringBuilder()
                for (word in words) {
                    if (paint.measureText(word) > maxWidth) {
                        if (currentLine.isNotEmpty()) {
                            lines.add(currentLine.toString())
                            currentLine = StringBuilder()
                        }
                        val chunk = StringBuilder()
                        for (ch in word) {
                            if (paint.measureText(chunk.toString() + ch) > maxWidth && chunk.isNotEmpty()) {
                                lines.add(chunk.toString())
                                chunk.clear()
                            }
                            chunk.append(ch)
                        }
                        if (chunk.isNotEmpty()) {
                            currentLine = chunk
                        }
                        continue
                    }

                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(testLine) <= maxWidth) {
                        currentLine = StringBuilder(testLine)
                    } else {
                        if (currentLine.isNotEmpty()) {
                            lines.add(currentLine.toString())
                        }
                        currentLine = StringBuilder(word)
                    }
                }
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
            }
            return lines
        }

        fun drawWrappedText(text: String, paint: Paint, x: Float, maxWidth: Float, lineSpacing: Float = 13f) {
            val lines = wrapText(text, paint, maxWidth)
            for (line in lines) {
                ensureSpace(lineSpacing)
                if (line.isNotEmpty()) {
                    canvas.drawText(line, x, currentY, paint)
                }
                currentY += lineSpacing
            }
        }

        // =====================================================================
        // Document Cover / Header Banner
        // =====================================================================
        // Brand card
        val headerBoxHeight = 58f
        val headerRect = RectF(marginHorizontal, currentY, pageWidth - marginHorizontal, currentY + headerBoxHeight)
        canvas.drawRoundRect(headerRect, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(headerRect, 8f, 8f, cardBorderPaint)

        // Accent pill on the left of header
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00B4D8")
            style = Paint.Style.FILL
        }
        val pillRect = RectF(marginHorizontal, currentY, marginHorizontal + 6f, currentY + headerBoxHeight)
        canvas.drawRoundRect(pillRect, 4f, 4f, accentPaint)

        canvas.drawText("DOUBTSENSE KNOWLEDGE DIGEST", marginHorizontal + 16f, currentY + 20f, subtitlePaint)
        val safeTitle = fitSingleLine(documentTitle, titlePaint, contentWidth - 24f)
        canvas.drawText(safeTitle, marginHorizontal + 16f, currentY + 38f, titlePaint)
        val dateStr = buildString {
            append("Exported: ${dateFormat.format(Date())} • Doubts: ${doubts.size}")
            if (sessions.isNotEmpty()) append(" • Sessions: ${sessions.size}")
            if (conversationTurns.isNotEmpty()) append(" • Turns: ${conversationTurns.size}")
        }
        canvas.drawText(dateStr, marginHorizontal + 16f, currentY + 50f, metaPaint)

        currentY += headerBoxHeight + 20f

        if (sessions.isNotEmpty()) {
            ensureSpace(45f)
            canvas.drawText("📊 SESSION ANALYSIS OVERVIEW", marginHorizontal, currentY, sectionHeaderPaint)
            currentY += 6f
            canvas.drawLine(marginHorizontal, currentY, pageWidth - marginHorizontal, currentY, linePaint)
            currentY += 14f

            val avgPct = (sessions.map { it.averageDoubtIntensity }.average() * 100).toInt()
            val peakPct = ((sessions.maxOfOrNull { it.peakDoubtIntensity } ?: 0f) * 100).toInt()
            val tagsSummary = sessions
                .flatMap { it.getTagList().ifEmpty { listOf("General") } }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .joinToString(", ") { "${it.key} (${it.value})" }
            drawWrappedText(
                "Analyzed ${sessions.size} session(s) • Overall Avg Doubt Intensity: $avgPct% • Peak: $peakPct%",
                labelPaint,
                marginHorizontal,
                contentWidth,
                lineSpacing = 12f
            )
            if (tagsSummary.isNotBlank()) {
                drawWrappedText("Tags: $tagsSummary", metaPaint, marginHorizontal, contentWidth, lineSpacing = 11f)
            }
            currentY += 6f

            sessions.forEachIndexed { sIdx, session ->
                val sAvg = (session.averageDoubtIntensity * 100).toInt()
                val sPeak = (session.peakDoubtIntensity * 100).toInt()
                val sTags = session.getTagList().ifEmpty { listOf("General") }.joinToString(", ")
                val sDuration = formatDurationLabel(session.startTime, session.endTime)
                ensureSpace(48f)
                drawWrappedText(
                    "Session #${sIdx + 1}: ${session.title.ifBlank { "Conversation Session" }} [${session.category.ifBlank { "General" }}]",
                    topicTitlePaint,
                    marginHorizontal,
                    contentWidth,
                    lineSpacing = 13f
                )
                drawWrappedText(
                    "Avg Intensity: $sAvg% | Peak: $sPeak% | Doubts: ${session.doubtCount} | Duration: $sDuration | Tags: $sTags",
                    metaPaint,
                    marginHorizontal,
                    contentWidth,
                    lineSpacing = 11f
                )
                if (session.summary.isNotBlank()) {
                    drawWrappedText(
                        "Takeaway: ${session.summary}",
                        bodyPaint,
                        marginHorizontal,
                        contentWidth,
                        lineSpacing = 12f
                    )
                }
                currentY += 6f
            }
            currentY += 6f
        }

        if (conversationTurns.isNotEmpty()) {
            // =====================================================================
            // Part 1: Full Conversation Transcript
            // =====================================================================
            ensureSpace(35f)
            canvas.drawText("🗣️ PART 1: FULL CONVERSATION TRANSCRIPT", marginHorizontal, currentY, sectionHeaderPaint)
            currentY += 6f
            canvas.drawLine(marginHorizontal, currentY, pageWidth - marginHorizontal, currentY, linePaint)
            currentY += 14f

            conversationTurns.forEachIndexed { idx, turn ->
                val turnPrefix = "[Turn ${idx + 1}] "
                val prefixWidth = labelPaint.measureText(turnPrefix)
                val lines = wrapText(turn, bodyPaint, contentWidth - prefixWidth - 8f)
                val blockHeight = (lines.size * 13f).coerceAtLeast(14f) + 6f

                ensureSpace(blockHeight)
                val rowRect = RectF(marginHorizontal, currentY - 10f, pageWidth - marginHorizontal, currentY + blockHeight - 12f)
                val rowBgPaint = if (idx % 2 == 0) cardBgPaint else Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                canvas.drawRoundRect(rowRect, 4f, 4f, rowBgPaint)

                canvas.drawText(turnPrefix, marginHorizontal + 6f, currentY, labelPaint)
                var lineY = currentY
                lines.forEach { line ->
                    canvas.drawText(line, marginHorizontal + prefixWidth + 8f, lineY, bodyPaint)
                    lineY += 13f
                }
                currentY += blockHeight
            }
            currentY += 10f
        }

        // =====================================================================
        // Part 2: Detected Doubts / Saved Q&A Answers
        // =====================================================================
        ensureSpace(35f)
        val qaPdfHeader = if (conversationTurns.isNotEmpty()) {
            "💡 PART 2: DETECTED DOUBTS & AI-GENERATED ANSWERS"
        } else {
            "💡 SAVED QUESTIONS & AI-GENERATED ANSWERS"
        }
        canvas.drawText(qaPdfHeader, marginHorizontal, currentY, sectionHeaderPaint)
        currentY += 6f
        canvas.drawLine(marginHorizontal, currentY, pageWidth - marginHorizontal, currentY, linePaint)
        currentY += 16f

        if (doubts.isEmpty()) {
            drawWrappedText("No doubts or Q&A entries recorded yet.", metaPaint, marginHorizontal, contentWidth)
        } else {
            doubts.forEachIndexed { dIdx, doubt ->
                val doubtHeader = "Doubt #${dIdx + 1}: ${if (doubt.subject.isNotBlank()) "[${doubt.subject}] " else ""}${doubt.topic}"
                ensureSpace(45f)
                canvas.drawText(doubtHeader, marginHorizontal, currentY, topicTitlePaint)
                currentY += 14f

                // Doubt Snippet in styled blockquote
                val snippetLines = wrapText("❓ \"${doubt.doubtSnippet}\"", quotePaint, contentWidth - 16f)
                val snippetHeight = (snippetLines.size * 14f) + 12f
                ensureSpace(snippetHeight)
                val quoteRect = RectF(marginHorizontal, currentY - 10f, pageWidth - marginHorizontal, currentY + snippetHeight - 12f)
                canvas.drawRoundRect(quoteRect, 6f, 6f, cardBgPaint)
                canvas.drawRoundRect(quoteRect, 6f, 6f, cardBorderPaint)
                // Left accent bar
                val quoteAccent = RectF(marginHorizontal, currentY - 10f, marginHorizontal + 4f, currentY + snippetHeight - 12f)
                val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F59E0B") }
                canvas.drawRoundRect(quoteAccent, 2f, 2f, amberPaint)

                var qY = currentY + 2f
                snippetLines.forEach { line ->
                    canvas.drawText(line, marginHorizontal + 12f, qY, quotePaint)
                    qY += 14f
                }
                currentY += snippetHeight + 6f

                // Context Tags & Intent
                if (doubt.intent.isNotBlank() || doubt.doubtType.isNotBlank() || doubt.importantConcepts.isNotBlank()) {
                    ensureSpace(28f)
                    val tagText = buildString {
                        if (doubt.doubtType.isNotBlank()) append("Type: ${doubt.doubtType}   ")
                        if (doubt.intent.isNotBlank()) append("Intent: ${doubt.intent}   ")
                        if (doubt.importantConcepts.isNotBlank()) append("Concepts: ${doubt.importantConcepts}")
                    }
                    drawWrappedText(tagText, metaPaint, marginHorizontal, contentWidth, lineSpacing = 11f)
                    currentY += 4f
                }

                // Surrounding conversation context if present
                if (doubt.conversationalContext.isNotBlank()) {
                    ensureSpace(24f)
                    drawWrappedText("💬 Conversation Flow: ${doubt.conversationalContext}", metaPaint, marginHorizontal, contentWidth, lineSpacing = 11f)
                    currentY += 4f
                }

                // Quick Answer highlighted box
                val qaLines = wrapText(doubt.quickAnswer, bodyPaint, contentWidth - 24f)
                val qaHeight = (qaLines.size * 13f) + 26f
                ensureSpace(qaHeight)
                val qaRect = RectF(marginHorizontal, currentY - 8f, pageWidth - marginHorizontal, currentY + qaHeight - 10f)
                canvas.drawRoundRect(qaRect, 6f, 6f, quickAnswerBgPaint)
                canvas.drawRoundRect(qaRect, 6f, 6f, quickAnswerBorderPaint)

                canvas.drawText("⚡ QUICK ANSWER", marginHorizontal + 12f, currentY + 4f, labelPaint)
                var qaY = currentY + 18f
                qaLines.forEach { line ->
                    canvas.drawText(line, marginHorizontal + 12f, qaY, bodyPaint)
                    qaY += 13f
                }
                currentY += qaHeight + 6f

                // Detailed Explanation
                ensureSpace(25f)
                canvas.drawText("📖 DETAILED EXPLANATION", marginHorizontal, currentY, labelPaint)
                currentY += 12f
                drawWrappedText(doubt.detailedAnswer, bodyPaint, marginHorizontal, contentWidth, lineSpacing = 13f)
                currentY += 8f

                // Clarification details if resolved
                if (doubt.isAmbiguous && !doubt.clarificationQuestion.isNullOrBlank()) {
                    ensureSpace(30f)
                    canvas.drawText("🔍 CLARIFICATION EXCHANGE", marginHorizontal, currentY, labelPaint)
                    currentY += 12f
                    drawWrappedText("Clarification Question: ${doubt.clarificationQuestion}", metaPaint, marginHorizontal, contentWidth)
                    if (!doubt.userClarificationResponse.isNullOrBlank()) {
                        drawWrappedText("User Clarification: ${doubt.userClarificationResponse}", bodyPaint, marginHorizontal, contentWidth)
                    }
                    currentY += 6f
                }

                // Fact-checking verified sources
                if (doubt.isFactChecked) {
                    val verifiedSources = doubt.getVerificationSources()
                    ensureSpace(28f)
                    canvas.drawText(
                        "✅ FACT-CHECKED VERIFICATION: ${doubt.verificationStatus.ifBlank { "Verified" }}",
                        marginHorizontal,
                        currentY,
                        labelPaint
                    )
                    currentY += 12f
                    verifiedSources.forEach { src ->
                        drawWrappedText("• ${src.title} (${src.url})", metaPaint, marginHorizontal, contentWidth, lineSpacing = 11f)
                    }
                    currentY += 4f
                }

                // Reference materials
                if (doubt.referenceMaterials.isNotBlank()) {
                    ensureSpace(25f)
                    drawWrappedText("📚 References: ${doubt.referenceMaterials}", metaPaint, marginHorizontal, contentWidth, lineSpacing = 11f)
                    currentY += 4f
                }

                // Divider after doubt
                ensureSpace(18f)
                canvas.drawLine(marginHorizontal, currentY, pageWidth - marginHorizontal, currentY, linePaint)
                currentY += 18f
            }
        }

        // Finish last page
        drawHeaderAndFooter(canvas, pageNumber)
        pdfDocument.finishPage(currentPage)

        // Write to target file
        FileOutputStream(outputFile).use { outStream ->
            pdfDocument.writeTo(outStream)
        }
        } catch (nativePdfError: Throwable) {
            Log.w(TAG, "Native PdfDocument unavailable (${nativePdfError.message}); using pure-Kotlin PDF 1.4 fallback.")
            writeFallbackPdfDocument(
                conversationTurns = conversationTurns,
                doubts = doubts,
                documentTitle = documentTitle,
                outputFile = outputFile,
                sessions = sessions
            )
        } finally {
            runCatching { pdfDocument.close() }
        }
    }

    /**
     * Spec-compliant PDF 1.4 fallback writer used when Android's native Skia PdfDocument
     * cannot allocate a native document handle (such as Robolectric JVM unit tests).
     */
    private fun writeFallbackPdfDocument(
        conversationTurns: List<String>,
        doubts: List<DoubtItem>,
        documentTitle: String,
        outputFile: File,
        sessions: List<ConversationSession> = emptyList()
    ) {
        val plainText = buildStructuredText(conversationTurns, doubts, documentTitle, sessions)
        val wrappedLines = plainText.lines().flatMap { raw ->
            if (raw.length <= 88) listOf(raw) else raw.chunked(88)
        }
        val linesPerPage = 52
        val pageChunks = wrappedLines.chunked(linesPerPage).ifEmpty { listOf(listOf(documentTitle)) }

        fun escapePdfText(input: String): String {
            val ascii = buildString(input.length) {
                for (ch in input) {
                    if (ch.code in 32..126) append(ch) else append(' ')
                }
            }
            return ascii
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
        }

        val objects = mutableListOf<ByteArray>()
        // Obj 1: Catalog, Obj 2: Pages, Obj 3: Helvetica Font
        val pageObjIds = pageChunks.indices.map { 4 + (it * 2) }
        objects.add("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".toByteArray(Charsets.US_ASCII))
        objects.add("2 0 obj\n<< /Type /Pages /Kids [${pageObjIds.joinToString(" ") { "$it 0 R" }}] /Count ${pageObjIds.size} >>\nendobj\n".toByteArray(Charsets.US_ASCII))
        objects.add("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n".toByteArray(Charsets.US_ASCII))

        pageChunks.forEachIndexed { idx, chunk ->
            val pageObjId = 4 + (idx * 2)
            val contentObjId = pageObjId + 1
            val streamContent = buildString {
                append("BT\n/F1 10 Tf\n40 800 Td\n13 TL\n")
                chunk.forEachIndexed { lineIdx, line ->
                    if (lineIdx > 0) append("T*\n")
                    append("(${escapePdfText(line)}) Tj\n")
                }
                append("ET\n")
            }
            val streamBytes = streamContent.toByteArray(Charsets.US_ASCII)
            objects.add(
                "$pageObjId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents $contentObjId 0 R >>\nendobj\n"
                    .toByteArray(Charsets.US_ASCII)
            )
            objects.add(
                ("$contentObjId 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n$streamContent\nendstream\nendobj\n")
                    .toByteArray(Charsets.US_ASCII)
            )
        }

        FileOutputStream(outputFile).use { out ->
            val header = "%PDF-1.4\n".toByteArray(Charsets.US_ASCII)
            out.write(header)
            var offset = header.size
            val offsets = mutableListOf<Int>()
            objects.forEach { objBytes ->
                offsets.add(offset)
                out.write(objBytes)
                offset += objBytes.size
            }
            val xrefStart = offset
            val xrefBuilder = StringBuilder()
            xrefBuilder.append("xref\n0 ${objects.size + 1}\n")
            xrefBuilder.append("0000000000 65535 f \n")
            offsets.forEach { objOffset ->
                xrefBuilder.append(String.format(Locale.US, "%010d 00000 n \n", objOffset))
            }
            xrefBuilder.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
            xrefBuilder.append("startxref\n$xrefStart\n%%EOF\n")
            out.write(xrefBuilder.toString().toByteArray(Charsets.US_ASCII))
        }
    }
}
