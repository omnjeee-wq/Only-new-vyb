package com.example.ui.search

import com.example.domain.model.Album
import com.example.domain.model.Artist
import com.example.domain.model.HomeSection
import com.example.domain.model.ListeningEventType
import com.example.domain.model.Playlist
import com.example.domain.model.SectionItem
import com.example.domain.model.Song
import com.example.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class SearchCategoryFilteringTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class TestMusicRepository : MusicRepository {
        var searchSongCalls = 0
        var searchAlbumCalls = 0
        var searchPlaylistCalls = 0
        var searchArtistCalls = 0

        override suspend fun searchSongs(query: String, limit: Int): Result<List<Song>> {
            searchSongCalls++
            return Result.success(
                listOf(
                    Song(id = "s1", title = "Tum Hi Ho", artist = "Arijit Singh"),
                    Song(id = "s2", title = "Kesariya", artist = "Arijit Singh")
                )
            )
        }

        override suspend fun searchAlbums(query: String, limit: Int): Result<List<Album>> {
            searchAlbumCalls++
            return Result.success(
                listOf(
                    Album(id = "a1", title = "Aashiqui 2", artist = "Mithoon, Ankit Tiwari"),
                    Album(id = "a2", title = "Brahmastra", artist = "Pritam")
                )
            )
        }

        override suspend fun searchPlaylists(query: String, limit: Int): Result<List<Playlist>> {
            searchPlaylistCalls++
            return Result.success(
                listOf(
                    Playlist(id = "p1", title = "Best of Arijit", songCount = 25)
                )
            )
        }

        override suspend fun searchArtists(query: String, limit: Int): Result<List<Artist>> {
            searchArtistCalls++
            return Result.success(
                listOf(
                    Artist(id = "art1", name = "Arijit Singh", role = "Artist")
                )
            )
        }

        override fun getSearchHistory(): Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun saveSearchQuery(query: String) {}
        override suspend fun deleteSearchQuery(query: String) {}
        override suspend fun clearSearchHistory() {}
        override suspend fun getHomeSections(): Result<List<HomeSection>> = Result.success(emptyList())
        override suspend fun getRecommendationCandidates(): List<Song> = emptyList()
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
        override suspend fun incrementQualifyingPlayCount(): Int = 0
        override suspend fun resetQualifyingPlayCount() {}
        override suspend fun getSavedHeroSongId(): String? = null
        override suspend fun saveHeroSongId(songId: String) {}
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

    @Test
    fun performSearch_fetches_all_categories_and_defaults_to_ALL() = runTest(testDispatcher) {
        val repo = TestMusicRepository()
        val viewModel = SearchViewModel(repo)

        viewModel.performSearch("Arijit")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Success)
        val success = state as SearchUiState.Success
        assertEquals(SearchCategory.ALL, success.selectedCategory)
        assertEquals(2, success.songs.size)
        assertEquals(2, success.albums.size)
        assertEquals(1, success.playlists.size)
        assertEquals(1, success.artists.size)
        assertEquals(1, repo.searchSongCalls)
        assertEquals(1, repo.searchAlbumCalls)
        assertEquals(1, repo.searchPlaylistCalls)
        assertEquals(1, repo.searchArtistCalls)
    }

    @Test
    fun switching_categories_does_not_trigger_additional_API_calls() = runTest(testDispatcher) {
        val repo = TestMusicRepository()
        val viewModel = SearchViewModel(repo)

        viewModel.performSearch("Arijit")
        advanceUntilIdle()

        assertEquals(1, repo.searchSongCalls)
        assertEquals(1, repo.searchAlbumCalls)

        // Switch to Songs
        viewModel.selectCategory(SearchCategory.SONGS)
        advanceUntilIdle()
        assertEquals(SearchCategory.SONGS, (viewModel.uiState.value as SearchUiState.Success).selectedCategory)

        // Switch to Albums
        viewModel.selectCategory(SearchCategory.ALBUMS)
        advanceUntilIdle()
        assertEquals(SearchCategory.ALBUMS, (viewModel.uiState.value as SearchUiState.Success).selectedCategory)

        // Switch to Playlists
        viewModel.selectCategory(SearchCategory.PLAYLISTS)
        advanceUntilIdle()
        assertEquals(SearchCategory.PLAYLISTS, (viewModel.uiState.value as SearchUiState.Success).selectedCategory)

        // Switch to Artists
        viewModel.selectCategory(SearchCategory.ARTISTS)
        advanceUntilIdle()
        assertEquals(SearchCategory.ARTISTS, (viewModel.uiState.value as SearchUiState.Success).selectedCategory)

        // Switch back to All
        viewModel.selectCategory(SearchCategory.ALL)
        advanceUntilIdle()
        assertEquals(SearchCategory.ALL, (viewModel.uiState.value as SearchUiState.Success).selectedCategory)

        // Verifying no extra API calls were made!
        assertEquals(1, repo.searchSongCalls)
        assertEquals(1, repo.searchAlbumCalls)
        assertEquals(1, repo.searchPlaylistCalls)
        assertEquals(1, repo.searchArtistCalls)
    }

    @Test
    fun new_query_invalidates_previous_and_fetches_fresh_data() = runTest(testDispatcher) {
        val repo = TestMusicRepository()
        val viewModel = SearchViewModel(repo)

        viewModel.performSearch("Arijit")
        advanceUntilIdle()
        assertEquals(1, repo.searchSongCalls)

        viewModel.selectCategory(SearchCategory.ALBUMS)
        advanceUntilIdle()

        // Submit completely new query
        viewModel.performSearch("Pritam")
        advanceUntilIdle()

        // Fresh data fetched
        assertEquals(2, repo.searchSongCalls)
        assertEquals(2, repo.searchAlbumCalls)
        val success = viewModel.uiState.value as SearchUiState.Success
        assertEquals("Pritam", success.query)
        // Resets to ALL by default on new search
        assertEquals(SearchCategory.ALL, success.selectedCategory)
    }
}
