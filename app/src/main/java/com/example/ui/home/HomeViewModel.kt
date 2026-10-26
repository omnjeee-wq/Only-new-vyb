package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Album
import com.example.domain.model.HomeSection
import com.example.domain.model.Playlist
import com.example.domain.model.SectionItem
import com.example.domain.model.SectionItemType
import com.example.domain.model.Song
import com.example.domain.recommendation.PlaylistFeedEngine
import com.example.domain.recommendation.RecommendationEngine
import com.example.domain.repository.MusicRepository
import com.example.player.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionDetailState(
    val item: SectionItem,
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val errorMessage: String? = null
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val heroSong: Song? = null,
    val recommendations: List<Song> = emptyList(),
    val candidatePool: List<Song> = emptyList(),
    val poolIsPersonal: Boolean = false,
    val sections: List<HomeSection> = emptyList(),
    val selectedCollection: CollectionDetailState? = null,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: MusicRepository,
    private val recommendationEngine: RecommendationEngine,
    private val playlistFeedEngine: PlaylistFeedEngine,
    private val playerManager: PlayerManager
) : ViewModel() {

    private var feedKey: String? = null

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
        observeHeroRefreshSignal()
        observeRecsRefreshSignal()
    }

    private fun observeRecsRefreshSignal() {
        viewModelScope.launch {
            playerManager.recsRefreshSignal.collect {
                refreshRecommendations()
            }
        }
    }

    /** Har 3 qualifying plays pe: taste profile se fresh pool, hero same rehta hai. */
    private fun refreshRecommendations() {
        viewModelScope.launch(Dispatchers.Default) {
            val personal = recommendationEngine.fetchPersonalPool()
            val state = _uiState.value
            val usePersonal = personal.size >= 6
            val pool = if (usePersonal) personal else state.candidatePool
            if (pool.isEmpty()) return@launch

            val recs = recommendationEngine.generateRecommendations(
                candidatePool = pool,
                personalized = usePersonal,
                excludeIds = setOfNotNull(state.heroSong?.id)
            )
            if (usePersonal) recommendationEngine.rememberShown(recs)
            _uiState.update {
                it.copy(recommendations = recs, candidatePool = pool, poolIsPersonal = usePersonal)
            }
        }
    }

    private fun observeHeroRefreshSignal() {
        viewModelScope.launch {
            playerManager.heroRefreshSignal.collect {
                refreshRecommendationsAndHero()
                refreshPlaylistsIfTasteChanged()
            }
        }
    }

    /** Taste-based playlist feed. Fail/empty ho to purana fixed list fallback. */
    private suspend fun loadPlaylistSections(): Result<List<HomeSection>> {
        val key = playlistFeedEngine.currentKey()
        val feed = playlistFeedEngine.buildFeed()
        if (feed.isNotEmpty()) {
            feedKey = key
            return Result.success(feed)
        }
        return repository.getHomeSections()
    }

    /**
     * Har 5 qualifying plays pe check hota hai (per-song nahi). Feed tabhi badalta hai jab
     * din/din-ka-hissa/top language/top artist badle, warna kuch nahi hota.
     */
    private fun refreshPlaylistsIfTasteChanged() {
        viewModelScope.launch {
            val key = playlistFeedEngine.currentKey()
            if (key == feedKey) return@launch
            val feed = playlistFeedEngine.buildFeed()
            if (feed.isNotEmpty()) {
                feedKey = key
                _uiState.update { it.copy(sections = feed) }
            }
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val candidatesDeferred = async { repository.getRecommendationCandidates() }
            val sectionsDeferred = async { loadPlaylistSections() }
            val personalDeferred = async { recommendationEngine.fetchPersonalPool() }

            val candidates = candidatesDeferred.await()
            val sectionsResult = sectionsDeferred.await()
            val personal = personalDeferred.await()
            val usePersonal = personal.size >= 6

            if (sectionsResult.isSuccess || candidates.isNotEmpty() || usePersonal) {
                val sections = sectionsResult.getOrNull() ?: emptyList()
                val candidatePool = when {
                    usePersonal -> personal
                    candidates.isNotEmpty() -> candidates
                    else -> sections.flatMap { it.songs }.distinctBy { it.id }
                }

                val hero = recommendationEngine.selectHeroTrack(candidatePool)
                val recommendations = recommendationEngine.generateRecommendations(
                    candidatePool = candidatePool,
                    personalized = usePersonal,
                    excludeIds = setOfNotNull(hero?.id)
                )
                if (usePersonal) {
                    recommendationEngine.rememberShown(listOfNotNull(hero) + recommendations)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        heroSong = hero,
                        recommendations = recommendations,
                        candidatePool = candidatePool,
                        poolIsPersonal = usePersonal,
                        sections = sections
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = sectionsResult.exceptionOrNull()?.localizedMessage ?: "Failed to load music"
                    )
                }
            }
        }
    }

    /** Har 5 qualifying plays pe: taste profile se naya hero + fresh recommendations. */
    private fun refreshRecommendationsAndHero() {
        viewModelScope.launch(Dispatchers.Default) {
            val personal = recommendationEngine.fetchPersonalPool()
            val state = _uiState.value
            val usePersonal = personal.size >= 6
            val pool = if (usePersonal) personal else state.candidatePool
            if (pool.isEmpty()) return@launch

            val newHero = recommendationEngine.selectHeroTrack(pool)
            val newRecs = recommendationEngine.generateRecommendations(
                candidatePool = pool,
                personalized = usePersonal,
                excludeIds = setOfNotNull(newHero?.id)
            )
            if (usePersonal) {
                recommendationEngine.rememberShown(listOfNotNull(newHero) + newRecs)
            }
            _uiState.update {
                it.copy(
                    heroSong = newHero,
                    recommendations = newRecs,
                    candidatePool = pool,
                    poolIsPersonal = usePersonal
                )
            }
        }
    }

    fun openCollection(item: SectionItem) {
        _uiState.update {
            it.copy(selectedCollection = CollectionDetailState(item = item, isLoading = true))
        }

        viewModelScope.launch {
            val result = when (item.type) {
                SectionItemType.ALBUM -> repository.getAlbumDetails(item.id).map { it.songs }
                SectionItemType.PLAYLIST, SectionItemType.CHART -> repository.getPlaylistDetails(item.id).map { it.songs }
            }

            _uiState.update { state ->
                if (state.selectedCollection?.item?.id == item.id) {
                    val songs = result.getOrNull() ?: emptyList()
                    state.copy(
                        selectedCollection = state.selectedCollection.copy(
                            isLoading = false,
                            songs = songs,
                            errorMessage = if (songs.isEmpty()) result.exceptionOrNull()?.localizedMessage ?: "No tracks found" else null
                        )
                    )
                } else {
                    state
                }
            }
        }
    }

    fun closeCollection() {
        _uiState.update { it.copy(selectedCollection = null) }
    }

    fun playCollection(item: SectionItem, shuffle: Boolean = false) {
        viewModelScope.launch {
            val result = when (item.type) {
                SectionItemType.ALBUM -> repository.getAlbumDetails(item.id).map { it.songs }
                SectionItemType.PLAYLIST, SectionItemType.CHART -> repository.getPlaylistDetails(item.id).map { it.songs }
            }
            val songs = result.getOrNull() ?: emptyList()
            if (songs.isNotEmpty()) {
                val playlist = if (shuffle) songs.shuffled() else songs
                val firstSong = playlist.first()
                playerManager.playSong(firstSong, playlist, item.title)
            }
        }
    }

    fun playSong(song: Song, playlist: List<Song> = listOf(song), playlistName: String = "Home") {
        playerManager.playSong(song, playlist, playlistName)
    }

    fun playSongWithRadio(song: Song, fallbackPlaylist: List<Song>, playlistName: String) {
    playerManager.playSong(song, fallbackPlaylist, playlistName)
    viewModelScope.launch {
        repository.getRadioQueue(song.id).onSuccess { radioSongs ->
            if (radioSongs.isNotEmpty()) {
                val fullQueue = listOf(song) + radioSongs.filterNot { it.id == song.id }
                playerManager.replaceUpcomingQueue(currentSong = song, newQueue = fullQueue)
            }
        }
    }
    }

    fun toggleHeroPlayPause() {
        val hero = _uiState.value.heroSong ?: return
        val currentSong = playerManager.playerState.value.currentSong
        if (currentSong?.id == hero.id) {
            playerManager.togglePlayPause()
        } else {
            val allSongs = _uiState.value.recommendations.ifEmpty { listOf(hero) }
            playerManager.playSong(hero, allSongs, "Personalised Vibe")
        }
    }

    fun addToQueue(song: Song) {
        playerManager.addToQueue(song)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song)
        }
    }

    fun toggleFavoriteCollection(item: SectionItem) {
        viewModelScope.launch {
            when (item.type) {
                SectionItemType.ALBUM -> {
                    repository.toggleFavoriteAlbum(
                        Album(
                            id = item.id,
                            title = item.title,
                            artist = item.subtitle,
                            artworkUrl = item.artworkUrl,
                            year = item.year,
                            songCount = item.songCount
                        )
                    )
                }
                SectionItemType.PLAYLIST, SectionItemType.CHART -> {
                    repository.toggleLikedPlaylist(
                        Playlist(
                            id = item.id,
                            title = item.title,
                            subtitle = item.subtitle,
                            artworkUrl = item.artworkUrl,
                            songCount = item.songCount
                        )
                    )
                }
            }
        }
    }
}
