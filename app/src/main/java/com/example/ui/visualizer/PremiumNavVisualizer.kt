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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.player.visualizer.AudioSpectrumHub
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Nav-bar centre orb: a dark glass lens wrapped in layered, translucent
 * "liquid membranes" (blue -> violet -> magenta) with a 7-bar equalizer inside.
 *
 * Everything is driven by the real playing audio (AudioSpectrumHub):
 *  - membranes ripple according to the frequency bands,
 *  - bass / beat pulses make the whole orb swell and glow on every kick,
 *  - the 7 bars jump with the music.
 * No microphone is used.
 */
@Composable
fun PremiumNavVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val playing by rememberUpdatedState(isPlaying)
    val orb = remember { OrbState() }

    LaunchedEffect(Unit) {
        var previous = 0L
        while (true) {
            if (!playing && orb.isSettled()) {
                // Fully calm and paused: stop drawing to save battery.
                previous = 0L
                delay(120)
                continue
            }
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = if (previous == 0L) 1f / 60f
            else ((now - previous) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
            previous = now
            orb.step(dt, playing)
            orb.tick = now // invalidates the Canvas draw only
        }
    }

    Canvas(modifier = modifier) {
        orb.tick // subscribe to frame updates
        val d = density
        val center = Offset(size.width / 2f, size.height / 2f)

        val energy = orb.energy
        val beat = orb.beat
        val bass = orb.bass
        val pulse = 1f + 0.045f * beat + 0.03f * bass

        // ---- 1. Soft aura (blue-violet, flashes on beats) ----
        val auraRadius = 48f * d
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Violet.copy(alpha = 0.26f + 0.30f * beat + 0.12f * energy),
                    Blue.copy(alpha = 0.10f + 0.10f * energy),
                    Color.Transparent
                ),
                center = center,
                radius = auraRadius
            ),
            radius = auraRadius,
            center = center
        )

        // ---- 2. Liquid membranes ----
        for (layer in LAYERS.indices) {
            val spec = LAYERS[layer]
            buildMembrane(orb, spec, d, center, pulse)

            val rotation = orb.phase * spec.spin * 18f
            rotate(degrees = rotation, pivot = center) {
                // translucent body
                drawPath(
                    path = orb.path,
                    brush = Brush.sweepGradient(
                            0f to Blue.copy(alpha = spec.fill),
                            0.35f to Violet.copy(alpha = spec.fill * 1.3f),
                            0.7f to Magenta.copy(alpha = spec.fill),
                            1f to Blue.copy(alpha = spec.fill),
                        center = center
                    )
                )
                // soft halo line
                drawPath(
                    path = orb.path,
                    brush = Brush.sweepGradient(
                            0f to Blue.copy(alpha = spec.alpha * 0.30f),
                            0.4f to Violet.copy(alpha = spec.alpha * 0.30f),
                            0.75f to Magenta.copy(alpha = spec.alpha * 0.30f),
                            1f to Blue.copy(alpha = spec.alpha * 0.30f),
                        center = center
                    ),
                    style = Stroke(width = 3.2f * d, cap = StrokeCap.Round)
                )
                // crisp bright edge
                drawPath(
                    path = orb.path,
                    brush = Brush.sweepGradient(
                            0f to Ice.copy(alpha = spec.alpha),
                            0.25f to Blue.copy(alpha = spec.alpha),
                            0.55f to Violet.copy(alpha = spec.alpha),
                            0.8f to Magenta.copy(alpha = spec.alpha),
                            1f to Ice.copy(alpha = spec.alpha),
                        center = center
                    ),
                    style = Stroke(width = spec.width * d, cap = StrokeCap.Round)
                )
            }
        }

        // ---- 3. Dark glass lens ----
        val lensRadius = 28.5f * d * (1f + 0.012f * beat)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF2A1148),
                    Color(0xFF150B26),
                    Color(0xFF07050C)
                ),
                center = Offset(center.x - 6f * d, center.y - 9f * d),
                radius = lensRadius * 1.55f
            ),
            radius = lensRadius,
            center = center
        )
        // glowing rim
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Ice.copy(alpha = 0.85f),
                    Blue.copy(alpha = 0.75f),
                    Violet.copy(alpha = 0.85f),
                    Magenta.copy(alpha = 0.75f),
                    Ice.copy(alpha = 0.85f)
                ),
                center = center
            ),
            radius = lensRadius,
            center = center,
            style = Stroke(width = 1.1f * d)
        )
        // glossy top-left highlight
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(center.x - 9f * d, center.y - 13f * d),
                radius = 19f * d
            ),
            radius = lensRadius - 1f * d,
            center = center
        )

        // ---- 4. Seven equalizer bars ----
        val barWidth = 2.5f * d
        val gap = 2.3f * d
        val totalWidth = BAR_COUNT * barWidth + (BAR_COUNT - 1) * gap
        val startX = center.x - totalWidth / 2f
        for (i in 0 until BAR_COUNT) {
            val value = orb.barValue(i)
            val h = (BAR_BASE[i] + value * BAR_GAIN[i]) * d
            val left = startX + i * (barWidth + gap)
            val top = center.y - h / 2f
            // glow
            drawRoundRect(
                color = Violet.copy(alpha = 0.20f + 0.25f * value),
                topLeft = Offset(left - 0.9f * d, top - 0.9f * d),
                size = Size(barWidth + 1.8f * d, h + 1.8f * d),
                cornerRadius = CornerRadius(barWidth)
            )
            // bar
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Ice, Color(0xFFC9A2FF)),
                    startY = top,
                    endY = top + h
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Geometry
// ---------------------------------------------------------------------------

private fun androidx.compose.ui.graphics.drawscope.DrawScope.buildMembrane(
    orb: OrbState,
    spec: LayerSpec,
    d: Float,
    center: Offset,
    pulse: Float
) {
    val phase = orb.phase * spec.speed + spec.offset
    val energy = orb.energy
    val bass = orb.bass
    val bands = orb.bands
    val usable = (AudioSpectrumHub.BANDS * 0.78f)

    // Idle wobble keeps the surface liquid even in quiet passages.
    val wobble = 0.85f + energy * 1.6f + bass * 1.1f
    val audioGain = (3.2f + 3.6f * energy) * spec.audio

    for (i in 0 until POINTS) {
        val angle = orb.angles[i]

        // Mirror the spectrum left/right and let it travel around the ring.
        var t = (angle / TWO_PI + phase * 0.035f * spec.spin) % 1f
        if (t < 0f) t += 1f
        val m = if (t < 0.5f) t * 2f else (1f - t) * 2f
        val pos = m * usable
        val lo = floor(pos).toInt().coerceIn(0, AudioSpectrumHub.BANDS - 1)
        val hi = (lo + 1).coerceAtMost(AudioSpectrumHub.BANDS - 1)
        val f = pos - lo
        val smooth = f * f * (3f - 2f * f)
        val local = bands[lo] + (bands[hi] - bands[lo]) * smooth

        val flow =
            sin(angle * 2f + phase * 0.9f) * 1.00f +
                sin(angle * 3f - phase * 1.25f + 1.3f) * 0.80f +
                sin(angle * 5f + phase * 0.65f + 2.1f) * 0.45f
        val breathing = sin(angle * 2f + phase * 0.5f + spec.offset) * bass * 2.4f

        val r = (spec.radius + flow * wobble + breathing + local * audioGain) * d * pulse
        orb.xs[i] = center.x + cos(angle) * r
        orb.ys[i] = center.y + sin(angle) * r
    }

    // Smooth closed curve through midpoints (quadratic) => no kinks.
    val path = orb.path
    path.rewind()
    val last = POINTS - 1
    path.moveTo((orb.xs[last] + orb.xs[0]) / 2f, (orb.ys[last] + orb.ys[0]) / 2f)
    for (i in 0 until POINTS) {
        val n = if (i == last) 0 else i + 1
        path.quadraticTo(
            orb.xs[i], orb.ys[i],
            (orb.xs[i] + orb.xs[n]) / 2f, (orb.ys[i] + orb.ys[n]) / 2f
        )
    }
    path.close()
}

// ---------------------------------------------------------------------------
// State (mutated in place every frame; no allocations in the hot path)
// ---------------------------------------------------------------------------

private class OrbState {
    val frame = AudioSpectrumHub.Frame()
    val bands = FloatArray(AudioSpectrumHub.BANDS)
    var energy = 0f
    var bass = 0f
    var beat = 0f
    var phase = 0f
    var tick by mutableLongStateOf(0L)

    val path = Path()
    val xs = FloatArray(POINTS)
    val ys = FloatArray(POINTS)
    val angles = FloatArray(POINTS) { it * TWO_PI / POINTS }

    fun isSettled(): Boolean = energy < 0.004f && bass < 0.004f && beat < 0.004f

    fun barValue(i: Int): Float {
        val index = BAR_BANDS[i]
        // average of the band and its neighbour = steadier, still punchy
        val a = bands[index]
        val b = bands[(index + 1).coerceAtMost(AudioSpectrumHub.BANDS - 1)]
        return ((a + b) * 0.5f).coerceIn(0f, 1f)
    }

    fun step(dt: Float, playing: Boolean) {
        val has = playing && AudioSpectrumHub.sample(frame)

        // Fast attack, smooth release => reacts to every hit but stays fluid.
        val attack = 1f - exp(-dt / 0.030f)
        val release = 1f - exp(-dt / 0.150f)
        var sum = 0f
        for (i in 0 until AudioSpectrumHub.BANDS) {
            val target = if (has) frame.bands[i] else 0f
            val old = bands[i]
            bands[i] = old + (target - old) * (if (target > old) attack else release)
            sum += bands[i]
        }
        energy = sum / AudioSpectrumHub.BANDS

        val bassTarget = if (has) frame.bass else 0f
        bass += (bassTarget - bass) * (if (bassTarget > bass) attack else release)

        val beatTarget = if (has) frame.beat else 0f
        val beatAttack = 1f - exp(-dt / 0.015f)
        val beatRelease = 1f - exp(-dt / 0.180f)
        beat += (beatTarget - beat) * (if (beatTarget > beat) beatAttack else beatRelease)

        // The liquid flows faster with louder / more energetic music.
        val idle = if (playing) 0.55f else 0.2f
        phase += dt * (idle + 2.6f * energy + 2.2f * beat)
        if (abs(phase) > 10_000f) phase = 0f
    }
}

private class LayerSpec(
    val radius: Float, // dp, resting radius
    val offset: Float, // phase offset
    val speed: Float,  // flow speed multiplier (sign = direction)
    val spin: Float,   // gradient rotation / spectrum travel multiplier
    val audio: Float,  // how strongly audio deforms this layer
    val alpha: Float,  // edge opacity
    val fill: Float,   // body opacity
    val width: Float   // edge width in dp
)

private val LAYERS = listOf(
    LayerSpec(35.5f, 4.4f, -1.00f, -0.8f, 0.95f, 0.55f, 0.050f, 1.0f),
    LayerSpec(33.5f, 0.0f, 1.00f, 1.0f, 0.80f, 0.85f, 0.060f, 1.2f),
    LayerSpec(32.0f, 1.9f, -1.25f, 1.4f, 0.65f, 0.75f, 0.055f, 0.9f),
    LayerSpec(31.0f, 3.2f, 0.80f, -1.1f, 0.50f, 0.95f, 0.045f, 0.8f)
)

private const val POINTS = 96
private const val BAR_COUNT = 7
private const val TWO_PI = (2.0 * PI).toFloat()

// Bar heights in dp: resting height + extra from the music. Tall in the middle like the reference.
private val BAR_BASE = floatArrayOf(4f, 7f, 10f, 13f, 10f, 7f, 4f)
private val BAR_GAIN = floatArrayOf(10f, 14f, 15f, 14f, 15f, 14f, 10f)
// Frequency band driving each bar (left to right): treble ... bass in the centre ... treble.
private val BAR_BANDS = intArrayOf(21, 15, 9, 2, 11, 17, 24)

private val Blue = Color(0xFF5C7CFF)
private val Violet = Color(0xFF9C5CFF)
private val Magenta = Color(0xFFDB5CFF)
private val Ice = Color(0xFFF1E6FF)
