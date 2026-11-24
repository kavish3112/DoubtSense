package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.DailyDoubtFrequencyPoint
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import kotlin.math.roundToInt

/**
 * Interactive floating tooltip shown when a user taps or scrubs a data point
 * on the 30-day doubt frequency graph. Displays the specific date, average
 * doubt intensity percentage, doubt count, and primary subject.
 */
@Composable
fun FrequencyPointTooltip(
    point: DailyDoubtFrequencyPoint,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val intensityPercent = (point.averageIntensity * 100f).roundToInt().coerceIn(0, 100)
    val intensityColor = when {
        intensityPercent >= 75 -> RoseError
        intensityPercent >= 40 -> AmberWarning
        intensityPercent > 0 -> EmeraldSuccess
        else -> Slate400
    }
    val dateText = if (point.isToday) {
        "${point.fullDateLabel} (Today)"
    } else {
        point.fullDateLabel
    }
    val tooltipShape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .widthIn(min = 168.dp, max = 232.dp)
            .shadow(10.dp, tooltipShape)
            .clip(tooltipShape)
            .background(Slate900.copy(alpha = 0.96f))
            .border(1.2.dp, ElectricCyan.copy(alpha = 0.7f), tooltipShape)
            .testTag("frequency_point_tooltip")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("frequency_tooltip_date")
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Slate800)
                        .clickable(onClick = onDismiss)
                        .testTag("frequency_tooltip_dismiss"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss chart tooltip",
                        tint = Slate400,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(intensityColor.copy(alpha = 0.16f))
                        .border(0.8.dp, intensityColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Doubt intensity",
                            tint = intensityColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "$intensityPercent% Intensity",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = intensityColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            ),
                            modifier = Modifier.testTag("frequency_tooltip_intensity")
                        )
                    }
                }

                Text(
                    text = "${point.doubtCount} doubt${if (point.doubtCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ElectricCyan,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp
                    ),
                    maxLines = 1,
                    modifier = Modifier.testTag("frequency_tooltip_count")
                )
            }

            val contextLine = if (point.doubtCount > 0) {
                "${point.topSubject} • ${point.sessionCount} session(s)"
            } else {
                "No doubts logged"
            }
            Text(
                text = contextLine,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun FrequencyStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    sublabel: String? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Slate850,
        border = BorderStroke(1.dp, Slate800)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = accent,
                    fontSize = 16.sp
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!sublabel.isNullOrBlank()) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = accent.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp
            )
        )
    }
}
