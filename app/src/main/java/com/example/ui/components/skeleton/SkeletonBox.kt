package com.example.ui.components.skeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A reusable rounded skeleton placeholder component.
 *
 * @param modifier Additional modifier applied to the box.
 * @param width Optional fixed width. If null, width is dictated by the modifier.
 * @param height Optional fixed height. If null, height is dictated by the modifier.
 * @param shape Corner radius or clipping shape. Defaults to RoundedCornerShape(8.dp).
 * @param shimmerBrush The animated shimmer brush. Defaults to rememberSkeletonShimmerBrush().
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    shimmerBrush: Brush = rememberSkeletonShimmerBrush()
) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(shape)
            .background(shimmerBrush)
    )
}

/**
 * Modifier extension to apply skeleton shimmer background to any composable.
 */
fun Modifier.skeletonShimmer(
    shimmerBrush: Brush,
    shape: Shape = RoundedCornerShape(8.dp)
): Modifier = this
    .clip(shape)
    .background(shimmerBrush)
