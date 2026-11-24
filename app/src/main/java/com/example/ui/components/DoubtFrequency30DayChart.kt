package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import com.example.data.repository.DoubtFrequencyCalculator
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class FrequencyChartMode(val label: String) {
    BARS("Bars"),
    CURVE("Curve")
}

/**
 * Detailed 30-day Dashboard graph showing daily detected doubt frequency, 7-day rolling average
 * overlay, subject filtering, and interactive day-by-day inspection.
 */
@Composable
fun DoubtFrequency30DayChart(
    doubts: List<DoubtItem>,
    sessions: List<ConversationSession>,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis()
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var chartMode by remember { mutableStateOf(FrequencyChartMode.BARS) }
    var selectedDayIndex by remember { mutableStateOf<Int?>(null) }
    var isTooltipVisible by remember { mutableStateOf(false) }

    val summary = remember(doubts, sessions, selectedCategory, nowMillis) {
        DoubtFrequencyCalculator.compute30DayFrequency(
            doubts = doubts,
            sessions = sessions,
            subjectFilter = selectedCategory,
            nowMillis = nowMillis
        )
    }

    // Reset day highlight and hide floating tooltip if filter changes
    LaunchedEffect(selectedCategory, summary.totalDoubts30d) {
        selectedDayIndex = summary.peakDay?.dayIndex ?: (summary.points.lastOrNull()?.dayIndex)
        isTooltipVisible = false
    }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(summary.totalDoubts30d, selectedCategory, chartMode) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
    }

    val yAxisMax = remember(summary.maxDailyCount) {
        val raw = summary.maxDailyCount.coerceAtLeast(4)
        // Round up to a clean multiple of 4 so grid lines land on whole integers
        ceil(raw / 4.0).toInt().coerceAtLeast(1) * 4
    }

    val inspectedDay = selectedDayIndex?.let { summary.points.getOrNull(it) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("doubt_frequency_30d_chart"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = BorderStroke(1.dp, Slate800)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header + Chart Style Switcher (Bars / Curve)
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "30-Day Doubt Frequency",
                            tint = ElectricCyan,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "30-Day Doubt Frequency",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "Daily detected doubt instances & 7-day rolling avg",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    onClick = {
                        chartMode = if (chartMode == FrequencyChartMode.BARS) {
                            FrequencyChartMode.CURVE
                        } else {
                            FrequencyChartMode.BARS
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Slate850,
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                    modifier = Modifier.testTag("frequency_chart_mode_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (chartMode == FrequencyChartMode.BARS) Icons.Default.ShowChart else Icons.Default.BarChart,
                            contentDescription = "Switch frequency chart visualization mode",
                            tint = ElectricCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (chartMode == FrequencyChartMode.BARS) "Curve" else "Bars",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4-Stat KPI Strip for the 30-day window
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FrequencyStatTile(
                    label = "30d Total",
                    value = "${summary.totalDoubts30d}",
                    accent = ElectricCyan,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("frequency_30d_total")
                )
                FrequencyStatTile(
                    label = "Daily Avg",
                    value = String.format(Locale.US, "%.1f", summary.dailyAverage),
                    accent = NeonViolet,
                    modifier = Modifier.weight(1f)
                )
                FrequencyStatTile(
                    label = "Peak Day",
                    value = "${summary.peakDay?.doubtCount ?: 0}",
                    sublabel = summary.peakDay?.shortDateLabel,
                    accent = if ((summary.peakDay?.doubtCount ?: 0) >= 3) RoseError else AmberWarning,
                    modifier = Modifier.weight(1f)
                )
                FrequencyStatTile(
                    label = "Active Days",
                    value = "${summary.activeDaysCount}/30",
                    accent = EmeraldSuccess,
                    modifier = Modifier.weight(1f)
                )
            }

            // Optional Subject Filter Chips when multiple subjects exist
            if (summary.availableCategories.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All Subjects", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("freq_filter_all")
                    )
                    summary.availableCategories.forEach { cat ->
                        val isSel = selectedCategory.equals(cat, ignoreCase = true)
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedCategory = if (isSel) null else cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonViolet.copy(alpha = 0.22f),
                                selectedLabelColor = NeonViolet
                            ),
                            modifier = Modifier.testTag("freq_filter_$cat")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Inspected Day Callout
            if (inspectedDay != null) {
                val inspectedIntensityPct = (inspectedDay.averageIntensity * 100f).roundToInt().coerceIn(0, 100)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("frequency_selected_day_callout"),
                    shape = RoundedCornerShape(14.dp),
                    color = Slate850,
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (inspectedDay.isToday) {
                                    "${inspectedDay.fullDateLabel} (Today)"
                                } else {
                                    inspectedDay.fullDateLabel
                                },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            val detailText = if (inspectedDay.doubtCount > 0) {
                                "${inspectedDay.topSubject} • $inspectedIntensityPct% intensity • 7d avg: ${
                                    String.format(Locale.US, "%.1f", inspectedDay.rollingSevenDayAverage)
                                }/day"
                            } else {
                                "No doubts logged • Tap a point for tooltip"
                            }
                            Text(
                                text = detailText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (inspectedDay.doubtCount > 0) ElectricCyan.copy(alpha = 0.18f) else Slate800
                        ) {
                            Text(
                                text = "${inspectedDay.doubtCount} doubt${if (inspectedDay.doubtCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (inspectedDay.doubtCount > 0) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main 30-Day Canvas Graph + Interactive Floating Tooltip
            var chartContainerSize by remember { mutableStateOf(IntSize.Zero) }
            var tooltipSize by remember { mutableStateOf(IntSize.Zero) }
            val density = LocalDensity.current

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(248.dp)
                    .onSizeChanged { chartContainerSize = it }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(248.dp)
                        .testTag("frequency_30d_canvas")
                        .pointerInput(summary.points) {
                            val leftMargin = 32.dp.toPx()
                            val rightMargin = 12.dp.toPx()
                            fun dayIndexForX(x: Float): Int {
                                val pts = summary.points
                                if (pts.isEmpty()) return 0
                                val plotW = (size.width - leftMargin - rightMargin).coerceAtLeast(1f)
                                val slotW = plotW / pts.size.toFloat()
                                return ((x - leftMargin) / slotW).toInt().coerceIn(0, pts.size - 1)
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = true)
                                var isDragging = false
                                var lastX = down.position.x
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (!isDragging) {
                                            val idx = dayIndexForX(change.position.x)
                                            if (isTooltipVisible && selectedDayIndex == idx) {
                                                isTooltipVisible = false
                                            } else {
                                                selectedDayIndex = idx
                                                isTooltipVisible = true
                                            }
                                        }
                                        break
                                    }
                                    val distance = (change.position - down.position).getDistance()
                                    if (distance > viewConfiguration.touchSlop) {
                                        isDragging = true
                                        lastX = change.position.x
                                        change.consume()
                                        selectedDayIndex = dayIndexForX(lastX)
                                        isTooltipVisible = true
                                    }
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    val leftMargin = 32.dp.toPx()
                    val rightMargin = 12.dp.toPx()
                    val topMargin = 72.dp.toPx()
                    val bottomMargin = 28.dp.toPx()

                    val chartWidth = (w - leftMargin - rightMargin).coerceAtLeast(1f)
                    val chartHeight = (h - topMargin - bottomMargin).coerceAtLeast(1f)

                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()), 0f)
                    val yLabelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#64748B")
                        textSize = 9.sp.toPx()
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }

                    // 1. Horizontal Y-Axis Gridlines (5 levels: 0%, 25%, 50%, 75%, 100% of yAxisMax)
                    val stepValue = yAxisMax / 4
                    for (step in 0..4) {
                        val valTick = step * stepValue
                        val fraction = valTick.toFloat() / yAxisMax.toFloat()
                        val y = topMargin + (1f - fraction) * chartHeight

                        drawLine(
                            color = if (step == 0) Slate700 else Color(0xFF1E293B),
                            start = Offset(leftMargin, y),
                            end = Offset(w - rightMargin, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (step == 0) null else dashEffect
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            valTick.toString(),
                            leftMargin - 6.dp.toPx(),
                            y + 3.dp.toPx(),
                            yLabelPaint
                        )
                    }

                    val pts = summary.points
                    if (pts.isEmpty()) return@Canvas

                    val slotWidth = chartWidth / pts.size.toFloat()
                    val barWidth = (slotWidth * 0.62f).coerceIn(3.dp.toPx(), 12.dp.toPx())

                    // Vertical weekly markers (days 0, 7, 14, 21, 29)
                    val tickIndices = listOf(0, 7, 14, 21, 29)
                    val xLabelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#94A3B8")
                        textSize = 8.5.sp.toPx()
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    tickIndices.forEach { tickIdx ->
                        if (tickIdx in pts.indices) {
                            val cx = leftMargin + (tickIdx + 0.5f) * slotWidth
                            drawLine(
                                color = Color(0xFF172033),
                                start = Offset(cx, topMargin),
                                end = Offset(cx, topMargin + chartHeight),
                                strokeWidth = 1.dp.toPx()
                            )
                            val label = if (pts[tickIdx].isToday) "Today" else pts[tickIdx].shortDateLabel
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                cx.coerceIn(leftMargin + 14.dp.toPx(), w - rightMargin - 12.dp.toPx()),
                                topMargin + chartHeight + 17.dp.toPx(),
                                xLabelPaint
                            )
                        }
                    }

                    // Selected Day Highlight Guide
                    selectedDayIndex?.let { selIdx ->
                        if (selIdx in pts.indices) {
                            val selX = leftMargin + (selIdx + 0.5f) * slotWidth
                            drawLine(
                                color = ElectricCyan.copy(alpha = if (isTooltipVisible) 0.9f else 0.55f),
                                start = Offset(selX, if (isTooltipVisible) topMargin - 6.dp.toPx() else topMargin),
                                end = Offset(selX, topMargin + chartHeight),
                                strokeWidth = if (isTooltipVisible) 2.dp.toPx() else 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
                            )
                        }
                    }

                    if (chartMode == FrequencyChartMode.BARS) {
                        // 2A. Vertical Frequency Bars
                        pts.forEachIndexed { idx, pt ->
                            val cx = leftMargin + (idx + 0.5f) * slotWidth
                            val barLeft = cx - barWidth / 2f

                            // Subtle track for every day in the 30-day cadence
                            drawRoundRect(
                                color = Slate850.copy(alpha = 0.7f),
                                topLeft = Offset(barLeft, topMargin + chartHeight - 3.dp.toPx()),
                                size = Size(barWidth, 3.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )

                            val isSelected = selectedDayIndex == idx
                            if (pt.doubtCount > 0) {
                                val normalized = (pt.doubtCount.toFloat() / yAxisMax.toFloat()).coerceIn(0f, 1f)
                                val barHeight = (normalized * chartHeight * animProgress.value).coerceAtLeast(4.dp.toPx())
                                val barTop = topMargin + chartHeight - barHeight

                                val topColor = when {
                                    pt.averageIntensity >= 0.75f -> RoseError
                                    pt.doubtCount >= (yAxisMax * 0.65f) -> NeonViolet
                                    else -> ElectricCyan
                                }
                                val bottomColor = ElectricCyan.copy(alpha = if (isSelected) 0.95f else 0.45f)

                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(topColor, bottomColor),
                                        startY = barTop,
                                        endY = topMargin + chartHeight
                                    ),
                                    topLeft = Offset(barLeft, barTop),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                )

                                if (isSelected) {
                                    if (isTooltipVisible) {
                                        drawCircle(
                                            color = ElectricCyan.copy(alpha = 0.35f),
                                            radius = 7.dp.toPx(),
                                            center = Offset(cx, barTop)
                                        )
                                    }
                                    drawCircle(
                                        color = Color.White,
                                        radius = 3.5.dp.toPx(),
                                        center = Offset(cx, barTop)
                                    )
                                }
                            } else if (isSelected && isTooltipVisible) {
                                val baselineY = topMargin + chartHeight - 2.dp.toPx()
                                drawCircle(
                                    color = ElectricCyan.copy(alpha = 0.35f),
                                    radius = 6.dp.toPx(),
                                    center = Offset(cx, baselineY)
                                )
                                drawCircle(
                                    color = ElectricCyan,
                                    radius = 3.dp.toPx(),
                                    center = Offset(cx, baselineY)
                                )
                            }
                        }
                    } else {
                        // 2B. Smooth Area Curve Mode
                        val coords = pts.mapIndexed { idx, pt ->
                            val cx = leftMargin + (idx + 0.5f) * slotWidth
                            val norm = (pt.doubtCount.toFloat() / yAxisMax.toFloat()).coerceIn(0f, 1f)
                            val cy = topMargin + (1f - norm * animProgress.value) * chartHeight
                            Offset(cx, cy)
                        }

                        val linePath = Path()
                        val fillPath = Path()
                        linePath.moveTo(coords.first().x, coords.first().y)
                        fillPath.moveTo(coords.first().x, topMargin + chartHeight)
                        fillPath.lineTo(coords.first().x, coords.first().y)

                        for (i in 0 until coords.size - 1) {
                            val p0 = coords[i]
                            val p1 = coords[i + 1]
                            val midX = (p0.x + p1.x) / 2f
                            linePath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                            fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                        }
                        fillPath.lineTo(coords.last().x, topMargin + chartHeight)
                        fillPath.close()

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = 0.35f),
                                    NeonViolet.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                startY = topMargin,
                                endY = topMargin + chartHeight
                            )
                        )
                        drawPath(
                            path = linePath,
                            brush = Brush.horizontalGradient(listOf(ElectricCyan, NeonViolet)),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        coords.forEachIndexed { idx, ptOffset ->
                            val isSelected = selectedDayIndex == idx
                            if (pts[idx].doubtCount > 0 || isSelected) {
                                if (isSelected && isTooltipVisible) {
                                    drawCircle(
                                        color = ElectricCyan.copy(alpha = 0.32f),
                                        radius = 8.dp.toPx(),
                                        center = ptOffset
                                    )
                                }
                                val r = if (isSelected) 4.8.dp.toPx() else 2.8.dp.toPx()
                                drawCircle(color = ElectricCyan, radius = r, center = ptOffset)
                                drawCircle(color = Color.White, radius = r * 0.45f, center = ptOffset)
                            }
                        }
                    }

                    // 3. 7-Day Rolling Average Overlay Curve (always drawn cleanly)
                    if (summary.totalDoubts30d > 0) {
                        val avgCoords = pts.mapIndexed { idx, pt ->
                            val cx = leftMargin + (idx + 0.5f) * slotWidth
                            val norm = (pt.rollingSevenDayAverage / yAxisMax.toFloat()).coerceIn(0f, 1f)
                            val cy = topMargin + (1f - norm * animProgress.value) * chartHeight
                            Offset(cx, cy)
                        }
                        val avgPath = Path().apply {
                            moveTo(avgCoords.first().x, avgCoords.first().y)
                            for (i in 0 until avgCoords.size - 1) {
                                val p0 = avgCoords[i]
                                val p1 = avgCoords[i + 1]
                                val midX = (p0.x + p1.x) / 2f
                                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                            }
                        }
                        drawPath(
                            path = avgPath,
                            color = AmberWarning,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                            )
                        )
                    }
                }

                // Interactive Floating Tooltip anchored cleanly above tapped data point
                val activePoint = inspectedDay
                val containerW = chartContainerSize.width
                val containerH = chartContainerSize.height
                val offsetValues = if (activePoint != null && summary.points.isNotEmpty() && containerW > 0 && containerH > 0) {
                    with(density) {
                        val leftM = 32.dp.toPx()
                        val rightM = 12.dp.toPx()
                        val plotW = (containerW - leftM - rightM).coerceAtLeast(1f)
                        val slotW = plotW / summary.points.size.toFloat()
                        val anchorX = leftM + (activePoint.dayIndex + 0.5f) * slotW
                        val clampedX = (anchorX - tooltipSize.width / 2f).roundToInt()
                            .coerceIn(0, (containerW - tooltipSize.width).coerceAtLeast(0))
                        val topY = 2.dp.toPx().roundToInt()
                            .coerceIn(0, (containerH - tooltipSize.height).coerceAtLeast(0))
                        IntOffset(clampedX, topY)
                    }
                } else {
                    IntOffset.Zero
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isTooltipVisible && activePoint != null,
                    enter = fadeIn(tween(160)) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.92f),
                    modifier = Modifier
                        .offset { offsetValues }
                        .onSizeChanged { tooltipSize = it }
                ) {
                    if (activePoint != null) {
                        FrequencyPointTooltip(
                            point = activePoint,
                            onDismiss = { isTooltipVisible = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend + Week-over-Week Delta Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDot(color = ElectricCyan, label = "Daily Doubts")
                    LegendDot(color = AmberWarning, label = "7-Day Avg")
                }

                val wowText = when {
                    summary.totalDoubts30d == 0 -> "30-day baseline ready"
                    summary.weekOverWeekDelta > 0 -> "+${summary.weekOverWeekDelta} vs prior 7d"
                    summary.weekOverWeekDelta < 0 -> "${summary.weekOverWeekDelta} vs prior 7d"
                    else -> "Even vs prior 7d"
                }
                val wowColor = when {
                    summary.weekOverWeekDelta > 0 -> AmberWarning
                    summary.weekOverWeekDelta < 0 -> EmeraldSuccess
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = wowText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = wowColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                )
            }

            if (summary.totalDoubts30d == 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("frequency_empty_hint"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "No doubt instances logged in the past 30 days yet. Start Live Sense or load sample sessions to populate.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            fontSize = 10.5.sp
                        )
                    )
                }
            }
        }
    }
}

