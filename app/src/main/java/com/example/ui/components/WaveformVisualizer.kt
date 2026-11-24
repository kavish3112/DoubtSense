package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import kotlin.math.sin

/**
 * Visual waveform and pulse animation component that activates during live listening
 * to clearly indicate that DoubtSense is actively capturing and streaming audio.
 */
@Composable
fun WaveformVisualizer(
    isListening: Boolean,
    audioRms: Float,
    batterySaverActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val waveDuration = if (batterySaverActive) 4500 else 1800
    val pulseDuration = if (batterySaverActive) 3400 else 1400

    // Continuous harmonic phase transition for natural fluid wave motion
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = waveDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Concentric pulse wave for the live audio beacon
    val pulseProgress1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress_1"
    )

    val pulseProgress2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration, delayMillis = pulseDuration / 2, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress_2"
    )

    // Smooth responsive RMS multiplier
    val smoothedRms by animateFloatAsState(
        targetValue = if (isListening) audioRms.coerceIn(0f, 10f) else 0f,
        animationSpec = spring(stiffness = if (batterySaverActive) 120f else 300f),
        label = "smoothed_rms"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("waveform_visualizer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Live Audio Telemetry Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Status Indicator with Pulsing REC Beacon
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotColor = if (isListening) RoseError else Color(0xFF64748B)
                val dotScale = if (isListening) (0.8f + (pulseProgress1 * 0.4f)) else 1f
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(dotScale)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isListening) "LIVE AUDIO CAPTURE" else "AUDIO FEED INACTIVE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isListening) ElectricCyan else Color(0xFF94A3B8),
                        letterSpacing = 1.sp,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.testTag("waveform_visualizer_status")
                )
            }

            // Real-time Audio Level & Telemetry
            if (isListening) {
                val decibels = (smoothedRms * 3.5f + 12f).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$decibels dB",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ElectricCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                    )
                }
            } else {
                Text(
                    text = "STANDBY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main Waveform & Pulse Animation Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            ) {
                val width = size.width
                val height = size.height
                val centerX = width / 2f
                val centerY = height / 2f

                // 1. Live Pulse Rings around Center when Listening
                if (isListening) {
                    val maxRadius = height * 0.52f
                    val rmsBonus = (smoothedRms / 10f) * 12.dp.toPx()

                    // Ring 1
                    val r1 = (pulseProgress1 * maxRadius + rmsBonus).coerceAtLeast(8.dp.toPx())
                    val alpha1 = (1f - pulseProgress1).coerceIn(0f, 1f) * 0.45f
                    drawCircle(
                        color = ElectricCyan.copy(alpha = alpha1),
                        radius = r1,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Ring 2
                    val r2 = (pulseProgress2 * maxRadius + rmsBonus).coerceAtLeast(8.dp.toPx())
                    val alpha2 = (1f - pulseProgress2).coerceIn(0f, 1f) * 0.35f
                    drawCircle(
                        color = NeonViolet.copy(alpha = alpha2),
                        radius = r2,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // 2. Center Baseline Glow
                drawLine(
                    color = if (isListening) ElectricCyan.copy(alpha = 0.2f) else Slate800,
                    start = Offset(0f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 1.dp.toPx()
                )

                // 3. Multi-Frequency Waveform Bars
                val barCount = 32
                val barSpacing = width / barCount
                val barWidth = (barSpacing * 0.58f).coerceAtLeast(2.dp.toPx())
                val activeMultiplier = if (isListening) {
                    ((smoothedRms / 10f) * 0.8f + 0.35f).coerceIn(0.2f, 1.3f)
                } else {
                    0.10f
                }

                for (i in 0 until barCount) {
                    val normalizedX = i.toFloat() / barCount
                    val distanceFromCenter = kotlin.math.abs(i - (barCount / 2f)) / (barCount / 2f)
                    val centerAttenuation = 1f - (distanceFromCenter * 0.35f)

                    val waveHeight = if (isListening) {
                        // Multi-harmonic sinusoidal wave formula
                        val primarySine = sin(normalizedX * 10f + phase).toFloat()
                        val secondarySine = sin(normalizedX * 18f - phase * 1.4f).toFloat() * 0.5f
                        val combinedWave = (primarySine + secondarySine + 1.5f) / 3f

                        val dynamicHeight = height * 0.85f * activeMultiplier * combinedWave * centerAttenuation
                        dynamicHeight.coerceIn(8.dp.toPx(), height * 0.92f)
                    } else {
                        // Subtle gentle resting breathing animation
                        val idleWave = sin(normalizedX * 6f + phase * 0.5f).toFloat()
                        (6.dp.toPx() + (idleWave * 2.dp.toPx())).coerceAtLeast(4.dp.toPx())
                    }

                    val x = i * barSpacing + (barSpacing - barWidth) / 2f
                    val top = centerY - waveHeight / 2f

                    // Color gradient for active waveform
                    val topColor = if (isListening) {
                        if (i % 2 == 0) ElectricCyan else NeonViolet
                    } else {
                        Color(0xFF334155)
                    }
                    val bottomColor = if (isListening) {
                        if (i % 2 == 0) NeonViolet else ElectricCyan
                    } else {
                        Color(0xFF1E293B)
                    }

                    val gradientBrush = Brush.verticalGradient(
                        colors = listOf(
                            topColor.copy(alpha = if (isListening) 0.95f else 0.4f),
                            bottomColor.copy(alpha = if (isListening) 0.80f else 0.25f)
                        ),
                        startY = top,
                        endY = top + waveHeight
                    )

                    drawRoundRect(
                        brush = gradientBrush,
                        topLeft = Offset(x, top),
                        size = Size(barWidth, waveHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
}
