package com.example.ui.visualizer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Compact liquid-spectrum orb for the center navigation slot.
 * The three contours are smooth, low-amplitude FFT ribbons (not oversized
 * circular rings); a frame-rate smoother prevents capture-rate stepping.
 */
@Composable
fun PremiumNavVisualizer(
    spectrum: FloatArray,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val latestSpectrum by rememberUpdatedState(spectrum)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    var smoothBands by remember { mutableStateOf(FloatArray(BAND_COUNT)) }

    // Interpolate the lower-rate Android Visualizer callbacks at display frame rate.
    // Fast attack and slower release make transients clear without a jittery outline.
    LaunchedEffect(Unit) {
        var previousFrame = 0L
        while (true) {
            val frameTime = withFrameNanos { it }
            val deltaSeconds = if (previousFrame == 0L) 1f / 60f
            else ((frameTime - previousFrame) / 1_000_000_000f).coerceIn(1f / 120f, 1f / 20f)
            previousFrame = frameTime

            val source = latestSpectrum
            val current = smoothBands
            val next = FloatArray(BAND_COUNT)
            var changed = false
            for (i in 0 until BAND_COUNT) {
                val position = if (BAND_COUNT <= 1 || source.size <= 1) 0f
                else i.toFloat() * (source.lastIndex) / (BAND_COUNT - 1)
                val low = floor(position).toInt().coerceIn(0, source.lastIndex.coerceAtLeast(0))
                val high = (low + 1).coerceAtMost(source.lastIndex.coerceAtLeast(0))
                val fraction = position - low
                val target = if (latestIsPlaying && source.isNotEmpty()) {
                    (source[low] + (source[high] - source[low]) * fraction).coerceIn(0f, 1f)
                } else 0f
                val old = current[i]
                val timeConstant = if (target > old) 0.060f else 0.185f
                val alpha = (1f - exp(-deltaSeconds / timeConstant)).coerceIn(0f, 1f)
                val value = old + (target - old) * alpha
                next[i] = value
                if (abs(value - old) > 0.0008f) changed = true
            }
            if (changed) smoothBands = next
        }
    }

    val transition = rememberInfiniteTransition(label = "nav_liquid_phase")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing)
        ),
        label = "nav_liquid_phase_value"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val density = this.density
        val bands = smoothBands
        val purple = Color(0xFFB95CFF)
        val violet = Color(0xFF813BFF)
        val ice = Color(0xFFF0DEFF)
        val energy = bands.average().toFloat().coerceIn(0f, 1f)
        val bass = bands.take(5).average().toFloat().coerceIn(0f, 1f)

        // Restrained aura; keep the visualizer's footprint close to the reference.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    purple.copy(alpha = 0.19f + energy * 0.12f),
                    violet.copy(alpha = 0.085f),
                    Color.Transparent
                ),
                center = center,
                radius = 41f * density
            ),
            radius = 41f * density,
            center = center
        )

        // Three offset, smooth fluid ribbons. Each contour follows a softened
        // frequency band and a different harmonic phase, producing a liquid
        // spectrum silhouette rather than static concentric circles.
        val contourSpecs = listOf(
            ContourSpec(32.5f, 0.0f, 0.90f),
            ContourSpec(30.2f, 1.35f, 0.70f),
            ContourSpec(28.0f, 2.55f, 0.54f)
        )
        contourSpecs.forEachIndexed { layer, spec ->
            val pointCount = 96
            val points = ArrayList<Offset>(pointCount)
            for (i in 0 until pointCount) {
                val angle = (2.0 * PI * i / pointCount).toFloat()
                val normalizedAngle = (angle + (PI / 2.0).toFloat()) / (2f * PI.toFloat())
                val bandPosition = normalizedAngle * (bands.size - 1)
                val bandLow = floor(bandPosition).toInt().coerceIn(0, bands.lastIndex)
                val bandHigh = (bandLow + 1).coerceAtMost(bands.lastIndex)
                val bandFraction = bandPosition - bandLow
                val smoothFraction = bandFraction * bandFraction * (3f - 2f * bandFraction)
                val localEnergy = bands[bandLow] + (bands[bandHigh] - bands[bandLow]) * smoothFraction

                val broadFlow = sin(angle * 3f + phase * 0.72f + spec.phase) * (1.65f + energy * 0.85f)
                val fineFlow = sin(angle * 5f - phase * 0.43f + spec.phase * 1.55f) * 0.95f
                val bassFlow = sin(angle * 2f + phase * 0.34f + spec.phase) * bass * 1.55f
                val audioFlow = localEnergy * (3.0f + 0.95f * sin(angle * 2f + spec.phase))
                val radius = (spec.radius + broadFlow + fineFlow + bassFlow + audioFlow) * density
                points += Offset(
                    center.x + cos(angle) * radius,
                    center.y + sin(angle) * radius
                )
            }

            val path = smoothClosedPath(points)
            val contourAlpha = spec.alpha * (0.88f + energy * 0.12f)
            drawPath(path, purple.copy(alpha = 0.025f * contourAlpha))
            drawPath(
                path,
                color = purple.copy(alpha = 0.085f * contourAlpha),
                style = Stroke(width = 3.0f * density, cap = StrokeCap.Round)
            )
            drawPath(
                path,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        ice.copy(alpha = contourAlpha * 0.92f),
                        purple.copy(alpha = contourAlpha),
                        violet.copy(alpha = contourAlpha * 0.92f),
                        Color(0xFFE6B7FF).copy(alpha = contourAlpha),
                        ice.copy(alpha = contourAlpha * 0.92f)
                    ),
                    center = center
                ),
                style = Stroke(width = 0.95f * density, cap = StrokeCap.Round)
            )
        }

        // Small dark glass lens, with a quiet top-left specular highlight.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF321044).copy(alpha = 0.97f),
                    Color(0xFF170D21).copy(alpha = 0.99f),
                    Color(0xFF09070D)
                ),
                center = Offset(center.x - 6.5f * density, center.y - 8.5f * density),
                radius = 34f * density
            ),
            radius = 22f * density,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.16f),
            radius = 22f * density,
            center = center,
            style = Stroke(width = 0.75f * density)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(center.x - 9f * density, center.y - 12f * density),
                radius = 20f * density
            ),
            radius = 21f * density,
            center = center
        )

        // Compact seven-bar spectrum: short, crisp pillars like the reference.
        val baseHeights = floatArrayOf(4.5f, 7f, 9.5f, 12.5f, 10f, 7.5f, 5f)
        val fftIndices = intArrayOf(3, 7, 12, 18, 24, 31, 39)
        val barWidth = 2.2f * density
        val gap = 2.15f * density
        val totalWidth = baseHeights.size * barWidth + (baseHeights.size - 1) * gap
        val startX = center.x - totalWidth / 2f
        baseHeights.forEachIndexed { index, baseHeight ->
            val fftValue = bands.getOrElse(fftIndices[index]) { 0f }.coerceIn(0f, 1f)
            val height = (baseHeight + fftValue * 4.2f) * density
            val left = startX + index * (barWidth + gap)
            val top = center.y - height / 2f
            drawRoundRect(
                color = purple.copy(alpha = 0.22f),
                topLeft = Offset(left - 0.65f * density, top - 0.7f * density),
                size = Size(barWidth + 1.3f * density, height + 1.4f * density),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, ice, Color(0xFFD5A4FF)),
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

private data class ContourSpec(
    val radius: Float,
    val phase: Float,
    val alpha: Float
)

private const val BAND_COUNT = 32

private fun smoothClosedPath(points: List<Offset>): Path {
    val path = Path()
    if (points.size < 3) return path
    fun midpoint(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    val firstMidpoint = midpoint(points.last(), points.first())
    path.moveTo(firstMidpoint.x, firstMidpoint.y)
    for (i in points.indices) {
        val current = points[i]
        val nextMidpoint = midpoint(current, points[(i + 1) % points.size])
        path.quadraticTo(current.x, current.y, nextMidpoint.x, nextMidpoint.y)
    }
    path.close()
    return path
}
