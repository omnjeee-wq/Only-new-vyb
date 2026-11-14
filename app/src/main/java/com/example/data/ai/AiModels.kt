package com.example.data.ai

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AiSongDto(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val language: String = "",
    val durationSeconds: Int = 0,
    val bpm: Double? = null,
    val key: String? = null,
    val mood: String? = null
)

@JsonClass(generateAdapter = true)
data class AiAssistantRequest(val message: String, val context: Map<String, String> = emptyMap())
@JsonClass(generateAdapter = true)
data class AiMoodSearchRequest(val prompt: String, val language: String? = null, val limit: Int = 30)
@JsonClass(generateAdapter = true)
data class AiLyricsRequest(val lyrics: String, val question: String = "Explain the meaning, mood and themes briefly.")
@JsonClass(generateAdapter = true)
data class AiRecommendationRequest(
    val history: List<AiSongDto>,
    val candidates: List<AiSongDto>,
    val limit: Int = 12
)
@JsonClass(generateAdapter = true)
data class AiDjRequest(
    val songs: List<AiSongDto>,
    val startingSongId: String? = null,
    val preferredMood: String? = null
)
@JsonClass(generateAdapter = true)
data class AiResponse(
    val text: String? = null,
    val query: String? = null,
    val tags: List<String> = emptyList(),
    val songIds: List<String> = emptyList(),
    val bpm: Double? = null,
    val musicalKey: String? = null,
    val mood: String? = null,
    val energy: Double? = null,
    val danceability: Double? = null,
    val provider: String? = null
)
