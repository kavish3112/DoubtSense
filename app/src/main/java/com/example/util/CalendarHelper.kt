package com.example.util

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import com.example.data.model.DoubtItem
import java.util.Calendar

/**
 * Launches the system Calendar app pre-filled with a study review session
 * for a resolved doubt without requiring calendar permissions.
 */
object CalendarHelper {

    fun buildScheduleReviewIntent(
        title: String,
        description: String,
        startMillis: Long = defaultReviewStartMillis(),
        durationMillis: Long = 30L * 60L * 1000L
    ): Intent {
        val endMillis = startMillis + durationMillis
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "DoubtSense Review: $title")
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        }
    }

    fun scheduleStudyReview(context: Context, doubt: DoubtItem): Boolean {
        val description = buildString {
            appendLine("Question: ${doubt.doubtSnippet}")
            if (doubt.quickAnswer.isNotBlank()) {
                appendLine()
                appendLine("Quick Answer: ${doubt.quickAnswer}")
            }
            if (doubt.oneSentenceSummary.isNotBlank()) {
                appendLine()
                appendLine("Summary: ${doubt.oneSentenceSummary}")
            }
        }.trim()
        return launchCalendarIntent(
            context = context,
            intent = buildScheduleReviewIntent(
                title = doubt.topic.ifBlank { "Doubt Review" },
                description = description
            )
        )
    }

    fun scheduleStudyReview(
        context: Context,
        topic: String,
        question: String,
        quickAnswer: String
    ): Boolean {
        val description = buildString {
            appendLine("Question: $question")
            if (quickAnswer.isNotBlank()) {
                appendLine()
                appendLine("Quick Answer: $quickAnswer")
            }
        }.trim()
        return launchCalendarIntent(
            context = context,
            intent = buildScheduleReviewIntent(
                title = topic.ifBlank { "Doubt Review" },
                description = description
            )
        )
    }

    private fun launchCalendarIntent(context: Context, intent: Intent): Boolean {
        return try {
            val launchIntent = Intent(intent).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            runCatching {
                Toast.makeText(
                    context,
                    "No calendar app found to schedule study reminder.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            false
        }
    }

    private fun defaultReviewStartMillis(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
