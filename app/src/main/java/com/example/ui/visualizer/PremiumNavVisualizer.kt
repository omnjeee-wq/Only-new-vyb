package com.example.ui.visualizer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium navigation-player orb inspired by the supplied VYBE reference.
 *
 * The FFT values drive the organic contour and the seven center bars. The
 * low-amplitude phase animation is only ambient lighting; it never pretends
 * to be audio data when playback is paused.
 */
@Composable
fun PremiumNavVisualizer(
    spectrum: FloatArray,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "nav_visualizer_motion")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing)
        ),
        label = "nav_visualizer_phase"
    )

    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val unit = density
        val purple = Color(0xFFB85CFF)
        val violet = Color(0xFF7B35FF)
        val ice = Color(0xFFE8D5FF)
        val averageEnergy = if (isPlaying && spectrum.isNotEmpty()) {
            spectrum.average().toFloat().coerceIn(0f, 1f)
        } else 0f
        val breathe = sin(phase) * 1.25f * unit
        val audioLift = averageEnergy * 4.5f * unit

        // Wide, soft aura behind the glass orb.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    purple.copy(alpha = 0.30f + averageEnergy * 0.16f),
                    violet.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = c,
                radius = 57f * unit
            ),
            radius = 57f * unit + audioLift,
            center = c
        )

        // Three independently breathing FFT-reactive contours create the
        // layered liquid-crystal / aurora silhouette from the reference.
        val contourSpecs = listOf(
            Triple(45f, 0f, 0.88f),
            Triple(41f, 1.25f, 0.72f),
            Triple(37f, 2.45f, 0.54f)
        )
        contourSpecs.forEachIndexed { layer, spec ->
            val points = 112
            val baseRadius = (spec.first * unit) + breathe * (if (layer == 0) 1f else 0.65f) + audioLift
            val contour = ArrayList<Offset>(points)
            for (i in 0 until points) {
                val angle = (2.0 * PI * i / points).toFloat()
                val binIndex = ((i * spectrum.size) / points).coerceIn(0, (spectrum.size - 1).coerceAtLeast(0))
                val energy = if (isPlaying) spectrum.getOrElse(binIndex) { 0f }.coerceIn(0f, 1f) else 0f
                val harmonic = sin(angle * 3.0f + phase * 0.75f + spec.second).toFloat()
                val fineWave = sin(angle * 5.0f - phase * 0.48f + spec.second * 1.7f).toFloat()
                val audioShape = energy * (7.0f + 3.0f * sin(angle * 2f + spec.second))
                val radius = baseRadius + harmonic * (2.0f + averageEnergy * 2.5f) * unit +
                    fineWave * 1.25f * unit + audioShape * unit
                contour += Offset(
                    c.x + cos(angle) * radius,
                    c.y + sin(angle) * radius
                )
            }

            val path = Path()
            fun midpoint(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            path.moveTo(
                midpoint(contour.last(), contour.first()).x,
                midpoint(contour.last(), contour.first()).y
            )
            for (i in contour.indices) {
                val current = contour[i]
                val next = contour[(i + 1) % contour.size]
                val mid = midpoint(current, next)
                path.quadraticTo(current.x, current.y, mid.x, mid.y)
            }
            path.close()

            val gradient = Brush.sweepGradient(
                colors = listOf(
                    ice.copy(alpha = spec.third),
                    purple.copy(alpha = spec.third),
                    violet.copy(alpha = spec.third * 0.92f),
                    Color(0xFFEEB9FF).copy(alpha = spec.third),
                    ice.copy(alpha = spec.third)
                ),
                center = c
            )
            drawPath(
                path = path,
                color = purple.copy(alpha = 0.12f + averageEnergy * 0.10f),
                style = Stroke(width = (8f - layer * 1.5f) * unit, cap = StrokeCap.Round)
            )
            drawPath(
                path = path,
                brush = gradient,
                style = Stroke(width = (2.5f - layer * 0.35f) * unit, cap = StrokeCap.Round)
            )
        }

        // Dark, glossy center lens with a restrained inner highlight.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF321044).copy(alpha = 0.96f),
                    Color(0xFF160D20).copy(alpha = 0.98f),
                    Color(0xFF09070D).copy(alpha = 0.99f)
                ),
                center = Offset(c.x - 8f * unit, c.y - 12f * unit),
                radius = 43f * unit
            ),
            radius = 31.5f * unit,
            center = c
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.18f),
            radius = 31.5f * unit,
            center = c,
            style = Stroke(width = 0.8f * unit)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(c.x - 13f * unit, c.y - 18f * unit),
                radius = 24f * unit
            ),
            radius = 30f * unit,
            center = c
        )

        // Seven crisp, rounded spectrum pillars. Heights are driven by live FFT.
        val baseHeights = floatArrayOf(9f, 15f, 22f, 29f, 23f, 16f, 10f)
        val barWidth = 3.1f * unit
        val gap = 3.1f * unit
        val totalWidth = baseHeights.size * barWidth + (baseHeights.size - 1) * gap
        val startX = c.x - totalWidth / 2f
        baseHeights.forEachIndexed { index, baseHeight ->
            val fftIndex = when (index) {
                0 -> 3
                1 -> 6
                2 -> 10
                3 -> 15
                4 -> 21
                5 -> 28
                else -> 36
            }
            val energy = if (isPlaying) spectrum.getOrElse(fftIndex) { 0f }.coerceIn(0f, 1f) else 0f
            val height = (baseHeight + energy * 18f) * unit
            val left = startX + index * (barWidth + gap)
            val top = c.y - height / 2f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, ice, purple.copy(alpha = 0.92f)),
                    startY = top,
                    endY = top + height
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}
