package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DoubtItem
import com.example.util.ShareFormat
import com.example.util.ShareHelper
import com.example.util.TtsManager
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DoubtCard(
    doubt: DoubtItem,
    modifier: Modifier = Modifier,
    initialExpandedDetailed: Boolean = false,
    onToggleStar: ((DoubtItem) -> Unit)? = null,
    onDelete: ((DoubtItem) -> Unit)? = null
) {
    val context = LocalContext.current
    var showDetailed by remember(doubt.id) { mutableStateOf(initialExpandedDetailed) }
    var showShareMenu by remember { mutableStateOf(false) }
    var showInteractiveClarification by remember(doubt.id) { mutableStateOf(doubt.isAmbiguous) }

    val isTtsSpeaking by TtsManager.isSpeaking.collectAsState()
    val currentlySpeakingId by TtsManager.currentlySpeakingDoubtId.collectAsState()
    val isThisDoubtSpeaking = isTtsSpeaking && currentlySpeakingId == doubt.id

    val formattedTime = remember(doubt.timestamp) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(doubt.timestamp))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .testTag("doubt_card_${doubt.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Slate850
        ),
        border = BorderStroke(1.dp, Slate800)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Topic badge + Date + Star/Delete actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = NeonViolet.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = doubt.topic,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = ElectricCyan
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    )

                    if (onToggleStar != null) {
                        IconButton(
                            onClick = { onToggleStar(doubt) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (doubt.isStarred) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (doubt.isStarred) "Unstar" else "Star",
                                tint = if (doubt.isStarred) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (onDelete != null) {
                        IconButton(
                            onClick = { onDelete(doubt) },
                            modifier = Modifier.size(36.dp)
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
                    Text(
                        text = "\"${doubt.doubtSnippet}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
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

            // Answer Content Container
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (showDetailed) NeonViolet.copy(alpha = 0.08f) else ElectricCyan.copy(alpha = 0.08f),
                border = BorderStroke(
                    1.dp,
                    if (showDetailed) NeonViolet.copy(alpha = 0.25f) else ElectricCyan.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (!showDetailed) {
                        Text(
                            text = doubt.quickAnswer,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    } else {
                        Text(
                            text = doubt.detailedAnswer,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            // Context note & Action buttons (Copy & Share)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (doubt.conversationalContext.isNotBlank()) {
                    Text(
                        text = "Inferred from conversation",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Read Aloud (TTS) Button
                    FilledTonalButton(
                        onClick = {
                            val textToSpeak = if (showDetailed) doubt.detailedAnswer else doubt.quickAnswer
                            TtsManager.speakAnswer(context, textToSpeak, doubt.id)
                        },
                        colors = if (isThisDoubtSpeaking) {
                            ButtonDefaults.filledTonalButtonColors(containerColor = ElectricCyan.copy(alpha = 0.35f))
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        },
                        modifier = Modifier.testTag("read_aloud_button_${doubt.id}")
                    ) {
                        Icon(
                            imageVector = if (isThisDoubtSpeaking) Icons.Default.StopCircle else Icons.Default.VolumeUp,
                            contentDescription = if (isThisDoubtSpeaking) "Stop reading aloud" else "Read answer aloud",
                            tint = if (isThisDoubtSpeaking) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isThisDoubtSpeaking) "Stop" else "Listen",
                            fontSize = 12.sp,
                            color = if (isThisDoubtSpeaking) ElectricCyan else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Copy Answer button
                    FilledTonalButton(
                        onClick = {
                            val textToCopy = if (showDetailed) doubt.detailedAnswer else doubt.quickAnswer
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Doubt Answer", textToCopy))
                            Toast.makeText(context, "Answer copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_button_${doubt.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy answer",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 12.sp)
                    }

                    // Share Answer with options
                    Box {
                        OutlinedButton(
                            onClick = { showShareMenu = true },
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                            modifier = Modifier.testTag("share_button_${doubt.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share answer",
                                tint = ElectricCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", color = ElectricCyan, fontSize = 12.sp)
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
            }
        }
    }
}
