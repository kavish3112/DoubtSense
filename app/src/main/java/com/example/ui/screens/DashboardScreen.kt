package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import com.example.util.ExportDocumentFormat
import com.example.util.ShareHelper
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.DoubtItem
import com.example.service.ListeningState
import com.example.service.ListeningStateManager
import com.example.ui.components.DoubtCard
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.DeepViolet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

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
    val audioRms by ListeningStateManager.audioRms.collectAsState()
    val latestDoubt by ListeningStateManager.latestDetectedDoubt.collectAsState()
    val isAnalyzing by ListeningStateManager.isAnalyzing.collectAsState()
    val statusMessage by ListeningStateManager.statusMessage.collectAsState()
    val repository = remember { ListeningStateManager.getRepository(context) }
    val recentDoubts by repository.allDoubts.collectAsState(initial = emptyList())

    var manualInputText by remember { mutableStateOf("") }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        hasMicPermission = micGranted
        if (micGranted) {
            ListeningStateManager.startListening(context)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
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

                // Status Indicator Pill
                StatusPill(listeningState = listeningState, isAnalyzing = isAnalyzing)
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
                    containerColor = Slate850
                ),
                border = BorderStroke(1.dp, Slate800)
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
                        audioRms = audioRms
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
                                        if (hasMicPermission) {
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

        // Live Speech Transcript Box
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_transcript_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Slate900
                ),
                border = BorderStroke(1.dp, Slate800)
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
                                            .height(32.dp)
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
                                            text = { Text("📄 Export Session as PDF") },
                                            onClick = {
                                                showSessionExportMenu = false
                                                val sessionDoubts = if (latestDoubt != null) {
                                                    listOf(latestDoubt!!) + recentDoubts.filter { it.id != latestDoubt!!.id }.take(4)
                                                } else {
                                                    recentDoubts.take(5)
                                                }
                                                ShareHelper.exportFullConversation(
                                                    context = context,
                                                    conversationTurns = conversationHistory,
                                                    doubts = sessionDoubts,
                                                    format = ExportDocumentFormat.PDF,
                                                    title = "DoubtSense Live Session Digest"
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("📝 Export as Structured Text (.txt)") },
                                            onClick = {
                                                showSessionExportMenu = false
                                                val sessionDoubts = if (latestDoubt != null) {
                                                    listOf(latestDoubt!!) + recentDoubts.filter { it.id != latestDoubt!!.id }.take(4)
                                                } else {
                                                    recentDoubts.take(5)
                                                }
                                                ShareHelper.exportFullConversation(
                                                    context = context,
                                                    conversationTurns = conversationHistory,
                                                    doubts = sessionDoubts,
                                                    format = ExportDocumentFormat.STRUCTURED_TEXT,
                                                    title = "DoubtSense Live Session Digest"
                                                )
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            if (conversationHistory.isNotEmpty() || liveTranscript.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { ListeningStateManager.clearTranscript() },
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Clear", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Slate850,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            if (conversationHistory.isEmpty() && liveTranscript.isBlank()) {
                                Text(
                                    text = "Start speaking or tap a sample conversation below to see real-time doubt recognition in action...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                )
                            } else {
                                // Show past conversational turns for context
                                conversationHistory.takeLast(4).forEachIndexed { i, turn ->
                                    Text(
                                        text = "• $turn",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily.SansSerif
                                        ),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }

                                if (liveTranscript.isNotBlank()) {
                                    Text(
                                        text = "🎙️ $liveTranscript...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = ElectricCyan,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
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
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = BorderStroke(1.dp, Slate800)
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Manual Typed Speech Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualInputText,
                            onValueChange = { manualInputText = it },
                            placeholder = { Text("Or type a student doubt/speech...") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("manual_speech_input"),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = Slate800,
                                focusedContainerColor = Slate850,
                                unfocusedContainerColor = Slate850
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (manualInputText.isNotBlank()) {
                                    ListeningStateManager.onUtteranceFinalized(manualInputText, context)
                                    manualInputText = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ElectricCyan)
                                .testTag("send_manual_speech_button")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = Slate950)
                        }
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
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("All History", fontSize = 11.sp)
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
                        color = Slate900,
                        border = BorderStroke(1.dp, Slate800)
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
            Spacer(modifier = Modifier.height(24.dp))
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
        else -> Slate800 to "Idle"
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
        modifier = Modifier.height(36.dp)
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
