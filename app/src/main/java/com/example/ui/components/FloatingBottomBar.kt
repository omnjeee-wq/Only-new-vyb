package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.player.PlayerState
import com.example.ui.visualizer.PremiumNavVisualizer
import com.example.ui.components.glass.GlassMiniPlayerContainer
import com.example.ui.components.glass.GlassNavigationContainer
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.animation.MotionSpecs
import com.example.ui.theme.animation.pressScale

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
    audioSpectrum: FloatArray = FloatArray(64),
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
        // Floating Mini-Player (shows with fluid Apple spring slide + fade when active)
        AnimatedVisibility(
            visible = currentSong != null,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
            ) { it / 2 } + fadeIn(animationSpec = tween(MotionSpecs.ComponentDuration)),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 0.95f, stiffness = 600f)
            ) { it / 2 } + fadeOut(animationSpec = tween(MotionSpecs.MicroDuration))
        ) {
            if (currentSong != null) {
                GlassMiniPlayerContainer(
                    modifier = Modifier
                        .pressScale(pressedScale = 0.985f) { onOpenFullPlayer() }
                        .testTag("mini_player")
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Artwork with smooth crossfade
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                androidx.compose.animation.Crossfade(
                                    targetState = currentSong.artworkUrl,
                                    animationSpec = tween(MotionSpecs.ComponentDuration),
                                    label = "mini_art_crossfade"
                                ) { artUrl ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(artUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = currentSong.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Song Title & Artist
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = currentSong.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentSong.artist,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Play/Pause icon with animated scale + fade transition
                            IconButton(
                                onClick = onPlayPause,
                                modifier = Modifier
                                    .size(38.dp)
                                    .pressScale()
                            ) {
                                AnimatedContent(
                                    targetState = playerState.isPlaying,
                                    transitionSpec = {
                                        (fadeIn(tween(140)) + scaleIn(initialScale = 0.8f, animationSpec = tween(140))) togetherWith
                                                (fadeOut(tween(140)) + scaleOut(targetScale = 0.8f, animationSpec = tween(140)))
                                    },
                                    label = "mini_play_icon"
                                ) { isPlaying ->
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Next Track Button
                            IconButton(
                                onClick = onNext,
                                modifier = Modifier
                                    .size(38.dp)
                                    .pressScale()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Bottom progress indicator
                        val progressAnim by animateFloatAsState(
                            targetValue = playerState.progress,
                            animationSpec = tween(MotionSpecs.MicroDuration),
                            label = "mini_progress"
                        )
                        LinearProgressIndicator(
                            progress = { progressAnim },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        }

        // Floating Glass Navigation Bar. The center visualizer is overlaid outside
        // the clipped glass surface so its luminous organic rings can protrude like the reference.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GlassNavigationContainer {
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

                // Reserve a centered slot; the premium visualizer is drawn as an
                // unclipped overlay below so the glowing contour can extend beyond the bar.
                Spacer(modifier = Modifier.width(84.dp))

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

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(116.dp)
                    .pressScale(pressedScale = 0.94f) { onOpenFullPlayer() }
                    .testTag("center_player_button"),
                contentAlignment = Alignment.Center
            ) {
                PremiumNavVisualizer(
                    spectrum = audioSpectrum,
                    isPlaying = playerState.isPlaying,
                    modifier = Modifier.fillMaxSize()
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
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "nav_icon_scale"
    )

    val itemTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(MotionSpecs.MicroDuration),
        label = "nav_item_tint"
    )

    val pillBackground by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(MotionSpecs.MicroDuration),
        label = "nav_pill_bg"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(pillBackground)
            .pressScale(pressedScale = 0.92f) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("nav_item_${label.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = label,
            tint = itemTint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = itemTint,
            fontSize = 10.sp,
            fontFamily = OutfitFontFamily,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        if (selected) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
