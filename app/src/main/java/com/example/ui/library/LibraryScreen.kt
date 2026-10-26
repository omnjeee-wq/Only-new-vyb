package com.example.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import com.example.domain.smartshuffle.SmartShuffleEngine
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.smartshuffle.MoodCategory
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.domain.model.Album
import com.example.domain.model.Playlist
import com.example.domain.model.SectionItem
import com.example.domain.model.SectionItemType
import com.example.domain.model.Song
import com.example.player.PlayerManager
import com.example.ui.components.CollectionDetailSheet
import com.example.ui.components.EqualizerWaveformIndicator
import com.example.ui.components.TopHeader
import com.example.ui.home.CollectionDetailState
import com.example.util.MusicUtils

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    playerManager: PlayerManager,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerManager.playerState.collectAsStateWithLifecycle()

    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val likedAlbums by viewModel.likedAlbums.collectAsStateWithLifecycle()
    val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()
    val totalBytes by viewModel.totalDownloadedBytes.collectAsStateWithLifecycle()
    val myPlaylists by viewModel.myPlaylists.collectAsStateWithLifecycle()
    val likedPlaylists by viewModel.likedPlaylists.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Active User Playlist Detail View
    if (uiState.activeUserPlaylist != null) {
        UserPlaylistDetailContent(
            playlist = uiState.activeUserPlaylist!!,
            songs = uiState.activeUserPlaylistSongs,
            currentPlayingSongId = playerState.currentSong?.id,
            isPlaying = playerState.isPlaying,
            onBack = { viewModel.closeUserPlaylist() },
            onPlayAll = {
                viewModel.playAll(uiState.activeUserPlaylistSongs, uiState.activeUserPlaylist!!.title)
            },
            onShuffle = {
                viewModel.shufflePlay(uiState.activeUserPlaylistSongs, uiState.activeUserPlaylist!!.title)
            },
            onPlaySong = { song ->
                viewModel.playSong(song, uiState.activeUserPlaylistSongs, uiState.activeUserPlaylist!!.title)
            },
            onAddSongs = { viewModel.openAddSongsSheet() },
            onRemoveSong = { songId -> viewModel.removeSongFromActivePlaylist(songId) },
            onDeletePlaylist = {
                viewModel.deleteUserPlaylist(uiState.activeUserPlaylist!!.id)
            }
        )

        // Add Songs Sheet
        if (uiState.isAddSongsSheetOpen) {
            AddSongsToPlaylistSheet(
                playlist = uiState.activeUserPlaylist!!,
                currentPlaylistSongIds = uiState.activeUserPlaylistSongs.map { it.id }.toSet(),
                candidateSongs = uiState.searchSongCandidates,
                isSearching = uiState.isSearchingCandidates,
                onSearch = { query -> viewModel.searchSongsForPlaylist(query) },
                onAddSong = { song -> viewModel.addSongToActivePlaylist(song) },
                onDismiss = { viewModel.closeAddSongsSheet() }
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("library_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopHeader()

            // Header Title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Your Library",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Playlists, liked tracks, albums and downloads",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            // Category Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryCategoryChip(
                    label = "All",
                    selected = uiState.currentTab == LibraryTab.ALL,
                    onClick = { viewModel.selectTab(LibraryTab.ALL) }
                )
                LibraryCategoryChip(
                    label = "Liked Songs (${likedSongs.size})",
                    selected = uiState.currentTab == LibraryTab.LIKED_SONGS,
                    onClick = { viewModel.selectTab(LibraryTab.LIKED_SONGS) }
                )
                LibraryCategoryChip(
                    label = "Albums (${likedAlbums.size})",
                    selected = uiState.currentTab == LibraryTab.ALBUMS,
                    onClick = { viewModel.selectTab(LibraryTab.ALBUMS) }
                )
                LibraryCategoryChip(
                    label = "Downloads (${downloadedSongs.size})",
                    selected = uiState.currentTab == LibraryTab.DOWNLOADS,
                    onClick = { viewModel.selectTab(LibraryTab.DOWNLOADS) }
                )
                LibraryCategoryChip(
                    label = "Playlists (${myPlaylists.size + likedPlaylists.size})",
                    selected = uiState.currentTab == LibraryTab.PLAYLISTS,
                    onClick = { viewModel.selectTab(LibraryTab.PLAYLISTS) }
                )
            }

            // Tab Content
            when (uiState.currentTab) {
                LibraryTab.ALL -> {
                    LibraryOverviewContent(
                        likedSongsCount = likedSongs.size,
                        likedAlbumsCount = likedAlbums.size,
                        downloadsCount = downloadedSongs.size,
                        myPlaylistsCount = myPlaylists.size,
                        likedPlaylistsCount = likedPlaylists.size,
                        onOpenLikedSongs = { viewModel.selectTab(LibraryTab.LIKED_SONGS) },
                        onOpenAlbums = { viewModel.selectTab(LibraryTab.ALBUMS) },
                        onOpenDownloads = { viewModel.selectTab(LibraryTab.DOWNLOADS) },
                        onOpenPlaylists = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                        onCreatePlaylist = { viewModel.openCreatePlaylistDialog() }
                    )
                }

                LibraryTab.LIKED_SONGS -> {
                    LikedSongsListContent(
                        songs = likedSongs,
                        selectedMoodFilter = uiState.selectedMoodFilter,
                        currentPlayingSongId = playerState.currentSong?.id,
                        isPlaying = playerState.isPlaying,
                        onPlaySong = { song -> viewModel.playSong(song, likedSongs, "Liked Songs") },
                        onPlayAll = { viewModel.playAll(likedSongs, "Liked Songs") },
                        onShuffle = { viewModel.shufflePlay(likedSongs, "Liked Songs") },
                        onSmartShuffle = { mood -> viewModel.smartShufflePlay(likedSongs, "Liked Songs", mood) },
                        onSelectMoodFilter = { mood -> viewModel.selectMoodFilter(mood) },
                        onToggleFavorite = { song -> viewModel.toggleFavorite(song) }
                    )
                }

                LibraryTab.ALBUMS -> {
                    LikedAlbumsContent(
                        albums = likedAlbums,
                        onOpenAlbum = { album -> viewModel.openLikedAlbum(album) },
                        onToggleLikeAlbum = { album -> viewModel.toggleFavoriteAlbum(album) }
                    )
                }

                LibraryTab.DOWNLOADS -> {
                    val formattedSize = formatStorageSize(totalBytes)
                    DownloadsContent(
                        downloadedSongs = downloadedSongs,
                        storageSizeString = formattedSize,
                        currentPlayingSongId = playerState.currentSong?.id,
                        isPlaying = playerState.isPlaying,
                        onPlaySong = { song -> viewModel.playSong(song, downloadedSongs, "Downloaded Songs") },
                        onPlayAll = { viewModel.playAll(downloadedSongs, "Downloaded Songs") },
                        onShuffle = { viewModel.shufflePlay(downloadedSongs, "Downloaded Songs") },
                        onRemoveDownload = { song -> viewModel.toggleDownloadSong(song) },
                        onClearAll = { viewModel.clearAllDownloads() }
                    )
                }

                LibraryTab.PLAYLISTS -> {
                    // Two sections as requested: "My Playlist" and "Liked Playlist"
                    PlaylistsTabContent(
                        selectedSubTab = uiState.playlistSubTab,
                        myPlaylists = myPlaylists,
                        likedPlaylists = likedPlaylists,
                        onSelectSubTab = { viewModel.selectPlaylistSubTab(it) },
                        onCreatePlaylist = { viewModel.openCreatePlaylistDialog() },
                        onOpenUserPlaylist = { viewModel.openUserPlaylist(it) },
                        onDeleteUserPlaylist = { viewModel.deleteUserPlaylist(it.id) },
                        onOpenLikedPlaylist = { playlist ->
                            // Play directly or open collection
                            viewModel.playSong(
                                Song(id = playlist.id, title = playlist.title, artist = playlist.subtitle, artworkUrl = playlist.artworkUrl),
                                emptyList(),
                                playlist.title
                            )
                        },
                        onToggleLikedPlaylist = { viewModel.toggleLikedPlaylist(it) }
                    )
                }
            }
        }

        // Create Playlist Dialog
        if (uiState.isCreatePlaylistDialogOpen) {
            CreatePlaylistDialog(
                onDismiss = { viewModel.closeCreatePlaylistDialog() },
                onCreate = { title, desc -> viewModel.createPlaylist(title, desc) }
            )
        }

        // Album Detail Sheet for Liked Album
        uiState.activeAlbumDetail?.let { album ->
            CollectionDetailSheet(
                state = CollectionDetailState(
                    item = SectionItem(
                        id = album.id,
                        title = album.title,
                        subtitle = album.artist,
                        artworkUrl = album.artworkUrl,
                        type = SectionItemType.ALBUM,
                        songCount = album.songCount,
                        year = album.year
                    ),
                    songs = uiState.activeAlbumSongs,
                    isLoading = uiState.isDetailLoading
                ),
                currentPlayingSongId = playerState.currentSong?.id,
                isPlaying = playerState.isPlaying,
                onDismiss = { viewModel.closeLikedAlbum() },
                onPlaySong = { song, list -> viewModel.playSong(song, list, album.title) },
                onPlayAll = { list -> viewModel.playAll(list, album.title) },
                onShuffle = { list -> viewModel.shufflePlay(list, album.title) },
                onToggleFavorite = { song -> viewModel.toggleFavorite(song) }
            )
        }
    }
}

@Composable
private fun LibraryCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("library_tab_${label.take(6).lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// -------------------------------------------------------------
// 1. OVERVIEW CONTENT (4 MAIN CATEGORIES)
// -------------------------------------------------------------
@Composable
private fun LibraryOverviewContent(
    likedSongsCount: Int,
    likedAlbumsCount: Int,
    downloadsCount: Int,
    myPlaylistsCount: Int,
    likedPlaylistsCount: Int,
    onOpenLikedSongs: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onCreatePlaylist: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Liked Songs Hero Card
            LibraryHeroCard(
                title = "Liked Songs",
                subtitle = "$likedSongsCount songs saved to your vibe",
                icon = Icons.Default.Favorite,
                gradient = listOf(MaterialTheme.colorScheme.primary, Color(0xFF7C3AED)),
                onClick = onOpenLikedSongs,
                testTag = "card_liked_songs"
            )
        }

        item {
            // Playlists Card (My Playlists & Liked Playlists)
            LibraryHeroCard(
                title = "Playlists",
                subtitle = "$myPlaylistsCount My Playlists • $likedPlaylistsCount Liked Playlists",
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                gradient = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary),
                onClick = onOpenPlaylists,
                testTag = "card_playlists"
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Albums Card
                LibrarySquareCard(
                    title = "Albums",
                    subtitle = "$likedAlbumsCount saved",
                    icon = Icons.Default.Album,
                    gradient = listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6)),
                    onClick = onOpenAlbums,
                    modifier = Modifier.weight(1f),
                    testTag = "card_albums"
                )

                // Downloads Card
                LibrarySquareCard(
                    title = "Downloads",
                    subtitle = "$downloadsCount offline tracks",
                    icon = Icons.Default.DownloadDone,
                    gradient = listOf(MaterialTheme.colorScheme.primary, Color(0xFF10B981)),
                    onClick = onOpenDownloads,
                    modifier = Modifier.weight(1f),
                    testTag = "card_downloads"
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            // Quick action to create playlist
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clickable { onCreatePlaylist() }
                    .padding(16.dp)
                    .testTag("overview_create_playlist_btn"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create a New Playlist",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryHeroCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradient))
            .clickable { onClick() }
            .padding(20.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LibrarySquareCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Box(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradient))
            .clickable { onClick() }
            .padding(16.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }

            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 2. PLAYLISTS TAB (MY PLAYLISTS & LIKED PLAYLISTS)
// -------------------------------------------------------------
@Composable
private fun PlaylistsTabContent(
    selectedSubTab: PlaylistSubTab,
    myPlaylists: List<Playlist>,
    likedPlaylists: List<Playlist>,
    onSelectSubTab: (PlaylistSubTab) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenUserPlaylist: (Playlist) -> Unit,
    onDeleteUserPlaylist: (Playlist) -> Unit,
    onOpenLikedPlaylist: (Playlist) -> Unit,
    onToggleLikedPlaylist: (Playlist) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-Tabs Header: "My Playlist" and "Liked Playlist"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // My Playlist Sub-Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selectedSubTab == PlaylistSubTab.MY_PLAYLISTS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelectSubTab(PlaylistSubTab.MY_PLAYLISTS) }
                    .padding(vertical = 10.dp)
                    .testTag("tab_my_playlists"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = if (selectedSubTab == PlaylistSubTab.MY_PLAYLISTS) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "My Playlist (${myPlaylists.size})",
                        color = if (selectedSubTab == PlaylistSubTab.MY_PLAYLISTS) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Liked Playlist Sub-Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selectedSubTab == PlaylistSubTab.LIKED_PLAYLISTS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelectSubTab(PlaylistSubTab.LIKED_PLAYLISTS) }
                    .padding(vertical = 10.dp)
                    .testTag("tab_liked_playlists"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = if (selectedSubTab == PlaylistSubTab.LIKED_PLAYLISTS) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Liked Playlist (${likedPlaylists.size})",
                        color = if (selectedSubTab == PlaylistSubTab.LIKED_PLAYLISTS) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        when (selectedSubTab) {
            PlaylistSubTab.MY_PLAYLISTS -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // "+ Create Playlist" Button
                    item(key = "create_playlist_btn") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    )
                                )
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .clickable { onCreatePlaylist() }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                                .testTag("create_new_playlist_item"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Create", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Create Playlist",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Build your own custom mix of songs",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (myPlaylists.isEmpty()) {
                        item(key = "empty_my_playlists") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistAdd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No custom playlists yet",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap 'Create Playlist' above to create your first personal playlist!",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = myPlaylists,
                            key = { it.id }
                        ) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onOpenUserPlaylist(playlist) }
                                    .padding(12.dp)
                                    .testTag("user_playlist_${playlist.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (playlist.description.isNotBlank()) playlist.description else "Custom Playlist",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteUserPlaylist(playlist) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            PlaylistSubTab.LIKED_PLAYLISTS -> {
                if (likedPlaylists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No liked playlists yet",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Browse playlists from Home or Search, and tap the heart icon to save them to your library.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = likedPlaylists,
                            key = { it.id }
                        ) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onOpenLikedPlaylist(playlist) }
                                    .padding(12.dp)
                                    .testTag("liked_playlist_${playlist.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(playlist.artworkUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = playlist.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = playlist.subtitle.ifBlank { "Liked Playlist" },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { onToggleLikedPlaylist(playlist) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Liked",
                                        tint = Color.Red,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. LIKED SONGS CONTENT
// -------------------------------------------------------------
@Composable
 private fun LikedSongsListContent(
    songs: List<Song>,
    selectedMoodFilter: MoodCategory?,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSmartShuffle: (MoodCategory?) -> Unit,
    onSelectMoodFilter: (MoodCategory?) -> Unit,
    onToggleFavorite: (Song) -> Unit
) {
    val context = LocalContext.current

    if (songs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No liked songs yet",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap the heart icon on any song to save your personal favorites here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 140.dp)
        ) {
            // Actions Header
            item(key = "liked_songs_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayAll,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary))
                            )
                            .testTag("liked_songs_play_all")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play All (${songs.size})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onShuffle,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("liked_songs_shuffle")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, song -> song.id }
            ) { index, song ->
                val isCurrent = song.id == currentPlayingSongId

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaySong(song) }
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("liked_song_item_${song.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(song.artworkUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = MusicUtils.formatDuration(song.durationSeconds),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { onToggleFavorite(song) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Unlike",
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. ALBUMS (LIKED ALBUMS) CONTENT
// -------------------------------------------------------------
@Composable
private fun LikedAlbumsContent(
    albums: List<Album>,
    onOpenAlbum: (Album) -> Unit,
    onToggleLikeAlbum: (Album) -> Unit
) {
    val context = LocalContext.current

    if (albums.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No liked albums yet",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "When you like an album from Home or Search, it will appear here in your collection.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = albums,
                key = { it.id }
            ) { album ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpenAlbum(album) }
                        .padding(12.dp)
                        .testTag("liked_album_${album.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(album.artworkUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = album.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${album.artist} • ${if (album.year.isNotBlank()) album.year else "Album"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onToggleLikeAlbum(album) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Unlike Album",
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 5. DOWNLOADS CONTENT
// -------------------------------------------------------------
@Composable
private fun DownloadsContent(
    downloadedSongs: List<Song>,
    storageSizeString: String,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onRemoveDownload: (Song) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current

    if (downloadedSongs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.DownloadDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No offline downloads yet",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Download songs from the player options to listen offline without internet connection.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 140.dp)
        ) {
            // Header with Storage info & Actions
            item(key = "downloads_header") {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Offline Storage: $storageSizeString",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = "Clear All",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onClearAll() }
                                .padding(4.dp)
                                .testTag("clear_all_downloads_btn")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayAll,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF10B981)))
                                )
                                .testTag("downloads_play_all")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play Offline", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onShuffle,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("downloads_shuffle")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shuffle", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            itemsIndexed(
                items = downloadedSongs,
                key = { _, song -> song.id }
            ) { index, song ->
                val isCurrent = song.id == currentPlayingSongId

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaySong(song) }
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("downloaded_song_item_${song.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(song.artworkUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.title,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { onRemoveDownload(song) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatStorageSize(bytes: Long): String {
    return when {
        bytes <= 0 -> "0 MB"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
