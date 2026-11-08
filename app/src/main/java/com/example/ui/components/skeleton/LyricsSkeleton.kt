package com.example.ui.components.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Skeleton placeholder for lyrics display that simulates authentic song lyrics
 * verses and stanzas with varying natural line widths and subtle shimmer.
 */
@Composable
fun LyricsSkeleton(
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberSkeletonShimmerBrush()

    // Realistic verse stanza line-width percentages
    val stanzas = listOf(
        listOf(0.72f, 0.88f, 0.64f, 0.82f),
        listOf(0.85f, 0.68f, 0.92f, 0.58f),
        listOf(0.78f, 0.84f, 0.70f, 0.89f),
        listOf(0.65f, 0.80f, 0.55f, 0.75f)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .testTag("lyrics_skeleton"),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        stanzas.forEachIndexed { stanzaIndex, stanzaLines ->
            stanzaLines.forEach { lineFraction ->
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth(lineFraction),
                    height = 18.dp,
                    shape = RoundedCornerShape(4.dp),
                    shimmerBrush = shimmerBrush
                )
            }

            // Stanza separator
            if (stanzaIndex < stanzas.size - 1) {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}
