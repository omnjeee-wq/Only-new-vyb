package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.navigationBarsPadding
import com.example.player.PlayerState
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.VybeBorder
import com.example.ui.theme.VybeCoral
import com.example.ui.theme.VybeGlass
import com.example.ui.theme.VybeOrange
import com.example.ui.theme.VybeSurfaceVariant
import com.example.ui.theme.VybeTextMuted
import com.example.ui.theme.VybeTextPrimary
import com.example.ui.theme.VybeTextSecondary
import com.example.ui.theme.glowingBorder

enum class NavDestination {
    HOME,
    SEARCH,
    LIBRARY,
    SETTINGS
}

@Composable
fun FloatingBottomBar(
    currentDestination: NavDestination,
    playerState: PlayerState,
    onNavigate: (NavDestination) -> Unit,
    onOpenFullPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentSong = playerState.currentSong

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("floating_bottom_bar"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Floating Mini-Player (shows when a track is active)
        AnimatedVisibility(
            visible = currentSong != null,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut()
        ) {
            if (currentSong != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(VybeGlass)
                        .border(1.dp, VybeBorder, RoundedCornerShape(16.dp))
                        .clickable { onOpenFullPlayer() }
                        .testTag("mini_player")
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Artwork
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VybeSurfaceVariant)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(currentSong.artworkUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = currentSong.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Song Title & Artist
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = currentSong.title,
                                    color = VybeTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentSong.artist,
                                    color = VybeTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Mini Controls
                            IconButton(
                                onClick = { onPlayPause() },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = { onNext() },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = VybeTextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Bottom progress indicator
                        val progressAnim by animateFloatAsState(
                            targetValue = playerState.progress,
                            label = "mini_progress"
                        )
                        LinearProgressIndicator(
                            progress = { progressAnim },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = Color(0xFFB58AE8),
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        }

        // Floating Pill Navigation Bar with Radiant Ambient Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(33.dp))
                .background(Color(0xF51D141F))
                .glowingBorder(
                    color = Color(0xFF403044),
                    strokeWidth = 1.dp,
                    glowRadius = 0.dp,
                    cornerRadius = 33.dp
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home
                NavIconItem(
                    selected = currentDestination == NavDestination.HOME,
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    label = "Home",
                    onClick = { onNavigate(NavDestination.HOME) }
                )

                // Search
                NavIconItem(
                    selected = currentDestination == NavDestination.SEARCH,
                    selectedIcon = Icons.Filled.Search,
                    unselectedIcon = Icons.Outlined.Search,
                    label = "Search",
                    onClick = { onNavigate(NavDestination.SEARCH) }
                )

                // CENTER EQUALIZER BUTTON with dynamic pulsing glow
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFB58AE8), Color(0xFF9D75D0))
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        .clickable { onOpenFullPlayer() }
                        .testTag("center_player_button"),
                    contentAlignment = Alignment.Center
                ) {
                    EqualizerWaveformIndicator(
                        modifier = Modifier.size(22.dp),
                        isPlaying = playerState.isPlaying,
                        color = Color.White
                    )
                }

                // Library
                NavIconItem(
                    selected = currentDestination == NavDestination.LIBRARY,
                    selectedIcon = Icons.Filled.LibraryMusic,
                    unselectedIcon = Icons.Outlined.LibraryMusic,
                    label = "Library",
                    onClick = { onNavigate(NavDestination.LIBRARY) }
                )

                // Settings
                NavIconItem(
                    selected = currentDestination == NavDestination.SETTINGS,
                    selectedIcon = Icons.Filled.Settings,
                    unselectedIcon = Icons.Outlined.Settings,
                    label = "Settings",
                    onClick = { onNavigate(NavDestination.SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun NavIconItem(
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0x24B58AE8) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("nav_item_${label.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = label,
            tint = if (selected) Color(0xFFB58AE8) else VybeTextMuted,
            modifier = Modifier
                .size(22.dp)
                .then(
                    if (selected) {
                        Modifier.then(Modifier)
                    } else {
                        Modifier
                    }
                )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (selected) Color(0xFFB58AE8) else VybeTextMuted,
            fontSize = 10.sp,
            fontFamily = OutfitFontFamily,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        if (selected) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .then(Modifier)
                    .clip(CircleShape)
                    .background(Color(0xFFB58AE8))
            )
        }
    }
}
