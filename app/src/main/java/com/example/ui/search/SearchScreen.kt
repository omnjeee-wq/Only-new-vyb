package com.example.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.domain.model.SectionItem
import com.example.domain.model.Song
import com.example.player.PlayerManager
import com.example.ui.components.CollectionDetailSheet
import com.example.ui.home.CollectionDetailState
import com.example.ui.components.EqualizerWaveformIndicator
import com.example.ui.theme.VybeBlack
import com.example.ui.theme.VybeBorder
import com.example.ui.theme.VybeOrange
import com.example.ui.theme.VybePink
import com.example.ui.theme.VybeSurface
import com.example.ui.theme.VybeSurfaceVariant
import com.example.ui.theme.VybeTextMuted
import com.example.ui.theme.VybeTextPrimary
import com.example.ui.theme.VybeTextSecondary
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
            .background(VybeBlack)
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

        // Main Content Area (History vs Results vs Loading vs Empty)
        when (val state = uiState) {
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
                SearchLoadingContent(query = query)
            }
            is SearchUiState.Success -> {
                SearchResultsContent(
                    query = state.query,
                    songs = state.songs,
                    collections = state.collections,
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
                    onCollectionClick = { collection ->
                        viewModel.openCollection(collection)
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
            .background(VybeBlack)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
    ) {
        Text(
            text = "Search",
            color = VybeTextPrimary,
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
                        text = "Search songs, artists...",
                        color = VybeTextMuted,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon",
                        tint = if (query.isNotBlank()) VybeOrange else VybeTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = onClearQuery,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("search_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = VybeTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = VybeSurfaceVariant,
                    unfocusedContainerColor = VybeSurface,
                    disabledContainerColor = VybeSurface,
                    focusedTextColor = VybeTextPrimary,
                    unfocusedTextColor = VybeTextPrimary,
                    focusedBorderColor = VybeOrange,
                    unfocusedBorderColor = VybeBorder,
                    cursorColor = VybeOrange
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearchTriggered() }
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Search action button for easy one-tap search
            IconButton(
                onClick = onSearchTriggered,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(VybeOrange, VybePink)
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
                        .background(VybeSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = VybeOrange,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Search For Music",
                    color = VybeTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Type a song or artist name and press Enter to search.",
                    color = VybeTextMuted,
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
                        color = VybeTextMuted,
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
                            tint = VybePink,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear All",
                            color = VybePink,
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
                            tint = VybeOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = queryItem,
                            color = VybeTextPrimary,
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
                            contentDescription = "Delete search $queryItem",
                            tint = VybeTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    query: String,
    songs: List<Song>,
    collections: List<SectionItem>,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onCollectionClick: (SectionItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_results_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp)
    ) {
        if (collections.isNotEmpty()) {
            item(key = "collections_header") {
                Text(
                    text = "ALBUMS & PLAYLISTS",
                    color = VybeTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
                )
            }

            item(key = "collections_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    collections.forEach { collection ->
                        SearchCollectionCard(
                            collection = collection,
                            onClick = { onCollectionClick(collection) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - collections.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item(key = "collections_spacer") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Summary & Play All Button
        item(key = "results_header") {
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
                        color = VybeTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "for \"$query\"",
                        color = VybeTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onPlayAll,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VybeOrange
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

        // List of Songs
        itemsIndexed(
            items = songs,
            key = { _, song -> song.id }
        ) { index, song ->
            val isCurrent = song.id == currentSongId
            val isPlayingThis = isCurrent && isPlaying

            SearchSongRow(
                song = song,
                index = index + 1,
                isCurrent = isCurrent,
                isPlaying = isPlayingThis,
                onClick = { onSongClick(song) },
                onToggleFavorite = { onToggleFavorite(song) }
            )
        }
    }
}

@Composable
private fun SearchCollectionCard(
    collection: SectionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(VybeSurfaceVariant)
        ) {
            if (collection.artworkUrl.isNotBlank()) {
                AsyncImage(
                    model = collection.artworkUrl,
                    contentDescription = collection.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = VybeOrange,
                    modifier = Modifier.align(Alignment.Center).size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = collection.title,
            color = VybeTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = collection.subtitle,
            color = VybeTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("search_song_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Song Artwork or Waveform
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(VybeSurfaceVariant),
            contentAlignment = Alignment.Center
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
                    tint = VybeOrange,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    EqualizerWaveformIndicator(
                        modifier = Modifier.size(22.dp),
                        color = VybeOrange
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artists
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                color = if (isCurrent) VybeOrange else VybeTextPrimary,
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
                color = VybeTextSecondary,
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
                color = VybeTextMuted,
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
                tint = if (song.isLiked) Color.Red else VybeTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SearchLoadingContent(query: String) {
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
            CircularProgressIndicator(
                color = VybeOrange,
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Searching songs for \"$query\"...",
                color = VybeTextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SearchEmptyContent(
    query: String,
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
                    .background(VybeSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    tint = VybeTextMuted,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No songs found",
                color = VybeTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "We couldn't find any songs matching \"$query\". Check for spelling or try searching another song or artist.",
                color = VybeTextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Try searching:",
                color = VybeTextSecondary,
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
                            .background(VybeSurfaceVariant)
                            .clickable { onSuggestionClick(suggestion) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = suggestion,
                            color = VybeOrange,
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
                color = VybeTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = VybeTextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = VybeOrange)
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
