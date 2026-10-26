package com.example.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Special Glow System for VYBE App.
 * Provides GPU-accelerated neon halos, ambient backdrops, pulsing accents,
 * and card/button radiance to deliver a futuristic, high-fidelity music experience.
 */

// Core Neon Glow Colors
val GlowOrange = Color(0xFFFF6436)
val GlowPink = Color(0xFFFF2A85)
val GlowCyan = Color(0xFF00F0FF)
val GlowPurple = Color(0xFFA855F7)
val GlowAmber = Color(0xFFFFB800)
val GlowEmerald = Color(0xFF10B981)

/**
 * Draws a soft, radiant blurred glow behind any composable element.
 */
fun Modifier.neonGlow(
    color: Color = GlowOrange,
    radius: Dp = 16.dp,
    alpha: Float = 0.55f,
    cornerRadius: Dp = 0.dp
): Modifier = this.drawBehind {
    val glowColor = color.copy(alpha = alpha)
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.color = glowColor
            this.style = PaintingStyle.Fill
            this.asFrameworkPaint().apply {
                isAntiAlias = true
                maskFilter = BlurMaskFilter(radius.toPx().let { if (it.isFinite() && it > 0f) it.coerceAtMost(80f) else 1f }, BlurMaskFilter.Blur.NORMAL)
            }
        }
        if (cornerRadius <= 0.dp) {
            canvas.drawOval(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                paint = paint
            )
        } else {
            val rx = cornerRadius.toPx()
            canvas.drawRoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                radiusX = rx,
                radiusY = rx,
                paint = paint
            )
        }
    }
}

/**
 * Animated pulsing glow modifier for active elements (play button, now-playing indicator, center button).
 */
@Composable
fun Modifier.pulsingGlow(
    color: Color = GlowOrange,
    minRadius: Dp = 10.dp,
    maxRadius: Dp = 22.dp,
    minAlpha: Float = 0.25f,
    maxAlpha: Float = 0.70f,
    durationMs: Int = 1600,
    cornerRadius: Dp = 0.dp
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_glow")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_progress"
    )

    val currentRadius = minRadius + (maxRadius - minRadius) * pulseProgress
    val currentAlpha = minAlpha + (maxAlpha - minAlpha) * pulseProgress

    return this.then(
        Modifier.neonGlow(
            color = color,
            radius = currentRadius,
            alpha = currentAlpha,
            cornerRadius = cornerRadius
        )
    )
}

/**
 * Ambient backdrop diffused glow: creates large colored atmospheric aura behind banners or artwork.
 */
@Composable
fun AmbientBackdropGlow(
    color: Color,
    modifier: Modifier = Modifier,
    alpha: Float = 0.35f,
    diameter: Dp = 260.dp
) {
    Box(
        modifier = modifier
            .size(diameter)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.4f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
}

/**
 * Multi-layer neon border glow for cards and navigation bars.
 */
fun Modifier.glowingBorder(
    color: Color,
    strokeWidth: Dp = 1.dp,
    glowRadius: Dp = 8.dp,
    cornerRadius: Dp = 24.dp,
    glowAlpha: Float = 0.4f
): Modifier = this.drawBehind {
    val rx = cornerRadius.toPx()
    val strokePx = strokeWidth.toPx()
    drawIntoCanvas { canvas ->
        val glowPaint = Paint().apply {
            this.color = color.copy(alpha = glowAlpha)
            this.style = PaintingStyle.Stroke
            this.strokeWidth = strokePx * 2.5f
            this.asFrameworkPaint().apply {
                isAntiAlias = true
                maskFilter = BlurMaskFilter(glowRadius.toPx().let { if (it.isFinite() && it > 0f) it.coerceAtMost(80f) else 1f }, BlurMaskFilter.Blur.NORMAL)
            }
        }
        canvas.drawRoundRect(
            left = strokePx,
            top = strokePx,
            right = size.width - strokePx,
            bottom = size.height - strokePx,
            radiusX = rx,
            radiusY = rx,
            paint = glowPaint
        )
    }
}
