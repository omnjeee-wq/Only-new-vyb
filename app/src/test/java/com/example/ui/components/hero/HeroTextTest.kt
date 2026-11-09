package com.example.ui.components.hero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroTextTest {

    @Test
    fun splitTitle_movesFromSuffixToSubtitle() {
        val parts = splitTitle("Nakhre (From \"Tum Mere Ho\")")
        assertEquals("Nakhre", parts.main)
        assertEquals("From \"Tum Mere Ho\"", parts.sub)
    }

    @Test
    fun splitTitle_plainTitleUntouched() {
        val parts = splitTitle("Wagt Lage")
        assertEquals("Wagt Lage", parts.main)
        assertNull(parts.sub)
    }

    @Test
    fun splitTitle_onlyLastBracketIsSplit() {
        val parts = splitTitle("Song (Remix) (Live)")
        assertEquals("Song (Remix)", parts.main)
        assertEquals("Live", parts.sub)
    }

    @Test
    fun parseArtists_limitsToThreeAndCountsRest() {
        val (shown, extra) = parseArtists("A, B & C, D, E")
        assertEquals(listOf("A", "B", "C"), shown)
        assertEquals(2, extra)
    }

    @Test
    fun parseArtists_dedupesAndSkipsBlanks() {
        val (shown, extra) = parseArtists("Sanju Rathod, , sanju rathod, G-SPXRK")
        assertEquals(listOf("Sanju Rathod", "G-SPXRK"), shown)
        assertEquals(0, extra)
    }
}
