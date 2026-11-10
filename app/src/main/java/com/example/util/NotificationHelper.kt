package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_LISTENING_ID = "doubtsense_listening_channel"
    const val CHANNEL_ANSWERS_ID = "doubtsense_answers_channel"
    const val NOTIFICATION_LISTENING_ID = 1001
    const val NOTIFICATION_ANSWER_BASE_ID = 2000

    const val ACTION_STOP = "com.example.doubtsense.ACTION_STOP"
    const val ACTION_PAUSE = "com.example.doubtsense.ACTION_PAUSE"
    const val ACTION_RESUME = "com.example.doubtsense.ACTION_RESUME"
    const val EXTRA_DOUBT_ID = "extra_doubt_id"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val listeningChannel = NotificationChannel(
                CHANNEL_LISTENING_ID,
                "DoubtSense Active Listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while DoubtSense is actively listening in background"
                setShowBadge(false)
            }

            val answersChannel = NotificationChannel(
                CHANNEL_ANSWERS_ID,
                "Doubt Answers & Explanations",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a doubt is detected and an answer is ready"
                enableVibration(true)
                setShowBadge(true)
            }

            manager.createNotificationChannel(listeningChannel)
            manager.createNotificationChannel(answersChannel)
        }
    }

    fun buildListeningNotification(
        context: Context,
        isPaused: Boolean,
        transcriptSnippet: String? = null
    ): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, com.example.service.DoubtSenseListeningService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleActionIntent = Intent(context, com.example.service.DoubtSenseListeningService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePending = PendingIntent.getService(
            context,
            2,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isPaused) {
            "Listening paused. Tap Resume to continue monitoring."
        } else if (!transcriptSnippet.isNullOrBlank()) {
            "Hearing: \"$transcriptSnippet\""
        } else {
            "Actively listening for doubts in conversation..."
        }

        val toggleTitle = if (isPaused) "Resume" else "Pause"

        return NotificationCompat.Builder(context, CHANNEL_LISTENING_ID)
            .setSmallIcon(R.drawable.doubtsense_logo)
            .setContentTitle(if (isPaused) "DoubtSense: Paused" else "DoubtSense: Listening")
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, toggleTitle, togglePending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    fun showDoubtAnswerNotification(
        context: Context,
        doubtId: Long,
        topic: String,
        quickAnswer: String,
        doubtSnippet: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DOUBT_ID, doubtId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            doubtId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryText = "💡 $topic: $quickAnswer"

        val notification = NotificationCompat.Builder(context, CHANNEL_ANSWERS_ID)
            .setSmallIcon(R.drawable.doubtsense_logo)
            .setContentTitle("💡 Answer: $topic")
            .setContentText(quickAnswer)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("💡 $topic")
                    .setSummaryText("Doubt Answer")
                    .bigText("Doubt: \"$doubtSnippet\"\n\n$quickAnswer")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((NOTIFICATION_ANSWER_BASE_ID + (doubtId % 1000)).toInt(), notification)
    }
}
