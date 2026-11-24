package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ai.ContextAnalysisService
import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import com.example.data.repository.DoubtRepository
import com.example.service.ListeningStateManager
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate950
import com.example.util.AppPreferencesManager
import com.example.util.ClipboardHelper
import com.example.util.DocumentExportHelper
import com.example.util.ExportDocumentFormat
import com.example.util.ExternalReferenceHelper
import com.example.util.SemanticSearchHelper
import com.example.util.ShareFormat
import com.example.util.ShareHelper
import com.example.util.TtsManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SessionLogCard(
    session: ConversationSession,
    searchQuery: String = "",
    availableTags: List<String> = AppPreferencesManager.DEFAULT_TAGS,
    isSelected: Boolean = false,
    onOpenDetail: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    onAssignTag: (String) -> Unit = {},
    onCreateCustomTag: (String) -> Unit = {},
    onExportPdf: () -> Unit,
    onExportText: () -> Unit,
    onSaveSessionAsText: () -> Unit = {},
    onSaveSessionAsPdf: () -> Unit = {},
    onDelete: () -> Unit
) {
    val dateLabel = remember(session.startTime) {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(session.startTime))
    }
    val durationLabel = remember(session.startTime, session.endTime) {
        formatSessionDuration(session.startTime, session.endTime)
    }
    val turnsCount = remember(session.transcriptJson) { session.getTranscriptTurns().size }
    val intensityPct = (session.averageDoubtIntensity * 100).toInt()
    val intensityColor = when {
        session.averageDoubtIntensity >= 0.70f -> RoseError
        session.averageDoubtIntensity >= 0.40f -> AmberWarning
        else -> EmeraldSuccess
    }
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val highlightFg = MaterialTheme.colorScheme.primary
    var showTagPicker by remember(session.id) { mutableStateOf(false) }
    val assignedTags = remember(session.customTag) {
        session.getTagList().ifEmpty { listOf("General") }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpenDetail,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.action_delete_item)
            )
            .testTag("session_card_${session.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = when {
                isSelected -> ElectricCyan
                session.isPinned -> RoseError.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = ElectricCyan.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = session.category.ifBlank { "General" },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    assignedTags.forEachIndexed { idx, tagLabel ->
                        val accent = tagAccentColor(tagLabel)
                        Surface(
                            onClick = { showTagPicker = true },
                            color = accent.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.75.dp, accent.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .then(
                                    if (idx == 0) Modifier.testTag("session_tag_chip_${session.id}")
                                    else Modifier.testTag("session_tag_chip_${session.id}_$idx")
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Label,
                                    contentDescription = "Session category tag $tagLabel",
                                    tint = accent,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = tagLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = accent,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { showTagPicker = true },
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .testTag("session_add_tag_pill_${session.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Edit session tags",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Tag",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("pin_session_button_${session.id}")
                    ) {
                        Icon(
                            imageVector = if (session.isPinned) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (session.isPinned) "Unpin session" else "Pin session",
                            tint = if (session.isPinned) RoseError else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_session_button_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete session",
                            tint = RoseError.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val rawTitle = session.title.ifBlank { "Conversation Session" }
            val annotatedTitle = remember(rawTitle, searchQuery, highlightBg, highlightFg) {
                SemanticSearchHelper.buildHighlightedAnnotatedString(rawTitle, searchQuery, highlightBg, highlightFg)
            }
            Text(
                text = annotatedTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (session.transcriptPreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                val previewText = "\"${session.transcriptPreview}\""
                val annotatedPreview = remember(previewText, searchQuery, highlightBg, highlightFg) {
                    SemanticSearchHelper.buildHighlightedAnnotatedString(previewText, searchQuery, highlightBg, highlightFg)
                }
                Text(
                    text = annotatedPreview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (session.summary.isNotBlank() && !session.summary.equals(session.transcriptPreview, ignoreCase = true)) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldSuccess.copy(alpha = 0.11f),
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth()
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
                        val annotatedSummary = remember(session.summary, searchQuery, highlightBg, highlightFg) {
                            SemanticSearchHelper.buildHighlightedAnnotatedString(
                                session.summary,
                                searchQuery,
                                highlightBg,
                                highlightFg
                            )
                        }
                        Text(
                            text = annotatedSummary,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = EmeraldSuccess,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = intensityColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = "Avg Doubt: $intensityPct%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = intensityColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "$turnsCount turns • ${session.doubtCount} doubt(s) • $durationLabel",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onOpenDetail,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ElectricCyan.copy(alpha = 0.16f),
                            contentColor = ElectricCyan
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("session_revisit_button_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Revisit Answers",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    var showCardExportMenu by remember(session.id) { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { showCardExportMenu = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("session_card_export_button_${session.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export session as text or PDF",
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Export",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        DropdownMenu(
                            expanded = showCardExportMenu,
                            onDismissRequest = { showCardExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save as Text (.txt)") },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = NeonViolet)
                                },
                                onClick = {
                                    showCardExportMenu = false
                                    onSaveSessionAsText()
                                },
                                modifier = Modifier.testTag("session_save_txt_${session.id}")
                            )
                            DropdownMenuItem(
                                text = { Text("Save as PDF (.pdf)") },
                                leadingIcon = {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ElectricCyan)
                                },
                                onClick = {
                                    showCardExportMenu = false
                                    onSaveSessionAsPdf()
                                },
                                modifier = Modifier.testTag("session_save_pdf_${session.id}")
                            )
                            DropdownMenuItem(
                                text = { Text("Share Text (.txt)") },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = NeonViolet)
                                },
                                onClick = {
                                    showCardExportMenu = false
                                    onExportText()
                                },
                                modifier = Modifier.testTag("session_share_txt_${session.id}")
                            )
                            DropdownMenuItem(
                                text = { Text("Share PDF (.pdf)") },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = ElectricCyan)
                                },
                                onClick = {
                                    showCardExportMenu = false
                                    onExportPdf()
                                },
                                modifier = Modifier.testTag("session_share_pdf_${session.id}")
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onExportPdf,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("export_session_pdf_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Export session PDF",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onExportText,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("export_session_txt_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Export session text",
                            tint = NeonViolet,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showTagPicker) {
        SessionTagPickerDialog(
            sessionTitle = session.title.ifBlank { "Conversation Session" },
            initialTags = assignedTags.filterNot { it.equals("General", ignoreCase = true) },
            availableTags = availableTags,
            onCreateCustomTag = onCreateCustomTag,
            onConfirmTags = { encoded ->
                showTagPicker = false
                onAssignTag(encoded)
            },
            onDismiss = { showTagPicker = false }
        )
    }
}

@Composable
fun SessionDetailDialog(
    session: ConversationSession,
    repository: DoubtRepository,
    availableTags: List<String> = AppPreferencesManager.DEFAULT_TAGS,
    exportProgress: Float? = null,
    onToggleSessionPin: (ConversationSession) -> Unit = {},
    onAssignSessionTag: (ConversationSession, String) -> Unit = { _, _ -> },
    onCreateCustomTag: (String) -> Unit = {},
    onToggleDoubtStar: (DoubtItem) -> Unit = {},
    onRevisitDoubtInAnswerEngine: (DoubtItem) -> Unit = {},
    onSaveSessionAsText: (ConversationSession) -> Unit = {},
    onSaveSessionAsPdf: (ConversationSession) -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 720.dp)
                .testTag("session_detail_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
        ) {
            SessionDetailBody(
                session = session,
                repository = repository,
                availableTags = availableTags,
                exportProgress = exportProgress,
                onToggleSessionPin = onToggleSessionPin,
                onAssignSessionTag = onAssignSessionTag,
                onCreateCustomTag = onCreateCustomTag,
                onToggleDoubtStar = onToggleDoubtStar,
                onRevisitDoubtInAnswerEngine = { doubt ->
                    onDismiss()
                    onRevisitDoubtInAnswerEngine(doubt)
                },
                onSaveSessionAsText = onSaveSessionAsText,
                onSaveSessionAsPdf = onSaveSessionAsPdf,
                onClose = onDismiss
            )
        }
    }
}

@Composable
fun SessionDetailPane(
    session: ConversationSession?,
    repository: DoubtRepository,
    availableTags: List<String> = AppPreferencesManager.DEFAULT_TAGS,
    exportProgress: Float? = null,
    onToggleSessionPin: (ConversationSession) -> Unit = {},
    onAssignSessionTag: (ConversationSession, String) -> Unit = { _, _ -> },
    onCreateCustomTag: (String) -> Unit = {},
    onToggleDoubtStar: (DoubtItem) -> Unit = {},
    onRevisitDoubtInAnswerEngine: (DoubtItem) -> Unit = {},
    onSaveSessionAsText: (ConversationSession) -> Unit = {},
    onSaveSessionAsPdf: (ConversationSession) -> Unit = {},
    onClearSelection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxSize()
            .testTag("session_detail_pane"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.32f))
    ) {
        if (session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Select a session to revisit",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Inspect full conversation transcripts, step-by-step AI answers, key concepts, sources, and audio playback.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        } else {
            SessionDetailBody(
                session = session,
                repository = repository,
                availableTags = availableTags,
                exportProgress = exportProgress,
                onToggleSessionPin = onToggleSessionPin,
                onAssignSessionTag = onAssignSessionTag,
                onCreateCustomTag = onCreateCustomTag,
                onToggleDoubtStar = onToggleDoubtStar,
                onRevisitDoubtInAnswerEngine = onRevisitDoubtInAnswerEngine,
                onSaveSessionAsText = onSaveSessionAsText,
                onSaveSessionAsPdf = onSaveSessionAsPdf,
                onClose = onClearSelection
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionDetailBody(
    session: ConversationSession,
    repository: DoubtRepository,
    availableTags: List<String>,
    exportProgress: Float?,
    onToggleSessionPin: (ConversationSession) -> Unit,
    onAssignSessionTag: (ConversationSession, String) -> Unit,
    onCreateCustomTag: (String) -> Unit,
    onToggleDoubtStar: (DoubtItem) -> Unit,
    onRevisitDoubtInAnswerEngine: (DoubtItem) -> Unit,
    onSaveSessionAsText: (ConversationSession) -> Unit,
    onSaveSessionAsPdf: (ConversationSession) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val sessionDoubts by repository.getDoubtsForSession(session.id).collectAsState(initial = emptyList())
    val turns = remember(session.transcriptJson) { session.getTranscriptTurns() }
    val dateStr = remember(session.startTime) {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(session.startTime))
    }
    val durationLabel = remember(session.startTime, session.endTime) {
        formatSessionDuration(session.startTime, session.endTime)
    }
    var showTagPicker by remember(session.id) { mutableStateOf(false) }
    val assignedTags = remember(session.customTag) {
        session.getTagList().ifEmpty { listOf("General") }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = session.category.ifBlank { "General" },
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    assignedTags.forEachIndexed { idx, tagLabel ->
                        val accent = tagAccentColor(tagLabel)
                        Surface(
                            onClick = { showTagPicker = true },
                            color = accent.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.75.dp, accent.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .then(
                                    if (idx == 0) Modifier.testTag("session_detail_tag_chip")
                                    else Modifier.testTag("session_detail_tag_chip_$idx")
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Label,
                                    contentDescription = "Session tag $tagLabel",
                                    tint = accent,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = tagLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = accent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { showTagPicker = true },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .testTag("session_detail_add_tag_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add or edit session tags",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Edit Tags",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = session.title.ifBlank { "Conversation Session" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = "$dateStr • $durationLabel",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onToggleSessionPin(session) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("session_detail_pin_button")
                ) {
                    Icon(
                        imageVector = if (session.isPinned) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (session.isPinned) "Unpin session" else "Pin session",
                        tint = if (session.isPinned) RoseError else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("session_detail_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close session details")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Intensity & session metrics
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(session.averageDoubtIntensity * 100).toInt()}%",
                            fontWeight = FontWeight.ExtraBold,
                            color = ElectricCyan
                        )
                        Text("Avg Intensity", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(session.peakDoubtIntensity * 100).toInt()}%",
                            fontWeight = FontWeight.ExtraBold,
                            color = AmberWarning
                        )
                        Text("Peak Intensity", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${sessionDoubts.size.coerceAtLeast(session.doubtCount)}",
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonViolet
                        )
                        Text("Doubts", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${turns.size}",
                            fontWeight = FontWeight.ExtraBold,
                            color = EmeraldSuccess
                        )
                        Text("Turns", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (session.summary.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldSuccess.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Session Takeaway",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = EmeraldSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = session.summary,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }

            // Transcript Turns
            Text(
                text = "Conversation Transcript (${turns.size} turns)",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (turns.isEmpty()) {
                        Text(
                            text = session.transcriptPreview.ifBlank { "No transcript recorded." },
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        turns.forEachIndexed { idx, turn ->
                            Text(
                                text = "${idx + 1}. $turn",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }

            // Linked Doubts & Past AI-Generated Answers
            Text(
                text = "Detected Doubts & AI Answers (${sessionDoubts.size})",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            if (sessionDoubts.isEmpty()) {
                Text(
                    text = session.summary.ifBlank { "No AI-generated doubt answers linked to this session yet." },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            } else {
                sessionDoubts.forEach { doubt ->
                    SessionDoubtAnswerCard(
                        doubt = doubt,
                        onToggleStar = { onToggleDoubtStar(doubt) },
                        onRevisitInAnswerEngine = { onRevisitDoubtInAnswerEngine(doubt) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        if (exportProgress != null) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { exportProgress.coerceIn(0f, 1f) },
                color = ElectricCyan,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_detail_export_progress")
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        var showDetailExportMenu by remember(session.id) { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = { showDetailExportMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Slate950),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("session_detail_export_button")
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                DropdownMenu(
                    expanded = showDetailExportMenu,
                    onDismissRequest = { showDetailExportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Save as Text File (.txt)") },
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null, tint = NeonViolet)
                        },
                        onClick = {
                            showDetailExportMenu = false
                            onSaveSessionAsText(session)
                        },
                        modifier = Modifier.testTag("session_detail_save_txt")
                    )
                    DropdownMenuItem(
                        text = { Text("Save as PDF File (.pdf)") },
                        leadingIcon = {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ElectricCyan)
                        },
                        onClick = {
                            showDetailExportMenu = false
                            onSaveSessionAsPdf(session)
                        },
                        modifier = Modifier.testTag("session_detail_save_pdf")
                    )
                    DropdownMenuItem(
                        text = { Text("Share as Text (.txt)") },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null, tint = NeonViolet)
                        },
                        onClick = {
                            showDetailExportMenu = false
                            ShareHelper.exportSession(context, session, sessionDoubts, ExportDocumentFormat.STRUCTURED_TEXT)
                        },
                        modifier = Modifier.testTag("session_detail_share_txt")
                    )
                    DropdownMenuItem(
                        text = { Text("Share as PDF (.pdf)") },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null, tint = ElectricCyan)
                        },
                        onClick = {
                            showDetailExportMenu = false
                            ShareHelper.exportSession(context, session, sessionDoubts, ExportDocumentFormat.PDF)
                        },
                        modifier = Modifier.testTag("session_detail_share_pdf")
                    )
                    DropdownMenuItem(
                        text = { Text("Export as CSV (.csv)") },
                        leadingIcon = {
                            Icon(Icons.Default.TableChart, contentDescription = null, tint = EmeraldSuccess)
                        },
                        onClick = {
                            showDetailExportMenu = false
                            ShareHelper.exportSession(context, session, sessionDoubts, ExportDocumentFormat.CSV)
                        },
                        modifier = Modifier.testTag("session_detail_export_csv")
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    DocumentExportHelper.printPdfDocument(
                        context = context,
                        conversationTurns = turns,
                        doubts = sessionDoubts,
                        documentTitle = session.title
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("session_detail_print_pdf_button")
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print PDF", fontSize = 12.sp)
            }
        }
    }

    if (showTagPicker) {
        SessionTagPickerDialog(
            sessionTitle = session.title.ifBlank { "Conversation Session" },
            initialTags = assignedTags.filterNot { it.equals("General", ignoreCase = true) },
            availableTags = availableTags,
            onCreateCustomTag = onCreateCustomTag,
            onConfirmTags = { encoded ->
                showTagPicker = false
                onAssignSessionTag(session, encoded)
            },
            onDismiss = { showTagPicker = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionDoubtAnswerCard(
    doubt: DoubtItem,
    onToggleStar: () -> Unit,
    onRevisitInAnswerEngine: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isTtsSpeaking by TtsManager.isSpeaking.collectAsState()
    val isTtsPaused by TtsManager.isPaused.collectAsState()
    val speakingId by TtsManager.currentlySpeakingDoubtId.collectAsState()
    val spokenText by TtsManager.spokenText.collectAsState()
    val spokenRange by TtsManager.spokenRange.collectAsState()
    val ttsSpeechRate by AppPreferencesManager.ttsSpeechRate.collectAsState()
    val ttsSpeechPitch by AppPreferencesManager.ttsSpeechPitch.collectAsState()
    val factCheckingMode by ListeningStateManager.factCheckingMode.collectAsState()

    val isThisDoubtSpeaking = isTtsSpeaking && speakingId == doubt.id
    val isThisDoubtPaused = isTtsPaused && speakingId == doubt.id
    val isThisDoubtActive = isThisDoubtSpeaking || isThisDoubtPaused

    var showDetailed by remember(doubt.id) { mutableStateOf(false) }
    var showDoubtSources by remember(doubt.id) { mutableStateOf(false) }
    var showTtsControls by remember(doubt.id) { mutableStateOf(false) }
    var showShareMenu by remember(doubt.id) { mutableStateOf(false) }
    var justCopied by remember(doubt.id) { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(2_000L)
            justCopied = false
        }
    }

    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val highlightFg = MaterialTheme.colorScheme.primary

    val concepts = remember(doubt.importantConcepts) {
        doubt.importantConcepts.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    val sources = remember(doubt.verificationSourcesJson, doubt.isFactChecked, factCheckingMode) {
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
    val canShowSources = (factCheckingMode || doubt.isFactChecked) && sources.isNotEmpty()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (doubt.isStarred) RoseError.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .testTag("session_doubt_card_${doubt.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = doubt.topic,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    DoubtConfidenceBadge(
                        confidence = doubt.confidenceScore,
                        modifier = Modifier.testTag("session_doubt_confidence_${doubt.id}")
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleStar()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("session_doubt_favorite_button_${doubt.id}")
                ) {
                    Icon(
                        imageVector = if (doubt.isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (doubt.isStarred) "Unpin answer" else "Pin answer",
                        tint = if (doubt.isStarred) RoseError else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = "\"${doubt.doubtSnippet}\"",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            if (doubt.oneSentenceSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldSuccess.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = doubt.oneSentenceSummary,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = EmeraldSuccess,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (concepts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    concepts.take(5).forEach { concept ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NeonViolet.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = concept,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = NeonViolet,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick vs Detailed Answer Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showDetailed,
                    onClick = { showDetailed = false },
                    label = { Text("⚡ Quick Answer", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                        selectedLabelColor = ElectricCyan
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = !showDetailed,
                        borderColor = Slate800,
                        selectedBorderColor = ElectricCyan
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("session_doubt_toggle_quick_${doubt.id}")
                )

                FilterChip(
                    selected = showDetailed,
                    onClick = { showDetailed = true },
                    label = { Text("📖 Detailed Answer", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonViolet.copy(alpha = 0.22f),
                        selectedLabelColor = NeonViolet
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = showDetailed,
                        borderColor = Slate800,
                        selectedBorderColor = NeonViolet
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("session_doubt_toggle_detailed_${doubt.id}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (showDetailed) NeonViolet.copy(alpha = 0.08f) else ElectricCyan.copy(alpha = 0.08f),
                border = BorderStroke(
                    1.dp,
                    if (showDetailed) NeonViolet.copy(alpha = 0.28f) else ElectricCyan.copy(alpha = 0.28f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                val activeAnswerText = if (showDetailed) {
                    doubt.detailedAnswer.ifBlank { doubt.quickAnswer }
                } else {
                    doubt.quickAnswer
                }
                val highlightedAnswer = remember(activeAnswerText, isThisDoubtActive, spokenText, spokenRange) {
                    buildTtsHighlightedText(
                        displayText = activeAnswerText,
                        fullSpokenText = if (isThisDoubtActive) spokenText else null,
                        spokenRange = if (isThisDoubtActive) spokenRange else null,
                        highlightBg = highlightBg,
                        highlightFg = highlightFg
                    )
                }
                Text(
                    text = highlightedAnswer,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    ),
                    modifier = Modifier
                        .padding(12.dp)
                        .testTag("session_doubt_answer_text_${doubt.id}")
                )
            }

            if (canShowSources) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = { showDoubtSources = !showDoubtSources },
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldSuccess.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("session_doubt_sources_toggle_${doubt.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Sources",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sources (${sources.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = EmeraldSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Icon(
                            imageVector = if (showDoubtSources) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (showDoubtSources) "Hide sources" else "Show sources",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = showDoubtSources) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sources.forEach { src ->
                            Surface(
                                onClick = { ExternalReferenceHelper.openUrl(context, src.url, src.title) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = src.title,
                                        style = MaterialTheme.typography.labelSmall.copy(
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
                                    if (src.excerpt.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "\"${src.excerpt}\"",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isThisDoubtActive || showTtsControls) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
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
                                val textToSpeak = if (showDetailed) {
                                    doubt.detailedAnswer.ifBlank { doubt.quickAnswer }
                                } else {
                                    doubt.quickAnswer
                                }
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
                        testTagPrefix = "session_doubt_${doubt.id}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val textToSpeak = if (showDetailed) {
                            doubt.detailedAnswer.ifBlank { doubt.quickAnswer }
                        } else {
                            doubt.quickAnswer
                        }
                        TtsManager.speakAnswer(context, textToSpeak, doubt.id)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("session_doubt_listen_button_${doubt.id}")
                ) {
                    Icon(
                        imageVector = if (isThisDoubtActive) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isThisDoubtActive) "Stop reading aloud" else "Speak answer",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isThisDoubtActive) "Stop" else "Listen", fontSize = 11.sp)
                }

                IconButton(
                    onClick = { showTtsControls = !showTtsControls },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("session_doubt_tts_controls_toggle_${doubt.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = if (showTtsControls) "Hide voice speed and pitch controls" else "Show voice speed and pitch controls",
                        tint = if (showTtsControls || isThisDoubtActive) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                OutlinedButton(
                    onClick = {
                        val copyText = ClipboardHelper.formatAnswerForClipboard(
                            quickAnswer = doubt.quickAnswer,
                            detailedAnswer = doubt.detailedAnswer,
                            includeDetailed = true
                        )
                        if (ClipboardHelper.copyAnswer(context, copyText)) {
                            justCopied = true
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (justCopied) EmeraldSuccess else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("session_doubt_copy_button_${doubt.id}")
                ) {
                    Icon(
                        imageVector = if (justCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_answer),
                        tint = if (justCopied) EmeraldSuccess else ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(if (justCopied) R.string.action_copied_answer else R.string.action_copy_answer),
                        fontSize = 11.sp,
                        color = if (justCopied) EmeraldSuccess else MaterialTheme.colorScheme.onSurface
                    )
                }

                Box {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showShareMenu = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("session_doubt_share_button_${doubt.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share AI answer",
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 11.sp)
                    }

                    DropdownMenu(
                        expanded = showShareMenu,
                        onDismissRequest = { showShareMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("📤 Share Quick + Detailed") },
                            onClick = {
                                showShareMenu = false
                                ShareHelper.shareDoubt(context, doubt, ShareFormat.FULL_SUMMARY)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📄 Export as PDF") },
                            onClick = {
                                showShareMenu = false
                                ShareHelper.shareDoubt(context, doubt, ShareFormat.PDF_DOCUMENT)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📝 Export as Text (.txt)") },
                            onClick = {
                                showShareMenu = false
                                ShareHelper.shareDoubt(context, doubt, ShareFormat.STRUCTURED_TEXT_DOCUMENT)
                            }
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onRevisitInAnswerEngine,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = NeonViolet.copy(alpha = 0.22f),
                        contentColor = NeonViolet
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("session_doubt_revisit_button_${doubt.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Open in Answer Engine for follow-up",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open in Answer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { ExternalReferenceHelper.openInGoogle(context, doubt) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Google", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { ExternalReferenceHelper.openInChatGpt(context, doubt) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ChatGPT", fontSize = 11.sp)
                }
            }
        }
    }
}

internal fun formatSessionDuration(startTime: Long, endTime: Long): String {
    val diffSeconds = ((endTime - startTime).coerceAtLeast(0L)) / 1000L
    val mins = diffSeconds / 60L
    val secs = diffSeconds % 60L
    return when {
        mins >= 60L -> "${mins / 60L}h ${mins % 60L}m"
        mins > 0L -> "${mins}m ${secs}s"
        else -> "${secs.coerceAtLeast(1L)}s"
    }
}

internal fun tagAccentColor(tag: String): Color {
    return when (tag.trim().lowercase(Locale.US)) {
        "work" -> ElectricCyan
        "personal" -> EmeraldSuccess
        "study" -> NeonViolet
        "education" -> AmberWarning
        "general" -> Color(0xFF94A3B8)
        else -> {
            val palette = listOf(ElectricCyan, NeonViolet, EmeraldSuccess, AmberWarning)
            val idx = kotlin.math.abs(tag.lowercase(Locale.US).hashCode()) % palette.size
            palette[idx]
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionTagPickerDialog(
    sessionTitle: String,
    initialTags: List<String>,
    availableTags: List<String>,
    onCreateCustomTag: (String) -> Unit,
    onConfirmTags: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTags by remember(initialTags) {
        mutableStateOf(initialTags.distinctBy { it.lowercase() })
    }
    var customTagDraft by remember { mutableStateOf("") }
    val allChoices = remember(availableTags, selectedTags) {
        (availableTags + selectedTags)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 600.dp)
                .testTag("session_tag_picker_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column {
                    Text(
                        text = "Categorize Session",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sessionTitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Select one or more labels for this saved doubt session:",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allChoices.forEach { tag ->
                            val isSelected = selectedTags.any { it.equals(tag, ignoreCase = true) }
                            val accent = tagAccentColor(tag)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTags = if (isSelected) {
                                        selectedTags.filterNot { it.equals(tag, ignoreCase = true) }
                                    } else {
                                        (selectedTags + tag).distinctBy { it.lowercase() }
                                    }
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Label,
                                            contentDescription = null,
                                            tint = accent.copy(alpha = 0.75f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = tag,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.22f),
                                    selectedLabelColor = accent
                                ),
                                modifier = Modifier.testTag("tag_picker_chip_$tag")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    Text(
                        text = "Or create a new custom label:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customTagDraft,
                            onValueChange = { customTagDraft = it.take(28) },
                            placeholder = { Text("e.g. Exam Prep, Project", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_tag_name_input")
                        )

                        FilledTonalButton(
                            onClick = {
                                val clean = customTagDraft.replace(",", " ").replace(Regex("\\s+"), " ").trim()
                                if (clean.isNotBlank()) {
                                    onCreateCustomTag(clean)
                                    if (selectedTags.none { it.equals(clean, ignoreCase = true) }) {
                                        selectedTags = selectedTags + clean
                                    }
                                    customTagDraft = ""
                                }
                            },
                            enabled = customTagDraft.isNotBlank(),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("confirm_add_custom_tag_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val draft = customTagDraft.replace(",", " ").replace(Regex("\\s+"), " ").trim()
                            val finalTags = if (draft.isNotBlank()) {
                                onCreateCustomTag(draft)
                                (selectedTags + draft).distinctBy { it.lowercase() }
                            } else {
                                selectedTags
                            }
                            onConfirmTags(ConversationSession.encodeTags(finalTags))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Slate950),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("save_session_tags_button")
                    ) {
                        Text("Save Labels", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
