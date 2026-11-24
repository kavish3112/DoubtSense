package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeUp
import com.example.util.AppPreferencesManager
import com.example.util.CalendarHelper
import com.example.util.ExternalReferenceHelper
import com.example.util.SemanticSearchHelper
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.ContextAnalysisService
import com.example.data.model.DoubtItem
import com.example.service.ListeningStateManager
import com.example.util.ClipboardHelper
import com.example.util.ShareFormat
import com.example.util.ShareHelper
import com.example.util.TtsManager
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate800
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DoubtCard(
    doubt: DoubtItem,
    modifier: Modifier = Modifier,
    initialExpandedDetailed: Boolean = false,
    searchQuery: String = "",
    availableTags: List<String> = AppPreferencesManager.DEFAULT_TAGS,
    isSummarizing: Boolean = false,
    onToggleStar: ((DoubtItem) -> Unit)? = null,
    onAssignTag: ((DoubtItem, String) -> Unit)? = null,
    onGenerateSummary: ((DoubtItem) -> Unit)? = null,
    onDelete: ((DoubtItem) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showDetailed by remember(doubt.id) { mutableStateOf(initialExpandedDetailed) }
    var showShareMenu by remember { mutableStateOf(false) }
    var showTagMenu by remember { mutableStateOf(false) }
    var showInteractiveClarification by remember(doubt.id) { mutableStateOf(doubt.isAmbiguous) }
    var showSources by remember(doubt.id) { mutableStateOf(false) }
    var showTtsControls by remember(doubt.id) { mutableStateOf(false) }
    var justCopied by remember(doubt.id) { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(2_000L)
            justCopied = false
        }
    }

    val factCheckingMode by ListeningStateManager.factCheckingMode.collectAsState()
    val isRefiningAnswer by ListeningStateManager.isRefiningAnswer.collectAsState()
    val isTtsSpeaking by TtsManager.isSpeaking.collectAsState()
    val isTtsPaused by TtsManager.isPaused.collectAsState()
    val currentlySpeakingId by TtsManager.currentlySpeakingDoubtId.collectAsState()
    val spokenText by TtsManager.spokenText.collectAsState()
    val spokenRange by TtsManager.spokenRange.collectAsState()
    val ttsSpeechRate by AppPreferencesManager.ttsSpeechRate.collectAsState()
    val ttsSpeechPitch by AppPreferencesManager.ttsSpeechPitch.collectAsState()

    val isThisDoubtSpeaking = isTtsSpeaking && currentlySpeakingId == doubt.id
    val isThisDoubtPaused = isTtsPaused && currentlySpeakingId == doubt.id
    val isThisDoubtActive = isThisDoubtSpeaking || isThisDoubtPaused

    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val highlightFg = MaterialTheme.colorScheme.primary

    val verificationSources = remember(doubt.verificationSourcesJson, doubt.isFactChecked, factCheckingMode) {
        val stored = doubt.getVerificationSources()
        when {
            stored.isNotEmpty() -> stored
            factCheckingMode || doubt.isFactChecked -> ContextAnalysisService.defaultVerificationSourcesForTopic(
                subject = doubt.subject,
                topic = doubt.topic,
                queryText = doubt.doubtSnippet
            )
            else -> emptyList()
        }
    }
    val hasSourcesExpander = (factCheckingMode || doubt.isFactChecked) && verificationSources.isNotEmpty()

    val formattedTime = remember(doubt.timestamp) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(doubt.timestamp))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = onLongClick,
                        onLongClickLabel = stringResource(R.string.action_delete_item)
                    )
                } else {
                    Modifier
                }
            )
            .testTag("doubt_card_${doubt.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            1.dp,
            if (doubt.isStarred) RoseError.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Topic + Custom Tag badges + Date + Favorite Heart/Delete actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = NeonViolet.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = doubt.topic,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = ElectricCyan
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box {
                        Surface(
                            onClick = { if (onAssignTag != null) showTagMenu = true },
                            color = ElectricCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("doubt_tag_chip_${doubt.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = "Category tag",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = doubt.customTag.ifBlank { "Education" },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showTagMenu,
                            onDismissRequest = { showTagMenu = false }
                        ) {
                            availableTags.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text(tag) },
                                    onClick = {
                                        showTagMenu = false
                                        onAssignTag?.invoke(doubt, tag)
                                    }
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.5.sp
                        )
                    )

                    if (onToggleStar != null) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleStar(doubt)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("favorite_button_${doubt.id}")
                        ) {
                            Icon(
                                imageVector = if (doubt.isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (doubt.isStarred) "Unpin favorite" else "Pin as favorite",
                                tint = if (doubt.isStarred) RoseError else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (onDelete != null) {
                        IconButton(
                            onClick = { onDelete(doubt) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete doubt",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ambiguity Clarification Banner (if doubt is ambiguous)
            if (doubt.isAmbiguous && !doubt.clarificationQuestion.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AmberWarning.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Ambiguity Alert",
                                tint = AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ambiguous Context Detected",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AmberWarning
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = doubt.clarificationQuestion,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showInteractiveClarification = !showInteractiveClarification },
                            border = BorderStroke(1.dp, AmberWarning),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = if (showInteractiveClarification) "Hide Clarification" else "Interactive Clarification",
                                color = AmberWarning,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Quoted Doubt Utterance
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
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
                    Spacer(modifier = Modifier.width(10.dp))
                    val highlightedSnippet = remember(doubt.doubtSnippet, searchQuery, highlightBg, highlightFg) {
                        SemanticSearchHelper.buildHighlightedAnnotatedString(
                            text = "\"${doubt.doubtSnippet}\"",
                            query = searchQuery,
                            highlightBackground = highlightBg,
                            highlightForeground = highlightFg
                        )
                    }
                    Text(
                        text = highlightedSnippet,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DoubtConfidenceBadge(
                        confidence = doubt.confidenceScore,
                        modifier = Modifier.testTag("doubt_confidence_badge_${doubt.id}")
                    )
                }
            }

            // One-Sentence Summary row or generator button
            if (doubt.oneSentenceSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldSuccess.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doubt_summary_text_${doubt.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = doubt.oneSentenceSummary,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = EmeraldSuccess,
                                fontStyle = FontStyle.Italic,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            } else if (onGenerateSummary != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onGenerateSummary(doubt) },
                    enabled = !isSummarizing,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("doubt_summary_button_${doubt.id}")
                ) {
                    if (isSummarizing) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Summarizing...", fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.Summarize, contentDescription = "Generate one-sentence summary", tint = ElectricCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("1-Sentence Summary", fontSize = 11.sp)
                    }
                }
            }

            // Tailored Clarification Resolution Indicator
            if (doubt.isResolvedWithClarification) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = EmeraldSuccess.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✨ Tailored Answer: Clarified on '${doubt.userClarificationResponse}' (${doubt.preferredStyle})",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = EmeraldSuccess,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notification summary chip
            if (doubt.notificationSummary.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Notification Summary: ${doubt.notificationSummary}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = EmeraldSuccess,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // Answer Mode & Clarification Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !showDetailed,
                    onClick = { showDetailed = false },
                    label = { Text("⚡ Quick Answer") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.25f),
                        selectedLabelColor = ElectricCyan
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = !showDetailed,
                        borderColor = Slate800,
                        selectedBorderColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("toggle_quick_${doubt.id}")
                )

                FilterChip(
                    selected = showDetailed,
                    onClick = { showDetailed = true },
                    label = { Text("📖 Detailed Answer") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonViolet.copy(alpha = 0.25f),
                        selectedLabelColor = NeonViolet
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = showDetailed,
                        borderColor = Slate800,
                        selectedBorderColor = NeonViolet
                    ),
                    modifier = Modifier.testTag("toggle_detailed_${doubt.id}")
                )

                FilterChip(
                    selected = showInteractiveClarification,
                    onClick = { showInteractiveClarification = !showInteractiveClarification },
                    label = { Text("✨ Clarify & Context") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberWarning.copy(alpha = 0.25f),
                        selectedLabelColor = AmberWarning
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = showInteractiveClarification,
                        borderColor = Slate800,
                        selectedBorderColor = AmberWarning
                    ),
                    modifier = Modifier.testTag("toggle_clarify_${doubt.id}")
                )
            }

            // Interactive Clarification Component Expansion
            if (showInteractiveClarification) {
                Spacer(modifier = Modifier.height(14.dp))
                InteractiveClarificationCard(doubt = doubt)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Answer Content Container (AI Response Bubble with Fact-Checking Sources Expander)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(14.dp),
                color = if (showDetailed) NeonViolet.copy(alpha = 0.08f) else ElectricCyan.copy(alpha = 0.08f),
                border = BorderStroke(
                    1.dp,
                    if (showDetailed) NeonViolet.copy(alpha = 0.25f) else ElectricCyan.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (!showDetailed) {
                        val quickDisplay = remember(doubt.quickAnswer, searchQuery, isThisDoubtActive, spokenText, spokenRange) {
                            if (searchQuery.isNotBlank()) {
                                SemanticSearchHelper.buildHighlightedAnnotatedString(doubt.quickAnswer, searchQuery, highlightBg, highlightFg)
                            } else {
                                buildTtsHighlightedText(
                                    displayText = doubt.quickAnswer,
                                    fullSpokenText = if (isThisDoubtActive) spokenText else null,
                                    spokenRange = if (isThisDoubtActive) spokenRange else null,
                                    highlightBg = highlightBg,
                                    highlightFg = highlightFg
                                )
                            }
                        }
                        Text(
                            text = quickDisplay,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    } else {
                        val detailedDisplay = remember(doubt.detailedAnswer, searchQuery, isThisDoubtActive, spokenText, spokenRange) {
                            if (searchQuery.isNotBlank()) {
                                SemanticSearchHelper.buildHighlightedAnnotatedString(doubt.detailedAnswer, searchQuery, highlightBg, highlightFg)
                            } else {
                                buildTtsHighlightedText(
                                    displayText = doubt.detailedAnswer,
                                    fullSpokenText = if (isThisDoubtActive) spokenText else null,
                                    spokenRange = if (isThisDoubtActive) spokenRange else null,
                                    highlightBg = highlightBg,
                                    highlightFg = highlightFg
                                )
                            }
                        }
                        Text(
                            text = detailedDisplay,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    // 'Sources' toggle / expander when Fact-Checking Mode is active or doubt is fact-checked
                    if (hasSourcesExpander) {
                        Spacer(modifier = Modifier.height(10.dp))
                        androidx.compose.material3.HorizontalDivider(
                            color = EmeraldSuccess.copy(alpha = 0.25f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(EmeraldSuccess.copy(alpha = 0.12f))
                                .clickable { showSources = !showSources }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("sources_toggle_${doubt.id}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Sources",
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sources (${verificationSources.size})",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = EmeraldSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (showSources) "Hide references" else "View URLs & snippets",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(
                                imageVector = if (showSources) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (showSources) "Collapse sources" else "Expand sources",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(visible = showSources) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .testTag("fact_checked_panel_${doubt.id}"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = doubt.verificationStatus.ifBlank {
                                        "Fact-Checked • Verified External References"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = EmeraldSuccess,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.testTag("fact_checked_badge_${doubt.id}")
                                )

                                verificationSources.forEachIndexed { idx, source ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                        border = BorderStroke(1.dp, Slate800),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                ExternalReferenceHelper.openUrl(context, source.url, source.title)
                                            }
                                            .testTag("verified_source_link_${doubt.id}_$idx")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = source.title,
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        color = ElectricCyan,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = source.url,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = EmeraldSuccess.copy(alpha = 0.9f),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (source.excerpt.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "\"${source.excerpt}\"",
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 11.sp,
                                                            lineHeight = 16.sp
                                                        )
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.OpenInBrowser,
                                                contentDescription = "Open ${source.title}",
                                                tint = EmeraldSuccess,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Context note & Action buttons (Listen, Copy, Share)
            if (doubt.conversationalContext.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Inferred from conversation",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            AnimatedVisibility(visible = isThisDoubtActive || showTtsControls) {
                Column(modifier = Modifier.padding(bottom = 10.dp)) {
                    TtsControlPanel(
                        isSpeaking = isThisDoubtSpeaking,
                        isPaused = isThisDoubtPaused,
                        spokenText = spokenText,
                        spokenRange = spokenRange,
                        speechRate = ttsSpeechRate,
                        speechPitch = ttsSpeechPitch,
                        onPlayPauseResume = {
                            if (isThisDoubtActive) {
                                TtsManager.togglePauseResume(context)
                            } else {
                                val textToSpeak = if (showDetailed) doubt.detailedAnswer else doubt.quickAnswer
                                TtsManager.speakAnswer(context, textToSpeak, doubt.id)
                            }
                        },
                        onStop = { TtsManager.stop() },
                        onSelectSpeed = { AppPreferencesManager.setTtsSpeechRate(context, it) },
                        onPitchChange = { AppPreferencesManager.setTtsSpeechPitch(context, it) },
                        onResetAudioControls = {
                            AppPreferencesManager.setTtsSpeechRate(context, 1.0f)
                            AppPreferencesManager.setTtsSpeechPitch(context, 1.0f)
                        },
                        testTagPrefix = "doubt_${doubt.id}"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Read Aloud (TTS) Button
                FilledTonalButton(
                    onClick = {
                        val textToSpeak = if (showDetailed) doubt.detailedAnswer else doubt.quickAnswer
                        TtsManager.speakAnswer(context, textToSpeak, doubt.id)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = if (isThisDoubtActive) {
                        ButtonDefaults.filledTonalButtonColors(containerColor = ElectricCyan.copy(alpha = 0.35f))
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("read_aloud_button_${doubt.id}")
                ) {
                    Icon(
                        imageVector = if (isThisDoubtActive) Icons.Default.StopCircle else Icons.Default.VolumeUp,
                        contentDescription = if (isThisDoubtActive) "Stop reading aloud" else "Read answer aloud",
                        tint = if (isThisDoubtActive) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isThisDoubtActive) "Stop" else "Listen",
                        fontSize = 12.sp,
                        maxLines = 1,
                        color = if (isThisDoubtActive) ElectricCyan else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = { showTtsControls = !showTtsControls },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("tts_controls_toggle_${doubt.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = if (showTtsControls) "Hide voice speed and pitch controls" else "Show voice speed and pitch controls",
                        tint = if (showTtsControls || isThisDoubtActive) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Copy Answer button
                FilledTonalButton(
                    onClick = {
                        val textToCopy = ClipboardHelper.formatAnswerForClipboard(
                            quickAnswer = doubt.quickAnswer,
                            detailedAnswer = doubt.detailedAnswer,
                            includeDetailed = showDetailed
                        )
                        if (ClipboardHelper.copyAnswer(context, textToCopy, label = "DoubtSense Answer")) {
                            justCopied = true
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = if (justCopied) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = EmeraldSuccess.copy(alpha = 0.22f),
                            contentColor = EmeraldSuccess
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("copy_button_${doubt.id}")
                ) {
                    Icon(
                        imageVector = if (justCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_answer),
                        tint = if (justCopied) EmeraldSuccess else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(if (justCopied) R.string.action_copied_answer else R.string.action_copy_answer),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                // Share Answer with options + Haptic Feedback
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showShareMenu = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("share_button_${doubt.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share answer",
                            tint = ElectricCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", color = ElectricCyan, fontSize = 12.sp, maxLines = 1)
                    }

                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📄 Export as PDF Document") },
                                onClick = {
                                    showShareMenu = false
                                    ShareHelper.shareDoubt(context, doubt, ShareFormat.PDF_DOCUMENT)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📝 Export as Structured Text (.txt)") },
                                onClick = {
                                    showShareMenu = false
                                    ShareHelper.shareDoubt(context, doubt, ShareFormat.STRUCTURED_TEXT_DOCUMENT)
                                }
                            )
                            androidx.compose.material3.HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("📤 Share Both (Quick + Detailed)") },
                                onClick = {
                                    showShareMenu = false
                                    ShareHelper.shareDoubt(context, doubt, ShareFormat.FULL_SUMMARY)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("⚡ Share Quick Answer Only") },
                                onClick = {
                                    showShareMenu = false
                                    ShareHelper.shareDoubt(context, doubt, ShareFormat.QUICK_ONLY)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📖 Share Detailed Notes") },
                                onClick = {
                                    showShareMenu = false
                                    ShareHelper.shareDoubt(context, doubt, ShareFormat.DETAILED_ONLY)
                                }
                            )
                        }
                    }
                }

            Spacer(modifier = Modifier.height(10.dp))

            // On-demand Deepen & Verify Answer + External Reference Bar (Google / ChatGPT)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        showDetailed = true
                        ListeningStateManager.deepenAndVerifyDoubt(doubt, context)
                    },
                    enabled = !isRefiningAnswer,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = EmeraldSuccess.copy(alpha = 0.16f),
                        contentColor = EmeraldSuccess
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.05f)
                        .height(40.dp)
                        .testTag("deepen_verify_${doubt.id}")
                ) {
                    if (isRefiningAnswer) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = EmeraldSuccess
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verifying", fontSize = 11.sp, maxLines = 1)
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Deepen and verify answer with AI",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Deepen AI", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                OutlinedButton(
                    onClick = { ExternalReferenceHelper.openInGoogle(context, doubt) },
                    border = BorderStroke(1.dp, Slate800),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("refer_google_${doubt.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search on Google",
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Google", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        CalendarHelper.scheduleStudyReview(context = context, doubt = doubt)
                    },
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("schedule_review_${doubt.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = "Schedule study review",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                }

                OutlinedButton(
                    onClick = { ExternalReferenceHelper.openInChatGpt(context, doubt) },
                    border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("refer_chatgpt_${doubt.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Refer to ChatGPT",
                        tint = NeonViolet,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ChatGPT", fontSize = 11.sp, color = NeonViolet, maxLines = 1)
                }
            }
        }
    }
}
