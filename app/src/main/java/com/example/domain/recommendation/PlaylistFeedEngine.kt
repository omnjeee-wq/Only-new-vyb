package com.example.domain.recommendation

import com.example.data.local.SongDao
import com.example.domain.model.HomeSection
import com.example.domain.model.SectionItem
import com.example.domain.model.SectionItemType
import com.example.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.exp
import kotlin.random.Random

/**
 * Home ke playlist sections (New Releases, Top Charts, Mood, ...) ka taste-based feed.
 *
 * JioSaavn jaisa behaviour, per-song nahi:
 *  - Feed ek "key" se bandta hai: aaj ki date + din ka hissa (morning/afternoon/evening/night)
 *    + user ki top languages + top artists.
 *  - Key same => same feed (stable, scroll karte waqt kuch nahi badalta).
 *  - Key badli (naya din, naya din-ka-hissa, ya taste ke top artist/language badle) => naya feed.
 *  - Sections ke queries user ki language/artists se bante hain, aur har din ki seed se
 *    genre/page/order badalte hain, isliye roz fresh lagta hai.
 */
class PlaylistFeedEngine(
    private val repository: MusicRepository,
    private val songDao: SongDao
) {

    private data class Taste(val languages: List<String>, val artists: List<String>)

    private data class Spec(
        val id: String,
        val title: String,
        val queries: List<String>,
        val keepOrder: Boolean = false,
        val randomPage: Boolean = true
    )

    private suspend fun readTaste(): Taste = withContext(Dispatchers.IO) {
        val history = try { songDao.getRecentHistory(120) } catch (_: Exception) { emptyList() }
        val now = System.currentTimeMillis()
        val langScore = mutableMapOf<String, Double>()
        val artistScore = mutableMapOf<String, Double>()

        history.forEachIndexed { index, h ->
            val ageDays = (now - h.lastPlayedTimestamp) / 86_400_000.0
            val recency = exp(-ageDays / 10.0) * (if (index < 6) 1.6 else 1.0)
            val affinity = h.completedCount * 1.0 +
                (h.playCount - 1).coerceAtLeast(0) * 0.3 +
                (if (h.isLiked) 3.0 else 0.0) -
                h.skipCount * 1.2
            val weight = affinity * recency
            if (weight <= 0.0) return@forEachIndexed

            val lang = h.language.trim().lowercase()
            if (lang.isNotEmpty()) langScore[lang] = (langScore[lang] ?: 0.0) + weight

            h.artist.split(",", "&")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("Various Artists", ignoreCase = true) }
                .take(2) // sirf primary artists
                .forEach { a -> artistScore[a] = (artistScore[a] ?: 0.0) + weight }
        }

        Taste(
            languages = langScore.entries.filter { it.value > 0.2 }
                .sortedByDescending { it.value }.map { it.key }.take(2),
            artists = artistScore.entries.filter { it.value > 0.8 }
                .sortedByDescending { it.value }.map { it.key }.take(2)
        )
    }

    private fun dayPart(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "morning"
            in 11..16 -> "afternoon"
            in 17..21 -> "evening"
            else -> "night"
        }
    }

    private fun today(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun keyOf(taste: Taste) =
        "${today()}|${dayPart()}|${taste.languages.joinToString(",")}|${taste.artists.joinToString(",")}"

    /** Cheap (no network). ViewModel isse check karta hai ki feed purana ho gaya ya nahi. */
    suspend fun currentKey(): String = keyOf(readTaste())

    suspend fun buildFeed(): List<HomeSection> {
        val taste = readTaste()
        val key = keyOf(taste)
        val rnd = Random(key.hashCode())

        val l1 = (taste.languages.getOrNull(0) ?: "hindi").capitalizeWord()
        val l2 = taste.languages.getOrNull(1)?.capitalizeWord()

        val specs = mutableListOf<Spec>()
        specs += Spec(
            "new_releases", "New Releases",
            listOf("New $l1 Hits", "New $l1 Songs", "New Hindi Releases"),
            keepOrder = true, randomPage = false
        )
        specs += Spec(
            "top_charts", "Top Charts",
            listOf("Top $l1 Charts", "Top 50 $l1", "Top Charts"),
            keepOrder = true, randomPage = false
        )
        taste.artists.getOrNull(0)?.let { a ->
            specs += Spec("artist_0", "Because you like $a",
                listOf("$a Hits", "Best of $a", "This is $a"))
        }
        val (moodTitle, moodQueries) = when (dayPart()) {
            "morning" -> "Good Morning Vibes" to listOf("$l1 Morning Songs", "Good Morning")
            "afternoon" -> "Afternoon Energy" to listOf("$l1 Energy Songs", "Feel Good Hits")
            "evening" -> "Evening Chill" to listOf("$l1 Chill Songs", "Evening Chill")
            else -> "Late Night Feels" to listOf("$l1 Late Night Songs", "Night Drive")
        }
        specs += Spec("mood", moodTitle, moodQueries)

        val genres = listOf("Romantic", "Party", "Sad", "Indie", "Rap", "Retro", "Workout", "Dance")
            .shuffled(rnd)
        specs += Spec("genre_0", "${genres[0]} Hits", listOf("$l1 ${genres[0]} Hits", "${genres[0]} Hits"))

        taste.artists.getOrNull(1)?.let { a ->
            specs += Spec("artist_1", "More of $a",
                listOf("$a Hits", "Best of $a", "This is $a"))
        }
        l2?.let {
            specs += Spec("lang_2", "New in $it", listOf("New $it Hits", "Top $it Hits"))
        }
        specs += Spec("editors_picks", "Editor's Picks",
            listOf("$l1 Editor's Picks", "Editor's Picks"))
        specs += Spec("genre_1", "${genres[1]} Hits", listOf("$l1 ${genres[1]} Hits", "${genres[1]} Hits"))
        specs += Spec("throwback", "Throwback Classics",
            listOf("$l1 90s Hits", "$l1 Retro Classics", "Old Is Gold"))

        // Network: saare sections parallel
        val fetched: List<Pair<Spec, List<SectionItem>>> = coroutineScope {
            specs.map { spec ->
                val page = if (spec.randomPage) 1 + rnd.nextInt(2) else 1
                async { spec to fetchSpec(spec, page) }
            }.awaitAll()
        }

        // Ek playlist do sections me repeat na ho
        val seen = mutableSetOf<String>()
        val sections = mutableListOf<HomeSection>()
        for ((spec, items) in fetched) {
            val unique = items.filter { seen.add(it.id) }
            val ordered = if (spec.keepOrder) unique else unique.shuffled(rnd)
            val finalItems = ordered.take(12)
            if (finalItems.size >= 3) {
                sections += HomeSection(
                    id = spec.id,
                    title = spec.title,
                    itemType = SectionItemType.PLAYLIST,
                    items = finalItems
                )
            }
        }
        return sections
    }

    private suspend fun fetchSpec(spec: Spec, page: Int): List<SectionItem> {
        for (query in spec.queries) {
            var items = repository.getPlaylistItems(query, limit = 15, page = page)
            if (items.size < 4 && page != 1) {
                items = repository.getPlaylistItems(query, limit = 15, page = 1)
            }
            // 1-4 songs wali junk playlists hata do (0 = count unknown, rakho)
            val clean = items.filter { it.songCount == 0 || it.songCount >= 5 }
            if (clean.size >= 4) return clean
        }
        return emptyList()
    }

    private fun String.capitalizeWord(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
