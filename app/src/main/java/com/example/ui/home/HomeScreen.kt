package com.example.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.player.PlayerManager
import com.example.player.PlayerState
import com.example.ui.components.CollectionDetailSheet
import com.example.ui.components.HeroBanner
import com.example.ui.components.HorizontalSection
import com.example.ui.components.RecommendForYouSection
import com.example.ui.components.TopHeader

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    playerManager: PlayerManager,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerManager.playerState.collectAsStateWithLifecycle()
    val announcementBanner by com.example.VybeApp.instance.updateManager.announcementBanner.collectAsStateWithLifecycle()

      Box(
    modifier = modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .statusBarsPadding()
        .testTag("home_screen")
) { 
    
        // Atmospheric subtle background gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(
                    Brush.verticalGradient(
                        0.0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        0.5f to MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        1.0f to Color.Transparent
                    )
                )
        )

        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Crafting your musical vibe...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else if (uiState.errorMessage != null && uiState.sections.isEmpty() && uiState.recommendations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Unable to connect to music service",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage ?: "Please check your internet connection.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.loadHomeData() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Retry", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp) // Room for floating bar & mini player
            ) {
                // Remote Announcement Banner (from silent config update)
                announcementBanner?.let { banner ->
                    item(key = "announcement_banner_${banner.id}") {
                        com.example.ui.components.AnnouncementBanner(
                            banner = banner,
                            onDismiss = { com.example.VybeApp.instance.updateManager.dismissAnnouncement() }
                        )
                    }
                }

                // Cinematic Hero Banner (Positioned edge-to-edge at the very top)
                item(key = "hero_banner") {
                    val isHeroCurrent = playerState.currentSong?.id == uiState.heroSong?.id
                    HeroBanner(
                        heroSong = uiState.heroSong,
                        isPlaying = isHeroCurrent && playerState.isPlaying,
                        onPlayPauseClick = { viewModel.toggleHeroPlayPause() }
                    )
                }

                // Recommend For You Section
                item(key = "recommend_for_you") {
                    RecommendForYouSection(
                        songs = uiState.recommendations,
                        currentPlayingSongId = playerState.currentSong?.id,
                        isPlaying = playerState.isPlaying,
                        onSongClick = { song ->
                            viewModel.playSongWithRadio(song, uiState.recommendations, "Recommend For You")
                        },
                        onAddToQueue = { song ->
                            viewModel.addToQueue(song)
                        },
                        onToggleFavorite = { song ->
                            viewModel.toggleFavorite(song)
                        }
                    )
                }

                // Categorized Horizontal Sections (Albums & Charts/Playlists)
                items(
                    items = uiState.sections,
                    key = { it.id }
                ) { section ->
                    HorizontalSection(
                        section = section,
                        currentPlayingSongId = playerState.currentSong?.id,
                        isPlaying = playerState.isPlaying,
                        onCollectionClick = { item ->
                            viewModel.openCollection(item)
                        },
                        onPlayCollectionClick = { item ->
                            viewModel.playCollection(item)
                        },
                        onSongClick = { song, list ->
                            viewModel.playSong(song, list, section.title)
                        }
                    )
                }
            }
        }

        // Collection (Album / Chart / Playlist) Detail Sheet
        uiState.selectedCollection?.let { collectionState ->
            CollectionDetailSheet(
                state = collectionState,
                currentPlayingSongId = playerState.currentSong?.id,
                isPlaying = playerState.isPlaying,
                onDismiss = { viewModel.closeCollection() },
                onPlaySong = { song, list ->
                    viewModel.playSong(song, list, collectionState.item.title)
                },
                onPlayAll = { list ->
                    if (list.isNotEmpty()) {
                        viewModel.playSong(list.first(), list, collectionState.item.title)
                    }
                },
                onShuffle = { list ->
                    if (list.isNotEmpty()) {
                        val shuffled = list.shuffled()
                        viewModel.playSong(shuffled.first(), shuffled, collectionState.item.title)
                    }
                },
                onToggleFavorite = { song ->
                    viewModel.toggleFavorite(song)
                },
                onToggleFavoriteCollection = { item ->
                    viewModel.toggleFavoriteCollection(item)
                }
            )
        }
    }
}
