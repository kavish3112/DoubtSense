package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConversationSession
import com.example.data.model.DoubtItem
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.DeepViolet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data structure representing a multi-session doubt intensity data point.
 */
data class SessionTrendPoint(
    val sessionId: String,
    val sessionLabel: String,
    val timestamp: Long,
    val intensity: Float, // 0.0f (Clear) to 1.0f (High Uncertainty)
    val topic: String,
    val doubtCount: Int,
    val isAmbiguous: Boolean = false
)

/**
 * Custom Jetpack Compose Line Chart component visualizing the historical trend of
 * doubt intensity levels across multiple conversation and study sessions.
 */
@Composable
fun DoubtIntensityTrendChart(
    doubts: List<DoubtItem>,
    sessions: List<ConversationSession> = emptyList(),
    modifier: Modifier = Modifier
) {
    // Generate multi-session trend points from Room sessions, or doubts / demo baseline
    val sessionPoints = remember(doubts, sessions) {
        if (sessions.size >= 2) {
            deriveTrendPointsFromSessions(sessions)
        } else {
            deriveSessionTrendPoints(doubts)
        }
    }

    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    var showOnlyHighDoubt by remember { mutableStateOf(false) }

    val displayPoints = remember(sessionPoints, showOnlyHighDoubt) {
        if (showOnlyHighDoubt) {
            sessionPoints.filter { it.intensity >= 0.5f }
        } else {
            sessionPoints
        }
    }

    // Chart entry animation
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(displayPoints.size) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }

    // Derived Statistics
    val avgIntensity = remember(displayPoints) {
        if (displayPoints.isEmpty()) 0f
        else displayPoints.map { it.intensity }.average().toFloat()
    }
    val peakIntensity = remember(displayPoints) {
        displayPoints.maxOfOrNull { it.intensity } ?: 0f
    }
    val isTrendingDown = remember(displayPoints) {
        if (displayPoints.size >= 2) {
            val firstHalf = displayPoints.take(displayPoints.size / 2).map { it.intensity }.average()
            val secondHalf = displayPoints.takeLast(displayPoints.size / 2).map { it.intensity }.average()
            secondHalf <= firstHalf
        } else true
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("doubt_intensity_trend_chart"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = BorderStroke(1.dp, Slate800)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Chart Header with Icon & Trend Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = "Trend Chart",
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Historical Doubt Intensity Trend",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "Multi-Session Learning & Uncertainty Curve",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Trend Direction Badge
                val trendColor = if (isTrendingDown) EmeraldSuccess else AmberWarning
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(trendColor.copy(alpha = 0.15f))
                        .border(1.dp, trendColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isTrendingDown) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = trendColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isTrendingDown) "Improving" else "Active Doubt",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = trendColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metric Summary Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(
                    label = "Avg Intensity",
                    value = "${(avgIntensity * 100).toInt()}%",
                    color = if (avgIntensity >= 0.7f) RoseError else if (avgIntensity >= 0.4f) AmberWarning else ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "Peak Intensity",
                    value = "${(peakIntensity * 100).toInt()}%",
                    color = if (peakIntensity >= 0.75f) RoseError else AmberWarning,
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "Sessions",
                    value = "${displayPoints.size}",
                    color = NeonViolet,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !showOnlyHighDoubt,
                    onClick = { showOnlyHighDoubt = false },
                    label = { Text("All Sessions", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                        selectedLabelColor = ElectricCyan
                    )
                )
                FilterChip(
                    selected = showOnlyHighDoubt,
                    onClick = { showOnlyHighDoubt = true },
                    label = { Text("High Uncertainty (>50%)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RoseError.copy(alpha = 0.2f),
                        selectedLabelColor = RoseError
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Tooltip Callout if a point is selected
            val selectedPoint = selectedPointIndex?.let { displayPoints.getOrNull(it) }
            if (selectedPoint != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Slate850,
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${selectedPoint.sessionLabel} • ${selectedPoint.topic}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "${selectedPoint.doubtCount} doubt(s) recorded ${if (selectedPoint.isAmbiguous) "• Ambiguity detected" else ""}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                        }
                        Text(
                            text = "${(selectedPoint.intensity * 100).toInt()}% Intensity",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selectedPoint.intensity >= 0.75f) RoseError else if (selectedPoint.intensity >= 0.45f) AmberWarning else ElectricCyan,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }

            // Main Canvas Line Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .pointerInput(displayPoints) {
                            detectTapGestures { offset ->
                                val pointWidth = size.width / (displayPoints.size.coerceAtLeast(1))
                                val idx = ((offset.x - 36.dp.toPx()) / (size.width - 48.dp.toPx()) * (displayPoints.size - 1))
                                    .toInt()
                                    .coerceIn(0, displayPoints.size - 1)
                                selectedPointIndex = if (selectedPointIndex == idx) null else idx
                            }
                        }
                        .pointerInput(displayPoints) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val x = change.position.x
                                val idx = ((x - 36.dp.toPx()) / (size.width - 48.dp.toPx()) * (displayPoints.size - 1))
                                    .toInt()
                                    .coerceIn(0, displayPoints.size - 1)
                                selectedPointIndex = idx
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    val leftMargin = 38.dp.toPx()
                    val rightMargin = 16.dp.toPx()
                    val topMargin = 20.dp.toPx()
                    val bottomMargin = 30.dp.toPx()

                    val chartWidth = w - leftMargin - rightMargin
                    val chartHeight = h - topMargin - bottomMargin

                    // 1. Horizontal Gridlines & Y-Axis Labels
                    val yLevels = listOf(1.0f, 0.75f, 0.5f, 0.25f, 0.0f)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()), 0f)

                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#64748B")
                        textSize = 9.sp.toPx()
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }

                    yLevels.forEach { level ->
                        val y = topMargin + (1f - level) * chartHeight
                        drawLine(
                            color = Color(0xFF1E293B),
                            start = Offset(leftMargin, y),
                            end = Offset(w - rightMargin, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                        val label = "${(level * 100).toInt()}%"
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            leftMargin - 6.dp.toPx(),
                            y + 3.dp.toPx(),
                            textPaint
                        )
                    }

                    if (displayPoints.size < 2) {
                        return@Canvas
                    }

                    // Compute Coordinates
                    val stepX = chartWidth / (displayPoints.size - 1)
                    val pointsCoords = displayPoints.mapIndexed { i, p ->
                        val x = leftMargin + i * stepX
                        val currentY = topMargin + (1f - (p.intensity * animProgress.value)) * chartHeight
                        Offset(x, currentY)
                    }

                    // 2. Build Bezier Spline Path
                    val linePath = Path()
                    val fillPath = Path()

                    linePath.moveTo(pointsCoords.first().x, pointsCoords.first().y)
                    fillPath.moveTo(pointsCoords.first().x, topMargin + chartHeight)
                    fillPath.lineTo(pointsCoords.first().x, pointsCoords.first().y)

                    for (i in 0 until pointsCoords.size - 1) {
                        val p0 = pointsCoords[i]
                        val p1 = pointsCoords[i + 1]

                        val cp1X = p0.x + (p1.x - p0.x) / 2f
                        val cp1Y = p0.y
                        val cp2X = p0.x + (p1.x - p0.x) / 2f
                        val cp2Y = p1.y

                        linePath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p1.x, p1.y)
                        fillPath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p1.x, p1.y)
                    }

                    fillPath.lineTo(pointsCoords.last().x, topMargin + chartHeight)
                    fillPath.close()

                    // 3. Draw Gradient Area Fill under Curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ElectricCyan.copy(alpha = 0.35f),
                                NeonViolet.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            startY = topMargin,
                            endY = topMargin + chartHeight
                        )
                    )

                    // 4. Draw Line Curve
                    drawPath(
                        path = linePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(ElectricCyan, NeonViolet, RoseError)
                        ),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // 5. Selected Point Vertical Guide Line
                    selectedPointIndex?.let { selIdx ->
                        if (selIdx in pointsCoords.indices) {
                            val selPoint = pointsCoords[selIdx]
                            drawLine(
                                color = ElectricCyan.copy(alpha = 0.7f),
                                start = Offset(selPoint.x, topMargin),
                                end = Offset(selPoint.x, topMargin + chartHeight),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
                            )
                            // Pulse Ring around selected point
                            drawCircle(
                                color = ElectricCyan.copy(alpha = 0.35f),
                                radius = 10.dp.toPx(),
                                center = selPoint
                            )
                        }
                    }

                    // 6. Draw Data Point Dots & X-Axis Labels
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#94A3B8")
                        textSize = 8.5.sp.toPx()
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    pointsCoords.forEachIndexed { i, pt ->
                        val dataPoint = displayPoints[i]
                        val dotColor = when {
                            dataPoint.intensity >= 0.75f -> RoseError
                            dataPoint.intensity >= 0.45f -> AmberWarning
                            dataPoint.intensity >= 0.20f -> ElectricCyan
                            else -> EmeraldSuccess
                        }

                        // Outer ring
                        drawCircle(
                            color = Slate900,
                            radius = 5.dp.toPx(),
                            center = pt
                        )
                        // Colored bead
                        drawCircle(
                            color = dotColor,
                            radius = 4.dp.toPx(),
                            center = pt
                        )
                        // White core
                        drawCircle(
                            color = Color.White,
                            radius = 1.8.dp.toPx(),
                            center = pt
                        )

                        // X-axis Session Label
                        val xLabel = dataPoint.sessionId
                        drawContext.canvas.nativeCanvas.drawText(
                            xLabel,
                            pt.x,
                            topMargin + chartHeight + 16.dp.toPx(),
                            labelPaint
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hint Text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Tap or drag on any data point to inspect session doubt intensity",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Slate850,
        border = BorderStroke(1.dp, Slate800)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            )
        }
    }
}

/**
 * Derives session trend points from the database doubt records, or bundles realistic
 * progressive learning demonstration points when the user starts with few records.
 */
internal fun deriveTrendPointsFromSessions(sessions: List<ConversationSession>): List<SessionTrendPoint> {
    return sessions.sortedBy { it.startTime }.takeLast(8).mapIndexed { idx, session ->
        SessionTrendPoint(
            sessionId = "S${idx + 1}",
            sessionLabel = "Session ${idx + 1}",
            timestamp = session.startTime,
            intensity = session.averageDoubtIntensity.coerceIn(0.05f, 0.99f),
            topic = session.title.ifBlank { session.category },
            doubtCount = session.doubtCount,
            isAmbiguous = session.peakDoubtIntensity >= 0.8f
        )
    }
}

internal fun deriveSessionTrendPoints(doubts: List<DoubtItem>): List<SessionTrendPoint> {
    if (doubts.size >= 3) {
        // Group chronological doubts into distinct session batches (e.g. up to 8 sessions)
        val sorted = doubts.sortedBy { it.timestamp }
        val chunkCount = (sorted.size / 2).coerceIn(3, 8)
        val chunkSize = (sorted.size.toFloat() / chunkCount).coerceAtLeast(1f).toInt()

        return sorted.chunked(chunkSize).take(8).mapIndexed { idx, chunk ->
            val avgConf = chunk.map { it.confidenceScore }.average().toFloat().coerceIn(0.15f, 0.98f)
            val primaryTopic = chunk.groupingBy { it.topic }.eachCount().maxByOrNull { it.value }?.key ?: "General Doubt"
            val hasAmbiguous = chunk.any { it.isAmbiguous }
            SessionTrendPoint(
                sessionId = "S${idx + 1}",
                sessionLabel = "Session ${idx + 1}",
                timestamp = chunk.first().timestamp,
                intensity = avgConf,
                topic = primaryTopic,
                doubtCount = chunk.size,
                isAmbiguous = hasAmbiguous
            )
        }
    }

    // Comprehensive realistic multi-session learning trajectory showing progression
    val now = System.currentTimeMillis()
    val day = 86400000L
    return listOf(
        SessionTrendPoint(
            sessionId = "S1",
            sessionLabel = "Session 1",
            timestamp = now - (5 * day),
            intensity = 0.92f,
            topic = "Random Forest: Ensembles",
            doubtCount = 4,
            isAmbiguous = true
        ),
        SessionTrendPoint(
            sessionId = "S2",
            sessionLabel = "Session 2",
            timestamp = now - (4 * day),
            intensity = 0.84f,
            topic = "Biology: ATP Synthase",
            doubtCount = 3,
            isAmbiguous = false
        ),
        SessionTrendPoint(
            sessionId = "S3",
            sessionLabel = "Session 3",
            timestamp = now - (3 * day),
            intensity = 0.68f,
            topic = "Algorithms: Quicksort Pivot",
            doubtCount = 3,
            isAmbiguous = true
        ),
        SessionTrendPoint(
            sessionId = "S4",
            sessionLabel = "Session 4",
            timestamp = now - (2 * day),
            intensity = 0.55f,
            topic = "ML: Decision Tree Splits",
            doubtCount = 2,
            isAmbiguous = false
        ),
        SessionTrendPoint(
            sessionId = "S5",
            sessionLabel = "Session 5",
            timestamp = now - (1 * day),
            intensity = 0.38f,
            topic = "Photosynthesis Light Cycle",
            doubtCount = 2,
            isAmbiguous = false
        ),
        SessionTrendPoint(
            sessionId = "S6",
            sessionLabel = "Session 6 (Live)",
            timestamp = now,
            intensity = if (doubts.isNotEmpty()) doubts.map { it.confidenceScore }.average().toFloat().coerceIn(0.15f, 0.95f) else 0.28f,
            topic = doubts.lastOrNull()?.topic ?: "Concept Synthesis",
            doubtCount = doubts.size.coerceAtLeast(1),
            isAmbiguous = doubts.any { it.isAmbiguous }
        )
    )
}
