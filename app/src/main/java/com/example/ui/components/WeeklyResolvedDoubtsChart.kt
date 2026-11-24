package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DoubtItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyResolvedBucket(
    val dayLabel: String,
    val fullDateLabel: String,
    val resolvedCount: Int
)

/**
 * Pure helper computing daily resolved doubts across the trailing 7-day window.
 */
fun computeWeeklyResolvedBuckets(
    doubts: List<DoubtItem>,
    nowMillis: Long = System.currentTimeMillis()
): List<DailyResolvedBucket> {
    val shortDayFmt = SimpleDateFormat("EEE", Locale.getDefault())
    val fullDateFmt = SimpleDateFormat("MMM d", Locale.getDefault())

    val startCal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -6)
    }

    val buckets = mutableListOf<DailyResolvedBucket>()
    val iterCal = startCal.clone() as Calendar
    repeat(7) {
        val dayStart = iterCal.timeInMillis
        val nextDayCal = (iterCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val dayEnd = nextDayCal.timeInMillis

        val count = doubts.count { doubt ->
            doubt.timestamp in dayStart until dayEnd &&
                (doubt.quickAnswer.isNotBlank() || doubt.detailedAnswer.isNotBlank())
        }
        val date = Date(dayStart)
        buckets.add(
            DailyResolvedBucket(
                dayLabel = shortDayFmt.format(date),
                fullDateLabel = fullDateFmt.format(date),
                resolvedCount = count
            )
        )
        iterCal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return buckets
}

/**
 * Native Jetpack Compose Canvas weekly bar chart displaying doubts resolved per day.
 */
@Composable
fun WeeklyResolvedDoubtsChart(
    doubts: List<DoubtItem>,
    modifier: Modifier = Modifier
) {
    val buckets = remember(doubts) { computeWeeklyResolvedBuckets(doubts) }
    val weeklyTotal = remember(buckets) { buckets.sumOf { it.resolvedCount } }
    val maxCount = remember(buckets) { (buckets.maxOfOrNull { it.resolvedCount } ?: 0).coerceAtLeast(1) }
    var selectedBarIndex by remember { mutableIntStateOf(6) } // Default highlight: Today

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 650),
        label = "weekly_bar_growth"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_resolved_bar_chart"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Weekly Resolved Doubts Chart",
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Weekly Doubts Resolved",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "7-day resolution progress per day",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = labelColor,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = primaryColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "$weeklyTotal this week",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = primaryColor,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (weeklyTotal == 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No doubts resolved in the past 7 days yet. Ask a question in Answer Engine or Live Sense to populate your weekly bar chart.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = labelColor
                        ),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                val safeIndex = selectedBarIndex.coerceIn(0, buckets.lastIndex)
                val selectedBucket = buckets[safeIndex]

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .pointerInput(buckets) {
                            detectTapGestures { offset ->
                                val colWidth = size.width / 7f
                                val idx = (offset.x / colWidth).toInt().coerceIn(0, 6)
                                selectedBarIndex = idx
                            }
                        }
                        .testTag("weekly_bar_chart_canvas")
                ) {
                    val topPadding = 22.dp.toPx()
                    val bottomPadding = 26.dp.toPx()
                    val chartHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(20f)
                    val colWidth = size.width / 7f
                    val barWidth = (colWidth * 0.48f).coerceAtMost(34.dp.toPx())

                    // Horizontal dashed guide lines (0%, 50%, 100%)
                    listOf(0f, 0.5f, 1f).forEach { fraction ->
                        val y = topPadding + chartHeight * (1f - fraction)
                        drawLine(
                            color = outlineColor.copy(alpha = 0.35f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )
                    }

                    val textPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 10.sp.toPx()
                        color = labelColor.toArgb()
                    }
                    val countPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 10.sp.toPx()
                        isFakeBoldText = true
                        color = primaryColor.toArgb()
                    }

                    buckets.forEachIndexed { index, bucket ->
                        val centerX = colWidth * index + colWidth / 2f
                        val ratio = (bucket.resolvedCount.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                        val barHeight = (chartHeight * ratio * animProgress).coerceAtLeast(
                            if (bucket.resolvedCount > 0) 6.dp.toPx() else 2.dp.toPx()
                        )
                        val left = centerX - barWidth / 2f
                        val top = topPadding + chartHeight - barHeight
                        val isSelected = index == safeIndex

                        val brush = if (bucket.resolvedCount > 0) {
                            Brush.verticalGradient(
                                colors = if (isSelected) {
                                    listOf(primaryColor, secondaryColor)
                                } else {
                                    listOf(primaryColor.copy(alpha = 0.72f), secondaryColor.copy(alpha = 0.65f))
                                },
                                startY = top,
                                endY = top + barHeight
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(Color.Gray.copy(alpha = 0.25f), Color.Gray.copy(alpha = 0.15f)),
                                startY = top,
                                endY = top + barHeight
                            )
                        }

                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )

                        if (bucket.resolvedCount > 0) {
                            drawContext.canvas.nativeCanvas.drawText(
                                "${bucket.resolvedCount}",
                                centerX,
                                (top - 5.dp.toPx()).coerceAtLeast(12.dp.toPx()),
                                countPaint
                            )
                        }

                        textPaint.isFakeBoldText = isSelected
                        drawContext.canvas.nativeCanvas.drawText(
                            bucket.dayLabel,
                            centerX,
                            size.height - 6.dp.toPx(),
                            textPaint
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = surfaceColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${selectedBucket.fullDateLabel} (${selectedBucket.dayLabel}): ${selectedBucket.resolvedCount} doubt(s) resolved",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = primaryColor,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("weekly_bar_selected_summary")
                    )
                }
            }
        }
    }
}
