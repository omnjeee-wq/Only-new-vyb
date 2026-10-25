package com.example.domain.repository

import com.example.domain.model.Album
import com.example.domain.model.HomeSection
import com.example.domain.model.ListeningEventType
import com.example.domain.model.Playlist
import com.example.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun getHomeSections(): Result<List<HomeSection>>
    suspend fun getRecommendationCandidates(): List<Song>
    suspend fun searchSongs(query: String, limit: Int = 15): Result<List<Song>>
    suspend fun searchAlbums(query: String, limit: Int = 10): Result<List<Album>>
    suspend fun searchPlaylists(query: String, limit: Int = 10): Result<List<Playlist>>
    suspend fun getAlbumDetails(albumId: String): Result<Album>
    suspend fun getPlaylistDetails(playlistId: String): Result<Playlist>
    suspend fun getArtistTopSongs(artistId: String): Result<List<Song>>
    suspend fun getSongById(songId: String): Result<Song>
    suspend fun getRadioQueue(songId: String, limit: Int = 10, excludeIds: List<String> = emptyList()): Result<List<Song>>
    suspend fun getDiscoverFeed(
        seedIds: List<String>,
        languages: List<String>,
        excludeIds: List<String>,
        skippedIds: List<String>,
        likedIds: List<String>,
        limit: Int = 16
    ): Result<List<Song>>
    
    // Local persistence & user behavior
    fun getFavorites(): Flow<List<Song>>
    fun isFavorite(songId: String): Flow<Boolean>
    suspend fun toggleFavorite(song: Song): Boolean
    suspend fun recordListeningEvent(song: Song, event: ListeningEventType)
    suspend fun getRecentPlayedSongs(limit: Int = 20): List<Song>
    
    // Hero banner counter persistence (changes every 5 qualifying plays)
    suspend fun getQualifyingPlayCount(): Int
    suspend fun incrementQualifyingPlayCount(): Int
    suspend fun resetQualifyingPlayCount()
    suspend fun getSavedHeroSongId(): String?
    suspend fun saveHeroSongId(songId: String)

    // Search History persistence
    fun getSearchHistory(): Flow<List<String>>
    suspend fun saveSearchQuery(query: String)
    suspend fun deleteSearchQuery(query: String)
    suspend fun clearSearchHistory()

    // Favorite Albums ("Liked Albums")
    fun getFavoriteAlbums(): Flow<List<Album>>
    fun isAlbumFavorite(albumId: String): Flow<Boolean>
    suspend fun toggleFavoriteAlbum(album: Album): Boolean

    // Downloads
    fun getDownloadedSongs(): Flow<List<Song>>
    fun isSongDownloaded(songId: String): Flow<Boolean>
    suspend fun toggleDownloadSong(song: Song): Boolean
    suspend fun removeDownloadedSong(songId: String)
    suspend fun clearAllDownloads()
    fun getTotalDownloadedSizeBytes(): Flow<Long>

    // User Playlists ("My Playlists")
    fun getUserPlaylists(): Flow<List<Playlist>>
    suspend fun createUserPlaylist(title: String, description: String = ""): String
    suspend fun deleteUserPlaylist(playlistId: String)
    fun getUserPlaylistSongs(playlistId: String): Flow<List<Song>>
    suspend fun addSongToUserPlaylist(playlistId: String, song: Song)
    suspend fun removeSongFromUserPlaylist(playlistId: String, songId: String)

    // Liked Playlists ("Liked Playlists")
    fun getLikedPlaylists(): Flow<List<Playlist>>
    fun isPlaylistLiked(playlistId: String): Flow<Boolean>
    suspend fun toggleLikedPlaylist(playlist: Playlist): Boolean
}
