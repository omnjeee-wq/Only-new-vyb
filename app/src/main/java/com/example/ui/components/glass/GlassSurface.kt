package com.example.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable frosted glass surface providing Apple-inspired translucency,
 * fine specular border lighting, and subtle ambient depth.
 * Fully compatible with Android SDK 24+ without costly continuous runtime blur passes.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 8.dp,
    borderWidth: Dp = 1.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Specular highlight gradient on top edge to emulate real refraction
    val borderBrush = Brush.verticalGradient(
        0.0f to (if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.65f)),
        0.4f to (if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.35f)),
        1.0f to (if (isDark) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.15f))
    )

    // Subtle internal sheen gradient
    val sheenBrush = Brush.verticalGradient(
        0.0f to (if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.40f)),
        0.5f to Color.Transparent,
        1.0f to (if (isDark) Color.Black.copy(alpha = 0.12f) else Color.Transparent)
    )

    Box(
        modifier = modifier
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = shape,
                        spotColor = if (isDark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.08f),
                        ambientColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.05f)
                    )
                } else Modifier
            )
            .clip(shape)
            .background(backgroundColor)
            .background(sheenBrush)
            .border(borderWidth, borderBrush, shape)
    ) {
        content()
    }
}

/**
 * Modifier extension to apply glassmorphism styling directly to any composable container.
 */
fun Modifier.glassEffect(
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color? = null,
    borderAlpha: Float = 0.20f
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = 0.06f),
            0.5f to Color.Transparent,
            1.0f to Color.Black.copy(alpha = 0.10f)
        )
    )
    .border(
        1.dp,
        Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = borderAlpha),
            0.5f to Color.White.copy(alpha = borderAlpha * 0.4f),
            1.0f to Color.White.copy(alpha = borderAlpha * 0.15f)
        ),
        shape
    )

private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
