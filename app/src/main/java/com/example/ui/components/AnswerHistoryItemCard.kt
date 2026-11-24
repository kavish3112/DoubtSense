package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.AnswerEngineResult
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.util.SemanticSearchHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AnswerHistoryItemCard(
    item: AnswerEngineResult,
    searchQuery: String,
    availableTags: List<String>,
    isSummarizing: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAssignTag: (String) -> Unit,
    onGenerateSummary: () -> Unit,
    onDelete: () -> Unit,
    onLongClickDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showTagMenu by remember { mutableStateOf(false) }

    val formattedDate = remember(item.timestamp) {
        SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date(item.timestamp))
    }

    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val highlightFg = MaterialTheme.colorScheme.primary

    val highlightedQuery = remember(item.query, searchQuery, highlightBg, highlightFg) {
        SemanticSearchHelper.buildHighlightedAnnotatedString(
            text = item.query,
            query = searchQuery,
            highlightBackground = highlightBg,
            highlightForeground = highlightFg
        )
    }

    val highlightedQuickAnswer = remember(item.quickAnswer, searchQuery, highlightBg, highlightFg) {
        SemanticSearchHelper.buildHighlightedAnnotatedString(
            text = item.quickAnswer,
            query = searchQuery,
            highlightBackground = highlightBg,
            highlightForeground = highlightFg
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (item.isStarred) RoseError.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSelect,
                onLongClick = {
                    if (item.doubtId > 0L) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClickDelete()
                    }
                },
                onLongClickLabel = stringResource(R.string.action_delete_item)
            )
            .testTag("answer_history_item_${item.doubtId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NeonViolet.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = item.topic,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = NeonViolet,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Box {
                        Surface(
                            onClick = { showTagMenu = true },
                            shape = RoundedCornerShape(6.dp),
                            color = ElectricCyan.copy(alpha = 0.14f),
                            modifier = Modifier.testTag("answer_history_tag_${item.doubtId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = "Assign category tag",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = item.customTag,
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
                                        onAssignTag(tag)
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.doubtId > 0L) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleFavorite()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("answer_history_favorite_${item.doubtId}")
                        ) {
                            Icon(
                                imageVector = if (item.isStarred) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (item.isStarred) "Unpin favorite" else "Pin favorite",
                                tint = if (item.isStarred) RoseError else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("answer_history_delete_${item.doubtId}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete saved answer",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = highlightedQuery,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = ElectricCyan,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                DoubtConfidenceBadge(
                    confidence = item.confidence,
                    modifier = Modifier.testTag("answer_history_confidence_${item.doubtId}")
                )
            }

            Text(
                text = highlightedQuickAnswer,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // One-sentence summary display or generator button
            if (item.oneSentenceSummary.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldSuccess.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("answer_history_summary_text_${item.doubtId}")
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
                            text = item.oneSentenceSummary,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = EmeraldSuccess,
                                fontStyle = FontStyle.Italic,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            } else if (item.doubtId > 0L) {
                OutlinedButton(
                    onClick = onGenerateSummary,
                    enabled = !isSummarizing,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("answer_history_summary_btn_${item.doubtId}")
                ) {
                    if (isSummarizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Summarizing...", fontSize = 11.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Summarize,
                            contentDescription = "Generate 1-sentence summary",
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("1-Sentence Summary", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
