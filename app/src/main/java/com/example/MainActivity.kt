package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.service.ListeningStateManager
import com.example.ui.DoubtSenseApp
import com.example.ui.theme.MyApplicationTheme
import com.example.util.NotificationHelper
import com.example.util.configureAppCheck

class MainActivity : ComponentActivity() {

    private var targetDoubtId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check initialization with launch intent
        configureAppCheck(this, intent)

        // STEP 2: Initialize notification channels, repository, and TTS engine
        NotificationHelper.createNotificationChannels(this)
        ListeningStateManager.initRepository(this)
        com.example.util.TtsManager.init(this)

        // STEP 3: Handle notification click launch extra
        extractDoubtIdFromIntent(intent)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DoubtSenseApp(targetDoubtId = targetDoubtId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDoubtIdFromIntent(intent)
    }

    private fun extractDoubtIdFromIntent(intent: Intent?) {
        val doubtId = intent?.getLongExtra(NotificationHelper.EXTRA_DOUBT_ID, -1L) ?: -1L
        if (doubtId > 0) {
            targetDoubtId = doubtId
            ListeningStateManager.selectDoubtById(doubtId, this)
        }
    }

    override fun onDestroy() {
        com.example.util.TtsManager.shutdown()
        super.onDestroy()
    }
}
