package com.example.ui.components.skeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Skeleton row mimicking a track in CollectionDetailSheet (Album/Chart/Playlist).
 */
@Composable
fun CollectionTrackRowSkeleton(
    modifier: Modifier = Modifier,
    shimmerBrush: Brush = rememberSkeletonShimmerBrush()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number placeholder
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center
        ) {
            SkeletonBox(
                width = 16.dp,
                height = 14.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Title and Artist placeholders
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(0.68f),
                height = 14.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(0.42f),
                height = 11.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Duration placeholder
        SkeletonBox(
            width = 32.dp,
            height = 12.dp,
            shape = RoundedCornerShape(4.dp),
            shimmerBrush = shimmerBrush
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Favorite icon placeholder
        SkeletonBox(
            modifier = Modifier.size(20.dp),
            shape = CircleShape,
            shimmerBrush = shimmerBrush
        )
    }
}

/**
 * Skeleton row mimicking a song in UserPlaylistDetailContent.
 */
@Composable
fun UserPlaylistTrackRowSkeleton(
    modifier: Modifier = Modifier,
    shimmerBrush: Brush = rememberSkeletonShimmerBrush()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index placeholder
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            SkeletonBox(
                width = 14.dp,
                height = 13.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
        }

        // Artwork placeholder
        SkeletonBox(
            modifier = Modifier.size(46.dp),
            shape = RoundedCornerShape(8.dp),
            shimmerBrush = shimmerBrush
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Artist placeholders
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(0.65f),
                height = 14.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
            Spacer(modifier = Modifier.height(5.dp))
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(0.40f),
                height = 11.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
        }

        // Duration placeholder
        SkeletonBox(
            width = 30.dp,
            height = 11.dp,
            shape = RoundedCornerShape(4.dp),
            shimmerBrush = shimmerBrush
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Remove icon placeholder
        SkeletonBox(
            modifier = Modifier.size(20.dp),
            shape = CircleShape,
            shimmerBrush = shimmerBrush
        )
    }
}

/**
 * Skeleton row mimicking a song candidate in AddSongsToPlaylistSheet.
 */
@Composable
fun AddSongRowSkeleton(
    modifier: Modifier = Modifier,
    shimmerBrush: Brush = rememberSkeletonShimmerBrush()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork placeholder
        SkeletonBox(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            shimmerBrush = shimmerBrush
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Artist placeholders
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(0.70f),
                height = 14.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
            Spacer(modifier = Modifier.height(5.dp))
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(0.45f),
                height = 11.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Add action button placeholder
        SkeletonBox(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(18.dp),
            shimmerBrush = shimmerBrush
        )
    }
}

/**
 * Displays a vertical column of skeleton track rows.
 */
@Composable
fun PlaylistTracklistSkeleton(
    count: Int = 6,
    modifier: Modifier = Modifier,
    shimmerBrush: Brush = rememberSkeletonShimmerBrush()
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(count) {
            CollectionTrackRowSkeleton(shimmerBrush = shimmerBrush)
        }
    }
}
