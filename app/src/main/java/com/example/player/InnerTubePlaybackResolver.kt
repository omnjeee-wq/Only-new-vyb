package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.sabr.ExperimentalSabrApi
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.response.SearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.util.Locale

/**
 * Resolves VYBE's internal placeholder URI to a fresh YouTube Music audio URL.
 *
 * The app continues to use JioSaavn for its catalog/search metadata. At playback time,
 * this class searches YouTube Music using the song title + artist and asks InnerTubeX
 * for a direct/HLS audio stream. SABR and bounded-range streams are deliberately
 * excluded here because this integration uses Media3's regular HTTP/HLS data sources.
 */
@OptIn(ExperimentalSabrApi::class)
object InnerTubePlaybackResolver {
    private const val TAG = "VybeInnerTube"
    const val PLACEHOLDER_HOST = "vybe.invalid"

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    private val innerTube: InnerTube by lazy { InnerTube(httpClient) }

    private val extractor: InnerTubeExtractor by lazy {
        val configStore = RemotePlayerConfigStore(
            httpClient = httpClient,
            repository = PlayerConfigRepository.disabled()
        )
        val cipherService = YouTubeCipherService(httpClient, configStore)
        val configParser = YtConfigParserImpl(
            httpClient = httpClient,
            innerTube = innerTube,
            remotePlayerConfigStore = configStore,
            cipherService = cipherService
        )
        InnerTubeExtractor(
            configParser = configParser,
            cipherService = cipherService,
            innerTube = innerTube
        )
    }

    data class ResolvedAudio(
        val url: String,
        val headers: Map<String, String>
    )

    fun createMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        // Keep file/content/android.resource playback working for downloaded tracks.
        val upstreamFactory = DefaultDataSource.Factory(context, httpFactory)

        val resolvingFactory = ResolvingDataSource.Factory(
            upstreamFactory,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
                    if (uri.host != PLACEHOLDER_HOST) return dataSpec

                    val title = uri.getQueryParameter("title").orEmpty()
                    val artist = uri.getQueryParameter("artist").orEmpty()
                    val quality = uri.getQueryParameter("quality").orEmpty()

                    if (title.isBlank()) {
                        throw IOException("VYBE track title is missing")
                    }

                    val resolved = try {
                        runBlocking(Dispatchers.IO) {
                            resolve(title, artist, quality)
                        }
                    } catch (error: Exception) {
                        Log.e(TAG, "InnerTubeX stream resolution failed (${error::class.java.simpleName})")
                        null
                    } ?: throw IOException("No compatible YouTube Music audio stream was returned")

                    return dataSpec.buildUpon()
                        .setUri(Uri.parse(resolved.url))
                        .setHttpRequestHeaders(resolved.headers)
                        .build()
                }
            }
        )

        return DefaultMediaSourceFactory(resolvingFactory)
    }

    private suspend fun resolve(
        title: String,
        artist: String,
        quality: String
    ): ResolvedAudio? {
        val query = listOf(title, artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(180)

        val response = innerTube.search(
            client = YouTubeClient.WEB_REMIX,
            query = query
        )
        if (response.status.value !in 200..299) return null

        val searchResponse = response.body<SearchResponse>()
        val candidates = mutableListOf<SearchCandidate>()
        searchResponse.contents?.sectionListRenderer?.let {
            collectMusicCandidates(it, candidates)
        }
        searchResponse.contents?.tabbedSearchResultsRenderer?.let {
            collectMusicCandidates(it, candidates)
        }

        val normalizedTitle = normalize(title)
        val normalizedArtist = normalize(artist)
        val best = candidates
            .distinctBy { it.videoId }
            .maxByOrNull { candidate ->
                val candidateTitle = normalize(candidate.title)
                val candidateText = normalize(candidate.allText)
                val titleScore = tokenOverlap(normalizedTitle, candidateTitle)
                val artistScore = if (normalizedArtist.isBlank()) 0.0
                    else tokenOverlap(normalizedArtist, candidateText)
                titleScore * 0.8 + artistScore * 0.2
            } ?: return null

        // Avoid silently playing a completely unrelated search result.
        if (tokenOverlap(normalizedTitle, normalize(best.title)) < 0.30) return null

        val selectedQuality = when {
            quality.lowercase(Locale.ROOT).contains("low") ||
                quality.contains("96") || quality.contains("48") -> AudioQuality.LOW
            quality.lowercase(Locale.ROOT).contains("auto") -> AudioQuality.AUTO
            else -> AudioQuality.HIGH
        }

        val stream = extractor.extract(
            videoId = best.videoId,
            hints = ContentHints().withStreamCapabilities(
                allowHls = true,
                allowSabr = false,
                allowBoundedRange = false
            ),
            audioQuality = selectedQuality
        ) ?: return null

        // Media3's standard HTTP/HLS pipeline cannot consume InnerTubeX's SABR
        // bootstrap or custom bounded-range transport. Fail closed for those results.
        if (stream.sabrBootstrap != null || stream.useRangeChunks || stream.requireBoundedRange) {
            return null
        }
        if (stream.audioUrl.isBlank()) return null

        return ResolvedAudio(
            url = stream.audioUrl,
            headers = stream.headers + mapOf(
                "Referer" to "https://music.youtube.com/",
                "Origin" to "https://music.youtube.com"
            )
        )
    }

    private data class SearchCandidate(
        val videoId: String,
        val title: String,
        val allText: String
    )

    private fun collectMusicCandidates(
        element: JsonElement,
        output: MutableList<SearchCandidate>
    ) {
        when (element) {
            is JsonObject -> {
                val renderer = element["musicResponsiveListItemRenderer"] as? JsonObject
                if (renderer != null) {
                    val videoId = findStringByKey(renderer, "videoId")
                    if (!videoId.isNullOrBlank()) {
                        val flexText = collectText(renderer["flexColumns"])
                        val titleText = collectText(renderer["title"])
                        val subtitleText = collectText(renderer["subtitle"])
                        val allText = listOf(titleText, flexText, subtitleText)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                        val title = titleText.ifBlank {
                            flexText.split(" | ").firstOrNull().orEmpty()
                        }
                        if (title.isNotBlank()) {
                            output += SearchCandidate(videoId, title, allText)
                        }
                    }
                }
                element.values.forEach { collectMusicCandidates(it, output) }
            }
            is JsonArray -> element.forEach { collectMusicCandidates(it, output) }
            else -> Unit
        }
    }

    private fun findStringByKey(element: JsonElement, wantedKey: String): String? {
        return when (element) {
            is JsonObject -> {
                val direct = (element[wantedKey] as? JsonPrimitive)?.contentOrNull
                direct ?: element.values.firstNotNullOfOrNull {
                    findStringByKey(it, wantedKey)
                }
            }
            is JsonArray -> element.firstNotNullOfOrNull { findStringByKey(it, wantedKey) }
            else -> null
        }
    }

    private fun collectText(element: JsonElement?): String {
        if (element == null) return ""
        val pieces = mutableListOf<String>()
        fun visit(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    val text = (node["text"] as? JsonPrimitive)?.contentOrNull
                    if (!text.isNullOrBlank()) pieces += text
                    else node.values.forEach(::visit)
                    // Runs commonly hold the visible title/artist text.
                    (node["runs"] as? JsonArray)?.forEach(::visit)
                }
                is JsonArray -> node.forEach(::visit)
                else -> Unit
            }
        }
        visit(element)
        return pieces.distinct().joinToString(" ")
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun tokenOverlap(expected: String, actual: String): Double {
        val left = expected.split(' ').filter { it.length > 1 }.toSet()
        val right = actual.split(' ').filter { it.length > 1 }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size.toDouble() / left.size.toDouble()
    }
}
