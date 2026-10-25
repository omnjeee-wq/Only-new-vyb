package com.example.ui.library

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryLogicTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeLibraryRepository : MusicRepository {
        val likedSongs = MutableStateFlow<List<Song>>(emptyList())
        val likedAlbums = MutableStateFlow<List<Album>>(emptyList())
        val downloadedSongs = MutableStateFlow<List<Song>>(emptyList())
        val myPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
        val playlistSongs = mutableMapOf<String, MutableStateFlow<List<Song>>>()
        val likedPlaylists = MutableStateFlow<List<Playlist>>(emptyList())

        override fun getFavorites(): Flow<List<Song>> = likedSongs.asStateFlow()
        override fun isFavorite(songId: String): Flow<Boolean> = MutableStateFlow(likedSongs.value.any { it.id == songId })
        override suspend fun toggleFavorite(song: Song): Boolean {
            val list = likedSongs.value.toMutableList()
            val exists = list.any { it.id == song.id }
            if (exists) list.removeAll { it.id == song.id } else list.add(song)
            likedSongs.value = list
            return !exists
        }

        override fun getFavoriteAlbums(): Flow<List<Album>> = likedAlbums.asStateFlow()
        override fun isAlbumFavorite(albumId: String): Flow<Boolean> = MutableStateFlow(likedAlbums.value.any { it.id == albumId })
        override suspend fun toggleFavoriteAlbum(album: Album): Boolean {
            val list = likedAlbums.value.toMutableList()
            val exists = list.any { it.id == album.id }
            if (exists) list.removeAll { it.id == album.id } else list.add(album)
            likedAlbums.value = list
            return !exists
        }

        override fun getDownloadedSongs(): Flow<List<Song>> = downloadedSongs.asStateFlow()
        override fun isSongDownloaded(songId: String): Flow<Boolean> = MutableStateFlow(downloadedSongs.value.any { it.id == songId })
        override suspend fun toggleDownloadSong(song: Song): Boolean {
            val list = downloadedSongs.value.toMutableList()
            val exists = list.any { it.id == song.id }
            if (exists) list.removeAll { it.id == song.id } else list.add(song)
            downloadedSongs.value = list
            return !exists
        }
        override suspend fun removeDownloadedSong(songId: String) {
            downloadedSongs.value = downloadedSongs.value.filterNot { it.id == songId }
        }
        override suspend fun clearAllDownloads() {
            downloadedSongs.value = emptyList()
        }
        override fun getTotalDownloadedSizeBytes(): Flow<Long> = MutableStateFlow(downloadedSongs.value.size * 3_500_000L)

        // My Playlists
        override fun getUserPlaylists(): Flow<List<Playlist>> = myPlaylists.asStateFlow()
        override suspend fun createUserPlaylist(title: String, description: String): String {
            val id = UUID.randomUUID().toString()
            val newPlaylist = Playlist(id = id, title = title, description = description, isUserCreated = true)
            myPlaylists.value = listOf(newPlaylist) + myPlaylists.value
            playlistSongs[id] = MutableStateFlow(emptyList())
            return id
        }
        override suspend fun deleteUserPlaylist(playlistId: String) {
            myPlaylists.value = myPlaylists.value.filterNot { it.id == playlistId }
            playlistSongs.remove(playlistId)
        }
        override fun getUserPlaylistSongs(playlistId: String): Flow<List<Song>> {
            return playlistSongs.getOrPut(playlistId) { MutableStateFlow(emptyList()) }.asStateFlow()
        }
        override suspend fun addSongToUserPlaylist(playlistId: String, song: Song) {
            val flow = playlistSongs.getOrPut(playlistId) { MutableStateFlow(emptyList()) }
            if (!flow.value.any { it.id == song.id }) {
                flow.value = flow.value + song
            }
        }
        override suspend fun removeSongFromUserPlaylist(playlistId: String, songId: String) {
            val flow = playlistSongs.getOrPut(playlistId) { MutableStateFlow(emptyList()) }
            flow.value = flow.value.filterNot { it.id == songId }
        }

        // Liked Playlists
        override fun getLikedPlaylists(): Flow<List<Playlist>> = likedPlaylists.asStateFlow()
        override fun isPlaylistLiked(playlistId: String): Flow<Boolean> = MutableStateFlow(likedPlaylists.value.any { it.id == playlistId })
        override suspend fun toggleLikedPlaylist(playlist: Playlist): Boolean {
            val list = likedPlaylists.value.toMutableList()
            val exists = list.any { it.id == playlist.id }
            if (exists) list.removeAll { it.id == playlist.id } else list.add(playlist)
            likedPlaylists.value = list
            return !exists
        }

        override suspend fun getHomeSections(): Result<List<HomeSection>> = Result.success(emptyList())
        override suspend fun getRecommendationCandidates(): List<Song> = emptyList()
        override suspend fun searchSongs(query: String, limit: Int): Result<List<Song>> = Result.success(emptyList())
        override suspend fun searchAlbums(query: String, limit: Int): Result<List<Album>> = Result.success(emptyList())
        override suspend fun searchPlaylists(query: String, limit: Int): Result<List<Playlist>> = Result.success(emptyList())
        override suspend fun getAlbumDetails(albumId: String): Result<Album> = Result.failure(Exception())
        override suspend fun getPlaylistDetails(playlistId: String): Result<Playlist> = Result.failure(Exception())
        override suspend fun getArtistTopSongs(artistId: String): Result<List<Song>> = Result.success(emptyList())
        override suspend fun getSongById(songId: String): Result<Song> = Result.failure(Exception())
        override suspend fun getRadioQueue(songId: String, limit: Int, excludeIds: List<String>): Result<List<Song>> = Result.success(emptyList())
        override suspend fun getDiscoverFeed(seedIds: List<String>, languages: List<String>, excludeIds: List<String>, skippedIds: List<String>, likedIds: List<String>, limit: Int): Result<List<Song>> = Result.success(emptyList())
        override suspend fun recordListeningEvent(song: Song, event: ListeningEventType) {}
        override suspend fun getRecentPlayedSongs(limit: Int): List<Song> = emptyList()
        override suspend fun getQualifyingPlayCount(): Int = 0
        override suspend fun incrementQualifyingPlayCount(): Int = 1
        override suspend fun resetQualifyingPlayCount() {}
        override suspend fun getSavedHeroSongId(): String? = null
        override suspend fun saveHeroSongId(songId: String) {}
        override fun getSearchHistory(): Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun saveSearchQuery(query: String) {}
        override suspend fun deleteSearchQuery(query: String) {}
        override suspend fun clearSearchHistory() {}
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
    fun create_user_playlist_and_add_remove_songs() = runTest(testDispatcher) {
        val repo = FakeLibraryRepository()

        // 1. Create a personal playlist
        val playlistId = repo.createUserPlaylist("Late Night Vibes", "Chill acoustic and lofi")
        advanceUntilIdle()

        val playlists = repo.getUserPlaylists().first()
        assertEquals(1, playlists.size)
        assertEquals("Late Night Vibes", playlists.first().title)
        assertTrue(playlists.first().isUserCreated)

        // 2. Add songs into the playlist
        val song1 = Song(id = "s1", title = "Kesariya", artist = "Arijit Singh")
        val song2 = Song(id = "s2", title = "Apna Bana Le", artist = "Arijit Singh")

        repo.addSongToUserPlaylist(playlistId, song1)
        repo.addSongToUserPlaylist(playlistId, song2)
        advanceUntilIdle()

        val songsInPlaylist = repo.getUserPlaylistSongs(playlistId).first()
        assertEquals(2, songsInPlaylist.size)
        assertEquals("Kesariya", songsInPlaylist[0].title)
        assertEquals("Apna Bana Le", songsInPlaylist[1].title)

        // 3. Remove a song
        repo.removeSongFromUserPlaylist(playlistId, "s1")
        advanceUntilIdle()

        val songsAfterRemoval = repo.getUserPlaylistSongs(playlistId).first()
        assertEquals(1, songsAfterRemoval.size)
        assertEquals("Apna Bana Le", songsAfterRemoval.first().title)

        // 4. Delete playlist
        repo.deleteUserPlaylist(playlistId)
        advanceUntilIdle()

        assertTrue(repo.getUserPlaylists().first().isEmpty())
    }

    @Test
    fun liked_albums_and_liked_playlists_persistence() = runTest(testDispatcher) {
        val repo = FakeLibraryRepository()

        // Like album
        val album = Album(id = "alb1", title = "Rockstar", artist = "A.R. Rahman")
        repo.toggleFavoriteAlbum(album)
        advanceUntilIdle()

        val albums = repo.getFavoriteAlbums().first()
        assertEquals(1, albums.size)
        assertEquals("Rockstar", albums.first().title)

        // Toggle unlike album
        repo.toggleFavoriteAlbum(album)
        advanceUntilIdle()
        assertTrue(repo.getFavoriteAlbums().first().isEmpty())

        // Like playlist
        val playlist = Playlist(id = "pl1", title = "Top Bollywood Hits", subtitle = "Trending")
        repo.toggleLikedPlaylist(playlist)
        advanceUntilIdle()

        val likedPls = repo.getLikedPlaylists().first()
        assertEquals(1, likedPls.size)
        assertEquals("Top Bollywood Hits", likedPls.first().title)
    }

    @Test
    fun downloads_and_offline_cache() = runTest(testDispatcher) {
        val repo = FakeLibraryRepository()
        val song = Song(id = "s10", title = "Chaleya", artist = "Arijit Singh")

        repo.toggleDownloadSong(song)
        advanceUntilIdle()

        val downloads = repo.getDownloadedSongs().first()
        assertEquals(1, downloads.size)
        assertEquals("Chaleya", downloads.first().title)

        repo.clearAllDownloads()
        advanceUntilIdle()
        assertTrue(repo.getDownloadedSongs().first().isEmpty())
    }
}
