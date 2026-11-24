package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.AnswerEngineResult
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.util.AppPreferencesManager
import com.example.util.CalendarHelper
import com.example.util.ClipboardHelper
import com.example.util.ExternalReferenceHelper
import com.example.util.ShareHelper
import com.example.util.TtsManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnswerEngineResultCard(
    result: AnswerEngineResult,
    onFollowUpVoiceClick: (() -> Unit)? = null,
    onSmartReplyClick: ((String) -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showSources by remember(result.timestamp) { mutableStateOf(false) }
    var showFullDetails by remember(result.timestamp) { mutableStateOf(true) }
    var justCopied by remember(result.timestamp) { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(2_000L)
            justCopied = false
        }
    }

    val isSpeaking by TtsManager.isSpeaking.collectAsState()
    val isPaused by TtsManager.isPaused.collectAsState()
    val speakingId by TtsManager.currentlySpeakingDoubtId.collectAsState()
    val spokenText by TtsManager.spokenText.collectAsState()
    val spokenRange by TtsManager.spokenRange.collectAsState()
    val ttsSpeechRate by AppPreferencesManager.ttsSpeechRate.collectAsState()
    val ttsSpeechPitch by AppPreferencesManager.ttsSpeechPitch.collectAsState()

    val ttsKey = result.doubtId.takeIf { it > 0L } ?: result.timestamp
    val isThisSpeaking = isSpeaking && speakingId == ttsKey
    val isThisPaused = isPaused && speakingId == ttsKey
    val isThisActive = isThisSpeaking || isThisPaused

    val speechBody = remember(result.quickAnswer, result.detailedAnswer, showFullDetails) {
        if (showFullDetails &&
            result.detailedAnswer.isNotBlank() &&
            result.detailedAnswer != result.quickAnswer
        ) {
            "${result.quickAnswer} ${result.detailedAnswer}"
        } else {
            result.quickAnswer
        }
    }

    val doubtProxy = remember(result) { result.toDoubtItem() }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val cardContainer = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline

    val activeSpokenHighlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val activeSpokenHighlightFg = MaterialTheme.colorScheme.primary

    val highlightedQuickAnswer = remember(
        result.quickAnswer,
        isThisActive,
        spokenText,
        spokenRange
    ) {
        buildTtsHighlightedText(
            displayText = result.quickAnswer,
            fullSpokenText = if (isThisActive) spokenText else null,
            spokenRange = if (isThisActive) spokenRange else null,
            highlightBg = activeSpokenHighlightBg,
            highlightFg = activeSpokenHighlightFg
        )
    }

    val highlightedDetailedAnswer = remember(
        result.detailedAnswer,
        isThisActive,
        spokenText,
        spokenRange
    ) {
        buildTtsHighlightedText(
            displayText = result.detailedAnswer,
            fullSpokenText = if (isThisActive) spokenText else null,
            spokenRange = if (isThisActive) spokenRange else null,
            highlightBg = activeSpokenHighlightBg,
            highlightFg = activeSpokenHighlightFg
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .testTag("answer_engine_result_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainer),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header badges + Favorite pin button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NeonViolet.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${result.subject} • ${result.topic}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EmeraldSuccess.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = result.groundingLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = EmeraldSuccess,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    if (result.responseLanguage.isNotBlank() && !result.responseLanguage.equals("English", ignoreCase = true)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = result.responseLanguage,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (result.capturedViaVoice) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElectricCyan.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Captured from microphone",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Voice Mic",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }

                if (onToggleFavorite != null && result.doubtId > 0L) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleFavorite()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("answer_engine_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (result.isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (result.isStarred) "Unpin favorite" else "Pin as favorite",
                            tint = if (result.isStarred) RoseError else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Spoken or typed query echo + Doubt Confidence Score indicator
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = surfaceColor
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "\"${result.query}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("answer_engine_echoed_query")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DoubtConfidenceBadge(
                        confidence = result.confidence,
                        modifier = Modifier.testTag("answer_engine_confidence_badge")
                    )
                }
            }

            // Direct Immediate Answer with subtle TTS active-word highlight
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ElectricCyan.copy(alpha = 0.09f),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "IMMEDIATE ANSWER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ElectricCyan,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = highlightedQuickAnswer,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("answer_engine_quick_answer")
                    )
                }
            }

            // Deep Step-by-Step Breakdown
            if (result.detailedAnswer.isNotBlank() && result.detailedAnswer != result.quickAnswer) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = surfaceColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFullDetails = !showFullDetails }
                                .testTag("answer_engine_details_toggle"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Step-by-Step Breakdown & Example",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = NeonViolet,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Icon(
                                imageVector = if (showFullDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle detailed breakdown",
                                tint = NeonViolet
                            )
                        }
                        AnimatedVisibility(visible = showFullDetails) {
                            Text(
                                text = highlightedDetailedAnswer,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .testTag("answer_engine_detailed_answer")
                            )
                        }
                    }
                }
            }

            // Smart Replies (3 context-aware 1-tap follow-up questions)
            if (result.smartReplies.isNotEmpty() && onSmartReplyClick != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_replies_row"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "SMART FOLLOW-UPS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        result.smartReplies.take(3).forEachIndexed { idx, suggestion ->
                            SuggestionChip(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSmartReplyClick(suggestion)
                                },
                                label = {
                                    Text(
                                        text = suggestion,
                                        fontSize = 11.5.sp,
                                        maxLines = 2
                                    )
                                },
                                modifier = Modifier.testTag("smart_reply_$idx")
                            )
                        }
                    }
                }
            }

            // Key Concepts
            if (result.keyConcepts.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    result.keyConcepts.take(5).forEach { concept ->
                        AssistChip(
                            onClick = {},
                            label = { Text(concept, fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = surfaceColor,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            // Verified Sources Expander
            if (result.sources.isNotEmpty()) {
                HorizontalDivider(color = EmeraldSuccess.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EmeraldSuccess.copy(alpha = 0.12f))
                        .clickable { showSources = !showSources }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("answer_engine_sources_toggle"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified Sources",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sources (${result.sources.size})",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = EmeraldSuccess,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Icon(
                        imageVector = if (showSources) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand verified sources",
                        tint = EmeraldSuccess
                    )
                }

                AnimatedVisibility(visible = showSources) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        result.sources.forEachIndexed { idx, src ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = surfaceColor,
                                border = BorderStroke(1.dp, outlineColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { ExternalReferenceHelper.openUrl(context, src.url, src.title) }
                                    .testTag("answer_engine_source_$idx")
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = src.title,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = ElectricCyan,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                        Text(
                                            text = src.url,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = EmeraldSuccess,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = "Open source link",
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Interactive Text-to-Speech Control Panel: Pause/Resume, Speed (0.5x..2.0x), Pitch Slider
            TtsControlPanel(
                isSpeaking = isThisSpeaking,
                isPaused = isThisPaused,
                spokenText = spokenText,
                spokenRange = spokenRange,
                speechRate = ttsSpeechRate,
                speechPitch = ttsSpeechPitch,
                onPlayPauseResume = {
                    if (isThisActive) {
                        TtsManager.togglePauseResume(context)
                    } else {
                        TtsManager.speakAnswer(context, speechBody, ttsKey)
                    }
                },
                onStop = { TtsManager.stop() },
                onSelectSpeed = { AppPreferencesManager.setTtsSpeechRate(context, it) },
                onPitchChange = { AppPreferencesManager.setTtsSpeechPitch(context, it) },
                onResetAudioControls = {
                    AppPreferencesManager.setTtsSpeechRate(context, 1.0f)
                    AppPreferencesManager.setTtsSpeechPitch(context, 1.0f)
                },
                testTagPrefix = "answer_engine"
            )

            // Primary Action Row: Listen, Copy, Share, Follow-up
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        if (isThisActive) {
                            TtsManager.stop()
                        } else {
                            TtsManager.speakAnswer(context, speechBody, ttsKey)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isThisActive) ElectricCyan.copy(alpha = 0.25f) else surfaceColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("answer_engine_listen_button")
                ) {
                    Icon(
                        imageVector = if (isThisActive) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isThisActive) "Stop audio" else "Listen to answer",
                        tint = ElectricCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(if (isThisActive) "Stop" else "Listen", fontSize = 11.sp, maxLines = 1)
                }

                FilledTonalButton(
                    onClick = {
                        val copyText = ClipboardHelper.formatAnswerForClipboard(
                            quickAnswer = result.quickAnswer,
                            detailedAnswer = result.detailedAnswer,
                            includeDetailed = true
                        )
                        if (ClipboardHelper.copyAnswer(context, copyText, label = "DoubtSense Answer")) {
                            justCopied = true
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (justCopied) EmeraldSuccess.copy(alpha = 0.2f) else surfaceColor,
                        contentColor = if (justCopied) EmeraldSuccess else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("answer_engine_copy_button")
                ) {
                    Icon(
                        imageVector = if (justCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_answer),
                        tint = if (justCopied) EmeraldSuccess else ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = stringResource(if (justCopied) R.string.action_copied_answer else R.string.action_copy_answer),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        ShareHelper.shareAnswerEngineResult(context, result)
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = surfaceColor),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("answer_engine_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share AI response",
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Share", fontSize = 11.sp, maxLines = 1)
                }

                if (onFollowUpVoiceClick != null) {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFollowUpVoiceClick()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NeonViolet.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("answer_engine_followup_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Record follow-up doubt",
                            tint = NeonViolet,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Follow-up", fontSize = 10.5.sp, color = NeonViolet, maxLines = 1)
                    }
                }
            }

            // Secondary Row: Schedule Study Review + Google + ChatGPT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        CalendarHelper.scheduleStudyReview(
                            context = context,
                            topic = result.topic,
                            question = result.query,
                            quickAnswer = result.quickAnswer
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, outlineColor),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("answer_engine_schedule_button")
                ) {
                    Icon(Icons.Default.Event, contentDescription = "Schedule study review", tint = EmeraldSuccess, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Schedule", fontSize = 11.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { ExternalReferenceHelper.openInGoogle(context, doubtProxy) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, outlineColor),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("answer_engine_google_button")
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Open Google Search", tint = ElectricCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Google", fontSize = 11.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { ExternalReferenceHelper.openInChatGpt(context, doubtProxy) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("answer_engine_chatgpt_button")
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Ask ChatGPT", tint = NeonViolet, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("ChatGPT", fontSize = 11.sp, color = NeonViolet, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Applies a subtle highlight SpanStyle onto the word currently being spoken by Android TTS.
 */
internal fun buildTtsHighlightedText(
    displayText: String,
    fullSpokenText: String?,
    spokenRange: IntRange?,
    highlightBg: Color,
    highlightFg: Color
): AnnotatedString {
    if (displayText.isBlank() || fullSpokenText.isNullOrBlank() || spokenRange == null) {
        return AnnotatedString(displayText)
    }
    if (spokenRange.first !in fullSpokenText.indices || spokenRange.last !in fullSpokenText.indices || spokenRange.first > spokenRange.last) {
        return AnnotatedString(displayText)
    }

    val spokenWord = fullSpokenText.substring(spokenRange.first, spokenRange.last + 1).trim()
    if (spokenWord.length < 2) return AnnotatedString(displayText)

    // Estimate proportional anchor inside displayText or locate nearest matching token
    val offsetInDisplay = fullSpokenText.indexOf(displayText.take(24)).let { baseIdx ->
        if (baseIdx >= 0) spokenRange.first - baseIdx else -1
    }

    val matchStart = if (offsetInDisplay in displayText.indices &&
        displayText.regionMatches(offsetInDisplay, spokenWord, 0, spokenWord.length, ignoreCase = true)
    ) {
        offsetInDisplay
    } else {
        displayText.indexOf(spokenWord, ignoreCase = true)
    }

    if (matchStart < 0 || matchStart + spokenWord.length > displayText.length) {
        return AnnotatedString(displayText)
    }

    return buildAnnotatedString {
        append(displayText)
        addStyle(
            style = SpanStyle(
                background = highlightBg,
                color = highlightFg,
                fontWeight = FontWeight.Bold
            ),
            start = matchStart,
            end = matchStart + spokenWord.length
        )
    }
}

/**
 * Formats a normalized confidence score (0f..1f) into a percentage and clarity label.
 */
internal fun formatConfidenceIndicator(confidence: Float): Pair<Int, String> {
    val pct = (confidence.coerceIn(0f, 1f) * 100f).toInt()
    val label = when {
        pct >= 80 -> "$pct% clear"
        pct >= 60 -> "$pct% fair"
        else -> "$pct% unclear"
    }
    return pct to label
}

/**
 * Compact pill displayed alongside a user's question showing how clearly the AI understood intent.
 */
@Composable
fun DoubtConfidenceBadge(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val (pct, label) = remember(confidence) { formatConfidenceIndicator(confidence) }
    val tint = when {
        pct >= 80 -> EmeraldSuccess
        pct >= 60 -> ElectricCyan
        else -> AmberWarning
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.42f)),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Doubt intent confidence $pct percent"
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TrackChanges,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = tint,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                maxLines = 1
            )
        }
    }
}
