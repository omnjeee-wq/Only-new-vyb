package com.example.ui.components.hero

import com.example.util.MusicUtils

/**
 * Pure helpers that keep long / messy API strings from breaking the hero layout.
 */
internal data class TitleParts(val main: String, val sub: String?)

private val TrailingBracket = Regex("""^(.+?)\s*[(\[]([^()\[\]]+)[)\]]\s*$""")

/**
 * `Nakhre (From "Tum Mere Ho")` -> main = `Nakhre`, sub = `From "Tum Mere Ho"`.
 * The big script title stays short and readable, the extra info moves to a small line.
 */
internal fun splitTitle(raw: String): TitleParts {
    val title = MusicUtils.unescapeHtml(raw).trim()
    val match = TrailingBracket.find(title)
    if (match != null) {
        val main = match.groupValues[1].trim()
        val sub = match.groupValues[2].trim().trim('"', '\u201C', '\u201D').trim()
        if (main.isNotBlank()) return TitleParts(main, sub.ifBlank { null })
    }
    return TitleParts(title, null)
}

/**
 * Returns at most [limit] distinct artists plus how many were left out.
 * Splits on `,`  `&`  `;` so "A, B & C" works.
 */
internal fun parseArtists(raw: String, limit: Int = 3): Pair<List<String>, Int> {
    val all = MusicUtils.unescapeHtml(raw)
        .split(',', '&', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
    return all.take(limit) to (all.size - limit).coerceAtLeast(0)
}
