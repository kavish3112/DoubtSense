package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.DoubtItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "DocumentExportHelper"

enum class ExportDocumentFormat {
    STRUCTURED_TEXT,
    PDF
}

/**
 * Helper to export the full conversation transcript and AI-generated doubt answers
 * into structured text (.txt/markdown) or styled multi-page PDF documents.
 */
object DocumentExportHelper {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Generates a structured plain-text / Markdown document from conversation turns and doubt answers.
     */
    fun buildStructuredText(
        conversationTurns: List<String>,
        doubts: List<DoubtItem>,
        documentTitle: String = "DoubtSense Conversation & Knowledge Digest"
    ): String {
        val now = dateFormat.format(Date())
        return buildString {
            appendLine("=================================================================")
            appendLine("           DOUBTSENSE - AI KNOWLEDGE & CONVERSATION DIGEST       ")
            appendLine("=================================================================")
            appendLine("Document: $documentTitle")
            appendLine("Generated: $now")
            appendLine("Total Conversation Turns: ${conversationTurns.size}")
            appendLine("Total Doubts Resolved: ${doubts.size}")
            appendLine("=================================================================")
            appendLine()

            // 1. Full Conversation Transcript
            appendLine("🗣️ PART 1: FULL CONVERSATION TRANSCRIPT")
            appendLine("-----------------------------------------------------------------")
            if (conversationTurns.isEmpty()) {
                appendLine("[No prior conversation turns captured in this session.]")
            } else {
                conversationTurns.forEachIndexed { index, turn ->
                    val lineNum = (index + 1).toString().padStart(2, '0')
                    appendLine("[$lineNum] $turn")
                }
            }
            appendLine()
            appendLine("=================================================================")
            appendLine()

            // 2. Detected Doubts and Explanations
            appendLine("💡 PART 2: DETECTED DOUBTS & AI-GENERATED ANSWERS")
            appendLine("-----------------------------------------------------------------")
            if (doubts.isEmpty()) {
                appendLine("[No doubts detected or recorded for this session.]")
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
     * Exports the conversation and doubt answers as either a Structured Text (.txt) or PDF document,
     * launching the system share sheet with Android FileProvider.
     */
    fun exportConversation(
        context: Context,
        conversationTurns: List<String>,
        doubts: List<DoubtItem>,
        format: ExportDocumentFormat,
        documentTitle: String = "DoubtSense Study Digest"
    ) {
        try {
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val timestamp = fileTimestampFormat.format(Date())

            when (format) {
                ExportDocumentFormat.STRUCTURED_TEXT -> {
                    val content = buildStructuredText(conversationTurns, doubts, documentTitle)
                    val file = File(exportsDir, "doubtsense_session_$timestamp.txt")
                    file.writeText(content, Charsets.UTF_8)

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )

                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "$documentTitle (${doubts.size} Doubts)")
                        putExtra(Intent.EXTRA_TEXT, content)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val chooser = Intent.createChooser(sendIntent, "Export Structured Text To...")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }

                ExportDocumentFormat.PDF -> {
                    val file = File(exportsDir, "doubtsense_session_$timestamp.pdf")
                    generatePdfDocument(context, conversationTurns, doubts, documentTitle, file)

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )

                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_SUBJECT, "$documentTitle (${doubts.size} Doubts)")
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val chooser = Intent.createChooser(sendIntent, "Export PDF Document To...")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export document", e)
            Toast.makeText(context, "Export failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
        }
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
        outputFile: File
    ) {
        val pdfDocument = PdfDocument()

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

        fun drawHeaderAndFooter(c: Canvas, pNum: Int) {
            // Running header line (on pages > 1)
            if (pNum > 1) {
                c.drawText("DoubtSense • $documentTitle", marginHorizontal, 30f, metaPaint)
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
        canvas.drawText(documentTitle, marginHorizontal + 16f, currentY + 38f, titlePaint)
        val dateStr = "Exported: ${dateFormat.format(Date())} • Doubts: ${doubts.size} • Turns: ${conversationTurns.size}"
        canvas.drawText(dateStr, marginHorizontal + 16f, currentY + 50f, metaPaint)

        currentY += headerBoxHeight + 20f

        // =====================================================================
        // Part 1: Full Conversation Transcript
        // =====================================================================
        ensureSpace(35f)
        canvas.drawText("🗣️ PART 1: FULL CONVERSATION TRANSCRIPT", marginHorizontal, currentY, sectionHeaderPaint)
        currentY += 6f
        canvas.drawLine(marginHorizontal, currentY, pageWidth - marginHorizontal, currentY, linePaint)
        currentY += 14f

        if (conversationTurns.isEmpty()) {
            drawWrappedText("No prior conversation speech turns recorded in this session.", metaPaint, marginHorizontal, contentWidth)
            currentY += 8f
        } else {
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
        // Part 2: Detected Doubts & AI-Generated Answers
        // =====================================================================
        ensureSpace(35f)
        canvas.drawText("💡 PART 2: DETECTED DOUBTS & AI-GENERATED ANSWERS", marginHorizontal, currentY, sectionHeaderPaint)
        currentY += 6f
        canvas.drawLine(marginHorizontal, currentY, pageWidth - marginHorizontal, currentY, linePaint)
        currentY += 16f

        if (doubts.isEmpty()) {
            drawWrappedText("No doubts detected or recorded for this session.", metaPaint, marginHorizontal, contentWidth)
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
        pdfDocument.close()
    }
}
