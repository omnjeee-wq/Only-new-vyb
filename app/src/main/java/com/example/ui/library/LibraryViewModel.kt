package com.example.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Album
import com.example.domain.model.Playlist
import com.example.domain.model.Song
import com.example.domain.repository.MusicRepository
import com.example.domain.smartshuffle.MoodCategory
import com.example.player.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab {
    ALL,
    LIKED_SONGS,
    ALBUMS,
    DOWNLOADS,
    PLAYLISTS
}

enum class PlaylistSubTab {
    MY_PLAYLISTS,
    LIKED_PLAYLISTS
}

data class LibraryUiState(
    val currentTab: LibraryTab = LibraryTab.ALL,
    val playlistSubTab: PlaylistSubTab = PlaylistSubTab.MY_PLAYLISTS,
    val searchQuery: String = "",
    val isCreatePlaylistDialogOpen: Boolean = false,
    val activeUserPlaylist: Playlist? = null,
    val activeUserPlaylistSongs: List<Song> = emptyList(),
    val activeAlbumDetail: Album? = null,
    val activeAlbumSongs: List<Song> = emptyList(),
    val isAddSongsSheetOpen: Boolean = false,
    val isDetailLoading: Boolean = false,
    val searchSongCandidates: List<Song> = emptyList(),
    val isSearchingCandidates: Boolean = false,
    val selectedMoodFilter: MoodCategory? = null
)

class LibraryViewModel(
    private val repository: MusicRepository,
    private val playerManager: PlayerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // 1. Liked Songs
    val likedSongs: StateFlow<List<Song>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. Liked Albums
    val likedAlbums: StateFlow<List<Album>> = repository.getFavoriteAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Downloads
    val downloadedSongs: StateFlow<List<Song>> = repository.getDownloadedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalDownloadedBytes: StateFlow<Long> = repository.getTotalDownloadedSizeBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // 4. Playlists: My Playlists & Liked Playlists
    val myPlaylists: StateFlow<List<Playlist>> = repository.getUserPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedPlaylists: StateFlow<List<Playlist>> = repository.getLikedPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Pre-seed a welcome "My Favorites" playlist if user has no playlists yet
        viewModelScope.launch {
            // Observe once to seed starter liked playlist or album if empty so user has immediate rich content
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(currentTab = tab, searchQuery = "") }
    }

    fun selectPlaylistSubTab(subTab: PlaylistSubTab) {
        _uiState.update { it.copy(playlistSubTab = subTab) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openCreatePlaylistDialog() {
        _uiState.update { it.copy(isCreatePlaylistDialogOpen = true) }
    }

    fun closeCreatePlaylistDialog() {
        _uiState.update { it.copy(isCreatePlaylistDialogOpen = false) }
    }

    fun createPlaylist(name: String, description: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.createUserPlaylist(name.trim(), description.trim())
            closeCreatePlaylistDialog()
            // Optionally open this playlist immediately
            openUserPlaylist(Playlist(id = newId, title = name.trim(), description = description.trim(), isUserCreated = true))
        }
    }

    fun deleteUserPlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deleteUserPlaylist(playlistId)
            if (_uiState.value.activeUserPlaylist?.id == playlistId) {
                closeUserPlaylist()
            }
        }
    }

    fun openUserPlaylist(playlist: Playlist) {
        _uiState.update { it.copy(activeUserPlaylist = playlist, isDetailLoading = true) }
        viewModelScope.launch {
            repository.getUserPlaylistSongs(playlist.id).collect { songs ->
                _uiState.update {
                    if (it.activeUserPlaylist?.id == playlist.id) {
                        it.copy(activeUserPlaylistSongs = songs, isDetailLoading = false)
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun closeUserPlaylist() {
        _uiState.update { it.copy(activeUserPlaylist = null, activeUserPlaylistSongs = emptyList(), isDetailLoading = false) }
    }

    fun openLikedAlbum(album: Album) {
        _uiState.update { it.copy(activeAlbumDetail = album, isDetailLoading = true) }
        viewModelScope.launch {
            val result = repository.getAlbumDetails(album.id)
            result.onSuccess { fullAlbum ->
                _uiState.update {
                    it.copy(
                        activeAlbumDetail = fullAlbum,
                        activeAlbumSongs = fullAlbum.songs,
                        isDetailLoading = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isDetailLoading = false) }
            }
        }
    }

    fun closeLikedAlbum() {
        _uiState.update { it.copy(activeAlbumDetail = null, activeAlbumSongs = emptyList()) }
    }

    fun openAddSongsSheet() {
        _uiState.update { it.copy(isAddSongsSheetOpen = true, searchSongCandidates = likedSongs.value) }
    }

    fun closeAddSongsSheet() {
        _uiState.update { it.copy(isAddSongsSheetOpen = false, searchSongCandidates = emptyList()) }
    }

    fun searchSongsForPlaylist(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchSongCandidates = likedSongs.value, isSearchingCandidates = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingCandidates = true) }
            val result = repository.searchSongs(query, limit = 15)
            result.onSuccess { songs ->
                _uiState.update { it.copy(searchSongCandidates = songs, isSearchingCandidates = false) }
            }.onFailure {
                _uiState.update { it.copy(isSearchingCandidates = false) }
            }
        }
    }

    fun addSongToActivePlaylist(song: Song) {
        val playlist = _uiState.value.activeUserPlaylist ?: return
        viewModelScope.launch {
            repository.addSongToUserPlaylist(playlist.id, song)
        }
    }

    fun removeSongFromActivePlaylist(songId: String) {
        val playlist = _uiState.value.activeUserPlaylist ?: return
        viewModelScope.launch {
            repository.removeSongFromUserPlaylist(playlist.id, songId)
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song)
        }
    }

    fun toggleFavoriteAlbum(album: Album) {
        viewModelScope.launch {
            repository.toggleFavoriteAlbum(album)
            if (_uiState.value.activeAlbumDetail?.id == album.id) {
                closeLikedAlbum()
            }
        }
    }

    fun toggleLikedPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.toggleLikedPlaylist(playlist)
        }
    }

    fun toggleDownloadSong(song: Song) {
        viewModelScope.launch {
            repository.toggleDownloadSong(song)
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            repository.clearAllDownloads()
        }
    }

    fun playSong(song: Song, list: List<Song>, sourceName: String) {
        playerManager.playSong(song, list, sourceName)
    }

    fun playAll(list: List<Song>, sourceName: String) {
        if (list.isNotEmpty()) {
            playerManager.playSong(list.first(), list, sourceName)
        }
    }

    fun shufflePlay(list: List<Song>, sourceName: String) {
        if (list.isNotEmpty()) {
            val shuffled = list.shuffled()
            playerManager.playSong(shuffled.first(), shuffled, sourceName)
        }
    }

    fun selectMoodFilter(mood: MoodCategory?) {
        _uiState.update { it.copy(selectedMoodFilter = mood) }
    }

    fun smartShufflePlay(list: List<Song>, sourceName: String, preferredMood: MoodCategory? = null) {
        playerManager.smartShufflePlay(list, sourceName, preferredMood)
    }
}
