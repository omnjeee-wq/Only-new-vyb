package com.example.domain.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String = "",
    val albumId: String? = null,
    val durationSeconds: Int = 0,
    val year: String = "",
    val language: String = "",
    val artworkUrl: String = "",
    val streamUrl: String = "",
    val hasLyrics: Boolean = false,
    val lyricsId: String? = null,
    val isLiked: Boolean = false
)

data class Album(
    val id: String,
    val title: String,
    val artist: String = "",
    val artworkUrl: String = "",
    val year: String = "",
    val songCount: Int = 0,
    val language: String = "",
    val songs: List<Song> = emptyList(),
    val isLiked: Boolean = false
)

data class Playlist(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String = "",
    val songCount: Int = 0,
    val language: String = "",
    val songs: List<Song> = emptyList(),
    val isUserCreated: Boolean = false,
    val isLiked: Boolean = false,
    val createdTimestamp: Long = 0L,
    val description: String = ""
)

data class Artist(
    val id: String,
    val name: String,
    val role: String = "Artist",
    val artworkUrl: String = "",
    val type: String = "artist"
)

enum class SectionItemType {
    ALBUM,
    PLAYLIST,
    CHART
}

data class SectionItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String = "",
    val type: SectionItemType = SectionItemType.ALBUM,
    val songCount: Int = 0,
    val year: String = "",
    val language: String = ""
)

data class HomeSection(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val itemType: SectionItemType = SectionItemType.ALBUM,
    val items: List<SectionItem> = emptyList(),
    val songs: List<Song> = emptyList()
)

enum class ListeningEventType {
    PLAY_STARTED,
    PLAY_30_SECONDS,
    PLAY_50_PERCENT,
    PLAY_COMPLETED,
    SKIPPED,
    LIKED,
    UNLIKED,
    ADDED_TO_PLAYLIST,
    REPLAYED
}
