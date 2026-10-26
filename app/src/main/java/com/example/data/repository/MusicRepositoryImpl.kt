package com.example.data.repository

import com.example.data.local.AppPreferenceEntity
import com.example.data.local.DownloadedSongEntity
import com.example.data.local.FavoriteAlbumEntity
import com.example.data.local.FavoriteSongEntity
import com.example.data.local.LikedPlaylistEntity
import com.example.data.local.SongDao
import com.example.data.local.SongHistoryEntity
import com.example.data.local.UserPlaylistEntity
import com.example.data.local.UserPlaylistSongEntity
import com.example.data.remote.JioSaavnApiService
import com.example.domain.model.Album
import com.example.domain.model.HomeSection
import com.example.domain.model.ListeningEventType
import com.example.domain.model.Playlist
import com.example.domain.model.SectionItem
import com.example.domain.model.SectionItemType
import com.example.domain.model.Song
import com.example.domain.repository.MusicRepository
import com.example.util.MusicUtils
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.VybeApp
import com.example.data.remote.RetrofitClient
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.R

class MusicRepositoryImpl(
    private val apiService: JioSaavnApiService,
    private val songDao: SongDao,
    private val context: Context = VybeApp.instance
) : MusicRepository {

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val customUrl = songDao.getPreference("pref_backend_url")
                if (!customUrl.isNullOrBlank()) {
                    RetrofitClient.setBaseUrl(customUrl)
                }
            } catch (_: Exception) {}
        }
    }

    // In-memory cache for Home sections
    private var cachedSections: List<HomeSection>? = null
    private var lastCacheTimestamp: Long = 0
    private val cacheTtlMs = 60 * 60 * 1000L // 1 hour cache

    override suspend fun getHomeSections(): Result<List<HomeSection>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedSections != null && (now - lastCacheTimestamp < cacheTtlMs)) {
            return@withContext Result.success(cachedSections!!)
        }

        try {
            coroutineScope {
                // All home sections are now PLAYLISTS
                val newReleasesDeferred = async { fetchSectionPlaylists("New Hindi Releases", 12) }
                val topChartsDeferred = async { fetchSectionPlaylists("Top Charts", 12) }
                val editorsPicksDeferred = async { fetchSectionPlaylists("Editor's Picks", 12) }
                val genresMoodsDeferred = async { fetchSectionPlaylists("Top Bollywood Hits", 12) }
                val freshHitsDeferred = async { fetchSectionPlaylists("Fresh Hits", 12) }
                val marathiDeferred = async { fetchSectionPlaylists("Marathi Hits", 12) }
                val popHindiDeferred = async { fetchSectionPlaylists("Hindi Pop Hits", 12) }
                val moodDeferred = async { fetchSectionPlaylists("Romantic Moods", 12) }

                val sections = listOf(
                    HomeSection(
                        id = "new_releases",
                        title = "New Releases",
                        itemType = SectionItemType.PLAYLIST,
                        items = newReleasesDeferred.await()
                    ),
                    HomeSection(
                        id = "top_charts",
                        title = "Top Charts",
                        itemType = SectionItemType.PLAYLIST,
                        items = topChartsDeferred.await()
                    ),
                    HomeSection(
                        id = "editors_picks",
                        title = "Editor's Picks",
                        itemType = SectionItemType.PLAYLIST,
                        items = editorsPicksDeferred.await()
                    ),
                    HomeSection(
                        id = "genres_moods",
                        title = "Top Genres and Moods",
                        itemType = SectionItemType.PLAYLIST,
                        items = genresMoodsDeferred.await()
                    ),
                    HomeSection(
                        id = "fresh_hits",
                        title = "Fresh Hits",
                        itemType = SectionItemType.PLAYLIST,
                        items = freshHitsDeferred.await()
                    ),
                    HomeSection(
                        id = "marathi_chitrahaar",
                        title = "Marathi Chitrahaar",
                        itemType = SectionItemType.PLAYLIST,
                        items = marathiDeferred.await()
                    ),
                    HomeSection(
                        id = "pop_hindi",
                        title = "New Releases Pop - Hindi",
                        itemType = SectionItemType.PLAYLIST,
                        items = popHindiDeferred.await()
                    ),
                    HomeSection(
                        id = "your_mood",
                        title = "Made For Your Mood",
                        itemType = SectionItemType.PLAYLIST,
                        items = moodDeferred.await()
                    )
                )

                cachedSections = sections
                lastCacheTimestamp = now
                Result.success(sections)
            }
        } catch (e: Exception) {
            // Return cached if available, otherwise fail gracefully
            cachedSections?.let { return@withContext Result.success(it) }
            Result.failure(e)
        }
    }

    override suspend fun getRecommendationCandidates(): List<Song> = withContext(Dispatchers.IO) {
        try {
            val quality = songDao.getPreference("pref_streaming_quality") ?: "High (320 kbps)"
            coroutineScope {
                val trendingDeferred = async {
                    try {
                        val resp = apiService.searchSongs(query = "Trending Hindi", limit = 20)
                        resp.data?.results?.map { MusicUtils.toDomainSong(it, preferredQuality = quality) } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                val topDeferred = async {
                    try {
                        val resp = apiService.searchSongs(query = "Top Bollywood", limit = 20)
                        resp.data?.results?.map { MusicUtils.toDomainSong(it, preferredQuality = quality) } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                val list1 = trendingDeferred.await()
                val list2 = topDeferred.await()
                (list1 + list2).distinctBy { it.id }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchSectionAlbums(query: String, limit: Int): List<SectionItem> {
        return try {
            val response = apiService.searchAlbums(query = query, limit = limit)
            val results = response.data?.results ?: emptyList()
            results.map {
                val primaryArtists = it.artists?.primary?.mapNotNull { a -> a.name } ?: emptyList()
                val artistName = if (primaryArtists.isNotEmpty()) {
                    primaryArtists.joinToString(", ") { a -> MusicUtils.unescapeHtml(a) }
                } else {
                    it.artists?.all?.mapNotNull { a -> a.name }?.joinToString(", ") { a -> MusicUtils.unescapeHtml(a) }
                        ?: it.year?.let { y -> "Album • $y" }
                        ?: "Album"
                }
                SectionItem(
                    id = it.id,
                    title = MusicUtils.unescapeHtml(it.name),
                    subtitle = artistName,
                    artworkUrl = MusicUtils.selectBestImage(it.image),
                    type = SectionItemType.ALBUM,
                    songCount = it.songCount?.toIntOrNull() ?: 0,
                    year = it.year.orEmpty(),
                    language = it.language.orEmpty()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getPlaylistItems(query: String, limit: Int, page: Int): List<SectionItem> =
        withContext(Dispatchers.IO) { fetchSectionPlaylists(query, limit, page = page) }

    private suspend fun fetchSectionPlaylists(query: String, limit: Int, isChart: Boolean = false, page: Int = 1): List<SectionItem> {
        return try {
            val response = apiService.searchPlaylists(query = query, page = page, limit = limit)
            val results = response.data?.results ?: emptyList()
            results.map {
                val count = it.songCount?.toIntOrNull() ?: 0
                val countStr = if (count > 0) "$count Songs" else "Playlist"
                val subtitle = if (isChart) "Chart • $countStr" else "Playlist • $countStr"
                SectionItem(
                    id = it.id,
                    title = MusicUtils.unescapeHtml(it.name),
                    subtitle = subtitle,
                    artworkUrl = MusicUtils.selectBestImage(it.image),
                    type = if (isChart) SectionItemType.CHART else SectionItemType.PLAYLIST,
                    songCount = count,
                    language = it.language.orEmpty()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getAlbumDetails(albumId: String): Result<Album> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getAlbumById(albumId)
            val apiAlbum = response.data
            if (apiAlbum != null) {
                val quality = songDao.getPreference("pref_streaming_quality") ?: "High (320 kbps)"
                Result.success(MusicUtils.toDomainAlbum(apiAlbum, preferredQuality = quality))
            } else {
                Result.failure(NoSuchElementException("Album not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPlaylistDetails(playlistId: String): Result<Playlist> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPlaylistById(playlistId, limit = 50)
            val apiPlaylist = response.data
            if (apiPlaylist != null) {
                val quality = songDao.getPreference("pref_streaming_quality") ?: "High (320 kbps)"
                Result.success(MusicUtils.toDomainPlaylist(apiPlaylist, preferredQuality = quality))
            } else {
                Result.failure(NoSuchElementException("Playlist not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchSongs(query: String, limit: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchSongs(query = query, limit = limit)
            val quality = songDao.getPreference("pref_streaming_quality") ?: "High (320 kbps)"
            val songs = response.data?.results?.map { MusicUtils.toDomainSong(it, preferredQuality = quality) } ?: emptyList()
            Result.success(songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val queueUrl = "https://sdk.omnjeee.workers.dev/queue"
    private val discoverUrl = "https://sdk.omnjeee.workers.dev/discover"

    override suspend fun getRadioQueue(
        songId: String,
        limit: Int,
        excludeIds: List<String>
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val history = songDao.getRecentHistory(60)

            // Taste signals from listening history
            val likedIds = history.filter { it.isLiked }.map { it.songId }
            val goodIds = history
                .filter { it.completedCount > 0 && it.completedCount >= it.skipCount }
                .map { it.songId }
            val skippedIds = history
                .filter { it.skipCount > 0 && it.skipCount > it.completedCount }
                .map { it.songId }

            // Seeds: current song first, then liked / completed songs (not just "recent")
            val seeds = (listOf(songId) + likedIds.take(2) + goodIds.take(2))
                .distinct()
                .take(4)
                .joinToString(",")

            // Exclude: recent history + everything already in the queue
            val exclude = (history.take(50).map { it.songId } + excludeIds.takeLast(40) + songId)
                .distinct()
                .joinToString(",")

            val quality = songDao.getPreference("pref_streaming_quality") ?: "High (320 kbps)"
            val response = apiService.getQueue(
                url = queueUrl,
                seeds = seeds,
                exclude = exclude,
                limit = limit,
                skipped = skippedIds.take(10).joinToString(","),
                liked = likedIds.take(5).joinToString(",")
            )
            val songs = response.data?.map { MusicUtils.toDomainSong(it, preferredQuality = quality) } ?: emptyList()

            if (songs.isNotEmpty()) {
                Result.success(songs)
            } else {
                // backup: purana suggestions endpoint
                val old = apiService.getSongSuggestions(id = songId, limit = limit)
                Result.success(old.data?.map { MusicUtils.toDomainSong(it, preferredQuality = quality) } ?: emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDiscoverFeed(
        seedIds: List<String>,
        languages: List<String>,
        excludeIds: List<String>,
        skippedIds: List<String>,
        likedIds: List<String>,
        limit: Int
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val quality = songDao.getPreference("pref_streaming_quality") ?: "High (320 kbps)"
            val response = apiService.getDiscover(
                url = discoverUrl,
                seeds = seedIds.joinToString(","),
                langs = languages.joinToString(","),
                exclude = excludeIds.takeLast(70).joinToString(","),
                limit = limit,
                skipped = skippedIds.joinToString(","),
                liked = likedIds.joinToString(",")
            )
            Result.success(
                response.data?.map { MusicUtils.toDomainSong(it, preferredQuality = quality) } ?: emptyList()
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchAlbums(query: String, limit: Int): Result<List<Album>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchAlbums(query = query, limit = limit)
            val albums = response.data?.results?.map {
                Album(
                    id = it.id,
                    title = MusicUtils.unescapeHtml(it.name),
                    year = it.year.orEmpty(),
                    artworkUrl = MusicUtils.selectBestImage(it.image)
                )
            } ?: emptyList()
            Result.success(albums)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchPlaylists(query: String, limit: Int): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchPlaylists(query = query, limit = limit)
            val playlists = response.data?.results?.map {
                Playlist(
                    id = it.id,
                    title = MusicUtils.unescapeHtml(it.name),
                    artworkUrl = MusicUtils.selectBestImage(it.image),
                    language = it.language.orEmpty()
                )
            } ?: emptyList()
            Result.success(playlists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getArtistTopSongs(artistId: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getArtistById(id = artistId, songCount = 15)
            val songs = response.data?.topSongs?.map { MusicUtils.toDomainSong(it) } ?: emptyList()
            Result.success(songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSongById(songId: String): Result<Song> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getSongById(songId)
            val apiSong = response.data?.firstOrNull()
            if (apiSong != null) {
                val isFav = songDao.isFavorite(songId)
                val quality = songDao.getPreference("pref_streaming_quality") ?: "High (320 kbps)"
                Result.success(MusicUtils.toDomainSong(apiSong, isLiked = isFav, preferredQuality = quality))
            } else {
                Result.failure(NoSuchElementException("Song not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getFavorites(): Flow<List<Song>> {
        return songDao.getAllFavorites().map { list ->
            list.map {
                Song(
                    id = it.songId,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    artworkUrl = it.artworkUrl,
                    streamUrl = it.streamUrl,
                    durationSeconds = it.durationSeconds,
                    language = it.language,
                    isLiked = true
                )
            }
        }
    }

    override fun isFavorite(songId: String): Flow<Boolean> {
        return songDao.isFavoriteFlow(songId)
    }

    override suspend fun toggleFavorite(song: Song): Boolean = withContext(Dispatchers.IO) {
        val currentlyFav = songDao.isFavorite(song.id)
        if (currentlyFav) {
            songDao.removeFavorite(song.id)
            songDao.updateLikedInHistory(song.id, false)
            false
        } else {
            songDao.addFavorite(
                FavoriteSongEntity(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    artworkUrl = song.artworkUrl,
                    streamUrl = song.streamUrl,
                    durationSeconds = song.durationSeconds,
                    language = song.language
                )
            )
            songDao.updateLikedInHistory(song.id, true)
            true
        }
    }

    override suspend fun recordListeningEvent(song: Song, event: ListeningEventType) {
        withContext(Dispatchers.IO) {
            val existing = songDao.getHistoryBySongId(song.id)
            when (event) {
                ListeningEventType.PLAY_STARTED -> {
                    val isFav = songDao.isFavorite(song.id)
                    val entity = existing?.copy(
                        playCount = existing.playCount + 1,
                        lastPlayedTimestamp = System.currentTimeMillis(),
                        isLiked = isFav
                    ) ?: SongHistoryEntity(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        artistId = song.artistId,
                        album = song.album,
                        artworkUrl = song.artworkUrl,
                        streamUrl = song.streamUrl,
                        durationSeconds = song.durationSeconds,
                        language = song.language,
                        playCount = 1,
                        isLiked = isFav
                    )
                    songDao.insertOrUpdateHistory(entity)
                }
                ListeningEventType.PLAY_COMPLETED -> {
                    songDao.incrementCompleted(song.id)
                }
                ListeningEventType.SKIPPED -> {
                    songDao.incrementSkip(song.id)
                }
                ListeningEventType.LIKED -> {
                    toggleFavorite(song)
                }
                ListeningEventType.UNLIKED -> {
                    toggleFavorite(song)
                }
                ListeningEventType.REPLAYED -> {
                    existing?.let {
                        songDao.insertOrUpdateHistory(it.copy(playCount = it.playCount + 1))
                    }
                }
                else -> {}
            }
        }
    }

    override suspend fun getRecentPlayedSongs(limit: Int): List<Song> = withContext(Dispatchers.IO) {
        songDao.getRecentHistory(limit).map {
            Song(
                id = it.songId,
                title = it.title,
                artist = it.artist,
                artistId = it.artistId,
                album = it.album,
                artworkUrl = it.artworkUrl,
                streamUrl = it.streamUrl,
                durationSeconds = it.durationSeconds,
                language = it.language,
                isLiked = it.isLiked
            )
        }
    }

    override suspend fun getQualifyingPlayCount(): Int = withContext(Dispatchers.IO) {
        songDao.getPreference(KEY_QUALIFYING_PLAYS)?.toIntOrNull() ?: 0
    }

    override suspend fun incrementQualifyingPlayCount(): Int = withContext(Dispatchers.IO) {
        val current = getQualifyingPlayCount()
        val next = current + 1
        songDao.setPreference(AppPreferenceEntity(KEY_QUALIFYING_PLAYS, next.toString()))
        next
    }

    override suspend fun resetQualifyingPlayCount() = withContext(Dispatchers.IO) {
        songDao.setPreference(AppPreferenceEntity(KEY_QUALIFYING_PLAYS, "0"))
    }

    override suspend fun getSavedHeroSongId(): String? = withContext(Dispatchers.IO) {
        songDao.getPreference(KEY_HERO_SONG_ID)
    }

    override suspend fun saveHeroSongId(songId: String) = withContext(Dispatchers.IO) {
        songDao.setPreference(AppPreferenceEntity(KEY_HERO_SONG_ID, songId))
    }

    override fun getSearchHistory(): Flow<List<String>> = songDao.getRecentSearchQueries()

    override suspend fun saveSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            songDao.addSearchQueryAndTrim(trimmed, maxItems = 10)
        }
    }

    override suspend fun deleteSearchQuery(query: String) = withContext(Dispatchers.IO) {
        songDao.deleteSearchQuery(query.trim())
    }

    override suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        songDao.clearAllSearchHistory()
    }

    // Favorite Albums
    override fun getFavoriteAlbums(): Flow<List<Album>> {
        return songDao.getAllFavoriteAlbums().map { list ->
            list.map {
                Album(
                    id = it.albumId,
                    title = it.title,
                    artist = it.artist,
                    artworkUrl = it.artworkUrl,
                    year = it.year,
                    songCount = it.songCount,
                    isLiked = true
                )
            }
        }
    }

    override fun isAlbumFavorite(albumId: String): Flow<Boolean> {
        return songDao.isAlbumFavoriteFlow(albumId)
    }

    override suspend fun toggleFavoriteAlbum(album: Album): Boolean = withContext(Dispatchers.IO) {
        val exists = songDao.isAlbumFavorite(album.id)
        if (exists) {
            songDao.removeFavoriteAlbum(album.id)
            false
        } else {
            songDao.addFavoriteAlbum(
                FavoriteAlbumEntity(
                    albumId = album.id,
                    title = album.title,
                    artist = album.artist,
                    artworkUrl = album.artworkUrl,
                    year = album.year,
                    songCount = album.songCount
                )
            )
            true
        }
    }

    // Downloads
    private fun getAudioFile(songId: String): File {
        val dir = File(context.filesDir, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$songId.m4a")
    }

    private val downloadHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override fun getDownloadedSongs(): Flow<List<Song>> {
        return songDao.getAllDownloadedSongs().map { list ->
            list.filter { entity ->
                val file = getAudioFile(entity.songId)
                file.exists() && file.length() > 0
            }.map {
                Song(
                    id = it.songId,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    artworkUrl = it.artworkUrl,
                    streamUrl = it.streamUrl,
                    durationSeconds = it.durationSeconds
                )
            }
        }
    }

    override fun isSongDownloaded(songId: String): Flow<Boolean> {
        return songDao.isSongDownloadedFlow(songId).map { inDb ->
            inDb && getAudioFile(songId).let { it.exists() && it.length() > 0 }
        }
    }

    override suspend fun toggleDownloadSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        val targetFile = getAudioFile(song.id)
        val isDownloadedInDb = songDao.isSongDownloaded(song.id)

        // If currently downloaded in DB and the local file exists, remove the download
        if (isDownloadedInDb && targetFile.exists() && targetFile.length() > 0) {
            targetFile.delete()
            songDao.removeDownloadedSong(song.id)
            return@withContext false
        }

        // If the app already has a valid downloaded file, do not unnecessarily download it again
        if (targetFile.exists() && targetFile.length() > 0) {
            val sizeBytes = targetFile.length()
            songDao.addDownloadedSong(
                DownloadedSongEntity(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    artworkUrl = song.artworkUrl,
                    streamUrl = Uri.fromFile(targetFile).toString(),
                    durationSeconds = song.durationSeconds,
                    sizeBytes = sizeBytes,
                    downloadTimestamp = System.currentTimeMillis()
                )
            )
            return@withContext true
        }

        // If DB had a stale record but no file, clean it up
        if (isDownloadedInDb) {
            songDao.removeDownloadedSong(song.id)
        }

        // Check Wi-Fi only restriction
        val wifiOnly = songDao.getPreference("pref_download_wifi_only")?.toBooleanStrictOrNull() ?: false
        if (wifiOnly) {
            val networkMonitor = com.example.util.NetworkMonitor(context)
            if (!networkMonitor.isWifiConnected()) {
                Log.w("MusicRepository", "Download blocked: Wi-Fi only download is enabled and device is not on Wi-Fi")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download paused: Connect to Wi-Fi to download", Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }
        }

        // 1. Resolve audio stream URL with preferred download quality
        val downloadQuality = songDao.getPreference("pref_download_quality") ?: "High (320 kbps)"
        var audioUrl = ""
        val response = try {
            apiService.getSongById(song.id)
        } catch (_: Exception) {
            null
        }
        val apiSong = response?.data?.firstOrNull()
        if (apiSong != null) {
            audioUrl = MusicUtils.selectBestAudioUrl(apiSong.downloadUrl, downloadQuality)
        }

        if (audioUrl.isBlank() || audioUrl.startsWith("file://") || audioUrl.startsWith("/")) {
            audioUrl = MusicUtils.applyQualityToUrl(song.streamUrl, downloadQuality)
        }

        if (audioUrl.isBlank() || audioUrl.startsWith("file://") || audioUrl.startsWith("/")) {
            return@withContext false
        }

        if (audioUrl.startsWith("http://")) {
            audioUrl = audioUrl.replaceFirst("http://", "https://")
        }

        // 2. Start actual audio file download into a temporary file
        val tempFile = File(targetFile.parentFile, "${song.id}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        try {
            val request = Request.Builder()
                .url(audioUrl)
                .header("User-Agent", "VYBE-Android/1.0")
                .build()

            val response = downloadHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                tempFile.delete()
                return@withContext false
            }

            val body = response.body ?: run {
                response.close()
                tempFile.delete()
                return@withContext false
            }

            tempFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            response.close()

            // 3. Verify downloaded file exists and is non-empty
            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val movedSuccessfully = try {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                    targetFile.exists() && targetFile.length() > 0
                } catch (e: Exception) {
                    tempFile.renameTo(targetFile) && targetFile.exists() && targetFile.length() > 0
                }

                if (!movedSuccessfully) {
                    tempFile.delete()
                    targetFile.delete()
                    return@withContext false
                }

                // 4. Save/update downloaded-song record in Room ONLY AFTER file exists
                val sizeBytes = targetFile.length()
                songDao.addDownloadedSong(
                    DownloadedSongEntity(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        artworkUrl = song.artworkUrl,
                        streamUrl = Uri.fromFile(targetFile).toString(),
                        durationSeconds = song.durationSeconds,
                        sizeBytes = sizeBytes,
                        downloadTimestamp = System.currentTimeMillis()
                    )
                )

                val notifyEnabled = songDao.getPreference("pref_download_notifications")?.toBooleanStrictOrNull() ?: true
                if (notifyEnabled) {
                    try {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        if (nm != null) {
                            val channelId = "vybe_download_channel"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val channel = NotificationChannel(
                                    channelId,
                                    "Downloads",
                                    NotificationManager.IMPORTANCE_LOW
                                )
                                nm.createNotificationChannel(channel)
                            }
                            val notif = NotificationCompat.Builder(context, channelId)
                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                .setContentTitle("Download Complete")
                                .setContentText("${song.title} is ready for offline playback")
                                .setPriority(NotificationCompat.PRIORITY_LOW)
                                .setAutoCancel(true)
                                .build()
                            nm.notify(song.id.hashCode(), notif)
                        }
                    } catch (_: Exception) {}
                }

                return@withContext true
            } else {
                tempFile.delete()
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to download song ${song.id}: ${e.message}")
            tempFile.delete()
            if (targetFile.exists() && targetFile.length() == 0L) {
                targetFile.delete()
            }
            return@withContext false
        }
    }

    override suspend fun removeDownloadedSong(songId: String) = withContext(Dispatchers.IO) {
        val file = getAudioFile(songId)
        if (file.exists()) {
            file.delete()
        }
        songDao.removeDownloadedSong(songId)
    }

    override suspend fun clearAllDownloads() = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "downloads")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
        songDao.clearAllDownloadedSongs()
    }

    override fun getTotalDownloadedSizeBytes(): Flow<Long> {
        return songDao.getTotalDownloadedSizeBytes()
    }

    // User Playlists ("My Playlists")
    override fun getUserPlaylists(): Flow<List<Playlist>> {
        return songDao.getAllUserPlaylists().map { list ->
            list.map {
                Playlist(
                    id = it.playlistId,
                    title = it.title,
                    description = it.description,
                    artworkUrl = it.artworkUrl,
                    isUserCreated = true,
                    createdTimestamp = it.createdTimestamp
                )
            }
        }
    }

    override suspend fun createUserPlaylist(title: String, description: String): String = withContext(Dispatchers.IO) {
        val newId = UUID.randomUUID().toString()
        songDao.insertUserPlaylist(
            UserPlaylistEntity(
                playlistId = newId,
                title = title.trim(),
                description = description.trim(),
                createdTimestamp = System.currentTimeMillis()
            )
        )
        newId
    }

    override suspend fun deleteUserPlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        songDao.deleteUserPlaylistAndSongs(playlistId)
    }

    override fun getUserPlaylistSongs(playlistId: String): Flow<List<Song>> {
        return songDao.getSongsForPlaylistFlow(playlistId).map { list ->
            list.map {
                Song(
                    id = it.songId,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    artworkUrl = it.artworkUrl,
                    streamUrl = it.streamUrl,
                    durationSeconds = it.durationSeconds
                )
            }
        }
    }

    override suspend fun addSongToUserPlaylist(playlistId: String, song: Song) = withContext(Dispatchers.IO) {
        songDao.addSongToPlaylist(
            UserPlaylistSongEntity(
                playlistId = playlistId,
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                artworkUrl = song.artworkUrl,
                streamUrl = song.streamUrl,
                durationSeconds = song.durationSeconds
            )
        )
    }

    override suspend fun removeSongFromUserPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        songDao.removeSongFromPlaylist(playlistId, songId)
    }

    // Liked Playlists ("Liked Playlists")
    override fun getLikedPlaylists(): Flow<List<Playlist>> {
        return songDao.getAllLikedPlaylists().map { list ->
            list.map {
                Playlist(
                    id = it.playlistId,
                    title = it.title,
                    subtitle = it.subtitle,
                    artworkUrl = it.artworkUrl,
                    songCount = it.songCount,
                    isLiked = true
                )
            }
        }
    }

    override fun isPlaylistLiked(playlistId: String): Flow<Boolean> {
        return songDao.isPlaylistLikedFlow(playlistId)
    }

    override suspend fun toggleLikedPlaylist(playlist: Playlist): Boolean = withContext(Dispatchers.IO) {
        val exists = songDao.isPlaylistLiked(playlist.id)
        if (exists) {
            songDao.removeLikedPlaylist(playlist.id)
            false
        } else {
            songDao.addLikedPlaylist(
                LikedPlaylistEntity(
                    playlistId = playlist.id,
                    title = playlist.title,
                    subtitle = playlist.subtitle,
                    artworkUrl = playlist.artworkUrl,
                    songCount = playlist.songCount
                )
            )
            true
        }
    }

    companion object {
        private const val KEY_QUALIFYING_PLAYS = "qualifying_plays_count"
        private const val KEY_HERO_SONG_ID = "hero_song_id"
    }
}
