package com.example.data.repository

import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Single day bucket inside the 30-day doubt frequency timeline.
 */
data class DailyDoubtFrequencyPoint(
    val dayIndex: Int, // 0 = 29 days ago, 29 = today
    val dayStartMillis: Long,
    val shortDateLabel: String, // e.g., "Nov 12"
    val fullDateLabel: String, // e.g., "Nov 12, 2026"
    val doubtCount: Int,
    val sessionCount: Int,
    val averageIntensity: Float,
    val rollingSevenDayAverage: Float,
    val topSubject: String,
    val isToday: Boolean
)

/**
 * Aggregated 30-day frequency analytics summary for the Dashboard graph.
 */
data class ThirtyDayFrequencySummary(
    val points: List<DailyDoubtFrequencyPoint>,
    val totalDoubts30d: Int,
    val activeDaysCount: Int,
    val dailyAverage: Float,
    val peakDay: DailyDoubtFrequencyPoint?,
    val recentSevenDayTotal: Int,
    val priorSevenDayTotal: Int,
    val weekOverWeekDelta: Int,
    val availableCategories: List<String>,
    val maxDailyCount: Int
)

object DoubtFrequencyCalculator {

    const val WINDOW_DAYS = 30

    /**
     * Builds a continuous 30-day daily frequency series ending on [nowMillis]'s local calendar day.
     * Optionally filters by [subjectFilter] (case-insensitive) to focus on a single study category.
     */
    fun compute30DayFrequency(
        doubts: List<DoubtItem>,
        sessions: List<ConversationSession>,
        subjectFilter: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): ThirtyDayFrequencySummary {
        val normalizedFilter = subjectFilter?.trim()?.takeIf { it.isNotEmpty() && !it.equals("All", ignoreCase = true) }

        val allCategories = buildSet {
            doubts.forEach { d ->
                val cat = extractSubject(d)
                if (cat.isNotBlank()) add(cat)
            }
            sessions.forEach { s ->
                val cat = s.category.trim()
                if (cat.isNotBlank()) add(cat)
            }
        }.sorted()

        val filteredDoubts = if (normalizedFilter == null) {
            doubts
        } else {
            doubts.filter { extractSubject(it).equals(normalizedFilter, ignoreCase = true) }
        }

        val filteredSessions = if (normalizedFilter == null) {
            sessions
        } else {
            sessions.filter { it.category.trim().equals(normalizedFilter, ignoreCase = true) }
        }

        val todayMidnight = startOfDayMillis(nowMillis)
        val dayBoundaries = (0 until WINDOW_DAYS).map { idx ->
            val daysAgo = (WINDOW_DAYS - 1) - idx
            val cal = Calendar.getInstance().apply {
                timeInMillis = todayMidnight
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
            cal.timeInMillis
        }

        val endOfTodayMillis = Calendar.getInstance().apply {
            timeInMillis = todayMidnight
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val doubtsByDay = Array(WINDOW_DAYS) { mutableListOf<DoubtItem>() }
        for (doubt in filteredDoubts) {
            val dayIdx = findDayBucketIndex(doubt.timestamp, dayBoundaries, endOfTodayMillis)
            if (dayIdx != -1) {
                doubtsByDay[dayIdx].add(doubt)
            }
        }

        val sessionsByDay = Array(WINDOW_DAYS) { mutableListOf<ConversationSession>() }
        for (session in filteredSessions) {
            val ts = session.startTime.takeIf { it > 0L } ?: session.endTime
            val dayIdx = findDayBucketIndex(ts, dayBoundaries, endOfTodayMillis)
            if (dayIdx != -1) {
                sessionsByDay[dayIdx].add(session)
            }
        }

        val dateFmt = SimpleDateFormat("MMM d", Locale.US)
        val fullDateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)

        // First pass: raw daily counts & metrics
        val rawCounts = IntArray(WINDOW_DAYS)
        val dayIntensities = FloatArray(WINDOW_DAYS)
        val dayTopSubjects = Array(WINDOW_DAYS) { "None" }

        for (i in 0 until WINDOW_DAYS) {
            val dList = doubtsByDay[i]
            val sList = sessionsByDay[i]

            // Use recorded DoubtItem count when present; if a session logged doubtCount without linked DoubtItem rows,
            // fall back to session.doubtCount so no analyzed session is undercounted.
            val linkedSessionIds = dList.map { it.sessionId }.toSet()
            val unlinkedSessionDoubts = sList
                .filter { it.id == 0L || !linkedSessionIds.contains(it.id) }
                .sumOf { it.doubtCount.coerceAtLeast(0) }

            rawCounts[i] = dList.size + unlinkedSessionDoubts

            dayIntensities[i] = when {
                sList.isNotEmpty() -> sList.map { it.averageDoubtIntensity.coerceIn(0f, 1f) }.average().toFloat()
                dList.isNotEmpty() -> dList.map { it.confidenceScore.coerceIn(0f, 1f) }.average().toFloat()
                else -> 0f
            }

            dayTopSubjects[i] = when {
                dList.isNotEmpty() -> dList.groupingBy { extractSubject(it) }.eachCount().maxByOrNull { it.value }?.key ?: "General"
                sList.isNotEmpty() -> sList.groupingBy { it.category.ifBlank { "General" } }.eachCount().maxByOrNull { it.value }?.key ?: "General"
                else -> "None"
            }
        }

        // Second pass: assemble points with 7-day rolling average
        val points = (0 until WINDOW_DAYS).map { i ->
            val windowStart = (i - 6).coerceAtLeast(0)
            val windowLen = (i - windowStart) + 1
            var sum = 0
            for (j in windowStart..i) {
                sum += rawCounts[j]
            }
            val rollingAvg = sum.toFloat() / windowLen.toFloat()

            val dayDate = Date(dayBoundaries[i])
            DailyDoubtFrequencyPoint(
                dayIndex = i,
                dayStartMillis = dayBoundaries[i],
                shortDateLabel = dateFmt.format(dayDate),
                fullDateLabel = fullDateFmt.format(dayDate),
                doubtCount = rawCounts[i],
                sessionCount = sessionsByDay[i].size,
                averageIntensity = dayIntensities[i],
                rollingSevenDayAverage = rollingAvg,
                topSubject = dayTopSubjects[i],
                isToday = (i == WINDOW_DAYS - 1)
            )
        }

        val total30d = points.sumOf { it.doubtCount }
        val activeDays = points.count { it.doubtCount > 0 }
        val dailyAvg = total30d.toFloat() / WINDOW_DAYS.toFloat()
        val peakDay = points.filter { it.doubtCount > 0 }
            .maxWithOrNull(compareBy<DailyDoubtFrequencyPoint> { it.doubtCount }.thenBy { it.dayIndex })
        val recent7 = points.takeLast(7).sumOf { it.doubtCount }
        val prior7 = points.dropLast(7).takeLast(7).sumOf { it.doubtCount }
        val rawMax = points.maxOfOrNull { it.doubtCount } ?: 0

        return ThirtyDayFrequencySummary(
            points = points,
            totalDoubts30d = total30d,
            activeDaysCount = activeDays,
            dailyAverage = dailyAvg,
            peakDay = peakDay,
            recentSevenDayTotal = recent7,
            priorSevenDayTotal = prior7,
            weekOverWeekDelta = recent7 - prior7,
            availableCategories = allCategories,
            maxDailyCount = rawMax.coerceAtLeast(4)
        )
    }

    private fun extractSubject(doubt: DoubtItem): String {
        return doubt.subject.trim().ifBlank {
            doubt.topic.substringBefore(":").trim().ifBlank { "General" }
        }
    }

    private fun startOfDayMillis(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun findDayBucketIndex(
        timestamp: Long,
        dayBoundaries: List<Long>,
        endExclusive: Long
    ): Int {
        if (timestamp < dayBoundaries.first() || timestamp >= endExclusive) return -1
        for (i in dayBoundaries.indices) {
            val nextBoundary = if (i + 1 < dayBoundaries.size) dayBoundaries[i + 1] else endExclusive
            if (timestamp >= dayBoundaries[i] && timestamp < nextBoundary) {
                return i
            }
        }
        return -1
    }
}
