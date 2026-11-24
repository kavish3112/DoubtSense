package com.example.data.repository

import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Aggregated summary of the day's most significant doubt moment and longitudinal progress.
 */
data class DailyInsightSummary(
    val dateLabel: String,
    val isFromToday: Boolean,
    val mostSignificantDoubt: DoubtItem?,
    val significanceScore: Float,
    val todayDoubtCount: Int,
    val todaySessionCount: Int,
    val todayAverageIntensity: Float,
    val previousAverageIntensity: Float?,
    val intensityDelta: Float?,
    val headline: String,
    val momentQuote: String,
    val resolutionTakeaway: String,
    val detailedExplanation: String,
    val progressNarrative: String,
    val subjectCategory: String
)

object DailyInsightCalculator {

    /**
     * Computes a Daily Insight summary highlighting the most significant doubt moment analyzed
     * during the current day (or falls back to the latest active day if today has no doubts yet)
     * along with longitudinal progress metrics against earlier study days.
     */
    fun computeDailyInsight(
        doubts: List<DoubtItem>,
        sessions: List<ConversationSession>,
        nowMillis: Long = System.currentTimeMillis()
    ): DailyInsightSummary? {
        if (doubts.isEmpty() && sessions.isEmpty()) return null

        val sessionById = sessions.associateBy { it.id }

        val todayDoubts = doubts.filter { isSameDay(it.timestamp, nowMillis) }
        val todaySessions = sessions.filter {
            isSameDay(it.startTime, nowMillis) || isSameDay(it.endTime, nowMillis)
        }

        val hasTodayActivity = todayDoubts.isNotEmpty() || todaySessions.isNotEmpty()

        val referenceDayMillis = when {
            hasTodayActivity -> nowMillis
            doubts.isNotEmpty() -> doubts.maxOf { it.timestamp }
            else -> sessions.maxOf { it.endTime }
        }

        val activeDayDoubts = if (hasTodayActivity) {
            todayDoubts
        } else {
            doubts.filter { isSameDay(it.timestamp, referenceDayMillis) }
        }

        val activeDaySessions = if (hasTodayActivity) {
            todaySessions
        } else {
            sessions.filter {
                isSameDay(it.startTime, referenceDayMillis) || isSameDay(it.endTime, referenceDayMillis)
            }
        }

        val scoredDoubt = activeDayDoubts
            .map { doubt -> doubt to computeSignificanceScore(doubt, sessionById[doubt.sessionId]) }
            .maxWithOrNull(compareBy<Pair<DoubtItem, Float>> { it.second }.thenBy { it.first.timestamp })

        val topDoubt = scoredDoubt?.first
        val topSession = activeDaySessions.maxByOrNull { it.peakDoubtIntensity }

        val significanceScore = scoredDoubt?.second
            ?: topSession?.peakDoubtIntensity?.coerceIn(0f, 1f)
            ?: 0f

        val activeDayAvgIntensity = when {
            activeDaySessions.isNotEmpty() ->
                activeDaySessions.map { it.averageDoubtIntensity.coerceIn(0f, 1f) }.average().toFloat()
            activeDayDoubts.isNotEmpty() ->
                activeDayDoubts.map { it.confidenceScore.coerceIn(0f, 1f) }.average().toFloat()
            else -> 0f
        }

        val priorSessions = sessions.filter {
            !isSameDay(it.startTime, referenceDayMillis) &&
                !isSameDay(it.endTime, referenceDayMillis) &&
                it.endTime < referenceDayMillis
        }
        val priorDoubts = doubts.filter {
            !isSameDay(it.timestamp, referenceDayMillis) &&
                it.timestamp < referenceDayMillis
        }

        val previousAvgIntensity: Float? = when {
            priorSessions.isNotEmpty() ->
                priorSessions.map { it.averageDoubtIntensity.coerceIn(0f, 1f) }.average().toFloat()
            priorDoubts.isNotEmpty() ->
                priorDoubts.map { it.confidenceScore.coerceIn(0f, 1f) }.average().toFloat()
            else -> null
        }

        val intensityDelta = previousAvgIntensity?.let { activeDayAvgIntensity - it }

        val formattedDate = SimpleDateFormat("MMM d", Locale.US).format(Date(referenceDayMillis))
        val dateLabel = if (hasTodayActivity) "Today • $formattedDate" else "Latest • $formattedDate"

        val subjectCategory = topDoubt?.subject?.ifBlank {
            topDoubt.topic.substringBefore(":").trim().ifBlank { "General" }
        } ?: topSession?.category?.ifBlank { "General" } ?: "General"

        val headline = topDoubt?.topic?.ifBlank { "Key Doubt Moment" }
            ?: topSession?.title?.ifBlank { "Peak Session Moment" }
            ?: "Daily Study Milestone"

        val momentQuote = when {
            topDoubt != null && topDoubt.doubtSnippet.isNotBlank() -> "\"${topDoubt.doubtSnippet}\""
            topSession != null && topSession.transcriptPreview.isNotBlank() -> "\"${topSession.transcriptPreview}\""
            else -> "No transcript snippet recorded for this session."
        }

        val resolutionTakeaway = when {
            topDoubt != null -> topDoubt.notificationSummary.ifBlank { topDoubt.quickAnswer }
            topSession != null -> topSession.summary.ifBlank { "Session reviewed across $subjectCategory." }
            else -> ""
        }

        val detailedExplanation = topDoubt?.detailedAnswer?.ifBlank { topDoubt.quickAnswer }
            ?: topSession?.summary
            ?: ""

        val progressNarrative = buildProgressNarrative(
            hasTodayActivity = hasTodayActivity,
            dateLabel = formattedDate,
            todayDoubtCount = todayDoubts.size,
            activeDayAvg = activeDayAvgIntensity,
            intensityDelta = intensityDelta,
            subjectCategory = subjectCategory
        )

        return DailyInsightSummary(
            dateLabel = dateLabel,
            isFromToday = hasTodayActivity,
            mostSignificantDoubt = topDoubt,
            significanceScore = significanceScore,
            todayDoubtCount = todayDoubts.size,
            todaySessionCount = todaySessions.size,
            todayAverageIntensity = activeDayAvgIntensity,
            previousAverageIntensity = previousAvgIntensity,
            intensityDelta = intensityDelta,
            headline = headline,
            momentQuote = momentQuote,
            resolutionTakeaway = resolutionTakeaway,
            detailedExplanation = detailedExplanation,
            progressNarrative = progressNarrative,
            subjectCategory = subjectCategory
        )
    }

    internal fun computeSignificanceScore(
        doubt: DoubtItem,
        linkedSession: ConversationSession?
    ): Float {
        val base = maxOf(
            doubt.confidenceScore.coerceIn(0f, 1f),
            linkedSession?.peakDoubtIntensity?.coerceIn(0f, 1f) ?: 0f
        )
        var bonus = 0f
        if (doubt.isAmbiguous) bonus += 0.12f
        if (doubt.isResolvedWithClarification) bonus += 0.10f
        if (doubt.isFactChecked) bonus += 0.05f
        if (doubt.doubtSnippet.length >= 35) bonus += 0.03f
        return (base + bonus).coerceIn(0f, 1f)
    }

    private fun buildProgressNarrative(
        hasTodayActivity: Boolean,
        dateLabel: String,
        todayDoubtCount: Int,
        activeDayAvg: Float,
        intensityDelta: Float?,
        subjectCategory: String
    ): String {
        if (!hasTodayActivity) {
            return "No doubts analyzed today yet — showing your latest milestone ($dateLabel) to keep tracking continuity."
        }
        val pct = (activeDayAvg * 100).toInt()
        if (intensityDelta == null) {
            return "Baseline set for today: $pct% avg intensity across $todayDoubtCount analyzed doubt(s)."
        }
        val deltaPct = kotlin.math.abs((intensityDelta * 100).toInt())
        return when {
            intensityDelta <= -0.05f ->
                "Uncertainty dropped $deltaPct% vs prior days — steady mastery progress in $subjectCategory."
            intensityDelta >= 0.05f ->
                "Doubt intensity is +$deltaPct% higher than prior days — exploring deeper concepts in $subjectCategory."
            else ->
                "Doubt intensity is steady at $pct% compared to your prior study sessions."
        }
    }

    private fun isSameDay(ts1: Long, ts2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = ts1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = ts2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}
