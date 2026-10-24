package com.example.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.SectionItem
import com.example.domain.model.SectionItemType
import com.example.domain.model.Song
import com.example.domain.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import com.example.player.PlayerManager
import com.example.ui.home.CollectionDetailState

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(
        val query: String,
        val songs: List<Song>,
        val collections: List<SectionItem> = emptyList(),
        val selectedCollection: CollectionDetailState? = null
    ) : SearchUiState
    data class Empty(val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(
    private val repository: MusicRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Recent search history (maximum 10 searches, auto-trimmed in database)
    val searchHistory: StateFlow<List<String>> = repository.getSearchHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        // If the user clears the input while in success/error/empty state, return to Idle (history)
        if (newQuery.isBlank() && _uiState.value !is SearchUiState.Idle) {
            _uiState.value = SearchUiState.Idle
        }
    }

    /**
     * Executes the song search ONLY when the user explicitly triggers it
     * by pressing Enter (IME Search action), tapping Search icon, or clicking a history item.
     * Searches are only saved to history at this exact moment.
     */
    fun performSearch(queryToSearch: String? = null) {
        val targetQuery = (queryToSearch ?: _query.value).trim()
        if (targetQuery.isBlank()) return

        _query.value = targetQuery
        _uiState.value = SearchUiState.Loading

        viewModelScope.launch {
            // Save to search history (max 10, oldest deleted automatically)
            repository.saveSearchQuery(targetQuery)

            try {
                // Search songs + albums + playlists together so the top of the
                // results can surface collections without changing the existing song search.
                val songsDeferred = async { repository.searchSongs(targetQuery, limit = 40) }
                val albumsDeferred = async { repository.searchAlbums(targetQuery, limit = 6) }
                val playlistsDeferred = async { repository.searchPlaylists(targetQuery, limit = 6) }

                val songsResult = songsDeferred.await()
                val albumsResult = albumsDeferred.await()
                val playlistsResult = playlistsDeferred.await()

                val songs = songsResult.getOrNull().orEmpty()
                val collections = buildList {
                    albumsResult.getOrNull().orEmpty().take(2).forEach { album ->
                        add(
                            SectionItem(
                                id = album.id,
                                title = album.title,
                                subtitle = if (album.year.isNotBlank()) "Album • ${album.year}" else "Album",
                                artworkUrl = album.artworkUrl,
                                type = SectionItemType.ALBUM,
                                songCount = album.songCount,
                                year = album.year,
                                language = album.language
                            )
                        )
                    }
                    playlistsResult.getOrNull().orEmpty().take(2).forEach { playlist ->
                        add(
                            SectionItem(
                                id = playlist.id,
                                title = playlist.title,
                                subtitle = playlist.subtitle.ifBlank { "Playlist" },
                                artworkUrl = playlist.artworkUrl,
                                type = SectionItemType.PLAYLIST,
                                songCount = playlist.songCount,
                                language = playlist.language
                            )
                        )
                    }
                }.take(3)

                if (songs.isEmpty() && collections.isEmpty()) {
                    _uiState.value = SearchUiState.Empty(targetQuery)
                } else {
                    _uiState.value = SearchUiState.Success(
                        query = targetQuery,
                        songs = songs,
                        collections = collections
                    )
                }
            } catch (error: Exception) {
                _uiState.value = SearchUiState.Error(
                    error.localizedMessage ?: "Failed to search. Please check your network and try again."
                )
            }
        }
    }

    fun onHistoryItemClicked(historyQuery: String) {
        _query.value = historyQuery
        performSearch(historyQuery)
    }

    fun onDeleteHistoryItem(historyQuery: String) {
        viewModelScope.launch {
            repository.deleteSearchQuery(historyQuery)
        }
    }

    fun onClearAllHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    fun clearQuery() {
        _query.value = ""
        _uiState.value = SearchUiState.Idle
    }

     
     fun playSongWithRadio(
    song: Song,
    playerManager: PlayerManager,
    fallbackPlaylist: List<Song>,
    playlistName: String
) {
    // First play the selected song immediately
    playerManager.playSong(
        song = song,
        playlist = listOf(song),
        playlistName = playlistName
    )

    viewModelScope.launch {
        repository.getRadioQueue(song.id, limit = 10).onSuccess { radioSongs ->

            // IDs of songs already visible in search results
            val searchResultIds = fallbackPlaylist
                .map { it.id }
                .toSet()

            // Keep only genuinely different songs
            val recommendations = radioSongs
                .filter { it.id != song.id }
                .filterNot { it.id in searchResultIds }
                .distinctBy { it.id }

            if (recommendations.isNotEmpty()) {
                playerManager.replaceUpcomingQueue(
                    currentSong = song,
                    newQueue = recommendations
                )
            }
        }
    }
     }
     
    fun openCollection(item: SectionItem) {
        val current = _uiState.value
        if (current !is SearchUiState.Success) return

        _uiState.value = current.copy(
            selectedCollection = CollectionDetailState(item = item, isLoading = true)
        )

        viewModelScope.launch {
            val result = when (item.type) {
                SectionItemType.ALBUM -> repository.getAlbumDetails(item.id).map { it.songs }
                SectionItemType.PLAYLIST, SectionItemType.CHART -> repository.getPlaylistDetails(item.id).map { it.songs }
            }

            val latest = _uiState.value
            if (latest is SearchUiState.Success && latest.selectedCollection?.item?.id == item.id) {
                val songs = result.getOrNull().orEmpty()
                _uiState.value = latest.copy(
                    selectedCollection = latest.selectedCollection.copy(
                        isLoading = false,
                        songs = songs,
                        errorMessage = if (songs.isEmpty()) {
                            result.exceptionOrNull()?.localizedMessage ?: "No tracks found"
                        } else null
                    )
                )
            }
        }
    }

    fun closeCollection() {
        val current = _uiState.value
        if (current is SearchUiState.Success) {
            _uiState.value = current.copy(selectedCollection = null)
        }
    }

    fun playCollection(songs: List<Song>, title: String, shuffle: Boolean = false, playerManager: PlayerManager) {
        if (songs.isEmpty()) return
        val playlist = if (shuffle) songs.shuffled() else songs
        playerManager.playSong(playlist.first(), playlist, title)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val newLiked = repository.toggleFavorite(song)
            val currentState = _uiState.value
            if (currentState is SearchUiState.Success) {
                val updatedSongs = currentState.songs.map {
                    if (it.id == song.id) it.copy(isLiked = newLiked) else it
                }
                _uiState.value = currentState.copy(songs = updatedSongs)
            }
        }
    }
}
