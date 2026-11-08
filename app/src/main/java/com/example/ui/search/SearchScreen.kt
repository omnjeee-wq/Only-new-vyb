package com.example.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.domain.model.Album
import com.example.domain.model.Artist
import com.example.domain.model.Playlist
import com.example.domain.model.SectionItem
import com.example.domain.model.Song
import com.example.player.PlayerManager
import com.example.ui.components.CollectionDetailSheet
import com.example.ui.components.EqualizerWaveformIndicator
import com.example.ui.components.skeleton.SearchSkeleton
import com.example.ui.theme.animation.MotionSpecs
import com.example.ui.theme.animation.pressScale
import com.example.util.MusicUtils

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    playerManager: PlayerManager,
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val playerState by playerManager.playerState.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("search_screen")
    ) {
        // Top Search Header Bar
        SearchHeaderBar(
            query = query,
            onQueryChange = { viewModel.onQueryChange(it) },
            onSearchTriggered = {
                viewModel.performSearch()
                keyboardController?.hide()
            },
            onClearQuery = {
                viewModel.clearQuery()
            }
        )

        // Category Tab Bar: All, Songs, Playlists, Albums, Artists
        // Visible whenever we have search results (Success) or are loading (Loading)
        val selectedCategory = when (uiState) {
            is SearchUiState.Success -> (uiState as SearchUiState.Success).selectedCategory
            else -> SearchCategory.ALL
        }

        if (uiState is SearchUiState.Success || uiState is SearchUiState.Loading) {
            SearchCategoryTabBar(
                selectedCategory = selectedCategory,
                onCategorySelected = { category ->
                    viewModel.selectCategory(category)
                }
            )
        }

        // Main Content Area with fluid crossfade between History, Skeleton, Results, Empty, Error
        Crossfade(
            targetState = uiState,
            animationSpec = tween(MotionSpecs.ScreenDuration),
            label = "search_state_crossfade"
        ) { state ->
            when (state) {
                is SearchUiState.Idle -> {
                    SearchHistoryContent(
                        history = searchHistory,
                        onItemClick = { historyItem ->
                            viewModel.onHistoryItemClicked(historyItem)
                            keyboardController?.hide()
                        },
                        onDeleteItem = { historyItem ->
                            viewModel.onDeleteHistoryItem(historyItem)
                        },
                        onClearAll = {
                            viewModel.onClearAllHistory()
                        }
                    )
                }
                is SearchUiState.Loading -> {
                    SearchSkeleton(category = selectedCategory)
                }
                is SearchUiState.Success -> {
                    SearchResultsContent(
                        state = state,
                        currentSongId = playerState.currentSong?.id,
                        isPlaying = playerState.isPlaying,
                        onSongClick = { song ->
                            viewModel.playSongWithRadio(
                                song = song,
                                playerManager = playerManager,
                                fallbackPlaylist = state.songs,
                                playlistName = "Search: ${state.query}"
                            )
                        },
                        onPlayAll = {
                            if (state.songs.isNotEmpty()) {
                                viewModel.playSongWithRadio(
                                    song = state.songs.first(),
                                    playerManager = playerManager,
                                    fallbackPlaylist = state.songs,
                                    playlistName = "Search: ${state.query}"
                                )
                            }
                        },
                        onToggleFavorite = { song ->
                            viewModel.toggleFavorite(song)
                        },
                        onAlbumClick = { album ->
                            viewModel.openAlbum(album)
                        },
                        onPlaylistClick = { playlist ->
                            viewModel.openPlaylist(playlist)
                        },
                        onArtistClick = { artist ->
                            viewModel.openArtist(artist)
                        },
                        onCollectionClick = { collection ->
                            viewModel.openCollection(collection)
                        },
                        onSeeAllCategory = { category ->
                            viewModel.selectCategory(category)
                        }
                    )

                    state.selectedCollection?.let { collectionState ->
                        CollectionDetailSheet(
                            state = collectionState,
                            currentPlayingSongId = playerState.currentSong?.id,
                            isPlaying = playerState.isPlaying,
                            onDismiss = { viewModel.closeCollection() },
                            onPlaySong = { song, songs ->
                                playerManager.playSong(song, songs, collectionState.item.title)
                            },
                            onPlayAll = { songs ->
                                viewModel.playCollection(
                                    songs = songs,
                                    title = collectionState.item.title,
                                    playerManager = playerManager
                                )
                            },
                            onShuffle = { songs ->
                                viewModel.playCollection(
                                    songs = songs,
                                    title = collectionState.item.title,
                                    shuffle = true,
                                    playerManager = playerManager
                                )
                            },
                            onToggleFavorite = { song -> viewModel.toggleFavorite(song) }
                        )
                    }
                }
                is SearchUiState.Empty -> {
                    SearchEmptyContent(
                        query = state.query,
                        category = state.category,
                        onSuggestionClick = { suggestion ->
                            viewModel.onHistoryItemClicked(suggestion)
                        }
                    )
                }
                is SearchUiState.Error -> {
                    SearchErrorContent(
                        message = state.message,
                        onRetry = { viewModel.performSearch() }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 1. CATEGORY TAB BAR (Rounded compact pills)
// -------------------------------------------------------------
@Composable
private fun SearchCategoryTabBar(
    selectedCategory: SearchCategory,
    onCategorySelected: (SearchCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("search_category_tab_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchCategory.entries.forEach { category ->
            val isSelected = category == selectedCategory

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                },
                animationSpec = tween(MotionSpecs.MicroDuration),
                label = "category_tab_bg"
            )

            val textColor by animateColorAsState(
                targetValue = if (isSelected) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(MotionSpecs.MicroDuration),
                label = "category_tab_text"
            )

            val borderColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                },
                animationSpec = tween(MotionSpecs.MicroDuration),
                label = "category_tab_border"
            )

            Box(
                modifier = Modifier
                    .pressScale(0.95f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_tab_${category.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.displayName,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 2. SEARCH HEADER BAR
// -------------------------------------------------------------
@Composable
private fun SearchHeaderBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchTriggered: () -> Unit,
    onClearQuery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text = "Search",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_text_field"),
                placeholder = {
                    Text(
                        text = "Search songs, albums, artists...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon",
                        tint = if (query.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = onClearQuery,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("search_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearchTriggered() }
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Search execution button
            IconButton(
                onClick = onSearchTriggered,
                modifier = Modifier
                    .size(46.dp)
                    .pressScale(0.92f)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                        )
                    )
                    .testTag("search_submit_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Execute Search",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 3. SEARCH RESULTS CONTENT (Crossfaded by category)
// -------------------------------------------------------------
@Composable
private fun SearchResultsContent(
    state: SearchUiState.Success,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onCollectionClick: (SectionItem) -> Unit,
    onSeeAllCategory: (SearchCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = state.selectedCategory,
        transitionSpec = {
            fadeIn(tween(MotionSpecs.ComponentDuration)) togetherWith fadeOut(tween(MotionSpecs.MicroDuration))
        },
        label = "category_content_transition"
    ) { category ->
        when (category) {
            SearchCategory.ALL -> {
                AllResultsContent(
                    query = state.query,
                    songs = state.songs,
                    albums = state.albums,
                    playlists = state.playlists,
                    artists = state.artists,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onSongClick = onSongClick,
                    onPlayAll = onPlayAll,
                    onToggleFavorite = onToggleFavorite,
                    onAlbumClick = onAlbumClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick,
                    onSeeAll = onSeeAllCategory,
                    modifier = modifier
                )
            }
            SearchCategory.SONGS -> {
                SongsOnlyContent(
                    query = state.query,
                    songs = state.songs,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    onSongClick = onSongClick,
                    onPlayAll = onPlayAll,
                    onToggleFavorite = onToggleFavorite,
                    modifier = modifier
                )
            }
            SearchCategory.ALBUMS -> {
                AlbumsOnlyContent(
                    query = state.query,
                    albums = state.albums,
                    onAlbumClick = onAlbumClick,
                    modifier = modifier
                )
            }
            SearchCategory.PLAYLISTS -> {
                PlaylistsOnlyContent(
                    query = state.query,
                    playlists = state.playlists,
                    onPlaylistClick = onPlaylistClick,
                    modifier = modifier
                )
            }
            SearchCategory.ARTISTS -> {
                ArtistsOnlyContent(
                    query = state.query,
                    artists = state.artists,
                    onArtistClick = onArtistClick,
                    modifier = modifier
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 4. "ALL" TAB VIEW: Grouped Sections (Top, Songs, Albums, Playlists, Artists)
// -------------------------------------------------------------
@Composable
private fun AllResultsContent(
    query: String,
    songs: List<Song>,
    albums: List<Album>,
    playlists: List<Playlist>,
    artists: List<Artist>,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onSeeAll: (SearchCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_results_all"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp)
    ) {
        // A. Top Result Hero Card (First song or artist matching query closely)
        val topSong = songs.firstOrNull()
        if (topSong != null) {
            item(key = "top_result_header") {
                SectionHeader(title = "TOP RESULT")
            }
            item(key = "top_result_card") {
                TopResultCard(
                    song = topSong,
                    onClick = { onSongClick(topSong) }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // B. Songs Section
        if (songs.isNotEmpty()) {
            item(key = "all_songs_header") {
                SectionHeader(
                    title = "SONGS",
                    actionLabel = if (songs.size > 5) "See all" else null,
                    onAction = { onSeeAll(SearchCategory.SONGS) }
                )
            }

            // Show first 5 songs in All view
            itemsIndexed(
                items = songs.take(5),
                key = { _, song -> "all_song_${song.id}" }
            ) { index, song ->
                val isCurrent = song.id == currentSongId
                SearchSongRow(
                    song = song,
                    index = index + 1,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && isPlaying,
                    onClick = { onSongClick(song) },
                    onToggleFavorite = { onToggleFavorite(song) }
                )
            }

            item(key = "songs_divider_spacer") {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // C. Albums Section (Horizontal list)
        if (albums.isNotEmpty()) {
            item(key = "all_albums_header") {
                SectionHeader(
                    title = "ALBUMS",
                    actionLabel = if (albums.size > 4) "See all" else null,
                    onAction = { onSeeAll(SearchCategory.ALBUMS) }
                )
            }

            item(key = "all_albums_horizontal_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(
                        items = albums,
                        key = { "all_album_${it.id}" }
                    ) { album ->
                        AlbumSquareCard(
                            album = album,
                            onClick = { onAlbumClick(album) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // D. Playlists Section (Horizontal list)
        if (playlists.isNotEmpty()) {
            item(key = "all_playlists_header") {
                SectionHeader(
                    title = "PLAYLISTS",
                    actionLabel = if (playlists.size > 4) "See all" else null,
                    onAction = { onSeeAll(SearchCategory.PLAYLISTS) }
                )
            }

            item(key = "all_playlists_horizontal_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(
                        items = playlists,
                        key = { "all_playlist_${it.id}" }
                    ) { playlist ->
                        PlaylistSquareCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // E. Artists Section (Horizontal avatars)
        if (artists.isNotEmpty()) {
            item(key = "all_artists_header") {
                SectionHeader(
                    title = "ARTISTS",
                    actionLabel = if (artists.size > 4) "See all" else null,
                    onAction = { onSeeAll(SearchCategory.ARTISTS) }
                )
            }

            item(key = "all_artists_horizontal_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(
                        items = artists,
                        key = { "all_artist_${it.id}" }
                    ) { artist ->
                        ArtistCircleCard(
                            artist = artist,
                            onClick = { onArtistClick(artist) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// 5. "SONGS" ONLY TAB
// -------------------------------------------------------------
@Composable
private fun SongsOnlyContent(
    query: String,
    songs: List<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) {
        CategoryEmptyView(category = SearchCategory.SONGS, query = query)
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_results_songs"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp)
    ) {
        // Summary & Play All Button
        item(key = "songs_results_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SONGS FOUND (${songs.size})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "for \"$query\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("search_play_all")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Play All",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // List of all songs
        itemsIndexed(
            items = songs,
            key = { _, song -> "song_${song.id}" }
        ) { index, song ->
            val isCurrent = song.id == currentSongId
            SearchSongRow(
                song = song,
                index = index + 1,
                isCurrent = isCurrent,
                isPlaying = isCurrent && isPlaying,
                onClick = { onSongClick(song) },
                onToggleFavorite = { onToggleFavorite(song) }
            )
        }
    }
}

// -------------------------------------------------------------
// 6. "ALBUMS" ONLY TAB
// -------------------------------------------------------------
@Composable
private fun AlbumsOnlyContent(
    query: String,
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier
) {
    if (albums.isEmpty()) {
        CategoryEmptyView(category = SearchCategory.ALBUMS, query = query)
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_results_albums"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "albums_header") {
            Text(
                text = "ALBUMS (${albums.size})",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        items(
            items = albums,
            key = { "album_${it.id}" }
        ) { album ->
            AlbumListRow(
                album = album,
                onClick = { onAlbumClick(album) }
            )
        }
    }
}

// -------------------------------------------------------------
// 7. "PLAYLISTS" ONLY TAB
// -------------------------------------------------------------
@Composable
private fun PlaylistsOnlyContent(
    query: String,
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlists.isEmpty()) {
        CategoryEmptyView(category = SearchCategory.PLAYLISTS, query = query)
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_results_playlists"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "playlists_header") {
            Text(
                text = "PLAYLISTS (${playlists.size})",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        items(
            items = playlists,
            key = { "playlist_${it.id}" }
        ) { playlist ->
            PlaylistListRow(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) }
            )
        }
    }
}

// -------------------------------------------------------------
// 8. "ARTISTS" ONLY TAB
// -------------------------------------------------------------
@Composable
private fun ArtistsOnlyContent(
    query: String,
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) {
        CategoryEmptyView(category = SearchCategory.ARTISTS, query = query)
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_results_artists"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "artists_header") {
            Text(
                text = "ARTISTS (${artists.size})",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        items(
            items = artists,
            key = { "artist_${it.id}" }
        ) { artist ->
            ArtistListRow(
                artist = artist,
                onClick = { onArtistClick(artist) }
            )
        }
    }
}

// -------------------------------------------------------------
// 9. REUSABLE SECTION HEADER & CARDS
// -------------------------------------------------------------
@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun TopResultCard(
    song: Song,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(0.97f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp)
            .testTag("top_result_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (song.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = song.artworkUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Song • ${song.artist}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumSquareCard(
    album: Album,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .pressScale(0.95f)
            .clickable { onClick() }
            .testTag("album_card_${album.id}")
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (album.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = album.artworkUrl,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = album.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val meta = buildString {
            if (album.artist.isNotBlank()) append(album.artist)
            if (album.year.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(album.year)
            }
        }.ifBlank { "Album" }

        Text(
            text = meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistSquareCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .pressScale(0.95f)
            .clickable { onClick() }
            .testTag("playlist_card_${playlist.id}")
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (playlist.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = playlist.artworkUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = playlist.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val meta = if (playlist.songCount > 0) "${playlist.songCount} songs" else playlist.subtitle.ifBlank { "Playlist" }

        Text(
            text = meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistCircleCard(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .pressScale(0.95f)
            .clickable { onClick() }
            .testTag("artist_card_${artist.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (artist.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = artist.artworkUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Artist",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

// -------------------------------------------------------------
// 10. LIST ROWS (For Full Dedicated Category Tabs)
// -------------------------------------------------------------
@Composable
private fun AlbumListRow(
    album: Album,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(0.97f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("album_row_${album.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (album.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = album.artworkUrl,
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                )
            }
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
            Spacer(modifier = Modifier.height(3.dp))
            val meta = buildString {
                if (album.artist.isNotBlank()) append(album.artist)
                if (album.year.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(album.year)
                }
            }.ifBlank { "Album" }
            Text(
                text = meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun PlaylistListRow(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(0.97f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("playlist_row_${playlist.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (playlist.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = playlist.artworkUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                )
            }
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
            Spacer(modifier = Modifier.height(3.dp))
            val meta = if (playlist.songCount > 0) "${playlist.songCount} songs" else playlist.subtitle.ifBlank { "Playlist" }
            Text(
                text = meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun ArtistListRow(
    artist: Artist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(0.97f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("artist_row_${artist.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (artist.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = artist.artworkUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = artist.role.ifBlank { "Artist" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
    }
}

// -------------------------------------------------------------
// 11. SONG ROW (Preserved exact design, interactions, and test tags)
// -------------------------------------------------------------
@Composable
private fun SearchSongRow(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressScale(pressedScale = 0.97f) { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("search_song_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Song Artwork or Waveform
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrent && isPlaying) {
                EqualizerWaveformIndicator(
                    modifier = Modifier.size(20.dp),
                    isPlaying = true,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (song.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = song.artworkUrl,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artists
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = listOfNotNull(
                    song.artist.ifBlank { null },
                    song.album.ifBlank { null }
                ).joinToString(" • "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Duration
        if (song.durationSeconds > 0) {
            Text(
                text = MusicUtils.formatDuration(song.durationSeconds),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Favorite Toggle
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .size(36.dp)
                .testTag("search_fav_${song.id}")
        ) {
            Icon(
                imageVector = if (song.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Toggle favorite",
                tint = if (song.isLiked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// 12. SEARCH HISTORY, EMPTY & ERROR VIEWS
// -------------------------------------------------------------
@Composable
private fun SearchHistoryContent(
    history: List<String>,
    onItemClick: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit
) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Search For Music",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Type a song, album, or artist and press Enter to search.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("search_history_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp)
        ) {
            item(key = "history_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT SEARCHES (${history.size}/10)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.testTag("clear_all_history")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Clear all searches",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear All",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            itemsIndexed(
                items = history,
                key = { _, item -> item }
            ) { index, queryItem ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onItemClick(queryItem) }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag("search_history_item_$index"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Recent search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = queryItem,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Delete single history item
                    IconButton(
                        onClick = { onDeleteItem(queryItem) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_history_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryEmptyView(
    category: SearchCategory,
    query: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (category) {
                        SearchCategory.SONGS -> Icons.Default.MusicNote
                        SearchCategory.ALBUMS -> Icons.Default.Album
                        SearchCategory.PLAYLISTS -> Icons.Default.QueueMusic
                        SearchCategory.ARTISTS -> Icons.Default.Person
                        SearchCategory.ALL -> Icons.Default.SearchOff
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No ${category.displayName.lowercase()} found",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No ${category.displayName.lowercase()} matched \"$query\". Check other category tabs or try another query.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SearchEmptyContent(
    query: String,
    category: SearchCategory = SearchCategory.ALL,
    onSuggestionClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No results found",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "We couldn't find any results matching \"$query\". Check for spelling or try searching another title or artist.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Try searching:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Arijit Singh", "Trending Hindi", "A.R. Rahman").forEach { suggestion ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSuggestionClick(suggestion) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = suggestion,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Search Error",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Retry Search")
            }
        }
    }
}
