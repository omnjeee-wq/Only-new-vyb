package com.example.ui.visualizer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import com.example.player.visualizer.AudioSpectrumHub
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Nav-bar centre orb: dark glass lens + layered translucent "liquid membranes"
 * + 7 equalizer bars, driven by the real playing audio (AudioSpectrumHub).
 *
 * Performance design (important):
 *  - The orb lives in its OWN graphics layer, so the 40-60 fps redraw only
 *    re-records this tiny node, never the Home screen behind it.
 *  - All brushes/shaders are built once (drawWithCache), nothing is allocated per frame.
 *  - Frame rate is capped, and the loop sleeps when paused and calm.
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
                previous = 0L
                delay(150)
                continue
            }
            val now = withFrameNanos { it }
            if (previous != 0L && now - previous < MIN_FRAME_NANOS) continue // frame cap
            val dt = if (previous == 0L) 1f / 45f
            else ((now - previous) / 1_000_000_000f).coerceIn(0.004f, 0.06f)
            previous = now
            orb.step(dt, playing)
            orb.tick = now // invalidates only the orb layer's draw
        }
    }

    Spacer(
        modifier = modifier
            .graphicsLayer { } // own render node: isolates redraws from the rest of the screen
            .drawWithCache {
                val scene = OrbScene(size, density)
                onDrawBehind { drawOrb(orb, scene) }
            }
    )
}

// ---------------------------------------------------------------------------
// Cached, size-dependent resources (built once, reused every frame)
// ---------------------------------------------------------------------------

private class OrbScene(size: Size, val d: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val auraRadius = 48f * d
    val lensRadius = 28.5f * d

    val aura = Brush.radialGradient(
        colors = listOf(Violet.copy(alpha = 0.55f), Blue.copy(alpha = 0.22f), Color.Transparent),
        center = center,
        radius = auraRadius
    )
    val lens = Brush.radialGradient(
        colors = listOf(Color(0xFF2A1148), Color(0xFF150B26), Color(0xFF07050C)),
        center = Offset(center.x - 6f * d, center.y - 9f * d),
        radius = lensRadius * 1.55f
    )
    val rim = Brush.sweepGradient(
        colors = listOf(
            Ice.copy(alpha = 0.85f), Blue.copy(alpha = 0.75f), Violet.copy(alpha = 0.85f),
            Magenta.copy(alpha = 0.75f), Ice.copy(alpha = 0.85f)
        ),
        center = center
    )
    val gloss = Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
        center = Offset(center.x - 9f * d, center.y - 13f * d),
        radius = 19f * d
    )
    val fills = Array(LAYERS.size) { i ->
        val a = LAYERS[i].fill
        Brush.sweepGradient(
            0f to Blue.copy(alpha = a),
            0.35f to Violet.copy(alpha = (a * 1.3f).coerceAtMost(1f)),
            0.7f to Magenta.copy(alpha = a),
            1f to Blue.copy(alpha = a),
            center = center
        )
    }
    val edges = Array(LAYERS.size) { i ->
        val a = LAYERS[i].alpha
        Brush.sweepGradient(
            0f to Ice.copy(alpha = a),
            0.25f to Blue.copy(alpha = a),
            0.55f to Violet.copy(alpha = a),
            0.8f to Magenta.copy(alpha = a),
            1f to Ice.copy(alpha = a),
            center = center
        )
    }
}

// ---------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------

private fun DrawScope.drawOrb(orb: OrbState, s: OrbScene) {
    val stamp = orb.tick // subscribe this draw to frame updates
    if (stamp == Long.MIN_VALUE) return

    val d = s.d
    val center = s.center
    val energy = orb.energy
    val beat = orb.beat
    val bass = orb.bass
    val pulse = 1f + 0.045f * beat + 0.03f * bass

    // 1. aura: flashes on beats
    drawCircle(
        brush = s.aura,
        radius = s.auraRadius,
        center = center,
        alpha = (0.45f + 0.45f * beat + 0.25f * energy).coerceIn(0f, 1f)
    )

    // 2. liquid membranes (2 draw calls each)
    for (layer in LAYERS.indices) {
        val spec = LAYERS[layer]
        buildMembrane(orb, spec, d, center, pulse)
        rotate(degrees = orb.phase * spec.spin * 18f, pivot = center) {
            drawPath(path = orb.path, brush = s.fills[layer])
            drawPath(
                path = orb.path,
                brush = s.edges[layer],
                style = Stroke(width = spec.width * d, cap = StrokeCap.Round)
            )
        }
    }

    // 3. dark glass lens
    val lensRadius = s.lensRadius * (1f + 0.012f * beat)
    drawCircle(brush = s.lens, radius = lensRadius, center = center)
    drawCircle(brush = s.rim, radius = lensRadius, center = center, style = Stroke(width = 1.1f * d))
    drawCircle(brush = s.gloss, radius = lensRadius - 1f * d, center = center)

    // 4. seven equalizer bars (solid colours: no per-frame shaders)
    val barWidth = 2.5f * d
    val gap = 2.3f * d
    val totalWidth = BAR_COUNT * barWidth + (BAR_COUNT - 1) * gap
    val startX = center.x - totalWidth / 2f
    val radius = CornerRadius(barWidth / 2f)
    for (i in 0 until BAR_COUNT) {
        val value = orb.barValue(i)
        val h = (BAR_BASE[i] + value * BAR_GAIN[i]) * d
        val left = startX + i * (barWidth + gap)
        val top = center.y - h / 2f
        drawRoundRect(
            color = Violet.copy(alpha = 0.18f + 0.30f * value),
            topLeft = Offset(left - 0.9f * d, top - 0.9f * d),
            size = Size(barWidth + 1.8f * d, h + 1.8f * d),
            cornerRadius = CornerRadius(barWidth)
        )
        drawRoundRect(
            color = BarColor,
            topLeft = Offset(left, top),
            size = Size(barWidth, h),
            cornerRadius = radius
        )
    }
}

private fun buildMembrane(
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
    val usable = AudioSpectrumHub.BANDS * 0.78f
    val last = AudioSpectrumHub.BANDS - 1

    val wobble = 0.85f + energy * 1.6f + bass * 1.1f
    val audioGain = (3.2f + 3.6f * energy) * spec.audio
    val travel = phase * 0.035f * spec.spin

    // The 3 flow sines only depend on phase: compute their sin/cos once per layer
    // and combine with cached angle tables (cheap, no per-point trig except 3 sins).
    for (i in 0 until POINTS) {
        val angle = orb.angles[i]

        var t = angle / TWO_PI + travel
        t -= floor(t)
        val m = if (t < 0.5f) t * 2f else (1f - t) * 2f
        val pos = m * usable
        val lo = floor(pos).toInt().coerceIn(0, last)
        val hi = (lo + 1).coerceAtMost(last)
        val f = pos - lo
        val smooth = f * f * (3f - 2f * f)
        val local = bands[lo] + (bands[hi] - bands[lo]) * smooth

        val flow =
            sin(angle * 2f + phase * 0.9f) +
                sin(angle * 3f - phase * 1.25f + 1.3f) * 0.80f +
                sin(angle * 5f + phase * 0.65f + 2.1f) * 0.45f
        val breathing = sin(angle * 2f + phase * 0.5f + spec.offset) * bass * 2.4f

        val r = (spec.radius + flow * wobble + breathing + local * audioGain) * d * pulse
        orb.xs[i] = center.x + orb.cosA[i] * r
        orb.ys[i] = center.y + orb.sinA[i] * r
    }

    val path = orb.path
    path.rewind()
    val end = POINTS - 1
    path.moveTo((orb.xs[end] + orb.xs[0]) / 2f, (orb.ys[end] + orb.ys[0]) / 2f)
    for (i in 0 until POINTS) {
        val n = if (i == end) 0 else i + 1
        path.quadraticTo(
            orb.xs[i], orb.ys[i],
            (orb.xs[i] + orb.xs[n]) / 2f, (orb.ys[i] + orb.ys[n]) / 2f
        )
    }
    path.close()
}

// ---------------------------------------------------------------------------
// State (mutated in place; no allocations in the frame loop)
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
    val cosA = FloatArray(POINTS) { cos(angles[it]) }
    val sinA = FloatArray(POINTS) { sin(angles[it]) }

    fun isSettled(): Boolean = energy < 0.004f && bass < 0.004f && beat < 0.004f

    fun barValue(i: Int): Float {
        val index = BAR_BANDS[i]
        val a = bands[index]
        val b = bands[(index + 1).coerceAtMost(AudioSpectrumHub.BANDS - 1)]
        return ((a + b) * 0.5f).coerceIn(0f, 1f)
    }

    fun step(dt: Float, playing: Boolean) {
        val has = playing && AudioSpectrumHub.sample(frame)

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

        val idle = if (playing) 0.55f else 0.2f
        phase += dt * (idle + 2.6f * energy + 2.2f * beat)
        if (abs(phase) > 10_000f) phase = 0f
    }
}

private class LayerSpec(
    val radius: Float, // dp
    val offset: Float,
    val speed: Float,
    val spin: Float,
    val audio: Float,
    val alpha: Float,
    val fill: Float,
    val width: Float   // dp
)

private val LAYERS = listOf(
    LayerSpec(35.5f, 4.4f, -1.00f, -0.8f, 0.95f, 0.55f, 0.050f, 1.0f),
    LayerSpec(33.5f, 0.0f, 1.00f, 1.0f, 0.80f, 0.85f, 0.060f, 1.2f),
    LayerSpec(32.0f, 1.9f, -1.25f, 1.4f, 0.65f, 0.75f, 0.055f, 0.9f),
    LayerSpec(31.0f, 3.2f, 0.80f, -1.1f, 0.50f, 0.95f, 0.045f, 0.8f)
)

private const val POINTS = 64
private const val BAR_COUNT = 7
private const val TWO_PI = (2.0 * PI).toFloat()
private const val MIN_FRAME_NANOS = 15_000_000L // ~ max 60 fps, skips redundant frames on 90/120 Hz screens

private val BAR_BASE = floatArrayOf(4f, 7f, 10f, 13f, 10f, 7f, 4f)
private val BAR_GAIN = floatArrayOf(10f, 14f, 15f, 14f, 15f, 14f, 10f)
private val BAR_BANDS = intArrayOf(21, 15, 9, 2, 11, 17, 24)

private val Blue = Color(0xFF5C7CFF)
private val Violet = Color(0xFF9C5CFF)
private val Magenta = Color(0xFFDB5CFF)
private val Ice = Color(0xFFF1E6FF)
private val BarColor = Color(0xFFF4EBFF)
