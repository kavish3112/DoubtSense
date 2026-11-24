package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.util.ExportReminderManager
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "ExportReminderReceiver"

/**
 * Receives periodic background alarm checks, snooze actions, and device boot broadcasts
 * to keep export reminder notifications active and up to date.
 */
class ExportReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received broadcast action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                ExportReminderManager.init(context)
                ExportReminderManager.schedulePeriodicCheck(context)
            }

            ACTION_SNOOZE_EXPORT_REMINDER -> {
                NotificationHelper.cancelExportReminderNotification(context)
                ExportReminderManager.recordReminderSent(context)
            }

            ACTION_CHECK_EXPORT_REMINDER -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        ExportReminderManager.checkAndNotifyIfDue(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed periodic export reminder check", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_CHECK_EXPORT_REMINDER = "com.example.doubtsense.ACTION_CHECK_EXPORT_REMINDER"
        const val ACTION_SNOOZE_EXPORT_REMINDER = "com.example.doubtsense.ACTION_SNOOZE_EXPORT_REMINDER"
    }
}
