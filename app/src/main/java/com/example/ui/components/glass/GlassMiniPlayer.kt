package com.example.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Premium Apple-inspired frosted glass surface for the floating mini-player.
 */
@Composable
fun GlassMiniPlayerContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)

    // Specular border
    val borderBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.24f),
        0.5f to Color.White.copy(alpha = 0.08f),
        1.0f to Color.White.copy(alpha = 0.02f)
    )

    // Inner subtle light wash
    val internalSheen = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.07f),
        0.5f to Color.Transparent,
        1.0f to Color.Black.copy(alpha = 0.10f)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .shadow(
                elevation = 14.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.35f),
                ambientColor = Color.Black.copy(alpha = 0.20f)
            )
            .clip(shape)
            .background(surfaceColor)
            .background(internalSheen)
            .border(1.dp, borderBrush, shape)
    ) {
        content()
    }
}
