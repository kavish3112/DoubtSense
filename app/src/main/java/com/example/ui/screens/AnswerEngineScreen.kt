package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ai.AnswerEngineResult
import com.example.data.local.OfflineResponseCache
import com.example.service.ChatTurnHighlightState
import com.example.service.ListeningStateManager
import com.example.ui.components.AnswerEngineResultCard
import com.example.ui.components.AnswerHistoryItemCard
import com.example.ui.components.SwipeToDeleteContainer
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate950
import com.example.ui.viewmodel.AnswerEngineUiState
import com.example.ui.viewmodel.AnswerEngineViewModel
import com.example.util.AppPreferencesManager
import com.example.util.BatteryMonitor
import com.example.util.DocumentExportHelper
import com.example.util.ExportDocumentFormat
import com.example.util.ListeningCaptureMode
import com.example.util.SemanticSearchHelper
import com.example.util.ShareHelper

@Composable
fun AnswerEngineScreen(
    autoStartVoiceTrigger: Int = 0,
    revisitedResult: AnswerEngineResult? = null,
    revisitTrigger: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: AnswerEngineViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val queryInput by viewModel.queryInput.collectAsStateWithLifecycle()
    val preferDirectAudio by viewModel.preferDirectAudioCapture.collectAsStateWithLifecycle()
    val recentAnswers by viewModel.recentAnswers.collectAsStateWithLifecycle()
    val followUpContext by viewModel.followUpParentContext.collectAsStateWithLifecycle()
    val aiResponseLanguage by viewModel.aiResponseLanguage.collectAsStateWithLifecycle()
    val listeningCaptureMode by viewModel.listeningCaptureMode.collectAsStateWithLifecycle()
    val batterySaverPref by viewModel.batterySaverMode.collectAsStateWithLifecycle()
    val offlineModePref by viewModel.offlineMode.collectAsStateWithLifecycle()
    val cachedOfflineResults by viewModel.cachedOfflineResults.collectAsStateWithLifecycle()
    val summarizingIds by viewModel.summarizingItemIds.collectAsStateWithLifecycle()
    val availableTags by AppPreferencesManager.customTags.collectAsStateWithLifecycle()

    var pendingDeleteItem by remember { mutableStateOf<AnswerEngineResult?>(null) }
    var historySearchQuery by remember { mutableStateOf("") }
    var recentOnly7Days by remember { mutableStateOf(false) }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var showLanguageMenu by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.clearPermissionErrorIfGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startMicrophoneCapture()
        } else {
            val showRationale = findActivityFromContext(context)?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: true
            viewModel.onPermissionDenied(permanentlyDenied = !showRationale)
        }
    }

    LaunchedEffect(autoStartVoiceTrigger) {
        if (autoStartVoiceTrigger > 0) {
            if (viewModel.hasRecordPermission()) {
                viewModel.startMicrophoneCapture()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val batteryStatus = remember(batterySaverPref) { BatteryMonitor.getBatteryStatus(context) }
    val batterySaverActive = batterySaverPref && (batteryStatus.isLowBattery || batteryStatus.isPowerSaveMode || batterySaverPref)
    val isListening = uiState is AnswerEngineUiState.Listening
    val isBusy = uiState is AnswerEngineUiState.TranscribingAudio || uiState is AnswerEngineUiState.GeneratingAnswer
    val streamingState = uiState as? AnswerEngineUiState.GeneratingAnswer
    val liveLevel = (uiState as? AnswerEngineUiState.Listening)?.audioLevel ?: 0f

    val pulseScale by animateFloatAsState(
        targetValue = if (isListening) 1f + (liveLevel * 0.25f) else 1f,
        animationSpec = tween(durationMillis = if (batterySaverActive) 260 else 120),
        label = "mic_orb_pulse"
    )
    val orbTransition = rememberInfiniteTransition(label = "mic_ripple_rings")
    val ringDuration = if (batterySaverActive) 2800 else 1300
    val ring1Progress by orbTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ringDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mic_ring_1"
    )
    val ring2Progress by orbTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ringDuration, delayMillis = ringDuration / 2, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mic_ring_2"
    )

    val quickQuestions = remember {
        listOf(
            "Explain this code",
            "Summarize this",
            "Why does warm air rise?",
            "How does Random Forest work?",
            "TCP vs UDP difference",
            "Explain Bayes theorem"
        )
    }

    val listState = rememberLazyListState()
    val streamingTextScrollState = rememberScrollState()

    LaunchedEffect(revisitTrigger, revisitedResult) {
        if (revisitTrigger > 0 && revisitedResult != null) {
            viewModel.selectRecentAnswer(revisitedResult)
            listState.scrollToItem(index = 2)
        }
    }

    // Auto-scroll streaming text container AND parent list whenever a new streaming AI segment arrives
    LaunchedEffect(streamingState?.streamingAnswer) {
        val partial = streamingState?.streamingAnswer.orEmpty()
        if (partial.isNotBlank()) {
            streamingTextScrollState.animateScrollTo(streamingTextScrollState.maxValue)
            listState.animateScrollToItem(index = 2)
        }
    }

    val activeResult = (uiState as? AnswerEngineUiState.Success)?.result
    val sourceHistory = remember(recentAnswers, cachedOfflineResults, offlineModePref) {
        if (recentAnswers.isNotEmpty()) recentAnswers
        else if (offlineModePref) cachedOfflineResults
        else emptyList()
    }

    val filteredHistoryItems = remember(
        sourceHistory,
        activeResult,
        historySearchQuery,
        recentOnly7Days,
        selectedTagFilter
    ) {
        val base = if (activeResult != null) {
            sourceHistory.filter {
                if (activeResult.doubtId > 0L && it.doubtId > 0L) it.doubtId != activeResult.doubtId
                else it.timestamp != activeResult.timestamp
            }
        } else {
            sourceHistory
        }
        val cutoff7d = System.currentTimeMillis() - 7L * 86_400_000L
        val filtered = base.filter { item ->
            val matchesRecent = !recentOnly7Days || item.timestamp >= cutoff7d
            val matchesTag = selectedTagFilter == null || item.customTag.equals(selectedTagFilter, ignoreCase = true)
            matchesRecent && matchesTag
        }
        if (historySearchQuery.isBlank()) {
            filtered
        } else {
            SemanticSearchHelper.rankBySemanticAndKeyword(
                items = filtered,
                query = historySearchQuery,
                textSelector = {
                    "${it.query} ${it.topic} ${it.subject} ${it.quickAnswer} ${it.detailedAnswer} ${it.customTag} ${it.oneSentenceSummary}"
                }
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("answer_engine_screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(end = 104.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(ElectricCyan, NeonViolet))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Answer Engine",
                                tint = Slate950,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Answer Engine",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                            Text(
                                text = "${listeningCaptureMode.label} • AI: $aiResponseLanguage",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ElectricCyan,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (batterySaverActive) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AmberWarning.copy(alpha = 0.16f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BatterySaver,
                                    contentDescription = "Battery Saver Active",
                                    tint = AmberWarning,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(16.dp)
                                )
                            }
                        }
                        if (offlineModePref || !OfflineResponseCache.isNetworkAvailable(context)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NeonViolet.copy(alpha = 0.18f),
                                modifier = Modifier.testTag("offline_mode_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = "Offline Mode",
                                        tint = NeonViolet,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${cachedOfflineResults.size}/10",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = NeonViolet,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Voice Capture Console Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .testTag("voice_answer_console_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (isListening) ElectricCyan.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val stage = when (uiState) {
                            is AnswerEngineUiState.Listening -> 1
                            is AnswerEngineUiState.TranscribingAudio -> 2
                            is AnswerEngineUiState.GeneratingAnswer -> 3
                            is AnswerEngineUiState.Success -> 4
                            else -> 0
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PipelineStageBadge("1. Mic Audio", active = stage == 1, completed = stage > 1)
                            PipelineStageBadge("2. Speech-to-Text", active = stage == 2 || stage == 1, completed = stage > 2)
                            PipelineStageBadge("3. AI Answer", active = stage == 3, completed = stage >= 4)
                        }

                        if (followUpContext.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NeonViolet.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Follow-up context attached",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = NeonViolet,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    TextButton(onClick = { viewModel.clearFollowUpContext() }) {
                                        Text("Clear", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Capture Mode Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = !preferDirectAudio,
                                onClick = { viewModel.setPreferDirectAudioCapture(false) },
                                enabled = !isListening && !isBusy,
                                label = { Text("Auto Speech-to-Text", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCyan
                                ),
                                modifier = Modifier.testTag("mode_auto_stt")
                            )
                            FilterChip(
                                selected = preferDirectAudio,
                                onClick = { viewModel.setPreferDirectAudioCapture(true) },
                                enabled = !isListening && !isBusy,
                                label = { Text("Direct Mic Audio", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonViolet.copy(alpha = 0.22f),
                                    selectedLabelColor = NeonViolet
                                ),
                                modifier = Modifier.testTag("mode_direct_audio")
                            )
                        }

                        // Interactive Microphone Capture Orb with Haptic Feedback
                        Box(
                            modifier = Modifier
                                .size(124.dp)
                                .scale(pulseScale),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isListening) {
                                Canvas(
                                    modifier = Modifier
                                        .size(124.dp)
                                        .testTag("answer_engine_pulse_rings")
                                ) {
                                    val minR = 40.dp.toPx()
                                    val maxR = size.minDimension / 2f
                                    val r1 = minR + (maxR - minR) * ring1Progress
                                    val r2 = minR + (maxR - minR) * ring2Progress
                                    drawCircle(
                                        color = RoseError.copy(alpha = (1f - ring1Progress) * 0.55f),
                                        radius = r1,
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )
                                    drawCircle(
                                        color = ElectricCyan.copy(alpha = (1f - ring2Progress) * 0.5f),
                                        radius = r2,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(106.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isListening) RoseError.copy(alpha = 0.18f)
                                        else ElectricCyan.copy(alpha = 0.14f)
                                    )
                            )
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isListening) {
                                        viewModel.stopMicrophoneAndAnswer()
                                    } else if (!isBusy) {
                                        if (viewModel.hasRecordPermission()) {
                                            viewModel.startMicrophoneCapture()
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                enabled = !isBusy,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isListening) RoseError else ElectricCyan,
                                    contentColor = if (isListening) Color.White else Slate950
                                ),
                                modifier = Modifier
                                    .size(80.dp)
                                    .testTag("answer_engine_mic_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp,
                                        color = Slate950
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = if (isListening) "Stop recording and generate answer" else "Tap to capture live audio question",
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = isListening) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("answer_engine_audio_visualizer")
                            ) {
                                WaveformVisualizer(
                                    isListening = isListening,
                                    audioRms = (liveLevel * 10f).coerceIn(0f, 10f),
                                    batterySaverActive = batterySaverActive,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }

                        when (val state = uiState) {
                            is AnswerEngineUiState.Listening -> {
                                val hasLiveDoubtSignal = state.partialTranscript.isNotBlank() &&
                                    ListeningStateManager.classifyTextForChatHighlight(state.partialTranscript) != ChatTurnHighlightState.NEUTRAL
                                val transcriptBg by animateColorAsState(
                                    targetValue = if (hasLiveDoubtSignal) AmberWarning.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                    animationSpec = tween(250),
                                    label = "answer_live_bg"
                                )
                                val transcriptBorder by animateColorAsState(
                                    targetValue = if (hasLiveDoubtSignal) AmberWarning.copy(alpha = 0.45f) else ElectricCyan.copy(alpha = 0.4f),
                                    animationSpec = tween(250),
                                    label = "answer_live_border"
                                )
                                val transcriptTint = if (hasLiveDoubtSignal) AmberWarning else ElectricCyan
                                Text(
                                    text = "${state.modeLabel} • ${state.elapsedSeconds}s (Tap Stop when done)",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = RoseError,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.testTag("answer_engine_listening_status")
                                )
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = transcriptBg,
                                    border = BorderStroke(1.dp, transcriptBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = transcriptTint,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = state.partialTranscript.ifBlank {
                                                "Listening (${listeningCaptureMode.label})... speak your doubt or command."
                                            },
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (state.partialTranscript.isBlank()) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    transcriptTint
                                                }
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("answer_engine_live_transcript")
                                        )
                                        if (hasLiveDoubtSignal) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = AmberWarning.copy(alpha = 0.18f),
                                                modifier = Modifier
                                                    .padding(start = 6.dp)
                                                    .testTag("answer_engine_doubt_signal_pill")
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
                                        IconButton(
                                            onClick = { viewModel.cancelMicrophoneCapture() },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel capture",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            is AnswerEngineUiState.TranscribingAudio -> {
                                Text(
                                    text = "Converting captured microphone audio to text...",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = AmberWarning,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            is AnswerEngineUiState.GeneratingAnswer -> {
                                Text(
                                    text = "Answer Engine streaming verified response for \"${state.query}\"...",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            is AnswerEngineUiState.Error -> {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = RoseError.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, RoseError.copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .testTag("answer_engine_error_banner"),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall.copy(color = RoseError)
                                        )
                                        if (state.isPermissionError) {
                                            if (state.permanentlyDenied) {
                                                OutlinedButton(
                                                    onClick = {
                                                        val intent = Intent(
                                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                            Uri.fromParts("package", context.packageName, null)
                                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        context.startActivity(intent)
                                                    },
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("answer_engine_open_settings_button")
                                                ) {
                                                    Text(stringResource(R.string.action_open_app_settings))
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = ElectricCyan,
                                                        contentColor = Slate950
                                                    ),
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("answer_engine_grant_mic_button")
                                                ) {
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
                            else -> {
                                Text(
                                    text = "Tap mic (${listeningCaptureMode.label}) or say 'Export history', 'Switch to dark mode'",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        // Editable Query Text Input & Submit Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = queryInput,
                                onValueChange = { viewModel.updateQueryInput(it) },
                                placeholder = { Text("Ask any question or edit transcribed text...") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("answer_engine_query_input"),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { viewModel.submitCurrentTextQuery() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.submitCurrentTextQuery() },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ElectricCyan)
                                    .testTag("answer_engine_ask_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Submit question to Answer Engine",
                                    tint = Slate950
                                )
                            }
                        }

                        // Suggested Quick-Questions Horizontal Carousel
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_questions_carousel"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(quickQuestions) { sample ->
                                OutlinedButton(
                                    onClick = {
                                        viewModel.submitQueryToAnswerEngine(sample, capturedViaVoice = false)
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(sample, fontSize = 11.5.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // Streaming Live Answer Display Area (auto-scrolls as tokens stream in)
            if (streamingState != null && streamingState.streamingAnswer.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .testTag("streaming_answer_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.55f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = "LIVE STREAMING ANSWER ($aiResponseLanguage)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(streamingTextScrollState)
                            ) {
                                Text(
                                    text = streamingState.streamingAnswer,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.testTag("streaming_answer_text")
                                )
                            }
                        }
                    }
                }
            }

            // Immediate Answer Output Card
            if (activeResult != null) {
                item {
                    AnswerEngineResultCard(
                        result = activeResult,
                        onFollowUpVoiceClick = {
                            if (viewModel.hasRecordPermission()) {
                                viewModel.startFollowUpCapture(activeResult)
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onSmartReplyClick = { reply ->
                            viewModel.submitFollowUpQuery(reply, activeResult)
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(activeResult) },
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            }

            // Local Room Database Q&A History Section + Search & Quick Filters
            item {
                var showHistoryExportMenu by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .testTag("answer_engine_history_section"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Saved Q&A History",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = "${filteredHistoryItems.size} shown",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Box {
                            OutlinedButton(
                                onClick = { showHistoryExportMenu = true },
                                enabled = recentAnswers.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (recentAnswers.isNotEmpty()) ElectricCyan.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("answer_engine_export_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.cd_export_history),
                                    tint = if (recentAnswers.isNotEmpty()) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_export_history),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (recentAnswers.isNotEmpty()) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showHistoryExportMenu,
                                onDismissRequest = { showHistoryExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_as_text)) },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = ElectricCyan) },
                                    onClick = {
                                        showHistoryExportMenu = false
                                        ShareHelper.exportAnswerEngineHistory(context, recentAnswers, ExportDocumentFormat.STRUCTURED_TEXT)
                                    },
                                    modifier = Modifier.testTag("answer_engine_export_txt")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_as_pdf)) },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = NeonViolet) },
                                    onClick = {
                                        showHistoryExportMenu = false
                                        ShareHelper.exportAnswerEngineHistory(context, recentAnswers, ExportDocumentFormat.PDF)
                                    },
                                    modifier = Modifier.testTag("answer_engine_export_pdf")
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as CSV (.csv)") },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = EmeraldSuccess) },
                                    onClick = {
                                        showHistoryExportMenu = false
                                        ShareHelper.exportAnswerEngineHistory(context, recentAnswers, ExportDocumentFormat.CSV)
                                    },
                                    modifier = Modifier.testTag("answer_engine_export_csv")
                                )
                                DropdownMenuItem(
                                    text = { Text("Print / Save Printable PDF") },
                                    leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, tint = ElectricCyan) },
                                    onClick = {
                                        showHistoryExportMenu = false
                                        val doubts = recentAnswers.map { it.toDoubtItem() }
                                        DocumentExportHelper.printPdfDocument(
                                            context = context,
                                            conversationTurns = emptyList(),
                                            doubts = doubts,
                                            documentTitle = "DoubtSense Printable Study History"
                                        )
                                    },
                                    modifier = Modifier.testTag("answer_engine_print_pdf")
                                )
                            }
                        }
                    }

                    // Semantic & Keyword History Search Bar
                    OutlinedTextField(
                        value = historySearchQuery,
                        onValueChange = { historySearchQuery = it },
                        placeholder = { Text("Semantic or keyword search across saved answers...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search saved answers",
                                tint = ElectricCyan
                            )
                        },
                        trailingIcon = {
                            if (historySearchQuery.isNotBlank()) {
                                IconButton(onClick = { historySearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("answer_history_search_input")
                    )

                    // Quick Filter Row: Recent (7 days) separate from Tag Categories
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = recentOnly7Days,
                            onClick = { recentOnly7Days = !recentOnly7Days },
                            label = { Text("Recent (7d)", fontSize = 11.sp) },
                            modifier = Modifier.testTag("answer_filter_recent_7d")
                        )
                        availableTags.forEach { tag ->
                            val selected = selectedTagFilter.equals(tag, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = { selectedTagFilter = if (selected) null else tag },
                                label = { Text(tag, fontSize = 11.sp) },
                                modifier = Modifier.testTag("answer_tag_chip_$tag")
                            )
                        }
                    }
                }
            }

            if (filteredHistoryItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .testTag("answer_engine_history_empty")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (historySearchQuery.isNotBlank() || recentOnly7Days || selectedTagFilter != null) {
                                    "No saved answers match your active search or filters."
                                } else {
                                    "Questions and AI answers saved locally on this device will appear here for quick review."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            } else {
                items(filteredHistoryItems, key = { it.doubtId.takeIf { id -> id > 0L } ?: it.timestamp }) { item ->
                    val itemKey = item.doubtId.takeIf { id -> id > 0L } ?: item.timestamp
                    SwipeToDeleteContainer(
                        itemKey = itemKey,
                        enabled = item.doubtId > 0L,
                        cornerRadius = 16.dp,
                        onDelete = { viewModel.deleteHistoryItem(item.doubtId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        backgroundTestTag = "answer_history_swipe_bg_${item.doubtId}"
                    ) {
                        AnswerHistoryItemCard(
                            item = item,
                            searchQuery = historySearchQuery,
                            availableTags = availableTags,
                            isSummarizing = summarizingIds.contains(item.doubtId),
                            onSelect = { viewModel.selectRecentAnswer(item) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                            onAssignTag = { tag -> viewModel.updateCustomTag(item, tag) },
                            onGenerateSummary = { viewModel.generateOneSentenceSummary(item) },
                            onDelete = { viewModel.deleteHistoryItem(item.doubtId) },
                            onLongClickDelete = { pendingDeleteItem = item }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(92.dp)) }
        }

        // Floating AI Response Language Selector Button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 16.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = { showLanguageMenu = true },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Select AI response language"
                    )
                },
                text = {
                    val code = AppPreferencesManager.SUPPORTED_LANGUAGES
                        .firstOrNull { it.aiLanguageName.equals(aiResponseLanguage, ignoreCase = true) }
                        ?.shortCode ?: aiResponseLanguage.take(3).uppercase()
                    Text(
                        text = code,
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.testTag("floating_language_button")
            )

            DropdownMenu(
                expanded = showLanguageMenu,
                onDismissRequest = { showLanguageMenu = false }
            ) {
                AppPreferencesManager.SUPPORTED_LANGUAGES
                    .distinctBy { it.aiLanguageName }
                    .forEach { lang ->
                        DropdownMenuItem(
                            text = { Text("${lang.aiLanguageName} (${lang.shortCode})") },
                            onClick = {
                                showLanguageMenu = false
                                viewModel.setAiResponseLanguage(lang.aiLanguageName)
                            },
                            modifier = Modifier.testTag("lang_option_${lang.shortCode}")
                        )
                    }
            }
        }
    }

    pendingDeleteItem?.let { itemToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            modifier = Modifier.testTag("confirm_delete_history_dialog"),
            title = { Text(stringResource(R.string.delete_history_dialog_title)) },
            text = {
                Text("\"${itemToDelete.query}\"\n\nRemove this saved question and AI answer from local Room history?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHistoryItem(itemToDelete.doubtId)
                        pendingDeleteItem = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RoseError),
                    modifier = Modifier.testTag("confirm_delete_history_button")
                ) {
                    Text(stringResource(R.string.action_delete_item))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteItem = null },
                    modifier = Modifier.testTag("cancel_delete_history_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun PipelineStageBadge(
    label: String,
    active: Boolean,
    completed: Boolean
) {
    val bgColor = when {
        completed -> EmeraldSuccess.copy(alpha = 0.18f)
        active -> ElectricCyan.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        completed -> EmeraldSuccess
        active -> ElectricCyan
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = if (active || completed) 0.55f else 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (completed) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = textColor,
                    fontWeight = if (active || completed) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.sp
                )
            )
        }
    }
}

private tailrec fun findActivityFromContext(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> findActivityFromContext(context.baseContext)
    else -> null
}
