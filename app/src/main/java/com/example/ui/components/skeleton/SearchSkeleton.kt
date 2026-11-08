package com.example.ui.components.skeleton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.search.SearchCategory

/**
 * Skeleton placeholder for SearchScreen that dynamically adapts to the selected category:
 * - ALL: Top Results, Songs, Playlists, Albums, Artists
 * - SONGS: Play all header + list of song rows
 * - PLAYLISTS: Grid of playlist cards
 * - ALBUMS: Grid of album cards
 * - ARTISTS: Grid of round artist avatars
 */
@Composable
fun SearchSkeleton(
    modifier: Modifier = Modifier,
    category: SearchCategory = SearchCategory.ALL
) {
    val shimmerBrush = rememberSkeletonShimmerBrush()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_skeleton"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
        userScrollEnabled = false
    ) {
        when (category) {
            SearchCategory.ALL -> {
                // Section 1: Songs Header + 4 songs
                item(key = "skeleton_all_songs_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkeletonBox(
                            width = 120.dp,
                            height = 14.dp,
                            shape = RoundedCornerShape(4.dp),
                            shimmerBrush = shimmerBrush
                        )
                        SkeletonBox(
                            width = 80.dp,
                            height = 32.dp,
                            shape = RoundedCornerShape(16.dp),
                            shimmerBrush = shimmerBrush
                        )
                    }
                }

                items(4) {
                    SearchSongRowSkeleton(shimmerBrush = shimmerBrush)
                }

                // Section 2: Albums Horizontal Skeleton
                item(key = "skeleton_all_albums_header") {
                    SkeletonBox(
                        width = 110.dp,
                        height = 14.dp,
                        shape = RoundedCornerShape(4.dp),
                        shimmerBrush = shimmerBrush,
                        modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
                    )
                }

                item(key = "skeleton_all_albums_row") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(3) {
                            Column(modifier = Modifier.width(115.dp)) {
                                SkeletonBox(
                                    modifier = Modifier.size(115.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    shimmerBrush = shimmerBrush
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                SkeletonBox(
                                    modifier = Modifier.fillMaxWidth(0.85f),
                                    height = 12.dp,
                                    shape = RoundedCornerShape(4.dp),
                                    shimmerBrush = shimmerBrush
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                SkeletonBox(
                                    modifier = Modifier.fillMaxWidth(0.55f),
                                    height = 10.dp,
                                    shape = RoundedCornerShape(4.dp),
                                    shimmerBrush = shimmerBrush
                                )
                            }
                        }
                    }
                }

                // Section 3: Playlists Horizontal Skeleton
                item(key = "skeleton_all_playlists_header") {
                    SkeletonBox(
                        width = 120.dp,
                        height = 14.dp,
                        shape = RoundedCornerShape(4.dp),
                        shimmerBrush = shimmerBrush,
                        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
                    )
                }

                item(key = "skeleton_all_playlists_row") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        repeat(3) {
                            Column(modifier = Modifier.width(115.dp)) {
                                SkeletonBox(
                                    modifier = Modifier.size(115.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    shimmerBrush = shimmerBrush
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                SkeletonBox(
                                    modifier = Modifier.fillMaxWidth(0.85f),
                                    height = 12.dp,
                                    shape = RoundedCornerShape(4.dp),
                                    shimmerBrush = shimmerBrush
                                )
                            }
                        }
                    }
                }

                // Section 4: Artists Row Skeleton
                item(key = "skeleton_all_artists_header") {
                    SkeletonBox(
                        width = 100.dp,
                        height = 14.dp,
                        shape = RoundedCornerShape(4.dp),
                        shimmerBrush = shimmerBrush,
                        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
                    )
                }

                item(key = "skeleton_all_artists_row") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        repeat(3) {
                            Column(
                                modifier = Modifier.width(90.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                SkeletonBox(
                                    modifier = Modifier.size(80.dp),
                                    shape = CircleShape,
                                    shimmerBrush = shimmerBrush
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                SkeletonBox(
                                    modifier = Modifier.fillMaxWidth(0.8f),
                                    height = 12.dp,
                                    shape = RoundedCornerShape(4.dp),
                                    shimmerBrush = shimmerBrush
                                )
                            }
                        }
                    }
                }
            }

            SearchCategory.SONGS -> {
                item(key = "skeleton_songs_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkeletonBox(
                            width = 130.dp,
                            height = 14.dp,
                            shape = RoundedCornerShape(4.dp),
                            shimmerBrush = shimmerBrush
                        )
                        SkeletonBox(
                            width = 90.dp,
                            height = 36.dp,
                            shape = RoundedCornerShape(20.dp),
                            shimmerBrush = shimmerBrush
                        )
                    }
                }
                items(8) {
                    SearchSongRowSkeleton(shimmerBrush = shimmerBrush)
                }
            }

            SearchCategory.ALBUMS -> {
                items(5) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkeletonBox(
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(12.dp),
                            shimmerBrush = shimmerBrush
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth(0.7f),
                                height = 15.dp,
                                shape = RoundedCornerShape(4.dp),
                                shimmerBrush = shimmerBrush
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth(0.4f),
                                height = 12.dp,
                                shape = RoundedCornerShape(4.dp),
                                shimmerBrush = shimmerBrush
                            )
                        }
                    }
                }
            }

            SearchCategory.PLAYLISTS -> {
                items(5) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkeletonBox(
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(12.dp),
                            shimmerBrush = shimmerBrush
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth(0.7f),
                                height = 15.dp,
                                shape = RoundedCornerShape(4.dp),
                                shimmerBrush = shimmerBrush
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth(0.35f),
                                height = 12.dp,
                                shape = RoundedCornerShape(4.dp),
                                shimmerBrush = shimmerBrush
                            )
                        }
                    }
                }
            }

            SearchCategory.ARTISTS -> {
                items(5) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkeletonBox(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            shimmerBrush = shimmerBrush
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth(0.6f),
                                height = 15.dp,
                                shape = RoundedCornerShape(4.dp),
                                shimmerBrush = shimmerBrush
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth(0.3f),
                                height = 12.dp,
                                shape = RoundedCornerShape(4.dp),
                                shimmerBrush = shimmerBrush
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSongRowSkeleton(
    shimmerBrush: androidx.compose.ui.graphics.Brush
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        SkeletonBox(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            shimmerBrush = shimmerBrush
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artists
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(0.75f),
                height = 14.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(0.5f),
                height = 11.dp,
                shape = RoundedCornerShape(4.dp),
                shimmerBrush = shimmerBrush
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Duration Placeholder
        SkeletonBox(
            width = 32.dp,
            height = 10.dp,
            shape = RoundedCornerShape(4.dp),
            shimmerBrush = shimmerBrush
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Favorite Button Placeholder
        SkeletonBox(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            shimmerBrush = shimmerBrush
        )
    }
}
