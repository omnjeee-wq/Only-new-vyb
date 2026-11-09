package com.example.ui.components.hero

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import android.graphics.Color as AndroidColor

/**
 * Artwork-adaptive cinematic background.
 *
 * Give it ANY square album artwork URL and it paints a widescreen, premium-looking backdrop:
 *   1. full-bleed artwork (ContentScale.Crop, never stretched)
 *   2. heavy blur (pre-blurred once on a tiny bitmap -> works on every Android version, zero GPU cost per frame)
 *   3. adaptive colours (AndroidX Palette: dominant / vibrant / muted / dark-muted)
 *   4. soft radial ambient glow in those colours
 *   5. cinematic dark overlays (left, top, bottom, edge vignette)
 *   6. very slow ambient drift (can be switched off)
 *
 * It draws ONLY the background. No text, buttons or other UI.
 * The artwork is decoded + blurred + palette-extracted exactly once per URL and cached.
 */
@Composable
fun CinematicBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    seed: String? = artworkUrl,
    baseColor: Color = MaterialTheme.colorScheme.background,
    animated: Boolean = true
) {
    val data by rememberBackdropData(artworkUrl)
    val fallbackPalette = remember(seed) { BackdropPalette.fallback(seed) }
    val target = data?.palette ?: fallbackPalette

    val vibrant by animateColorAsState(target.vibrant, tween(700), label = "backdrop_vibrant")
    val dominant by animateColorAsState(target.dominant, tween(700), label = "backdrop_dominant")
    val muted by animateColorAsState(target.muted, tween(700), label = "backdrop_muted")
    val darkMuted by animateColorAsState(target.darkMuted, tween(700), label = "backdrop_dark")

    Box(
        modifier = modifier
            .clipToBounds()
            // Offscreen so the Screen-blended glows never leak into whatever is behind the banner.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        // Layers 1 + 2 (+ 6): blurred artwork, crossfading when the song changes.
        Crossfade(
            targetState = data,
            animationSpec = tween(600),
            label = "backdrop_artwork"
        ) { current ->
            if (current != null) {
                BlurredArtworkLayer(bitmap = current.blurred, animated = animated)
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                Brush.linearGradient(
                                    listOf(
                                        lerp(darkMuted, vibrant, 0.18f),
                                        darkMuted,
                                        Color.Black
                                    )
                                )
                            )
                        }
                )
            }
        }

        // Layers 4 + 5: ambient glow and cinematic overlays, all drawn in the draw phase only
        // (palette animation never triggers recomposition).
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val reach = max(w, h)
                    val shade = lerp(darkMuted, Color.Black, 0.55f)

                    // ---- Ambient glow (Screen blend = light, not paint) ----
                    drawRect(
                        brush = Brush.radialGradient(
                            0f to vibrant.copy(alpha = 0.50f),
                            0.55f to vibrant.copy(alpha = 0.16f),
                            1f to Color.Transparent,
                            center = Offset(w * 0.72f, h * 0.40f),
                            radius = reach * 0.80f
                        ),
                        blendMode = BlendMode.Screen
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            0f to dominant.copy(alpha = 0.34f),
                            1f to Color.Transparent,
                            center = Offset(w * 0.98f, h * 0.02f),
                            radius = reach * 0.65f
                        ),
                        blendMode = BlendMode.Screen
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            0f to muted.copy(alpha = 0.22f),
                            1f to Color.Transparent,
                            center = Offset(w * 0.12f, h * 0.88f),
                            radius = reach * 0.75f
                        ),
                        blendMode = BlendMode.Screen
                    )

                    // ---- Overall cinematic tint (keeps artwork colours alive but moody) ----
                    drawRect(darkMuted.copy(alpha = 0.34f))
                    drawRect(Color.Black.copy(alpha = 0.16f))

                    // ---- Left readability gradient (foreground text lives here) ----
                    drawRect(
                        Brush.horizontalGradient(
                            0f to shade.copy(alpha = 0.92f),
                            0.40f to shade.copy(alpha = 0.66f),
                            0.75f to shade.copy(alpha = 0.14f),
                            1f to Color.Transparent
                        )
                    )

                    // ---- Top shade (status bar / logo area) ----
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.50f),
                            0.28f to Color.Transparent
                        )
                    )

                    // ---- Bottom gradient, melting into the screen background ----
                    drawRect(
                        Brush.verticalGradient(
                            0.38f to Color.Transparent,
                            0.70f to shade.copy(alpha = 0.78f),
                            0.92f to baseColor.copy(alpha = 0.96f),
                            1f to baseColor
                        )
                    )

                    // ---- Subtle vignette ----
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.Transparent,
                            0.62f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                            center = Offset(w / 2f, h / 2f),
                            radius = hypot(w, h) / 2f
                        )
                    )
                }
        )
    }
}

@Composable
private fun BlurredArtworkLayer(bitmap: ImageBitmap, animated: Boolean) {
    val drift by if (animated) {
        rememberInfiniteTransition(label = "backdrop_drift").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(24_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "backdrop_drift_value"
        )
    } else {
        remember { mutableStateOf(0.5f) }
    }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High,
        colorFilter = SaturationBoost,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Over-scale so the drift never reveals an empty edge.
                scaleX = 1.40f
                scaleY = 1.40f
                translationX = (drift - 0.5f) * size.width * 0.05f
                translationY = (0.5f - drift) * size.height * 0.03f
            }
    )
}

private val SaturationBoost: ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.30f) })

// ---------------------------------------------------------------------------------------------
// Data + cache
// ---------------------------------------------------------------------------------------------

@Immutable
data class BackdropPalette(
    val dominant: Color,
    val vibrant: Color,
    val muted: Color,
    val darkMuted: Color
) {
    companion object {
        /** Deterministic colours for songs without artwork / while artwork is still loading. */
        fun fallback(seed: String?): BackdropPalette {
            val hue = (abs((seed ?: "vybe").hashCode()) % 360).toFloat()
            fun hsv(sat: Float, value: Float) =
                Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))
            return BackdropPalette(
                dominant = hsv(0.65f, 0.70f),
                vibrant = hsv(0.75f, 0.92f),
                muted = hsv(0.35f, 0.55f),
                darkMuted = hsv(0.60f, 0.14f)
            )
        }
    }
}

/** Reference-equality holder (intentionally not a data class: bitmaps are never compared). */
@Immutable
class BackdropData(val blurred: ImageBitmap, val palette: BackdropPalette)

private object BackdropCache {
    // Each entry is ~16 KB (64x64 bitmap) so 32 entries is tiny.
    private val lru = LruCache<String, BackdropData>(32)
    fun get(url: String): BackdropData? = lru.get(url)
    fun put(url: String, data: BackdropData) { lru.put(url, data) }
}

private const val SOURCE_PX = 128   // what Coil decodes for palette + blur
private const val BLUR_PX = 64      // size of the pre-blurred bitmap

@Composable
fun rememberBackdropData(artworkUrl: String?): State<BackdropData?> {
    val appContext = LocalContext.current.applicationContext
    val state = remember {
        mutableStateOf(artworkUrl?.takeIf { it.isNotBlank() }?.let { BackdropCache.get(it) })
    }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) {
            state.value = null
            return@LaunchedEffect
        }
        val cached = BackdropCache.get(artworkUrl)
        if (cached != null) {
            state.value = cached
        } else {
            // Keep showing the previous song's backdrop until the new one is ready (smooth crossfade).
            loadBackdropData(appContext, artworkUrl)?.let { state.value = it }
        }
    }
    return state
}

/**
 * One Coil request (shares Coil's memory + disk cache with the sharp artwork, so the image is never
 * downloaded twice) -> palette + pre-blurred bitmap, all off the main thread.
 */
suspend fun loadBackdropData(context: Context, url: String): BackdropData? {
    BackdropCache.get(url)?.let { return it }
    return withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(SOURCE_PX, SOURCE_PX)
                .allowHardware(false) // Palette / pixel access needs a software bitmap
                .build()
            val result = Coil.imageLoader(context).execute(request) as? SuccessResult
            val source = (result?.drawable as? BitmapDrawable)?.bitmap
            if (source == null) {
                null
            } else {
                val built = withContext(Dispatchers.Default) {
                    BackdropData(
                        blurred = buildBlurred(source).asImageBitmap(),
                        palette = buildPalette(source)
                    )
                }
                BackdropCache.put(url, built)
                built
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null // network error / OOM / bad image -> caller keeps the fallback gradient
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Palette extraction
// ---------------------------------------------------------------------------------------------

private fun buildPalette(source: Bitmap): BackdropPalette {
    val palette = Palette.from(source).maximumColorCount(20).generate()

    val dominantRgb = palette.dominantSwatch?.rgb
    val vibrantRgb = palette.vibrantSwatch?.rgb
        ?: palette.lightVibrantSwatch?.rgb
        ?: palette.darkVibrantSwatch?.rgb
        ?: dominantRgb
    val mutedRgb = palette.mutedSwatch?.rgb ?: palette.lightMutedSwatch?.rgb ?: dominantRgb
    val darkMutedRgb = palette.darkMutedSwatch?.rgb ?: palette.darkVibrantSwatch?.rgb ?: dominantRgb

    // Nothing usable (e.g. completely flat image) -> deterministic fallback.
    if (dominantRgb == null || vibrantRgb == null || mutedRgb == null || darkMutedRgb == null) {
        return BackdropPalette.fallback(source.hashCode().toString())
    }

    return BackdropPalette(
        dominant = glowTone(dominantRgb),
        vibrant = glowTone(vibrantRgb),
        muted = mutedTone(mutedRgb),
        darkMuted = shadowTone(darkMutedRgb)
    )
}

/** Keeps glows rich but never neon or muddy. Near-greys stay grey so B/W covers don't turn colourful. */
private fun glowTone(argb: Int): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(argb, hsv)
    if (hsv[1] > 0.12f) hsv[1] = hsv[1].coerceIn(0.40f, 0.88f)
    hsv[2] = hsv[2].coerceIn(0.55f, 0.95f)
    return Color(AndroidColor.HSVToColor(hsv))
}

private fun mutedTone(argb: Int): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(argb, hsv)
    hsv[1] = hsv[1].coerceIn(0.0f, 0.60f)
    hsv[2] = hsv[2].coerceIn(0.35f, 0.75f)
    return Color(AndroidColor.HSVToColor(hsv))
}

private fun shadowTone(argb: Int): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(argb, hsv)
    hsv[1] = hsv[1].coerceAtMost(0.80f)
    hsv[2] = hsv[2].coerceIn(0.08f, 0.20f)
    return Color(AndroidColor.HSVToColor(hsv))
}

// ---------------------------------------------------------------------------------------------
// Blur (runs once per artwork, on a 64x64 bitmap -> a few hundred microseconds)
// ---------------------------------------------------------------------------------------------

private fun buildBlurred(source: Bitmap): Bitmap {
    val small = Bitmap.createScaledBitmap(source, BLUR_PX, BLUR_PX, true)
    val w = small.width
    val h = small.height
    val a = IntArray(w * h)
    small.getPixels(a, 0, w, 0, 0, w, h)
    if (small !== source) small.recycle()

    val b = IntArray(w * h)
    repeat(3) {
        boxBlurPass(a, b, w, h, radius = 3, horizontal = true)
        boxBlurPass(b, a, w, h, radius = 3, horizontal = false)
    }
    return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
}

private fun boxBlurPass(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    val count = radius * 2 + 1
    for (y in 0 until h) {
        for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            for (k in -radius..radius) {
                val xx = if (horizontal) (x + k).coerceIn(0, w - 1) else x
                val yy = if (horizontal) y else (y + k).coerceIn(0, h - 1)
                val p = src[yy * w + xx]
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            dst[y * w + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
        }
    }
}
