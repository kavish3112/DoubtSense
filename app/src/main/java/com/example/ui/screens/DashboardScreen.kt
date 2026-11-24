package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeOff
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
import com.example.util.DocumentExportHelper
import com.example.util.ExportDocumentFormat
import com.example.util.ShareHelper
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.DoubtItem
import com.example.data.remote.CloudSyncManager
import com.example.data.remote.CloudSyncUiState
import com.example.data.remote.GoogleAuthHelper
import com.example.data.remote.GoogleSignInOutcome
import com.example.service.ChatTurnHighlightState
import com.example.service.ChatVoiceInputState
import com.example.service.ListeningState
import com.example.service.ListeningStateManager
import com.example.service.LiveChatTurn
import com.example.ui.components.DoubtCard
import com.example.ui.components.UncertaintyGauge
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate950
import com.example.util.AppPreferencesManager
import com.example.util.BatteryMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listeningState by ListeningStateManager.listeningState.collectAsState()
    val liveTranscript by ListeningStateManager.liveTranscript.collectAsState()
    val conversationHistory by ListeningStateManager.conversationHistory.collectAsState()
    val liveChatTurns by ListeningStateManager.liveChatTurns.collectAsState()
    val liveTranscriptHighlight by ListeningStateManager.liveTranscriptHighlight.collectAsState()
    val liveDoubtAlert by ListeningStateManager.liveDoubtAlert.collectAsState()
    val selectedDoubtForDetail by ListeningStateManager.selectedDoubtForDetail.collectAsState()
    val chatVoiceState by ListeningStateManager.chatVoiceInputState.collectAsState()
    val handsFreeVoiceMode by ListeningStateManager.handsFreeVoiceMode.collectAsState()
    val audioRms by ListeningStateManager.audioRms.collectAsState()
    val latestDoubt by ListeningStateManager.latestDetectedDoubt.collectAsState()
    val isAnalyzing by ListeningStateManager.isAnalyzing.collectAsState()
    val statusMessage by ListeningStateManager.statusMessage.collectAsState()
    val repository = remember { ListeningStateManager.getRepository(context) }
    val recentDoubts by repository.allDoubts.collectAsState(initial = emptyList())
    val uncertaintyIntensity by ListeningStateManager.uncertaintyIntensity.collectAsState()
    val uncertaintyLabel by ListeningStateManager.uncertaintyLabel.collectAsState()
    val autoReadAloud by ListeningStateManager.autoReadAloud.collectAsState()
    val factCheckingMode by ListeningStateManager.factCheckingMode.collectAsState()
    val cloudSyncState by CloudSyncManager.syncState.collectAsState()
    val batterySaverPref by AppPreferencesManager.batterySaverMode.collectAsState()
    val batterySaverActive = remember(batterySaverPref) {
        batterySaverPref || BatteryMonitor.getBatteryStatus(context).isPowerSaveMode
    }

    var manualInputText by remember { mutableStateOf("") }
    var pendingHandsFreeActivation by remember { mutableStateOf(false) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ListeningStateManager.hasRecordAudioPermission(context)
                ListeningStateManager.refreshChatMicPermissionState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val coroutineScope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        hasMicPermission = micGranted
        if (micGranted) {
            ListeningStateManager.refreshChatMicPermissionState(context)
            ListeningStateManager.startListening(context)
        } else {
            val showRationale = findHostActivity(context)?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: true
            ListeningStateManager.onChatMicPermissionResult(
                granted = false,
                shouldShowRationale = showRationale,
                context = context,
                startImmediately = false
            )
        }
    }

    val chatMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { micGranted ->
        hasMicPermission = micGranted
        if (micGranted) {
            val enableHandsFree = pendingHandsFreeActivation
            pendingHandsFreeActivation = false
            if (enableHandsFree) {
                ListeningStateManager.setHandsFreeVoiceMode(true, context)
            } else {
                ListeningStateManager.onChatMicPermissionResult(
                    granted = true,
                    shouldShowRationale = true,
                    context = context,
                    startImmediately = true
                )
            }
        } else {
            pendingHandsFreeActivation = false
            val showRationale = findHostActivity(context)?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: true
            ListeningStateManager.onChatMicPermissionResult(
                granted = false,
                shouldShowRationale = showRationale,
                context = context,
                startImmediately = false
            )
        }
    }

    fun currentSessionDoubts(): List<DoubtItem> {
        return if (latestDoubt != null) {
            listOf(latestDoubt!!) + recentDoubts.filter { it.id != latestDoubt!!.id }.take(4)
        } else {
            recentDoubts.take(5)
        }
    }

    fun liveSessionSnapshotList() = listOfNotNull(ListeningStateManager.getLiveSessionAnalysisSnapshot())

    val saveLiveSessionTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { targetUri ->
        if (targetUri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                DocumentExportHelper.writeExportToUri(
                    context = context,
                    targetUri = targetUri,
                    conversationTurns = conversationHistory,
                    doubts = currentSessionDoubts(),
                    format = ExportDocumentFormat.STRUCTURED_TEXT,
                    documentTitle = "DoubtSense Live Session & Analysis",
                    sessions = liveSessionSnapshotList()
                )
            }
        }
    }

    val saveLiveSessionPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        if (targetUri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                DocumentExportHelper.writeExportToUri(
                    context = context,
                    targetUri = targetUri,
                    conversationTurns = conversationHistory,
                    doubts = currentSessionDoubts(),
                    format = ExportDocumentFormat.PDF,
                    documentTitle = "DoubtSense Live Session & Analysis",
                    sessions = liveSessionSnapshotList()
                )
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_lazy_column"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // App Branding Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.doubtsense_logo),
                        contentDescription = "DoubtSense Logo",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "DoubtSense",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Text(
                            text = "Context-Aware Doubt Detection",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ElectricCyan,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { ListeningStateManager.setFactCheckingMode(!factCheckingMode, context) },
                        color = if (factCheckingMode) EmeraldSuccess.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (factCheckingMode) EmeraldSuccess.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("fact_check_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = if (factCheckingMode) "Fact-Checking Mode enabled" else "Fact-Checking Mode disabled",
                                tint = if (factCheckingMode) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (factCheckingMode) "Verified" else "Verify Off",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (factCheckingMode) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Surface(
                        onClick = { ListeningStateManager.setAutoReadAloud(!autoReadAloud) },
                        color = if (autoReadAloud) ElectricCyan.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (autoReadAloud) ElectricCyan.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("auto_tts_toggle_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (autoReadAloud) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = if (autoReadAloud) "Auto-TTS enabled" else "Auto-TTS muted",
                                tint = if (autoReadAloud) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (autoReadAloud) "TTS On" else "TTS Off",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (autoReadAloud) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Status Indicator Pill
                    StatusPill(listeningState = listeningState, isAnalyzing = isAnalyzing)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cross-device Firestore Sync Status Pill
            val syncTint = when (cloudSyncState) {
                is CloudSyncUiState.Synced -> EmeraldSuccess
                is CloudSyncUiState.Syncing -> ElectricCyan
                is CloudSyncUiState.Error -> RoseError
                is CloudSyncUiState.OfflinePaused -> AmberWarning
                CloudSyncUiState.SignedOut -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val syncLabel = when (val st = cloudSyncState) {
                is CloudSyncUiState.Synced ->
                    "Cloud Synced (${st.sessionCount} sessions)${st.userEmail?.let { " • $it" } ?: ""}"
                is CloudSyncUiState.Syncing -> st.message
                is CloudSyncUiState.OfflinePaused -> "Cloud Sync Paused (${st.userEmail ?: "Offline"})"
                is CloudSyncUiState.Error -> "Sync Issue • Tap to retry"
                CloudSyncUiState.SignedOut -> "Tap to sign in & sync sessions across devices"
            }
            Surface(
                onClick = {
                    coroutineScope.launch {
                        if (cloudSyncState is CloudSyncUiState.SignedOut) {
                            val auth = CloudSyncManager.resolveFirebaseAuthOrNull()
                            if (auth != null) {
                                when (val res = GoogleAuthHelper.signInInteractive(context, auth)) {
                                    is GoogleSignInOutcome.Success -> {
                                        CloudSyncManager.bindAuthenticatedSession(context, repository, res.user)
                                    }
                                    is GoogleSignInOutcome.Error -> {
                                        ListeningStateManager.setStatusMessage(res.message)
                                    }
                                    GoogleSignInOutcome.Cancelled -> Unit
                                }
                            } else {
                                ListeningStateManager.setStatusMessage("Google Sign-In unavailable in this environment.")
                            }
                        } else {
                            CloudSyncManager.syncNow(context)
                        }
                    }
                },
                color = syncTint.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, syncTint.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("dashboard_cloud_sync_pill")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = when (cloudSyncState) {
                                is CloudSyncUiState.Synced -> Icons.Default.CloudDone
                                is CloudSyncUiState.Syncing -> Icons.Default.Sync
                                else -> Icons.Default.CloudOff
                            },
                            contentDescription = "Cross-device cloud sync status",
                            tint = syncTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = syncLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (cloudSyncState is CloudSyncUiState.SignedOut) "Sign In" else "Sync Now",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = syncTint,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Hero Voice & Waveform Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_visualizer_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (listeningState) {
                            ListeningState.LISTENING -> "Listening to Conversation..."
                            ListeningState.PAUSED -> "Listening Paused"
                            ListeningState.PROCESSING -> "Analyzing Context & Doubts..."
                            ListeningState.IDLE -> "Microphone Idle"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Waveform Animation
                    WaveformVisualizer(
                        isListening = listeningState == ListeningState.LISTENING,
                        audioRms = audioRms,
                        batterySaverActive = batterySaverActive
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Primary Listening Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (listeningState) {
                            ListeningState.IDLE -> {
                                Button(
                                    onClick = {
                                        val grantedNow = ListeningStateManager.hasRecordAudioPermission(context)
                                        hasMicPermission = grantedNow
                                        if (grantedNow) {
                                            ListeningStateManager.startListening(context)
                                        } else {
                                            val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            permissionLauncher.launch(permissionsToRequest.toTypedArray())
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = Slate950
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("start_listening_button")
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Start Listening", fontWeight = FontWeight.Bold)
                                }
                            }
                            ListeningState.LISTENING, ListeningState.PROCESSING -> {
                                FilledTonalButton(
                                    onClick = { ListeningStateManager.pauseListening(context) },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("pause_listening_button")
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pause")
                                }

                                Button(
                                    onClick = { ListeningStateManager.stopListening(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RoseError,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("stop_listening_button")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stop")
                                }
                            }
                            ListeningState.PAUSED -> {
                                Button(
                                    onClick = { ListeningStateManager.resumeListening(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldSuccess,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("resume_listening_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Resume")
                                }

                                OutlinedButton(
                                    onClick = { ListeningStateManager.stopListening(context) },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("stop_paused_listening_button")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stop")
                                }
                            }
                        }
                    }

                    if (!statusMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = statusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }

        // Real-Time Speech Uncertainty Intensity Gauge
        item {
            UncertaintyGauge(
                uncertaintyIntensity = uncertaintyIntensity,
                uncertaintyLabel = uncertaintyLabel,
                isAnalyzing = isAnalyzing,
                isListening = listeningState == ListeningState.LISTENING
            )
        }

        // Live Speech Transcript Box
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_transcript_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live Speech Transcript & Context Flow",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (conversationHistory.isNotEmpty() || latestDoubt != null || recentDoubts.isNotEmpty()) {
                                var showSessionExportMenu by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { showSessionExportMenu = true },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("export_session_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Export conversation",
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Export", fontSize = 11.sp)
                                    }

                                    DropdownMenu(
                                        expanded = showSessionExportMenu,
                                        onDismissRequest = { showSessionExportMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("💾 Save Session as Text (.txt)") },
                                            onClick = {
                                                showSessionExportMenu = false
                                                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                                saveLiveSessionTxtLauncher.launch("doubtsense_live_session_$ts.txt")
                                            },
                                            modifier = Modifier.testTag("dashboard_save_session_txt")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("💾 Save Session as PDF (.pdf)") },
                                            onClick = {
                                                showSessionExportMenu = false
                                                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                                saveLiveSessionPdfLauncher.launch("doubtsense_live_session_$ts.pdf")
                                            },
                                            modifier = Modifier.testTag("dashboard_save_session_pdf")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("📄 Share Session & Analysis (PDF)") },
                                            onClick = {
                                                showSessionExportMenu = false
                                                ShareHelper.exportFullConversation(
                                                    context = context,
                                                    conversationTurns = conversationHistory,
                                                    doubts = currentSessionDoubts(),
                                                    format = ExportDocumentFormat.PDF,
                                                    title = "DoubtSense Live Session & Analysis",
                                                    sessions = liveSessionSnapshotList()
                                                )
                                            },
                                            modifier = Modifier.testTag("dashboard_share_session_pdf")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("📝 Share Session & Analysis (.txt)") },
                                            onClick = {
                                                showSessionExportMenu = false
                                                ShareHelper.exportFullConversation(
                                                    context = context,
                                                    conversationTurns = conversationHistory,
                                                    doubts = currentSessionDoubts(),
                                                    format = ExportDocumentFormat.STRUCTURED_TEXT,
                                                    title = "DoubtSense Live Session & Analysis",
                                                    sessions = liveSessionSnapshotList()
                                                )
                                            },
                                            modifier = Modifier.testTag("dashboard_share_session_txt")
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            if (conversationHistory.isNotEmpty() || liveTranscript.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { ListeningStateManager.clearTranscript() },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Clear", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Real-Time In-Chat Visual Notification Strip when a doubt is detected
                    AnimatedVisibility(
                        visible = liveDoubtAlert != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        liveDoubtAlert?.let { alert ->
                            val bannerAccent = if (alert.isAmbiguous) RoseError else AmberWarning
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = bannerAccent.copy(alpha = 0.11f),
                                border = BorderStroke(1.dp, bannerAccent.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("live_doubt_visual_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .background(bannerAccent)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lightbulb,
                                            contentDescription = stringResource(R.string.chat_visual_banner_title),
                                            tint = bannerAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (alert.isAmbiguous) {
                                                    "${stringResource(R.string.chat_doubt_ambiguous_badge)} • ${alert.topic}"
                                                } else {
                                                    "${stringResource(R.string.chat_doubt_detected_badge)} • ${alert.topic}"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = bannerAccent,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "\"${alert.snippet}\"",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        TextButton(
                                            onClick = { ListeningStateManager.selectDoubtById(alert.doubtId, context) },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("inspect_live_doubt_banner")
                                        ) {
                                            Text(
                                                text = stringResource(R.string.action_inspect_doubt),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = bannerAccent
                                            )
                                        }
                                        IconButton(
                                            onClick = { ListeningStateManager.dismissLiveDoubtAlert() },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("dismiss_live_doubt_banner")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.cd_dismiss_doubt_banner),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (liveChatTurns.isEmpty() && conversationHistory.isEmpty() && liveTranscript.isBlank()) {
                                Text(
                                    text = "Start speaking or tap a sample conversation below to see real-time doubt recognition in action...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                )
                            } else {
                                if (liveChatTurns.isNotEmpty()) {
                                    liveChatTurns.takeLast(6).forEach { turn ->
                                        ChatTranscriptTurnRow(
                                            turn = turn,
                                            batterySaverActive = batterySaverActive,
                                            onInspectDoubt = { doubtId ->
                                                ListeningStateManager.selectDoubtById(doubtId, context)
                                            }
                                        )
                                    }
                                } else {
                                    conversationHistory.takeLast(4).forEach { turn ->
                                        Text(
                                            text = "• $turn",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontFamily = FontFamily.SansSerif
                                            ),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }

                                if (liveTranscript.isNotBlank()) {
                                    val isLiveDoubt = liveTranscriptHighlight == ChatTurnHighlightState.UNCERTAIN_LIVE
                                    val liveBg by animateColorAsState(
                                        targetValue = if (isLiveDoubt) AmberWarning.copy(alpha = 0.13f) else ElectricCyan.copy(alpha = 0.08f),
                                        animationSpec = tween(250),
                                        label = "live_transcript_bg"
                                    )
                                    val liveBorder by animateColorAsState(
                                        targetValue = if (isLiveDoubt) AmberWarning.copy(alpha = 0.40f) else ElectricCyan.copy(alpha = 0.25f),
                                        animationSpec = tween(250),
                                        label = "live_transcript_border"
                                    )
                                    val liveTint = if (isLiveDoubt) AmberWarning else ElectricCyan
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = liveBg,
                                        border = BorderStroke(1.dp, liveBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp)
                                            .testTag("live_partial_transcript_row")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(liveTint)
                                            )
                                            Text(
                                                text = "🎙️ $liveTranscript...",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = liveTint,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isLiveDoubt) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = AmberWarning.copy(alpha = 0.18f),
                                                    modifier = Modifier.testTag("live_partial_doubt_indicator")
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.chat_live_doubt_signal),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = AmberWarning,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        ),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Inline Microphone Permission Rationale / Settings Banner inside Chat Interface
                    val permissionDeniedState = chatVoiceState as? ChatVoiceInputState.PermissionDenied
                    AnimatedVisibility(
                        visible = permissionDeniedState != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        permissionDeniedState?.let { denied ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = RoseError.copy(alpha = 0.11f),
                                border = BorderStroke(1.dp, RoseError.copy(alpha = 0.38f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .testTag("chat_mic_permission_banner")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MicOff,
                                            contentDescription = null,
                                            tint = RoseError,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.chat_mic_permission_title),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = RoseError,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { ListeningStateManager.dismissChatMicBanner() },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("dismiss_chat_mic_permission_banner")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.cd_dismiss_mic_permission),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (denied.permanentlyDenied) {
                                            stringResource(R.string.chat_mic_permission_permanently_denied)
                                        } else {
                                            stringResource(R.string.chat_mic_permission_rationale)
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (denied.permanentlyDenied) {
                                            FilledTonalButton(
                                                onClick = { openAppMicrophoneSettings(context) },
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("open_app_settings_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.action_open_app_settings))
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    chatMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = ElectricCyan,
                                                    contentColor = Slate950
                                                ),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("grant_chat_mic_permission_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Mic,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(R.string.action_grant_mic_permission),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Active Real-Time Voice-to-Text Recording Bar inside Chat
                    val activeRecording = chatVoiceState as? ChatVoiceInputState.Recording
                    val isTranscribingVoice = chatVoiceState is ChatVoiceInputState.Transcribing
                    val voiceErrorState = chatVoiceState as? ChatVoiceInputState.Error
                    AnimatedVisibility(
                        visible = activeRecording != null || isTranscribingVoice || voiceErrorState != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            activeRecording?.let { rec ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ElectricCyan.copy(alpha = 0.11f),
                                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("chat_voice_recording_bar")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(RoseError)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Recording Voice-to-Text • ${rec.elapsedSeconds}s (${rec.modeLabel})",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = ElectricCyan,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                                Text(
                                                    text = rec.partialTranscript.ifBlank {
                                                        "Speak your question naturally — auto-submits when you pause"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = if (rec.partialTranscript.isNotBlank()) FontWeight.Medium else FontWeight.Normal
                                                    ),
                                                    modifier = Modifier.testTag("chat_voice_live_text")
                                                )
                                            }
                                            IconButton(
                                                onClick = { ListeningStateManager.stopChatVoiceInput() },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(EmeraldSuccess.copy(alpha = 0.18f))
                                                    .testTag("chat_voice_stop_send_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = stringResource(R.string.chat_voice_mic_stop_cd),
                                                    tint = EmeraldSuccess
                                                )
                                            }
                                            IconButton(
                                                onClick = { ListeningStateManager.cancelChatVoiceInput() },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("chat_voice_cancel_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = stringResource(R.string.chat_voice_cancel_cd),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        LinearProgressIndicator(
                                            progress = { rec.audioLevel.coerceIn(0.08f, 1f) },
                                            color = ElectricCyan,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                        )
                                    }
                                }
                            }

                            if (isTranscribingVoice) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AmberWarning.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("chat_voice_transcribing_indicator")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = AmberWarning
                                        )
                                        Text(
                                            text = stringResource(R.string.chat_voice_transcribing),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = AmberWarning,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }
                                }
                            }

                            voiceErrorState?.let { err ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = RoseError.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, RoseError.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("chat_voice_error_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = err.message,
                                            style = MaterialTheme.typography.bodySmall.copy(color = RoseError),
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { ListeningStateManager.dismissChatMicBanner() },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.cd_dismiss_mic_permission),
                                                tint = RoseError,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Hands-Free Voice Toggle Strip inside Chat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = handsFreeVoiceMode,
                            onClick = {
                                if (handsFreeVoiceMode) {
                                    ListeningStateManager.setHandsFreeVoiceMode(false)
                                } else {
                                    val grantedNow = ListeningStateManager.hasRecordAudioPermission(context)
                                    hasMicPermission = grantedNow
                                    if (grantedNow) {
                                        ListeningStateManager.setHandsFreeVoiceMode(true, context)
                                    } else {
                                        pendingHandsFreeActivation = true
                                        chatMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.chat_hands_free_label),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricCyan,
                                selectedLeadingIconColor = ElectricCyan
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("chat_hands_free_toggle")
                        )
                        Text(
                            text = if (handsFreeVoiceMode) {
                                stringResource(R.string.chat_hands_free_active_hint)
                            } else if (!hasMicPermission) {
                                "Tap mic to grant audio permission"
                            } else {
                                "Tap mic to speak hands-free"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (handsFreeVoiceMode) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // In-Chat Voice + Text Composer Bar
                    val isRecordingChatVoice = activeRecording != null
                    val composerText = if (isRecordingChatVoice && activeRecording?.partialTranscript?.isNotBlank() == true) {
                        activeRecording.partialTranscript
                    } else {
                        manualInputText
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = composerText,
                            onValueChange = {
                                if (!isRecordingChatVoice) {
                                    manualInputText = it
                                }
                            },
                            readOnly = isRecordingChatVoice,
                            placeholder = { Text(stringResource(R.string.chat_composer_placeholder)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("manual_speech_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (isRecordingChatVoice) {
                                        ListeningStateManager.stopChatVoiceInput()
                                    } else if (manualInputText.isNotBlank()) {
                                        ListeningStateManager.onUtteranceFinalized(
                                            manualInputText,
                                            context,
                                            isExplicitQuery = true
                                        )
                                        manualInputText = ""
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isRecordingChatVoice) ElectricCyan else MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = if (isRecordingChatVoice) ElectricCyan.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        IconButton(
                            onClick = {
                                if (isRecordingChatVoice) {
                                    ListeningStateManager.stopChatVoiceInput()
                                } else {
                                    val grantedNow = ListeningStateManager.hasRecordAudioPermission(context)
                                    hasMicPermission = grantedNow
                                    if (grantedNow) {
                                        ListeningStateManager.startChatVoiceInput(context)
                                    } else {
                                        pendingHandsFreeActivation = false
                                        chatMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            enabled = !isTranscribingVoice,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isRecordingChatVoice) RoseError else NeonViolet.copy(alpha = 0.22f)
                                )
                                .testTag("chat_voice_mic_button")
                        ) {
                            Icon(
                                imageVector = if (isRecordingChatVoice) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecordingChatVoice) {
                                    stringResource(R.string.chat_voice_mic_stop_cd)
                                } else {
                                    stringResource(R.string.chat_voice_mic_start_cd)
                                },
                                tint = if (isRecordingChatVoice) Color.White else NeonViolet
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isRecordingChatVoice) {
                                    ListeningStateManager.stopChatVoiceInput()
                                } else if (manualInputText.isNotBlank()) {
                                    ListeningStateManager.onUtteranceFinalized(
                                        manualInputText,
                                        context,
                                        isExplicitQuery = true
                                    )
                                    manualInputText = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ElectricCyan)
                                .testTag("send_manual_speech_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Slate950
                            )
                        }
                    }
                }
            }
        }

        // Context Test Scenarios (Sample doubts to test without needing real microphone hardware)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_scenarios_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🧪 Test Contextual Scenarios (Tap to Simulate)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Verify context-tracking, ambiguity clarification, and notification summaries:",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScenarioChip(
                            label = "🌲 ML Random Forest (Dialogue Context)",
                            utterance = "But how do they actually work together?",
                            onClick = {
                                ListeningStateManager.onUtteranceFinalized("Person A: Random Forest uses multiple decision trees.", context)
                                ListeningStateManager.onUtteranceFinalized("Person B: But how do they actually work together?", context)
                            }
                        )
                        ScenarioChip(
                            label = "🧬 Cellular Biology Doubt",
                            utterance = "I don't understand how ATP synthase actually creates energy from the proton gradient.",
                            onClick = { ListeningStateManager.onUtteranceFinalized(it, context) }
                        )
                        ScenarioChip(
                            label = "⚡ Algorithms / Big-O",
                            utterance = "Why is merge sort O(n log n) in all cases while quicksort can degrade to O(n squared)?",
                            onClick = { ListeningStateManager.onUtteranceFinalized(it, context) }
                        )
                        ScenarioChip(
                            label = "❓ Ambiguous Doubt (Asks for Clarification)",
                            utterance = "Wait, why does that balance change when they react?",
                            onClick = { ListeningStateManager.onUtteranceFinalized(it, context) }
                        )
                        ScenarioChip(
                            label = "☕ Non-Doubt Statement (Ignored)",
                            utterance = "Hey guys, let's grab coffee at 3pm after the lecture.",
                            onClick = { ListeningStateManager.onUtteranceFinalized(it, context) }
                        )
                    }
                }
            }
        }

        // Latest Detected Doubt Card
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡 Latest Detected Doubt & Answer",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )

                    OutlinedButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("view_history_trends_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = ElectricCyan
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Trends & History", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (latestDoubt != null) {
                    DoubtCard(
                        doubt = latestDoubt!!,
                        initialExpandedDetailed = false
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No doubts detected yet. Start listening or tap a sample scenario above!",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(88.dp))
        }
    }

    selectedDoubtForDetail?.let { inspectedDoubt ->
        Dialog(
            onDismissRequest = { ListeningStateManager.selectDoubtForDetail(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 700.dp)
                    .testTag("live_doubt_inspect_dialog"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Highlighted Doubt Details",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        IconButton(
                            onClick = { ListeningStateManager.selectDoubtForDetail(null) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("close_live_doubt_inspect_dialog")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close highlighted doubt details")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        DoubtCard(
                            doubt = inspectedDoubt,
                            initialExpandedDetailed = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatTranscriptTurnRow(
    turn: LiveChatTurn,
    batterySaverActive: Boolean,
    onInspectDoubt: (Long) -> Unit
) {
    val isHighlightedDoubt = turn.highlightState == ChatTurnHighlightState.DOUBT_CONFIRMED ||
        turn.highlightState == ChatTurnHighlightState.AMBIGUOUS_DOUBT
    val isScanning = turn.highlightState == ChatTurnHighlightState.UNCERTAIN_LIVE

    if (!isHighlightedDoubt && !isScanning) {
        Text(
            text = "• ${turn.text}",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.SansSerif
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("chat_turn_row_${turn.id}")
        )
        return
    }

    val accent = when (turn.highlightState) {
        ChatTurnHighlightState.AMBIGUOUS_DOUBT -> RoseError
        ChatTurnHighlightState.DOUBT_CONFIRMED -> AmberWarning
        ChatTurnHighlightState.UNCERTAIN_LIVE -> ElectricCyan
        ChatTurnHighlightState.NEUTRAL -> MaterialTheme.colorScheme.outline
    }
    val bgAlpha = if (isHighlightedDoubt) 0.11f else 0.07f
    val borderAlpha = if (isHighlightedDoubt) 0.34f else 0.24f

    val pulseAlpha = if (isHighlightedDoubt && !batterySaverActive) {
        val transition = rememberInfiniteTransition(label = "doubt_dot_pulse_${turn.id}")
        val animated by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_alpha"
        )
        animated
    } else {
        1f
    }

    val badgeLabel = when (turn.highlightState) {
        ChatTurnHighlightState.AMBIGUOUS_DOUBT -> stringResource(R.string.chat_doubt_ambiguous_badge)
        ChatTurnHighlightState.DOUBT_CONFIRMED -> stringResource(R.string.chat_doubt_detected_badge)
        ChatTurnHighlightState.UNCERTAIN_LIVE -> stringResource(R.string.chat_doubt_scanning_badge)
        ChatTurnHighlightState.NEUTRAL -> ""
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = bgAlpha),
        border = BorderStroke(1.dp, accent.copy(alpha = borderAlpha)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (turn.doubtId != null) {
                    Modifier.clickable { onInspectDoubt(turn.doubtId) }
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {}
            .testTag("chat_turn_row_${turn.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = pulseAlpha))
                    )
                    Text(
                        text = buildString {
                            append(badgeLabel)
                            val topicHint = turn.doubtTopic?.takeIf { it.isNotBlank() }
                            if (topicHint != null) append(" • $topicHint")
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("chat_turn_doubt_badge_${turn.id}")
                    )
                }
                Text(
                    text = turn.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isHighlightedDoubt) FontWeight.Medium else FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif
                    )
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    listeningState: ListeningState,
    isAnalyzing: Boolean
) {
    val (color, text) = when {
        isAnalyzing -> AmberWarning to "Analyzing"
        listeningState == ListeningState.LISTENING -> EmeraldSuccess to "Live Mic"
        listeningState == ListeningState.PAUSED -> AmberWarning to "Paused"
        else -> MaterialTheme.colorScheme.outline to "Idle"
    }

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 2.dp,
                    color = color
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
        }
    }
}

@Composable
private fun ScenarioChip(
    label: String,
    utterance: String,
    onClick: (String) -> Unit
) {
    FilledTonalButton(
        onClick = { onClick(utterance) },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private tailrec fun findHostActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> findHostActivity(context.baseContext)
    else -> null
}

private fun openAppMicrophoneSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
