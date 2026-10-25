package com.example.domain.recommendation

import com.example.data.local.AppPreferenceEntity
import com.example.data.local.SongDao
import com.example.data.local.SongHistoryEntity
import com.example.domain.model.Song
import com.example.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp

/**
 * Taste-based engine for the Home hero banner and "Recommend For You".
 *
 * Flow:
 *  1. buildTasteProfile()  -> user ki recent history se favourite languages + seed songs
 *  2. fetchPersonalPool()  -> backend /discover se taste-based fresh candidates
 *  3. selectHeroTrack()    -> pool ke top me se best taste match (har 5 qualifying plays pe badalta hai)
 *  4. generateRecommendations() -> diversified list (har 3 qualifying plays pe refresh)
 *
 * Pehle ka loop isliye tha ki pool hamesha wahi 40 "Trending Hindi / Top Bollywood" songs the,
 * engine sirf unhe re-rank karta tha. Ab pool khud user ke taste se banta hai.
 */
class RecommendationEngine(
    private val repository: MusicRepository,
    private val songDao: SongDao
) {

    private data class TasteProfile(
        val languages: List<String>,
        val seeds: List<String>,
        val liked: List<String>,
        val skipped: List<String>,
        val exclude: List<String>
    )

    private suspend fun buildTasteProfile(): TasteProfile {
        val history = songDao.getRecentHistory(120)
        val now = System.currentTimeMillis()
        val langScore = mutableMapOf<String, Double>()
        val songScores = mutableListOf<Pair<SongHistoryEntity, Double>>()

        history.forEachIndexed { index, h ->
            val ageDays = (now - h.lastPlayedTimestamp) / 86_400_000.0
            // recent plays zyada matter karte hain, last 6 plays ko extra weight (current mood)
            val recency = exp(-ageDays / 10.0) * (if (index < 6) 1.6 else 1.0)
            val affinity = h.completedCount * 1.0 +
                (h.playCount - 1).coerceAtLeast(0) * 0.3 +
                (if (h.isLiked) 3.0 else 0.0) -
                h.skipCount * 1.2
            val weight = affinity * recency

            val lang = h.language.trim().lowercase()
            if (lang.isNotEmpty()) {
                langScore[lang] = (langScore[lang] ?: 0.0) + weight
            }
            songScores.add(h to weight)
        }

        val languages = langScore.entries
            .filter { it.value > 0.2 }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(2)

        val seeds = songScores
            .filter { it.second > 0.5 }
            .sortedByDescending { it.second }
            .map { it.first.songId }
            .take(5)

        val liked = history.filter { it.isLiked }.map { it.songId }.take(5)
        val skipped = history
            .filter { it.skipCount > 0 && it.skipCount > it.completedCount }
            .map { it.songId }
            .take(10)

        // Pehle dikhaye gaye songs + recent history dobara nahi aayenge (isi se feed rotate hoti hai)
        val exclude = (history.take(30).map { it.songId } + loadShownIds()).distinct()

        return TasteProfile(languages, seeds, liked, skipped, exclude)
    }

    private suspend fun loadShownIds(): List<String> =
        songDao.getPreference(KEY_SHOWN_IDS)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    /** Hero + recs me jo songs dikhe unhe yaad rakho, taaki agli refresh me naye aayein. */
    suspend fun rememberShown(songs: List<Song>) = withContext(Dispatchers.IO) {
        val merged = (songs.map { it.id } + loadShownIds()).distinct().take(30)
        songDao.setPreference(AppPreferenceEntity(KEY_SHOWN_IDS, merged.joinToString(",")))
    }

    /**
     * Backend se user ke taste ke hisaab se fresh candidate pool. Fail ho to empty list
     * (caller purane trending pool pe fallback karta hai).
     */
    suspend fun fetchPersonalPool(limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        try {
            val profile = buildTasteProfile()
            repository.getDiscoverFeed(
                seedIds = profile.seeds,
                languages = profile.languages,
                excludeIds = profile.exclude,
                skippedIds = profile.skipped,
                likedIds = profile.liked,
                limit = limit
            ).getOrNull() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Hero banner track. Personal pool server-ranked hota hai, isliye top 10 me se
     * taste score + server rank ke hisaab se best chuno (pichla hero repeat nahi hota).
     */
    suspend fun selectHeroTrack(candidatePool: List<Song>): Song? = withContext(Dispatchers.IO) {
        if (candidatePool.isEmpty()) return@withContext null

        val history = songDao.getRecentHistory(30)
        if (history.isEmpty()) {
            // Cold start: pool ke top 3 me se random, taaki har open pe same banner na ho
            return@withContext candidatePool.take(3).random()
        }

        val top = candidatePool.take(10)
        val scored = top.mapIndexed { index, song ->
            song to (calculateBannerScore(song, history) + (top.size - index) * 0.7)
        }.sortedByDescending { it.second }

        val savedId = repository.getSavedHeroSongId()
        val candidate = scored.firstOrNull { it.first.id != savedId }?.first
            ?: scored.firstOrNull()?.first
            ?: candidatePool.firstOrNull()

        candidate?.let { repository.saveHeroSongId(it.id) }
        candidate
    }

    /**
     * "Recommend For You". personalized=true: pool pehle se taste-ranked hai, bas diversity lagao.
     * personalized=false: purana fallback (static trending pool) history se re-rank hota hai.
     */
    suspend fun generateRecommendations(
        candidatePool: List<Song>,
        limit: Int = 12,
        personalized: Boolean = false,
        excludeIds: Set<String> = emptySet()
    ): List<Song> = withContext(Dispatchers.IO) {
        val pool = candidatePool.filter { it.id !in excludeIds }
        if (pool.isEmpty()) return@withContext emptyList()

        if (personalized) {
            return@withContext applyControlledDiversity(pool, limit)
        }

        val history = songDao.getRecentHistory(50)
        val basePool = if (history.isEmpty()) {
            pool.shuffled()
        } else {
            val scored = pool.map { song -> song to calculateRecommendationScore(song, history) }
                .sortedByDescending { it.second }
                .map { it.first }
            scored.take((limit * 2.5).toInt().coerceAtLeast(limit)).shuffled()
        }
        applyControlledDiversity(basePool, limit)
    }

    private fun calculateRecommendationScore(song: Song, history: List<SongHistoryEntity>): Double {
        var score = 0.0

        val matchedSong = history.find { it.songId == song.id }
        if (matchedSong != null) {
            score += matchedSong.completedCount * 3.0
            score += (matchedSong.playCount - 1) * 1.5
            if (matchedSong.isLiked) score += 5.0
            score -= matchedSong.skipCount * 3.5
            val recencyHours = (System.currentTimeMillis() - matchedSong.lastPlayedTimestamp) / (1000.0 * 3600.0)
            if (recencyHours < 2.0) score -= 4.0
        }

        val artistPlays = history.count {
            it.artist.contains(song.artist, ignoreCase = true) ||
                (song.artistId != null && it.artistId == song.artistId)
        }
        score += artistPlays * 2.5

        if (song.language.isNotBlank()) {
            val languagePlays = history.count { it.language.equals(song.language, ignoreCase = true) }
            score += languagePlays * 1.2
        }

        return score
    }

    private fun calculateBannerScore(song: Song, history: List<SongHistoryEntity>): Double {
        val baseScore = calculateRecommendationScore(song, history)
        val artistMatches = history.filter { it.artist.contains(song.artist, ignoreCase = true) }
        val artistBonus = if (artistMatches.isNotEmpty()) 4.0 else 0.0
        return baseScore + artistBonus
    }

    /** Ek artist ke max 2-3 songs, baaki diversity ke liye. */
    private fun applyControlledDiversity(songs: List<Song>, limit: Int): List<Song> {
        val result = mutableListOf<Song>()
        val seenArtists = mutableMapOf<String, Int>()
        val maxPerArtist = if (limit > 8) 3 else 2

        for (song in songs) {
            val count = seenArtists.getOrDefault(song.artist, 0)
            if (count < maxPerArtist) {
                result.add(song)
                seenArtists[song.artist] = count + 1
            }
            if (result.size >= limit) break
        }

        if (result.size < limit) {
            for (song in songs) {
                if (!result.contains(song)) {
                    result.add(song)
                    if (result.size >= limit) break
                }
            }
        }
        return result
    }

    private companion object {
        const val KEY_SHOWN_IDS = "recs_shown_ids"
    }
}
