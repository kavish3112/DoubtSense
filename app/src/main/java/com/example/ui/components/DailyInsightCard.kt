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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.DailyInsightSummary
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import kotlin.math.abs

/**
 * Dashboard spotlight card summarizing the most significant doubt moment analyzed during the day
 * and surfacing longitudinal intensity progress compared to earlier study days.
 */
@Composable
fun DailyInsightCard(
    insight: DailyInsightSummary?,
    onInspectDoubt: (Long) -> Unit,
    onSelectCategory: (String) -> Unit,
    onNavigateToLiveSense: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (insight == null) {
        EmptyDailyInsightCard(
            onNavigateToLiveSense = onNavigateToLiveSense,
            modifier = modifier
        )
        return
    }

    var isDetailsExpanded by remember(insight.headline, insight.mostSignificantDoubt?.id) {
        mutableStateOf(false)
    }

    val significanceColor = when {
        insight.significanceScore >= 0.75f -> RoseError
        insight.significanceScore >= 0.45f -> AmberWarning
        else -> ElectricCyan
    }

    val delta = insight.intensityDelta
    val (deltaIcon, deltaColor, deltaBadgeText) = when {
        delta == null -> Triple(
            Icons.Default.Timeline,
            ElectricCyan,
            "${insight.todayDoubtCount.coerceAtLeast(1)} Logged"
        )
        delta <= -0.05f -> Triple(
            Icons.Default.TrendingDown,
            EmeraldSuccess,
            "-${abs((delta * 100).toInt())}% Uncertainty"
        )
        delta >= 0.05f -> Triple(
            Icons.Default.TrendingUp,
            AmberWarning,
            "+${abs((delta * 100).toInt())}% Intensity"
        )
        else -> Triple(
            Icons.Default.Timeline,
            ElectricCyan,
            "Steady Pace"
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .testTag("daily_insight_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    ElectricCyan.copy(alpha = 0.55f),
                    NeonViolet.copy(alpha = 0.45f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.12f),
                            NeonViolet.copy(alpha = 0.09f),
                            Slate900
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Row: Title, Date Pill, and Progress Delta Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            ElectricCyan.copy(alpha = 0.28f),
                                            NeonViolet.copy(alpha = 0.28f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Daily Insight",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Daily Insight",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Slate850,
                                    border = BorderStroke(1.dp, Slate800)
                                ) {
                                    Text(
                                        text = insight.dateLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = ElectricCyan,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (insight.isFromToday) {
                                    "Most significant doubt moment today"
                                } else {
                                    "Latest recorded milestone"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = deltaColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, deltaColor.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("daily_insight_progress_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = deltaIcon,
                                contentDescription = deltaBadgeText,
                                tint = deltaColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = deltaBadgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = deltaColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Spotlight Box: Subject + Significance Score + Doubt Quote + Takeaway
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Slate850.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, Slate800),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NeonViolet.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, NeonViolet.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = insight.subjectCategory,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = NeonViolet,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (insight.mostSignificantDoubt?.isFactChecked == true) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Fact-checked insight",
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "${(insight.significanceScore * 100).toInt()}% Peak Focus",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = significanceColor,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = insight.headline,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("daily_insight_headline")
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = insight.momentQuote,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontStyle = FontStyle.Italic,
                                color = ElectricCyan.copy(alpha = 0.95f),
                                lineHeight = 18.sp
                            ),
                            maxLines = if (isDetailsExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (insight.resolutionTakeaway.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = insight.resolutionTakeaway,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 18.sp
                                    ),
                                    modifier = Modifier.testTag("daily_insight_takeaway")
                                )
                            }
                        }

                        // Expandable Deep-Dive Explanation
                        AnimatedVisibility(visible = isDetailsExpanded && insight.detailedExplanation.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .testTag("daily_insight_expanded_details")
                            ) {
                                HorizontalDivider(color = Slate800)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Full Resolution Context",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = NeonViolet,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = insight.detailedExplanation,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Longitudinal Progress Narrative
                Text(
                    text = insight.progressNarrative,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    ),
                    modifier = Modifier.testTag("daily_insight_progress_narrative")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action Row: Toggle Full Context & Inspect in Logs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (insight.detailedExplanation.isNotBlank()) {
                        OutlinedButton(
                            onClick = { isDetailsExpanded = !isDetailsExpanded },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Slate800),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("daily_insight_expand_button")
                        ) {
                            Icon(
                                imageVector = if (isDetailsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isDetailsExpanded) "Collapse full takeaway" else "Expand full takeaway",
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDetailsExpanded) "Show Less" else "Full Takeaway",
                                fontSize = 12.sp,
                                color = ElectricCyan
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            val doubtId = insight.mostSignificantDoubt?.id
                            if (doubtId != null && doubtId > 0L) {
                                onInspectDoubt(doubtId)
                            } else {
                                onSelectCategory(insight.subjectCategory)
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ElectricCyan.copy(alpha = 0.18f),
                            contentColor = ElectricCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("daily_insight_inspect_button")
                    ) {
                        Text(
                            text = "Review Moment",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Review highlighted doubt moment in logs",
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDailyInsightCard(
    onNavigateToLiveSense: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_insight_empty_state"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = BorderStroke(1.dp, Slate800)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToLiveSense)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(ElectricCyan.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Daily Insight awaiting data",
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Insight",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Analyze a conversation in Live Sense to spotlight today's most significant doubt moment and track mastery over time.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open Live Sense",
                tint = ElectricCyan,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
