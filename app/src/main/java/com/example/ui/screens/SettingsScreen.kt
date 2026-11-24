package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.OfflineResponseCache
import com.example.data.remote.CloudSyncManager
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.CloudSyncUiState
import com.example.data.remote.GoogleAuthHelper
import com.example.data.remote.GoogleSignInOutcome
import com.example.service.ListeningStateManager
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate950
import com.example.util.AppPreferencesManager
import com.example.util.BatteryMonitor
import com.example.util.DocumentExportHelper
import com.example.util.ExportDocumentFormat
import com.example.util.ExportReminderManager
import com.example.util.ListeningCaptureMode
import com.example.util.ThemeMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var explanationDepth by remember { mutableStateOf("Balanced") }
    var notifySummaries by remember { mutableStateOf(true) }
    var vibrationFeedback by remember { mutableStateOf(true) }

    val lowPowerMode by ListeningStateManager.lowPowerMode.collectAsState()
    val clarificationMode by ListeningStateManager.clarificationMode.collectAsState()
    val autoReadAloud by ListeningStateManager.autoReadAloud.collectAsState()
    val factCheckingMode by ListeningStateManager.factCheckingMode.collectAsState()

    val themeMode by AppPreferencesManager.themeMode.collectAsState()
    val listeningCaptureMode by AppPreferencesManager.listeningCaptureMode.collectAsState()
    val sttLanguageTag by AppPreferencesManager.sttLanguageTag.collectAsState()
    val ttsSpeechRate by AppPreferencesManager.ttsSpeechRate.collectAsState()
    val ttsSpeechPitch by AppPreferencesManager.ttsSpeechPitch.collectAsState()
    val batterySaverMode by AppPreferencesManager.batterySaverMode.collectAsState()
    val offlineMode by AppPreferencesManager.offlineMode.collectAsState()
    val autoCleanup30Days by AppPreferencesManager.autoCleanup30Days.collectAsState()
    val lastCloudSyncTimestamp by AppPreferencesManager.lastCloudSyncTimestamp.collectAsState()
    val autoCloudSyncEnabled by AppPreferencesManager.autoCloudSyncEnabled.collectAsState()
    val cloudSyncManagerState by CloudSyncManager.syncState.collectAsState()

    val exportRemindersEnabled by ExportReminderManager.remindersEnabled.collectAsState()
    val exportIntervalDays by ExportReminderManager.intervalDays.collectAsState()
    val lastExportTimestamp by ExportReminderManager.lastExportTimestamp.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportStatusText by remember { mutableStateOf<String?>(null) }

    var showSttLanguageMenu by remember { mutableStateOf(false) }
    var cachedOfflineCount by remember { mutableIntStateOf(0) }
    val batteryStatus = remember { BatteryMonitor.getBatteryStatus(context) }

    val firebaseAuth = remember { CloudSyncManager.resolveFirebaseAuthOrNull() }
    var currentUser by remember(firebaseAuth) { mutableStateOf<FirebaseUser?>(firebaseAuth?.currentUser) }
    if (firebaseAuth != null) {
        val observedUser by GoogleAuthHelper.authStateFlow(firebaseAuth).collectAsState(initial = firebaseAuth.currentUser)
        currentUser = observedUser
    }
    var isSyncBusy by remember { mutableStateOf(false) }
    var cloudSyncStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        AppPreferencesManager.init(context)
        ExportReminderManager.init(context)
        cachedOfflineCount = OfflineResponseCache.getCachedResults(context).size
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ExportReminderManager.setRemindersEnabled(context, true)
            Toast.makeText(context, "Export reminder notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification permission denied. Reminders won't appear in status bar.", Toast.LENGTH_SHORT).show()
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { targetUri ->
        if (targetUri != null) {
            coroutineScope.launch {
                isExporting = true
                exportProgress = 0.25f
                exportStatusText = "Preparing CSV spreadsheet..."
                val success = withContext(Dispatchers.IO) {
                    val repo = ListeningStateManager.getRepository(context)
                    val sessions = repo.getAllSessionsOnce()
                    val doubts = repo.getAllDoubtsOnce()
                    val turns = sessions.flatMap { it.getTranscriptTurns() }
                    DocumentExportHelper.writeExportToUri(
                        context = context,
                        targetUri = targetUri,
                        conversationTurns = turns,
                        doubts = doubts,
                        format = ExportDocumentFormat.CSV,
                        documentTitle = "DoubtSense Complete Backup",
                        onProgress = { p -> exportProgress = p }
                    )
                }
                exportProgress = 1.0f
                exportStatusText = if (success) "CSV saved successfully" else "CSV save failed"
                delay(700L)
                isExporting = false
            }
        }
    }

    val isBatteryExempt by remember {
        mutableStateOf(ListeningStateManager.isIgnoringBatteryOptimizations(context))
    }

    fun triggerHistoryExport(format: ExportDocumentFormat, printMode: Boolean = false) {
        coroutineScope.launch {
            isExporting = true
            exportProgress = 0.1f
            exportStatusText = if (printMode) "Generating printable PDF..." else "Preparing ${format.name} export..."
            val repo = ListeningStateManager.getRepository(context)
            val (sessions, doubts) = withContext(Dispatchers.IO) {
                repo.getAllSessionsOnce() to repo.getAllDoubtsOnce()
            }
            val allTurns = sessions.flatMap { it.getTranscriptTurns() }
            if (sessions.isEmpty() && doubts.isEmpty()) {
                Toast.makeText(context, "No conversation history to export yet", Toast.LENGTH_SHORT).show()
                isExporting = false
                exportProgress = 0f
                exportStatusText = null
                return@launch
            }
            exportProgress = 0.35f
            if (printMode) {
                DocumentExportHelper.printPdfDocument(
                    context = context,
                    conversationTurns = allTurns,
                    doubts = doubts,
                    documentTitle = "DoubtSense Printable Backup (${sessions.size} Sessions)",
                    onProgress = { p -> exportProgress = p }
                )
            } else {
                DocumentExportHelper.exportConversation(
                    context = context,
                    conversationTurns = allTurns,
                    doubts = doubts,
                    format = format,
                    documentTitle = "DoubtSense Complete Backup (${sessions.size} Sessions)",
                    onProgress = { p -> exportProgress = p }
                )
            }
            exportProgress = 1.0f
            exportStatusText = "Export complete"
            delay(750L)
            isExporting = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_list"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Settings & Privacy",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = "Customize themes, multilingual speech, battery saver, offline cache, and cloud sync",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // Dynamic Theme Picker (Dark, Light, High-Contrast)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("theme_picker_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Appearance theme",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Appearance & Accessibility Theme",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Switch between Dark, Light, and High-Contrast accessibility mode:",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val icon = when (mode) {
                                ThemeMode.DARK -> Icons.Default.DarkMode
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.HIGH_CONTRAST -> Icons.Default.Contrast
                            }
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { AppPreferencesManager.setThemeMode(context, mode) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(mode.label, fontSize = 12.sp)
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("theme_option_${mode.name}")
                            )
                        }
                    }
                }
            }
        }

        // Voice Capture Mode & Multilingual Speech-to-Text + TTS Speed
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("listening_mode_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice and Speech Settings",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Voice Capture, Language & Speech",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Microphone Capture Mode:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ListeningCaptureMode.entries.forEach { captureMode ->
                            FilterChip(
                                selected = listeningCaptureMode == captureMode,
                                onClick = { AppPreferencesManager.setListeningCaptureMode(context, captureMode) },
                                label = { Text(captureMode.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = ElectricCyan
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("listening_mode_${captureMode.name}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Speech-to-Text Recognition Language:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val currentLang = AppPreferencesManager.SUPPORTED_LANGUAGES.firstOrNull {
                        it.sttLocaleTag.equals(sttLanguageTag, ignoreCase = true)
                    } ?: AppPreferencesManager.SUPPORTED_LANGUAGES.first()
                    Box {
                        OutlinedButton(
                            onClick = { showSttLanguageMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("stt_language_dropdown")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${currentLang.displayName} (${currentLang.sttLocaleTag})",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Select speech recognition language"
                            )
                        }
                        DropdownMenu(
                            expanded = showSttLanguageMenu,
                            onDismissRequest = { showSttLanguageMenu = false }
                        ) {
                            AppPreferencesManager.SUPPORTED_LANGUAGES.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("${option.displayName} (${option.sttLocaleTag})") },
                                    onClick = {
                                        showSttLanguageMenu = false
                                        AppPreferencesManager.setSttLanguageTag(context, option.sttLocaleTag)
                                    },
                                    modifier = Modifier.testTag("stt_lang_option_${option.sttLocaleTag}")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TTS Speech Rate",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2fx", ttsSpeechRate),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Slider(
                        value = ttsSpeechRate,
                        onValueChange = { AppPreferencesManager.setTtsSpeechRate(context, it) },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = ElectricCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tts_speech_rate_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TTS Voice Pitch",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.2fx", ttsSpeechPitch),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = NeonViolet,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Slider(
                        value = ttsSpeechPitch,
                        onValueChange = { AppPreferencesManager.setTtsSpeechPitch(context, it) },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonViolet,
                            activeTrackColor = NeonViolet
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tts_speech_pitch_slider")
                    )
                }
            }
        }

        // Clarification Modes (Mode A vs Mode B)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = NeonViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Interactive Clarification Mode",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose how DoubtSense handles ambiguous or complex doubts during conversations:",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = clarificationMode == "PRIVATE",
                            onClick = { ListeningStateManager.setClarificationMode("PRIVATE") },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Mode A: Private", fontSize = 12.sp)
                                }
                            }
                        )

                        FilterChip(
                            selected = clarificationMode == "CONVERSATION",
                            onClick = { ListeningStateManager.setClarificationMode("CONVERSATION") },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Mode B: Classroom Discussion", fontSize = 12.sp)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (clarificationMode == "PRIVATE") {
                            "• Mode A: DoubtSense asks you questions privately on your device screen and personalizes the answer."
                        } else {
                            "• Mode B: DoubtSense suggests clarifying questions you can ask aloud in the ongoing discussion."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        // Battery Saver, Offline Mode (10-Recent Cache) & 30-Day Auto-Cleanup
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatterySaver,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Battery Saver, Offline & Auto-Cleanup",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Battery Saver Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Battery Saver Mode (${batteryStatus.levelPercent}% battery)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "Reduces waveform refresh rate and pauses background voice listening when battery is low.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Switch(
                            checked = batterySaverMode,
                            onCheckedChange = { AppPreferencesManager.setBatterySaverMode(context, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AmberWarning),
                            modifier = Modifier.testTag("battery_saver_switch")
                        )
                    }

                    // Offline Mode (10-Recent Response Cache)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Offline Mode ($cachedOfflineCount/10 cached)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                            Text(
                                text = "Caches the 10 most recent AI answers locally for offline access.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Switch(
                            checked = offlineMode,
                            onCheckedChange = { AppPreferencesManager.setOfflineMode(context, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan),
                            modifier = Modifier.testTag("offline_mode_switch")
                        )
                    }

                    if (cachedOfflineCount > 0) {
                        OutlinedButton(
                            onClick = {
                                OfflineResponseCache.clearCache(context)
                                cachedOfflineCount = 0
                                Toast.makeText(context, "Cleared offline response cache", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("clear_offline_cache_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Offline Cache", fontSize = 12.sp)
                        }
                    }

                    // 30-Day Auto-Cleanup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoDelete,
                                    contentDescription = null,
                                    tint = NeonViolet,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Auto-Cleanup (>30 Days)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                            Text(
                                text = "Automatically removes history items older than 30 days to keep database compact.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Switch(
                            checked = autoCleanup30Days,
                            onCheckedChange = { AppPreferencesManager.setAutoCleanup30Days(context, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonViolet),
                            modifier = Modifier.testTag("auto_cleanup_switch")
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val removed = ListeningStateManager.getRepository(context).deleteOlderThanDays(30)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Cleaned $removed history items older than 30 days", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("clean_now_button")
                    ) {
                        Icon(Icons.Default.AutoDelete, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clean >30d Items Now", fontSize = 12.sp)
                    }
                }
            }
        }

        // Battery Optimization & Low-Power Background Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Battery Optimization & Background Reliability",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Low-Power Background Listening",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "Optimizes microphone duty-cycling to conserve battery and reduce heat during long sessions.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Switch(
                            checked = lowPowerMode,
                            onCheckedChange = { ListeningStateManager.setLowPowerMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AmberWarning)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isBatteryExempt) Icons.Default.CheckCircle else Icons.Default.Power,
                                    contentDescription = null,
                                    tint = if (isBatteryExempt) EmeraldSuccess else AmberWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBatteryExempt) {
                                        "Protected from System Background Kills"
                                    } else {
                                        "System Battery Restrictions Active"
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isBatteryExempt) EmeraldSuccess else AmberWarning
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "To ensure continuous speech recognition when the screen is locked, Android requires background battery exemption.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )

                            if (!isBatteryExempt) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            }
                                        } catch (e: Exception) {
                                            try {
                                                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                context.startActivity(fallbackIntent)
                                            } catch (e2: Exception) {
                                                Toast.makeText(context, "Open system settings to disable battery optimization.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    border = BorderStroke(1.dp, AmberWarning),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Exempt from Optimization", color = AmberWarning, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Firestore Cloud Sync & Backup Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloud_sync_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Cloud Sync",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cross-Device Cloud Sync (Firestore)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Sync Sessions Across Devices",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "Automatically saves new chat sessions & tags to Firestore in real time when signed in.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Switch(
                            checked = autoCloudSyncEnabled,
                            onCheckedChange = { enabled ->
                                AppPreferencesManager.setAutoCloudSyncEnabled(context, enabled)
                                val repo = ListeningStateManager.getRepository(context)
                                CloudSyncManager.bindAuthenticatedSession(
                                    context = context,
                                    repo = repo,
                                    user = currentUser,
                                    autoSyncEnabled = enabled,
                                    offlineMode = offlineMode
                                )
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan),
                            modifier = Modifier.testTag("auto_cloud_sync_switch")
                        )
                    }

                    if (currentUser == null) {
                        Text(
                            text = "Sign in with Google to synchronize your chat sessions and doubts across all your Android devices.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Button(
                            onClick = {
                                val auth = firebaseAuth
                                if (auth == null) {
                                    cloudSyncStatus = "Google Sign-In unavailable in this environment."
                                    return@Button
                                }
                                coroutineScope.launch {
                                    isSyncBusy = true
                                    cloudSyncStatus = "Signing in with Google..."
                                    when (val outcome = GoogleAuthHelper.signInInteractive(context, auth)) {
                                        is GoogleSignInOutcome.Success -> {
                                            currentUser = outcome.user
                                            val repo = ListeningStateManager.getRepository(context)
                                            CloudSyncManager.bindAuthenticatedSession(context, repo, outcome.user)
                                            cloudSyncStatus = "Signed in as ${outcome.user.email ?: outcome.user.displayName ?: "Google User"}"
                                        }
                                        is GoogleSignInOutcome.Cancelled -> {
                                            cloudSyncStatus = "Sign-in cancelled"
                                        }
                                        is GoogleSignInOutcome.Error -> {
                                            cloudSyncStatus = outcome.message
                                        }
                                    }
                                    isSyncBusy = false
                                }
                            },
                            enabled = !isSyncBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Slate950),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("google_signin_button")
                        ) {
                            if (isSyncBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Slate950)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Sign in with Google", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val user = currentUser!!
                        val lastSyncLabel = if (lastCloudSyncTimestamp > 0L) {
                            SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(lastCloudSyncTimestamp))
                        } else {
                            "Never synced"
                        }
                        val liveSyncText = when (val st = cloudSyncManagerState) {
                            is CloudSyncUiState.Synced -> "Live Sync Active • ${st.sessionCount} sessions"
                            is CloudSyncUiState.Syncing -> st.message
                            is CloudSyncUiState.OfflinePaused -> "Paused (Offline Mode or Auto-Sync off)"
                            is CloudSyncUiState.Error -> "Sync issue: ${st.message}"
                            CloudSyncUiState.SignedOut -> "Signed in"
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = user.displayName?.ifBlank { null } ?: user.email ?: "Signed-In User",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = liveSyncText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = ElectricCyan,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = "Last sync: $lastSyncLabel",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = EmeraldSuccess,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isSyncBusy = true
                                        cloudSyncStatus = "Syncing sessions across devices..."
                                        val res = CloudSyncManager.syncNow(context)
                                        res.onSuccess { stats ->
                                            cloudSyncStatus = "Synced (${stats.sessionsInserted} new sessions, ${stats.sessionsUpdated} updated)"
                                            Toast.makeText(context, cloudSyncStatus, Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            cloudSyncStatus = "Sync failed: ${err.localizedMessage ?: "Permission or network error"}"
                                        }
                                        isSyncBusy = false
                                    }
                                },
                                enabled = !isSyncBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Slate950),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("cloud_sync_now_button")
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isSyncBusy = true
                                        cloudSyncStatus = "Backing up local database to Firestore..."
                                        val repo = ListeningStateManager.getRepository(context)
                                        val (doubts, sessions) = withContext(Dispatchers.IO) {
                                            repo.getAllDoubtsOnce() to repo.getAllSessionsOnce()
                                        }
                                        val syncRepo = CloudSyncRepository(context)
                                        val res = withContext(Dispatchers.IO) {
                                            syncRepo.backupAll(user.uid, doubts, sessions)
                                        }
                                        res.onSuccess { summary ->
                                            AppPreferencesManager.recordCloudSync(context, summary.syncedAtMillis)
                                            cloudSyncStatus = "Backed up ${summary.uploadedDoubts} doubts & ${summary.uploadedSessions} sessions"
                                            Toast.makeText(context, cloudSyncStatus, Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            cloudSyncStatus = "Backup failed: ${err.localizedMessage ?: "Permission or network error"}"
                                        }
                                        isSyncBusy = false
                                    }
                                },
                                enabled = !isSyncBusy,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("cloud_backup_button")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backup", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isSyncBusy = true
                                        cloudSyncStatus = "Merging sessions from Firestore..."
                                        val syncRepo = CloudSyncRepository(context)
                                        val res = withContext(Dispatchers.IO) {
                                            syncRepo.fetchRawBackups(user.uid)
                                        }
                                        res.onSuccess { (sessionBackups, doubtBackups) ->
                                            val stats = withContext(Dispatchers.IO) {
                                                val repo = ListeningStateManager.getRepository(context)
                                                repo.mergeRemoteSnapshot(
                                                    remoteSessions = sessionBackups.map { it.toRoomEntity() },
                                                    remoteDoubts = doubtBackups.map { it.toRoomEntity() }
                                                )
                                            }
                                            AppPreferencesManager.recordCloudSync(context)
                                            cloudSyncStatus = "Merged ${stats.sessionsInserted} new sessions, ${stats.doubtsInserted} new doubts"
                                            Toast.makeText(context, cloudSyncStatus, Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            cloudSyncStatus = "Restore failed: ${err.localizedMessage ?: "Permission or network error"}"
                                        }
                                        isSyncBusy = false
                                    }
                                },
                                enabled = !isSyncBusy,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("cloud_restore_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        CloudSyncManager.signOut(context)
                                        currentUser = null
                                        cloudSyncStatus = "Signed out"
                                    }
                                },
                                enabled = !isSyncBusy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("google_signout_button")
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sign Out", fontSize = 12.sp)
                            }
                        }
                    }

                    if (!cloudSyncStatus.isNullOrBlank()) {
                        Text(
                            text = cloudSyncStatus ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }
        }

        // AI Answer Preferences
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Default Explanation Depth",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Customize default depth prior to interactive clarification:",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Concise", "Balanced", "In-Depth").forEach { depth ->
                            FilterChip(
                                selected = explanationDepth == depth,
                                onClick = { explanationDepth = depth },
                                label = { Text(depth, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Fact-Checking Mode Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fact_checking_mode_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(
                    1.dp,
                    if (factCheckingMode) EmeraldSuccess.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Fact-Checking Mode",
                                tint = if (factCheckingMode) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Fact-Checking Mode",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (factCheckingMode) EmeraldSuccess.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (factCheckingMode) "ACTIVE" else "OFF",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (factCheckingMode) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Switch(
                            checked = factCheckingMode,
                            onCheckedChange = { ListeningStateManager.setFactCheckingMode(it, context) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldSuccess,
                                checkedTrackColor = EmeraldSuccess.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.testTag("fact_checking_mode_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Requires the AI to cross-verify generated answers and attach clickable external verification sources (official documentation, textbooks, and academic references).",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    )
                }
            }
        }

        // Notification Summaries
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Answer Notifications",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification Summaries",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "Displays quick answer preview directly in the status bar",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Switch(
                            checked = notifySummaries,
                            onCheckedChange = { notifySummaries = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Haptic Vibration Alert",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "Vibrates gently when a doubt is detected and resolved",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Switch(
                            checked = vibrationFeedback,
                            onCheckedChange = { vibrationFeedback = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Auto-Read AI Answers Aloud (TTS)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                            Text(
                                text = "Automatically speaks back detected doubt resolutions using Android Text-to-Speech",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Switch(
                            checked = autoReadAloud,
                            onCheckedChange = { ListeningStateManager.setAutoReadAloud(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan),
                            modifier = Modifier.testTag("auto_read_aloud_switch")
                        )
                    }
                }
            }
        }

        // Periodic Conversation Export Reminder System + Progress Export Controls
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_reminder_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(
                    1.dp,
                    if (exportRemindersEnabled) ElectricCyan.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Export Backup Reminders",
                                tint = if (exportRemindersEnabled) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "History Export & Periodic Reminders",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Local notification if history hasn't been saved recently",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Switch(
                            checked = exportRemindersEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!hasPerm) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        return@Switch
                                    }
                                }
                                ExportReminderManager.setRemindersEnabled(context, enabled)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan),
                            modifier = Modifier.testTag("export_reminder_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Remind if not exported within:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1 to "Daily", 3 to "Every 3 Days", 7 to "Weekly").forEach { (days, label) ->
                            val isSelected = exportIntervalDays == days
                            FilterChip(
                                selected = isSelected,
                                enabled = exportRemindersEnabled,
                                onClick = { ExportReminderManager.setIntervalDays(context, days) },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = ElectricCyan
                                ),
                                modifier = Modifier.testTag("export_interval_$days")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Last exported: ${ExportReminderManager.formatLastExportLabel(lastExportTimestamp)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (lastExportTimestamp > 0L) EmeraldSuccess else AmberWarning,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.5.sp
                            ),
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("last_export_status_text")
                        )
                    }

                    AnimatedVisibility(visible = isExporting) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            LinearProgressIndicator(
                                progress = { exportProgress.coerceIn(0f, 1f) },
                                color = ElectricCyan,
                                trackColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_export_progress")
                            )
                            if (!exportStatusText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = exportStatusText ?: "",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = ElectricCyan
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!hasPerm) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        return@OutlinedButton
                                    }
                                }
                                coroutineScope.launch(Dispatchers.IO) {
                                    val sent = ExportReminderManager.sendTestReminderNotification(context)
                                    withContext(Dispatchers.Main) {
                                        val msg = if (sent) {
                                            "Export reminder notification sent"
                                        } else {
                                            "Enable notifications permission to preview reminder"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.55f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("test_export_reminder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Send test export reminder notification",
                                tint = ElectricCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Alert", fontSize = 12.sp, color = ElectricCyan)
                        }

                        FilledTonalButton(
                            onClick = { triggerHistoryExport(ExportDocumentFormat.PDF) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = EmeraldSuccess.copy(alpha = 0.18f),
                                contentColor = EmeraldSuccess
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("settings_export_now_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export all conversation history as PDF",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = { triggerHistoryExport(ExportDocumentFormat.CSV) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ElectricCyan.copy(alpha = 0.18f),
                                contentColor = ElectricCyan
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("settings_export_csv_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = "Export all conversation history as CSV",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { triggerHistoryExport(ExportDocumentFormat.PDF, printMode = true) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("settings_print_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Print or save printable PDF",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print PDF", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { createCsvLauncher.launch("doubtsense_history.csv") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("settings_save_csv_device_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Save CSV file to device",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save CSV File", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Privacy and Safety Guarantee
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacy & Student Safety",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Never activates without explicit user tap\n• Audio is converted to text locally on the device\n• Persistent notification is always shown during active listening\n• No raw audio recordings are stored or transmitted\n• Easily clear your transcript and doubt history anytime",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
