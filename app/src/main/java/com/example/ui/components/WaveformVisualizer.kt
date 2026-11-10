package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonViolet
import kotlin.math.sin

@Composable
fun WaveformVisualizer(
    isListening: Boolean,
    audioRms: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val barCount = 28
        val barSpacing = width / barCount
        val barWidth = barSpacing * 0.55f

        val activeMultiplier = if (isListening) (audioRms.coerceIn(1f, 10f) / 10f) + 0.3f else 0.12f

        for (i in 0 until barCount) {
            val normalizedX = i.toFloat() / barCount
            val waveHeight = if (isListening) {
                val sine = sin(normalizedX * 12f + phase).toFloat()
                (height * 0.42f * activeMultiplier * (0.4f + 0.6f * (sine + 1f) / 2f)).coerceAtLeast(6f)
            } else {
                6f
            }

            val x = i * barSpacing + (barSpacing - barWidth) / 2f
            val top = centerY - waveHeight / 2f

            val gradientBrush = Brush.verticalGradient(
                colors = listOf(
                    ElectricCyan.copy(alpha = if (isListening) 0.95f else 0.35f),
                    NeonViolet.copy(alpha = if (isListening) 0.85f else 0.25f)
                ),
                startY = top,
                endY = top + waveHeight
            )

            drawRoundRect(
                brush = gradientBrush,
                topLeft = Offset(x, top),
                size = Size(barWidth, waveHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}
