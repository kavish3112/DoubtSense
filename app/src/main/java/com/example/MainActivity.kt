package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.service.ListeningStateManager
import com.example.ui.AppDestination
import com.example.ui.DoubtSenseApp
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AppPreferencesManager
import com.example.util.ExportReminderManager
import com.example.util.NotificationHelper
import com.example.util.configureAppCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SHORTCUT_ACTION = "extra_shortcut_action"
        const val ACTION_SHORTCUT_NEW_DOUBT = "NEW_DOUBT"
        const val ACTION_SHORTCUT_VIEW_HISTORY = "VIEW_HISTORY"
        const val ACTION_SHORTCUT_OPEN_SETTINGS = "OPEN_SETTINGS"
        const val EXTRA_AUTO_START_VOICE = "extra_auto_start_voice"
    }

    private var targetDoubtId by mutableStateOf<Long?>(null)
    private var openHistoryTrigger by mutableIntStateOf(0)
    private var shortcutDestination by mutableStateOf<AppDestination?>(null)
    private var shortcutTrigger by mutableIntStateOf(0)
    private var autoStartVoiceTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check initialization with launch intent
        configureAppCheck(this, intent)

        // STEP 2: Initialize preferences, notification channels, repository, TTS engine, and export reminders
        AppPreferencesManager.init(this)
        NotificationHelper.createNotificationChannels(this)
        ListeningStateManager.initRepository(this)
        ExportReminderManager.init(this)
        ExportReminderManager.schedulePeriodicCheck(this)
        com.example.util.TtsManager.init(this)

        lifecycleScope.launch(Dispatchers.IO) {
            if (AppPreferencesManager.autoCleanup30Days.value) {
                ListeningStateManager.getRepository(applicationContext).deleteOlderThanDays(30)
            }
            ExportReminderManager.checkAndNotifyIfDue(applicationContext)
        }

        // STEP 3: Handle notification click & app shortcut / widget launch extras
        extractLaunchExtrasFromIntent(intent)

        enableEdgeToEdge()

        setContent {
            val themeMode by AppPreferencesManager.themeMode.collectAsState()
            MyApplicationTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DoubtSenseApp(
                        targetDoubtId = targetDoubtId,
                        openHistoryTrigger = openHistoryTrigger,
                        initialDestination = shortcutDestination,
                        shortcutTrigger = shortcutTrigger,
                        autoStartVoiceTrigger = autoStartVoiceTrigger
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractLaunchExtrasFromIntent(intent)
    }

    private fun extractLaunchExtrasFromIntent(intent: Intent?) {
        val doubtId = intent?.getLongExtra(NotificationHelper.EXTRA_DOUBT_ID, -1L) ?: -1L
        if (doubtId > 0) {
            targetDoubtId = doubtId
            ListeningStateManager.selectDoubtById(doubtId, this)
        }
        val shouldOpenHistory = intent?.getBooleanExtra(NotificationHelper.EXTRA_OPEN_HISTORY, false) == true
        if (shouldOpenHistory) {
            openHistoryTrigger++
        }
        val shortcutAction = intent?.getStringExtra(EXTRA_SHORTCUT_ACTION)
        when (shortcutAction) {
            ACTION_SHORTCUT_NEW_DOUBT -> {
                shortcutDestination = AppDestination.ANSWER_ENGINE
                shortcutTrigger++
            }
            ACTION_SHORTCUT_VIEW_HISTORY -> {
                shortcutDestination = AppDestination.HISTORY
                shortcutTrigger++
            }
            ACTION_SHORTCUT_OPEN_SETTINGS -> {
                shortcutDestination = AppDestination.SETTINGS
                shortcutTrigger++
            }
        }
        if (intent?.getBooleanExtra(EXTRA_AUTO_START_VOICE, false) == true) {
            shortcutDestination = AppDestination.ANSWER_ENGINE
            shortcutTrigger++
            autoStartVoiceTrigger++
        }
    }

    override fun onDestroy() {
        com.example.util.TtsManager.shutdown()
        super.onDestroy()
    }
}
