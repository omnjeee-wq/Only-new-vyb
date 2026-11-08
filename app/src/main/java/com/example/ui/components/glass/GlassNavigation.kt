package com.example.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Premium Apple-inspired floating glass navigation bar container.
 * Features translucent frosted glass with soft ambient shadows and delicate top specular lighting.
 */
@Composable
fun GlassNavigationContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val cornerRadius = 33.dp
    val shape = RoundedCornerShape(cornerRadius)

    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)

    // Specular border: crisp bright sheen on top, gently fading towards bottom
    val borderBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.28f),
        0.4f to Color.White.copy(alpha = 0.10f),
        1.0f to Color.White.copy(alpha = 0.04f)
    )

    // Inner subtle downward light wash
    val internalSheen = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.08f),
        0.5f to Color.Transparent,
        1.0f to Color.Black.copy(alpha = 0.14f)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .shadow(
                elevation = 16.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.40f),
                ambientColor = Color.Black.copy(alpha = 0.25f)
            )
            .clip(shape)
            .background(surfaceColor)
            .background(internalSheen)
            .border(1.dp, borderBrush, shape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
