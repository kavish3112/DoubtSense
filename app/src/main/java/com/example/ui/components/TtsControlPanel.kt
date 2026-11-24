package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import java.util.Locale
import kotlin.math.abs

private data class TtsSpeedOption(
    val rate: Float,
    val label: String,
    val tagSuffix: String
)

private val TTS_SPEED_OPTIONS = listOf(
    TtsSpeedOption(0.5f, "0.5x", "0_5x"),
    TtsSpeedOption(1.0f, "1.0x", "1_0x"),
    TtsSpeedOption(1.5f, "1.5x", "1_5x"),
    TtsSpeedOption(2.0f, "2.0x", "2_0x")
)

/**
 * Interactive TTS audio control panel embedded on answer cards providing:
 * - Play / Pause / Resume & Stop playback controls
 * - Quick playback speed selector (0.5x, 1.0x, 1.5x, 2.0x)
 * - Voice pitch slider (0.5x .. 2.0x)
 */
@Composable
fun TtsControlPanel(
    isSpeaking: Boolean,
    isPaused: Boolean,
    spokenText: String,
    spokenRange: IntRange?,
    speechRate: Float,
    speechPitch: Float,
    onPlayPauseResume: () -> Unit,
    onStop: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onResetAudioControls: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    val isActive = isSpeaking || isPaused
    val accentColor = when {
        isSpeaking -> ElectricCyan
        isPaused -> AmberWarning
        else -> NeonViolet
    }

    val activeWord = remember(spokenText, spokenRange, isActive) {
        if (!isActive || spokenRange == null) "" else {
            if (spokenRange.first in spokenText.indices &&
                spokenRange.last < spokenText.length &&
                spokenRange.first <= spokenRange.last
            ) {
                spokenText.substring(spokenRange.first, spokenRange.last + 1)
            } else ""
        }
    }

    val statusText = when {
        isSpeaking && activeWord.isNotBlank() -> "Speaking: \"$activeWord\""
        isSpeaking -> "Reading response aloud..."
        isPaused && activeWord.isNotBlank() -> "Paused at \"$activeWord\""
        isPaused -> "Playback paused"
        else -> "Audio Playback & Voice Controls"
    }

    val isModified = abs(speechRate - 1.0f) > 0.04f || abs(speechPitch - 1.0f) > 0.04f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_tts_control_panel"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = accentColor.copy(alpha = if (isActive) 0.55f else 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isSpeaking -> Icons.AutoMirrored.Filled.VolumeUp
                            isPaused -> Icons.Default.PauseCircle
                            else -> Icons.Default.GraphicEq
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isActive) {
                            Modifier.testTag("${testTagPrefix}_tts_status")
                        } else {
                            Modifier
                        }
                    )
                }

                if (isModified) {
                    TextButton(
                        onClick = onResetAudioControls,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("${testTagPrefix}_tts_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset speed and pitch",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Reset",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Pause / Resume / Play + Stop Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onPlayPauseResume,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = when {
                            isSpeaking -> AmberWarning.copy(alpha = 0.22f)
                            isPaused -> ElectricCyan.copy(alpha = 0.24f)
                            else -> ElectricCyan.copy(alpha = 0.16f)
                        },
                        contentColor = when {
                            isSpeaking -> AmberWarning
                            else -> ElectricCyan
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("${testTagPrefix}_tts_pause_resume_button")
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = when {
                            isSpeaking -> "Pause audio playback"
                            isPaused -> "Resume audio playback"
                            else -> "Play audio response"
                        },
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isSpeaking -> "Pause"
                            isPaused -> "Resume"
                            else -> "Play"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                if (isActive) {
                    OutlinedButton(
                        onClick = onStop,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("${testTagPrefix}_tts_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "Stop audio playback",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Stop",
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Playback Speed Selector (0.5x, 1.0x, 1.5x, 2.0x)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Playback Speed",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.1fx", speechRate),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.testTag("${testTagPrefix}_tts_speed_value")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TTS_SPEED_OPTIONS.forEach { option ->
                        val selected = abs(speechRate - option.rate) < 0.05f
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectSpeed(option.rate) },
                            label = {
                                Text(
                                    text = option.label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                selectedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("${testTagPrefix}_tts_speed_${option.tagSuffix}")
                        )
                    }
                }
            }

            // Voice Pitch Slider (0.5x .. 2.0x)
            Column {
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
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Voice Pitch",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.2fx", speechPitch),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NeonViolet,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.testTag("${testTagPrefix}_tts_pitch_value")
                    )
                }

                Slider(
                    value = speechPitch.coerceIn(0.5f, 2.0f),
                    onValueChange = onPitchChange,
                    valueRange = 0.5f..2.0f,
                    steps = 5,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonViolet,
                        activeTrackColor = NeonViolet,
                        inactiveTrackColor = NeonViolet.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "TTS Voice Pitch Slider"
                        }
                        .testTag("${testTagPrefix}_tts_pitch_slider")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0.5x Deep",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = "1.0x Normal",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = "2.0x Bright",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}
