package com.example.ui.search

import com.example.domain.model.Album
import com.example.domain.model.HomeSection
import com.example.domain.model.ListeningEventType
import com.example.domain.model.Playlist
import com.example.domain.model.Song
import com.example.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchHistoryLogicTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeMusicRepository : MusicRepository {
        private val _history = MutableStateFlow<List<String>>(emptyList())
        val searchCalls = mutableListOf<String>()

        override fun getSearchHistory(): Flow<List<String>> = _history.asStateFlow()

        override suspend fun saveSearchQuery(query: String) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return
            val current = _history.value.toMutableList()
            current.remove(trimmed)
            current.add(0, trimmed)
            // Enforce maximum 10 items
            _history.value = current.take(10)
        }

        override suspend fun deleteSearchQuery(query: String) {
            val current = _history.value.toMutableList()
            current.remove(query.trim())
            _history.value = current
        }

        override suspend fun clearSearchHistory() {
            _history.value = emptyList()
        }

        override suspend fun searchSongs(query: String, limit: Int): Result<List<Song>> {
            searchCalls.add(query)
            return Result.success(emptyList())
        }

        override suspend fun getHomeSections(): Result<List<HomeSection>> = Result.success(emptyList())
        override suspend fun getRecommendationCandidates(): List<Song> = emptyList()
        override suspend fun searchAlbums(query: String, limit: Int): Result<List<Album>> = Result.success(emptyList())
        override suspend fun searchPlaylists(query: String, limit: Int): Result<List<Playlist>> = Result.success(emptyList())
        override suspend fun getAlbumDetails(albumId: String): Result<Album> = Result.failure(Exception())
        override suspend fun getPlaylistDetails(playlistId: String): Result<Playlist> = Result.failure(Exception())
        override suspend fun getArtistTopSongs(artistId: String): Result<List<Song>> = Result.success(emptyList())
        override suspend fun getSongById(songId: String): Result<Song> = Result.failure(Exception())
        override suspend fun getRadioQueue(songId: String, limit: Int, excludeIds: List<String>): Result<List<Song>> = Result.success(emptyList())
        override suspend fun getDiscoverFeed(seedIds: List<String>, languages: List<String>, excludeIds: List<String>, skippedIds: List<String>, likedIds: List<String>, limit: Int): Result<List<Song>> = Result.success(emptyList())
        override fun getFavorites(): Flow<List<Song>> = MutableStateFlow(emptyList())
        override fun isFavorite(songId: String): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun toggleFavorite(song: Song): Boolean = true
        override suspend fun recordListeningEvent(song: Song, event: ListeningEventType) {}
        override suspend fun getRecentPlayedSongs(limit: Int): List<Song> = emptyList()
        override suspend fun getQualifyingPlayCount(): Int = 0
        override suspend fun incrementQualifyingPlayCount(): Int = 1
        override suspend fun resetQualifyingPlayCount() {}
        override suspend fun getSavedHeroSongId(): String? = null
        override suspend fun saveHeroSongId(songId: String) {}

        // Library methods
        override fun getFavoriteAlbums(): Flow<List<Album>> = MutableStateFlow(emptyList())
        override fun isAlbumFavorite(albumId: String): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun toggleFavoriteAlbum(album: Album): Boolean = true
        override fun getDownloadedSongs(): Flow<List<Song>> = MutableStateFlow(emptyList())
        override fun isSongDownloaded(songId: String): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun toggleDownloadSong(song: Song): Boolean = true
        override suspend fun removeDownloadedSong(songId: String) {}
        override suspend fun clearAllDownloads() {}
        override fun getTotalDownloadedSizeBytes(): Flow<Long> = MutableStateFlow(0L)
        override fun getUserPlaylists(): Flow<List<Playlist>> = MutableStateFlow(emptyList())
        override suspend fun createUserPlaylist(title: String, description: String): String = "id"
        override suspend fun deleteUserPlaylist(playlistId: String) {}
        override fun getUserPlaylistSongs(playlistId: String): Flow<List<Song>> = MutableStateFlow(emptyList())
        override suspend fun addSongToUserPlaylist(playlistId: String, song: Song) {}
        override suspend fun removeSongFromUserPlaylist(playlistId: String, songId: String) {}
        override fun getLikedPlaylists(): Flow<List<Playlist>> = MutableStateFlow(emptyList())
        override fun isPlaylistLiked(playlistId: String): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun toggleLikedPlaylist(playlist: Playlist): Boolean = true
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun typing_does_not_save_to_search_history() = runTest(testDispatcher) {
        val fakeRepo = FakeMusicRepository()
        val viewModel = SearchViewModel(fakeRepo)

        // Typing query without pressing enter
        viewModel.onQueryChange("Arij")
        viewModel.onQueryChange("Arijit")
        advanceUntilIdle()

        // History must be empty because user has not hit Enter
        assertTrue(fakeRepo.getSearchHistory().first().isEmpty())
        assertTrue(fakeRepo.searchCalls.isEmpty())
    }

    @Test
    fun pressing_enter_saves_to_search_history_and_executes_search() = runTest(testDispatcher) {
        val fakeRepo = FakeMusicRepository()
        val viewModel = SearchViewModel(fakeRepo)

        viewModel.onQueryChange("Kesariya")
        // User presses Enter
        viewModel.performSearch()
        advanceUntilIdle()

        val history = fakeRepo.getSearchHistory().first()
        assertEquals(listOf("Kesariya"), history)
        assertEquals(listOf("Kesariya"), fakeRepo.searchCalls)
    }

    @Test
    fun search_history_holds_at_most_10_items_and_deletes_oldest() = runTest(testDispatcher) {
        val fakeRepo = FakeMusicRepository()
        val viewModel = SearchViewModel(fakeRepo)

        for (i in 1..15) {
            viewModel.performSearch("Song $i")
            advanceUntilIdle()
        }

        val history = fakeRepo.getSearchHistory().first()
        // Exactly 10 items
        assertEquals(10, history.size)
        // Latest searches are at top
        assertEquals("Song 15", history.first())
        assertEquals("Song 6", history.last())
        // Old searches 1-5 have been deleted
        assertTrue(!history.contains("Song 1"))
        assertTrue(!history.contains("Song 5"))
    }

    @Test
    fun delete_individual_item_and_clear_all_history() = runTest(testDispatcher) {
        val fakeRepo = FakeMusicRepository()
        val viewModel = SearchViewModel(fakeRepo)

        viewModel.performSearch("Track A")
        viewModel.performSearch("Track B")
        viewModel.performSearch("Track C")
        advanceUntilIdle()

        assertEquals(3, fakeRepo.getSearchHistory().first().size)

        // Delete one item
        viewModel.onDeleteHistoryItem("Track B")
        advanceUntilIdle()

        val historyAfterDelete = fakeRepo.getSearchHistory().first()
        assertEquals(listOf("Track C", "Track A"), historyAfterDelete)

        // Clear all
        viewModel.onClearAllHistory()
        advanceUntilIdle()

        assertTrue(fakeRepo.getSearchHistory().first().isEmpty())
    }
}
