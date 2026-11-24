package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.DeepViolet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visual Intensity Indicator Gauge representing real-time detected level of uncertainty
 * in speech as the AI captures and analyzes audio input.
 */
@Composable
fun UncertaintyGauge(
    uncertaintyIntensity: Float, // 0.0f (Clear/Confident) to 1.0f (High Confusion/Doubt)
    uncertaintyLabel: String,
    isAnalyzing: Boolean,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    // Smooth animated uncertainty value
    val animatedIntensity by animateFloatAsState(
        targetValue = uncertaintyIntensity.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "uncertainty_gauge_val"
    )

    // Pulse animation active during live AI speech analysis
    val infiniteTransition = rememberInfiniteTransition(label = "gauge_analysis_pulse")
    val analysisPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "analysis_pulse"
    )

    val sweepRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_rotation"
    )

    // Determine current color scheme based on intensity
    val activeColor = when {
        animatedIntensity >= 0.75f -> RoseError
        animatedIntensity >= 0.45f -> AmberWarning
        animatedIntensity >= 0.20f -> ElectricCyan
        else -> EmeraldSuccess
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("uncertainty_gauge"),
        shape = RoundedCornerShape(20.dp),
        color = Slate900,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isAnalyzing) ElectricCyan.copy(alpha = 0.5f) else Slate800
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Gauge Title & Real-time Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Uncertainty Detector",
                        tint = if (isAnalyzing) ElectricCyan else NeonViolet,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Speech Uncertainty Intensity",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                // AI Real-Time Status Pill
                val statusBg = if (isAnalyzing) ElectricCyan.copy(alpha = 0.15f) else Slate850
                val statusText = if (isAnalyzing) "AI Analyzing..." else if (isListening) "Live Feed" else "Standby"
                val statusColor = if (isAnalyzing) ElectricCyan else if (isListening) EmeraldSuccess else Color.Gray

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusBg)
                        .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gauge Radial Arc Canvas with Center Readout
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(170.dp)
            ) {
                Canvas(
                    modifier = Modifier.size(160.dp)
                ) {
                    val strokeWidth = 14.dp.toPx()
                    val arcSize = size.width - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                    // Arc span: 240 degrees (starts at 150deg, goes to 390deg)
                    val startAngle = 150f
                    val totalSweep = 240f
                    val currentSweep = totalSweep * animatedIntensity

                    // 1. Background Track Arc
                    drawArc(
                        color = Color(0xFF1E293B),
                        startAngle = startAngle,
                        sweepAngle = totalSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // 2. Active Gradient Arc
                    val gradient = Brush.sweepGradient(
                        colors = listOf(
                            EmeraldSuccess, // Low (0-20%)
                            ElectricCyan,   // Inquisitive (20-45%)
                            AmberWarning,   // Moderate Doubt (45-75%)
                            RoseError,      // High Uncertainty (75-100%)
                            RoseError
                        ),
                        center = center
                    )

                    if (currentSweep > 2f) {
                        drawArc(
                            brush = gradient,
                            startAngle = startAngle,
                            sweepAngle = currentSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // 3. Glowing Needle Tip Bead
                        val tipAngleRad = Math.toRadians((startAngle + currentSweep).toDouble())
                        val radius = arcSize / 2f
                        val tipX = center.x + (radius * cos(tipAngleRad)).toFloat()
                        val tipY = center.y + (radius * sin(tipAngleRad)).toFloat()

                        drawCircle(
                            color = activeColor,
                            radius = strokeWidth * 0.55f,
                            center = Offset(tipX, tipY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = strokeWidth * 0.25f,
                            center = Offset(tipX, tipY)
                        )
                    }

                    // 4. Subtle Tick Markers along track (0%, 25%, 50%, 75%, 100%)
                    for (i in 0..4) {
                        val tickRatio = i / 4f
                        val tickAngleRad = Math.toRadians((startAngle + totalSweep * tickRatio).toDouble())
                        val innerR = (arcSize / 2f) - (strokeWidth / 2f) - 4.dp.toPx()
                        val outerR = (arcSize / 2f) - (strokeWidth / 2f) + 1.dp.toPx()

                        val x1 = center.x + (innerR * cos(tickAngleRad)).toFloat()
                        val y1 = center.y + (innerR * sin(tickAngleRad)).toFloat()
                        val x2 = center.x + (outerR * cos(tickAngleRad)).toFloat()
                        val y2 = center.y + (outerR * sin(tickAngleRad)).toFloat()

                        drawLine(
                            color = Color(0xFF475569),
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Central Numerical & Percentage Readout
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val percent = (animatedIntensity * 100).toInt()
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = activeColor,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 32.sp
                        )
                    )
                    Text(
                        text = "UNCERTAINTY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dynamic Uncertainty Classification Pill
            val intensityIcon = when {
                animatedIntensity >= 0.75f -> Icons.Default.PriorityHigh
                animatedIntensity >= 0.45f -> Icons.Default.Help
                animatedIntensity >= 0.20f -> Icons.Default.QuestionMark
                else -> Icons.Default.CheckCircle
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(activeColor.copy(alpha = 0.12f))
                    .border(1.dp, activeColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = intensityIcon,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = uncertaintyLabel,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = activeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            }

            // Legend / Scale indicators
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendIndicator(color = EmeraldSuccess, label = "0% Clear")
                LegendIndicator(color = ElectricCyan, label = "25% Inquisitive")
                LegendIndicator(color = AmberWarning, label = "50% Doubt")
                LegendIndicator(color = RoseError, label = "75%+ Confused")
            }
        }
    }
}

@Composable
private fun LegendIndicator(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 9.sp
            )
        )
    }
}
