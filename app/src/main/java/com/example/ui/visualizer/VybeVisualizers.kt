package com.example.ui.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Stable IDs make visualizer styles easy to select or persist later. */
enum class VybeVisualizerStyle { CIRCULAR, FLUID_WAVE, SPECTRUM_BARS, PARTICLE_BLOB }

/** A small registry keeps renderers independent from playback and app data layers. */
object VybeVisualizerRegistry {
    val availableStyles = VybeVisualizerStyle.entries
    val defaultStyle = VybeVisualizerStyle.PARTICLE_BLOB
}

@Composable
fun VybeVisualizer(
    style: VybeVisualizerStyle,
    spectrum: FloatArray,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFC45CFF)
) {
    when (style) {
        VybeVisualizerStyle.CIRCULAR -> CircularSpectrum(spectrum, modifier, color)
        VybeVisualizerStyle.FLUID_WAVE -> FluidWave(spectrum, modifier, color)
        VybeVisualizerStyle.SPECTRUM_BARS -> SpectrumBars(spectrum, modifier, color)
        VybeVisualizerStyle.PARTICLE_BLOB -> ParticleBlob(spectrum, modifier, color)
    }
}

@Composable
fun CircularSpectrum(spectrum: FloatArray, modifier: Modifier = Modifier, color: Color = Color.Magenta) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * .27f
        val count = 64
        for (i in 0 until count) {
            val value = spectrum.getOrElse(i) { 0f }
            val angle = (2.0 * PI * i / count - PI / 2).toFloat()
            val inner = radius
            val outer = radius + 5.dp.toPx() + value * size.minDimension * .20f
            drawLine(color.copy(alpha = .45f + value * .55f),
                Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
                Offset(center.x + cos(angle) * outer, center.y + sin(angle) * outer),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(color.copy(alpha = .22f), radius = radius, style = Stroke(1.dp.toPx()))
    }
}

@Composable
fun FluidWave(spectrum: FloatArray, modifier: Modifier = Modifier, color: Color = Color.Magenta) {
    Canvas(modifier) {
        if (spectrum.isEmpty()) return@Canvas
        val path = Path()
        val mid = size.height / 2f
        val amplitude = size.height * .38f
        val points = 96
        for (i in 0..points) {
            val x = size.width * i / points
            val bin = spectrum[(i * spectrum.size / points).coerceIn(0, spectrum.lastIndex)]
            val y = mid + sin(i * .13f) * amplitude * (.12f + bin * 1.8f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color.copy(alpha = .95f), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, color.copy(alpha = .20f), style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun SpectrumBars(spectrum: FloatArray, modifier: Modifier = Modifier, color: Color = Color.Magenta) {
    Canvas(modifier) {
        val count = 32
        val gap = size.width * .012f
        val barWidth = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) {
            val value = spectrum.getOrElse(i * 2) { 0f }
            val height = size.height * (.08f + value * .88f)
            drawRoundRect(color.copy(alpha = .45f + value * .55f),
                topLeft = Offset(i * (barWidth + gap), (size.height - height) / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f))
        }
    }
}

@Composable
fun ParticleBlob(spectrum: FloatArray, modifier: Modifier = Modifier, color: Color = Color(0xFFC45CFF)) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension * .28f
        val count = 72
        val path = Path()
        val points = ArrayList<Offset>(count)
        for (i in 0 until count) {
            val angle = (2.0 * PI * i / count).toFloat()
            val energy = spectrum.getOrElse(i * spectrum.size / count) { 0f }
            val harmonic = spectrum.getOrElse((i * 7 % spectrum.size)) { 0f }
            val radius = base * (1f + energy * .62f + harmonic * .18f)
            points += Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
        }
        points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawCircle(color.copy(alpha = .10f), radius = base * 1.55f, center = center)
        drawPath(path, color.copy(alpha = .12f))
        drawPath(path, color.copy(alpha = .9f), style = Stroke(width = 2.dp.toPx()))
        points.forEachIndexed { index, point ->
            val energy = spectrum.getOrElse(index * spectrum.size / count) { 0f }
            drawCircle(color.copy(alpha = .45f + energy * .55f),
                radius = 1.1.dp.toPx() + energy * 1.8.dp.toPx(), center = point)
        }
    }
}
