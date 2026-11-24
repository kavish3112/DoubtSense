package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.DoubtDatabase
import com.example.data.repository.DoubtRepository
import com.example.receiver.ExportReminderReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ExportReminderManager"

/**
 * Manages periodic local export reminders that notify the user to back up their conversation
 * history whenever it has not been exported recently.
 */
object ExportReminderManager {

    private const val PREFS_NAME = "doubtsense_prefs"
    private const val KEY_EXPORT_REMINDERS_ENABLED = "export_reminders_enabled"
    private const val KEY_EXPORT_INTERVAL_DAYS = "export_reminder_interval_days"
    private const val KEY_LAST_EXPORT_TIMESTAMP = "last_export_timestamp"
    private const val KEY_LAST_REMINDER_SENT_TIMESTAMP = "last_export_reminder_sent_timestamp"

    private const val REMINDER_ALARM_REQUEST_CODE = 4401
    private const val ONE_DAY_MILLIS = 86_400_000L
    const val DEFAULT_INTERVAL_DAYS = 3

    private val _remindersEnabled = MutableStateFlow(true)
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    private val _intervalDays = MutableStateFlow(DEFAULT_INTERVAL_DAYS)
    val intervalDays: StateFlow<Int> = _intervalDays.asStateFlow()

    private val _lastExportTimestamp = MutableStateFlow(0L)
    val lastExportTimestamp: StateFlow<Long> = _lastExportTimestamp.asStateFlow()

    private val _lastReminderSentTimestamp = MutableStateFlow(0L)
    val lastReminderSentTimestamp: StateFlow<Long> = _lastReminderSentTimestamp.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _remindersEnabled.value = prefs.getBoolean(KEY_EXPORT_REMINDERS_ENABLED, true)
        _intervalDays.value = prefs.getInt(KEY_EXPORT_INTERVAL_DAYS, DEFAULT_INTERVAL_DAYS).coerceIn(1, 30)
        _lastExportTimestamp.value = prefs.getLong(KEY_LAST_EXPORT_TIMESTAMP, 0L)
        _lastReminderSentTimestamp.value = prefs.getLong(KEY_LAST_REMINDER_SENT_TIMESTAMP, 0L)
    }

    fun setRemindersEnabled(context: Context, enabled: Boolean) {
        _remindersEnabled.value = enabled
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXPORT_REMINDERS_ENABLED, enabled)
            .apply()

        if (enabled) {
            schedulePeriodicCheck(context)
        } else {
            cancelPeriodicCheck(context)
            NotificationHelper.cancelExportReminderNotification(context)
        }
    }

    fun setIntervalDays(context: Context, days: Int) {
        val sanitized = days.coerceIn(1, 30)
        _intervalDays.value = sanitized
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_EXPORT_INTERVAL_DAYS, sanitized)
            .apply()

        if (_remindersEnabled.value) {
            schedulePeriodicCheck(context)
        }
    }

    /**
     * Records a successful conversation/doubt export so upcoming reminder checks recognize the
     * history as recently saved and dismisses any active export reminder notification.
     */
    fun markHistoryExported(context: Context, timestampMillis: Long = System.currentTimeMillis()) {
        _lastExportTimestamp.value = timestampMillis
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_EXPORT_TIMESTAMP, timestampMillis)
            .apply()
        NotificationHelper.cancelExportReminderNotification(context)
    }

    fun recordReminderSent(context: Context, timestampMillis: Long = System.currentTimeMillis()) {
        _lastReminderSentTimestamp.value = timestampMillis
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_REMINDER_SENT_TIMESTAMP, timestampMillis)
            .apply()
    }

    /**
     * Schedules an inexact periodic background check via Android's AlarmManager.
     * Uses inexact scheduling so no restricted exact-alarm permission is required.
     */
    fun schedulePeriodicCheck(context: Context) {
        init(context)
        if (!_remindersEnabled.value) return

        try {
            val appCtx = context.applicationContext
            val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = buildCheckPendingIntent(appCtx)

            val checkIntervalMs = AlarmManager.INTERVAL_HALF_DAY
            val firstTriggerMs = System.currentTimeMillis() + checkIntervalMs

            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                firstTriggerMs,
                checkIntervalMs,
                pendingIntent
            )
            Log.d(TAG, "Scheduled periodic export reminder check (threshold=${_intervalDays.value}d)")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to schedule periodic export check", e)
        }
    }

    fun cancelPeriodicCheck(context: Context) {
        try {
            val appCtx = context.applicationContext
            val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = buildCheckPendingIntent(appCtx)
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to cancel periodic export check", e)
        }
    }

    private fun buildCheckPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ExportReminderReceiver::class.java).apply {
            action = ExportReminderReceiver.ACTION_CHECK_EXPORT_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            REMINDER_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Queries Room history freshness and dispatches a local notification if conversation history
     * has not been exported within the configured interval.
     */
    suspend fun checkAndNotifyIfDue(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        init(context)
        NotificationHelper.createNotificationChannels(context)

        val db = DoubtDatabase.getInstance(context)
        val repo = DoubtRepository(db.doubtDao(), db.conversationSessionDao())
        val (sessionCount, doubtCount, latestActivityTs) = repo.getHistoryStats()

        val shouldRemind = shouldSendExportReminder(
            remindersEnabled = _remindersEnabled.value,
            intervalDays = _intervalDays.value,
            lastExportTimestamp = _lastExportTimestamp.value,
            lastReminderSentTimestamp = _lastReminderSentTimestamp.value,
            latestActivityTimestamp = latestActivityTs,
            totalSessionsCount = sessionCount,
            totalDoubtsCount = doubtCount,
            nowMillis = nowMillis
        )

        if (!shouldRemind) return false

        val daysSinceExport = computeDaysSinceLastExport(_lastExportTimestamp.value, nowMillis)
        NotificationHelper.showExportReminderNotification(
            context = context,
            daysSinceLastExport = daysSinceExport,
            sessionCount = sessionCount,
            doubtCount = doubtCount
        )
        recordReminderSent(context, nowMillis)
        return true
    }

    /**
     * Sends an immediate local reminder notification (useful for verifying notification delivery
     * from Settings).
     */
    suspend fun sendTestReminderNotification(context: Context): Boolean {
        init(context)
        NotificationHelper.createNotificationChannels(context)
        val db = DoubtDatabase.getInstance(context)
        val repo = DoubtRepository(db.doubtDao(), db.conversationSessionDao())
        val (sessionCount, doubtCount, _) = repo.getHistoryStats()
        val daysSinceExport = computeDaysSinceLastExport(_lastExportTimestamp.value, System.currentTimeMillis())

        val posted = NotificationHelper.showExportReminderNotification(
            context = context,
            daysSinceLastExport = daysSinceExport,
            sessionCount = sessionCount,
            doubtCount = doubtCount
        )
        if (posted) {
            recordReminderSent(context)
        }
        return posted
    }

    /**
     * Pure-Kotlin eligibility evaluation for periodic export reminders.
     */
    fun shouldSendExportReminder(
        remindersEnabled: Boolean,
        intervalDays: Int,
        lastExportTimestamp: Long,
        lastReminderSentTimestamp: Long,
        latestActivityTimestamp: Long,
        totalSessionsCount: Int,
        totalDoubtsCount: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!remindersEnabled) return false
        val hasHistory = totalSessionsCount > 0 || totalDoubtsCount > 0
        if (!hasHistory) return false

        val thresholdMs = intervalDays.coerceAtLeast(1) * ONE_DAY_MILLIS

        // Avoid repeating reminders faster than the configured interval
        if (lastReminderSentTimestamp > 0L && (nowMillis - lastReminderSentTimestamp) < thresholdMs) {
            return false
        }

        // If user has exported previously, only remind once threshold has elapsed AND there is activity or staleness
        if (lastExportTimestamp > 0L) {
            val elapsedSinceExport = nowMillis - lastExportTimestamp
            return elapsedSinceExport >= thresholdMs
        }

        // User has never exported: remind once either threshold has elapsed since their latest activity
        // or they have accumulated at least 3 unexported sessions
        val activityStale = latestActivityTimestamp > 0L && (nowMillis - latestActivityTimestamp) >= thresholdMs
        return activityStale || totalSessionsCount >= 3
    }

    fun computeDaysSinceLastExport(lastExportTimestamp: Long, nowMillis: Long): Int? {
        if (lastExportTimestamp <= 0L || nowMillis < lastExportTimestamp) return null
        return ((nowMillis - lastExportTimestamp) / ONE_DAY_MILLIS).toInt()
    }

    fun formatLastExportLabel(lastExportTimestamp: Long): String {
        if (lastExportTimestamp <= 0L) return "Never exported"
        val fmt = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        return fmt.format(Date(lastExportTimestamp))
    }
}
