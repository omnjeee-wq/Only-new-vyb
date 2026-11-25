package com.example.ui.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.player.visualizer.AudioSpectrumHub
import kotlinx.coroutines.delay
import kotlin.math.exp

/**
 * Audio-reactive cat-ear nav spectrum.
 * Uses the existing AudioSpectrumHub playback feed; no microphone or fake waveform.
 */
@Composable
fun PremiumNavVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val playing by rememberUpdatedState(isPlaying)
    val state = remember { CatSpectrumState() }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (true) {
            if (!playing && state.settled()) {
                previous = 0L
                delay(120)
                continue
            }
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = if (previous == 0L) 1f / 60f
            else ((now - previous) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
            previous = now
            state.step(dt, playing)
            state.tick = now
        }
    }

    Canvas(modifier = modifier) {
        state.tick
        val d = density
        val c = Offset(size.width / 2f, size.height / 2f)
        val beat = state.beat
        val energy = state.energy
        val pulse = 1f + beat * .035f + state.bass * .025f

        // Soft blue/cyan ambient glow.
        val glowRadius = 49f * d
        drawCircle(
            Brush.radialGradient(
                listOf(
                    Blue.copy(alpha = .24f + energy * .18f + beat * .20f),
                    Cyan.copy(alpha = .10f + energy * .10f),
                    Color.Transparent
                ), center = c, radius = glowRadius
            ),
            radius = glowRadius, center = c
        )

        // Cat-ear silhouette: smooth rounded ears, circular lower shell.
        val w = 39f * d * pulse
        val h = 43f * d * pulse
        val earLift = (state.bass * 1.5f) * d
        val path = Path().apply {
            moveTo(c.x - w * .78f, c.y - h * .18f)
            cubicTo(c.x - w * 1.05f, c.y - h * .68f,
                c.x - w * 1.16f, c.y - h * 1.10f - earLift,
                c.x - w * .72f, c.y - h * 1.02f - earLift)
            cubicTo(c.x - w * .35f, c.y - h * .96f,
                c.x - w * .22f, c.y - h * .72f,
                c.x, c.y - h * .70f)
            cubicTo(c.x + w * .22f, c.y - h * .72f,
                c.x + w * .35f, c.y - h * .96f,
                c.x + w * .72f, c.y - h * 1.02f - earLift)
            cubicTo(c.x + w * 1.16f, c.y - h * 1.10f - earLift,
                c.x + w * 1.05f, c.y - h * .68f,
                c.x + w * .78f, c.y - h * .18f)
            cubicTo(c.x + w * 1.00f, c.y + h * .12f,
                c.x + w * .92f, c.y + h * .75f,
                c.x + w * .48f, c.y + h * .92f)
            cubicTo(c.x + w * .20f, c.y + h * 1.08f,
                c.x - w * .20f, c.y + h * 1.08f,
                c.x - w * .48f, c.y + h * .92f)
            cubicTo(c.x - w * .92f, c.y + h * .75f,
                c.x - w * 1.00f, c.y + h * .12f,
                c.x - w * .78f, c.y - h * .18f)
            close()
        }

        // Subtle filled membrane and layered neon edge.
        drawPath(path, Color(0xFF07132B).copy(alpha = .82f))
        drawPath(path, Blue.copy(alpha = .22f + beat * .16f),
            style = Stroke(width = 7f * d, cap = StrokeCap.Round))
        drawPath(path, Brush.verticalGradient(
            listOf(Blue, Cyan, Color(0xFF38E8FF)),
            startY = c.y - h, endY = c.y + h
        ), style = Stroke(width = 2.7f * d, cap = StrokeCap.Round))

        // Dark circular player lens, kept centered and clearly distinct.
        val r = 28.5f * d
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF10102A), Color(0xFF050711)),
                center = Offset(c.x - 5f * d, c.y - 7f * d),
                radius = r * 1.5f
            ), r, c
        )
        drawCircle(Cyan.copy(alpha = .88f),
            r, c, style = Stroke(width = 1.0f * d))
        drawCircle(Blue.copy(alpha = .55f),
            r + 1.8f * d, c, style = Stroke(width = 1.1f * d))

        // Seven live audio bars, sampled from the existing spectrum hub.
        val count = 7
        val bw = 2.7f * d
        val gap = 2.1f * d
        val total = count * bw + (count - 1) * gap
        val start = c.x - total / 2f
        val base = floatArrayOf(4f, 7f, 10f, 13f, 10f, 7f, 4f)
        val gain = floatArrayOf(10f, 14f, 15f, 14f, 15f, 14f, 10f)
        val bands = intArrayOf(21, 15, 9, 2, 11, 17, 24)
        for (i in 0 until count) {
            val value = state.bands[bands[i]].coerceIn(0f, 1f)
            val barH = (base[i] + value * gain[i]) * d
            val x = start + i * (bw + gap)
            val y = c.y - barH / 2f
            drawRoundRect(
                Color(0xFFB9F8FF).copy(alpha = .18f + value * .25f),
                Offset(x - d, y - d),
                Size(bw + 2f * d, barH + 2f * d),
                CornerRadius(bw)
            )
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color.White, Color(0xFFE5D8FF), Color(0xFF9BEFFF)),
                    startY = y, endY = y + barH
                ),
                Offset(x, y), Size(bw, barH), CornerRadius(bw / 2f)
            )
        }
    }
}

private class CatSpectrumState {
    val frame = AudioSpectrumHub.Frame()
    val bands = FloatArray(AudioSpectrumHub.BANDS)
    var energy = 0f
    var bass = 0f
    var beat = 0f
    var tick by mutableLongStateOf(0L)

    fun settled() = energy < .004f && bass < .004f && beat < .004f

    fun step(dt: Float, playing: Boolean) {
        val has = playing && AudioSpectrumHub.sample(frame)
        val attack = 1f - exp(-dt / .030f)
        val release = 1f - exp(-dt / .150f)
        var sum = 0f
        for (i in bands.indices) {
            val target = if (has) frame.bands[i] else 0f
            val old = bands[i]
            bands[i] = old + (target - old) * if (target > old) attack else release
            sum += bands[i]
        }
        energy = sum / bands.size
        val bassTarget = if (has) frame.bass else 0f
        bass += (bassTarget - bass) * if (bassTarget > bass) attack else release
        val beatTarget = if (has) frame.beat else 0f
        val beatAttack = 1f - exp(-dt / .015f)
        val beatRelease = 1f - exp(-dt / .180f)
        beat += (beatTarget - beat) * if (beatTarget > beat) beatAttack else beatRelease
    }
}

private val Blue = Color(0xFF164BFF)
private val Cyan = Color(0xFF22E8FF)
