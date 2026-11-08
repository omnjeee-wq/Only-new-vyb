package com.example.ui.components.skeleton

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Creates a synchronized, smooth animated shimmer brush for skeleton loading UI.
 * Coordinates are mapped to screen dimensions so all skeleton elements shimmer in
 * harmony across the screen from left to right, similar to Spotify and YouTube.
 */
@Composable
fun rememberSkeletonShimmerBrush(
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    highlightColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
    durationMillis: Int = 1350
): Brush {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer_transition")
    val progress by transition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton_shimmer_progress"
    )

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val shimmerBandWidth = screenWidthPx * 0.65f

    val startX = progress * (screenWidthPx + shimmerBandWidth) - shimmerBandWidth
    val endX = startX + shimmerBandWidth

    return Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor
        ),
        start = Offset(startX, 0f),
        end = Offset(endX, screenWidthPx * 0.35f)
    )
}
