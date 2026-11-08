package com.example.ui.components.skeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Skeleton loading screen for HomeScreen that mirrors the exact cinematic
 * structure of the Hero Banner, Recommend For You carousel, and categorized
 * music collections.
 */
@Composable
fun HomeSkeleton(
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberSkeletonShimmerBrush()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("home_skeleton")
    ) {
        // Atmospheric subtle background gradient matching HomeScreen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(
                    Brush.verticalGradient(
                        0.0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        0.5f to MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        1.0f to Color.Transparent
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            userScrollEnabled = false
        ) {
            // 1. Hero Banner Skeleton Placeholder
            item(key = "hero_banner_skeleton") {
                HeroBannerSkeleton(shimmerBrush = shimmerBrush)
            }

            // 2. Recommend For You Section Skeleton
            item(key = "recommend_for_you_skeleton") {
                RecommendForYouSkeleton(shimmerBrush = shimmerBrush)
            }

            // 3. Two Categorized Horizontal Sections
            item(key = "horizontal_section_skeleton_1") {
                HorizontalSectionSkeleton(
                    titleWidth = 160.dp,
                    shimmerBrush = shimmerBrush
                )
            }

            item(key = "horizontal_section_skeleton_2") {
                HorizontalSectionSkeleton(
                    titleWidth = 140.dp,
                    shimmerBrush = shimmerBrush
                )
            }
        }
    }
}

@Composable
private fun HeroBannerSkeleton(
    shimmerBrush: Brush
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        val availableWidth = maxWidth
        val discSize = (availableWidth * 0.44f).coerceIn(135.dp, 185.dp)

        // Background container with dark gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    RoundedCornerShape(24.dp)
                )
                .padding(16.dp)
        ) {
            // Vinyl disc placeholder on the right
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(discSize)
                    .clip(CircleShape)
                    .background(shimmerBrush),
                contentAlignment = Alignment.Center
            ) {
                // Inner center hole
                Box(
                    modifier = Modifier
                        .size(discSize * 0.35f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                )
            }

            // Left side content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = discSize * 0.75f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top bar in hero: Brand + Share icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SkeletonBox(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            shimmerBrush = shimmerBrush
                        )
                        SkeletonBox(
                            width = 65.dp,
                            height = 20.dp,
                            shape = RoundedCornerShape(4.dp),
                            shimmerBrush = shimmerBrush
                        )
                    }

                    SkeletonBox(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        shimmerBrush = shimmerBrush
                    )
                }

                // Middle: Script title + Song title + Artist
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(
                        width = 80.dp,
                        height = 18.dp,
                        shape = RoundedCornerShape(4.dp),
                        shimmerBrush = shimmerBrush
                    )
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        height = 28.dp,
                        shape = RoundedCornerShape(6.dp),
                        shimmerBrush = shimmerBrush
                    )
                    SkeletonBox(
                        width = 110.dp,
                        height = 14.dp,
                        shape = RoundedCornerShape(4.dp),
                        shimmerBrush = shimmerBrush
                    )
                }

                // Play button placeholder
                SkeletonBox(
                    width = 110.dp,
                    height = 38.dp,
                    shape = RoundedCornerShape(20.dp),
                    shimmerBrush = shimmerBrush
                )
            }
        }
    }
}

@Composable
private fun RecommendForYouSkeleton(
    shimmerBrush: Brush
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Section Header Placeholder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                )
                SkeletonBox(
                    width = 160.dp,
                    height = 18.dp,
                    shape = RoundedCornerShape(4.dp),
                    shimmerBrush = shimmerBrush
                )
            }

            SkeletonBox(
                width = 55.dp,
                height = 14.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
        }

        // Horizontal Row of 3-song Columns
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(top = 10.dp),
            userScrollEnabled = false
        ) {
            items(2) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.width(280.dp)
                ) {
                    repeat(3) {
                        RecommendSongItemSkeleton(shimmerBrush = shimmerBrush)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendSongItemSkeleton(
    shimmerBrush: Brush
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail placeholder
        SkeletonBox(
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(8.dp),
            shimmerBrush = shimmerBrush
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Titles
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(0.85f),
                height = 14.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(0.55f),
                height = 11.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
        }

        // Options icon placeholder
        SkeletonBox(
            modifier = Modifier.size(20.dp),
            shape = CircleShape,
            shimmerBrush = shimmerBrush
        )
    }
}

@Composable
private fun HorizontalSectionSkeleton(
    titleWidth: androidx.compose.ui.unit.Dp,
    shimmerBrush: Brush
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val cardSize = remember(screenWidth) { (screenWidth * 0.44f).coerceIn(150f, 220f).dp }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    ) {
        // Section Header Placeholder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBox(
                width = titleWidth,
                height = 20.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )

            SkeletonBox(
                width = 50.dp,
                height = 14.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
        }

        // Horizontal items row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 12.dp),
            userScrollEnabled = false
        ) {
            items(3) {
                Column(modifier = Modifier.width(cardSize)) {
                    // Square card artwork
                    SkeletonBox(
                        modifier = Modifier.size(cardSize),
                        shape = RoundedCornerShape(16.dp),
                        shimmerBrush = shimmerBrush
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Title bar
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.8f),
                        height = 14.dp,
                        shape = RoundedCornerShape(4.dp),
                        shimmerBrush = shimmerBrush
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtitle bar
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth(0.55f),
                        height = 11.dp,
                        shape = RoundedCornerShape(4.dp),
                        shimmerBrush = shimmerBrush
                    )
                }
            }
        }
    }
}
