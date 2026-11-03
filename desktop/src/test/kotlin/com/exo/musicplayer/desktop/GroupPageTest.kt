package com.exo.musicplayer.desktop

import com.exo.musicplayer.data.art.CoverColour
import com.exo.musicplayer.desktop.ui.lengthInWords
import com.exo.musicplayer.desktop.ui.releaseKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupPageTest {

    @Test
    fun lengthReadsAsSpotifyWritesIt() {
        assertEquals("24 min 10 sec", lengthInWords((24 * 60 + 10) * 1000L))
        assertEquals("1 hr 12 min", lengthInWords((72 * 60 + 40) * 1000L))
        assertEquals("45 sec", lengthInWords(45_900L))
    }

    @Test
    fun singlesAndEpsOnlyWhereTheNameSaysSo() {
        assertEquals("Single", releaseKind("Millionaire - Single"))
        assertEquals("EP", releaseKind("Fuzz - EP"))
        assertEquals("EP", releaseKind("Night Drive (EP)"))
        // Two songs of an album are still an album: the library may hold only part of it.
        assertEquals("Album", releaseKind("#salem"))
        assertEquals("Album", releaseKind("Deep"))
    }

    @Test
    fun theBannerKeepsWhiteWritingReadable() {
        for (colour in listOf(0xFFFFD400.toInt(), 0xFFE0115F.toInt(), 0xFF00C2A8.toInt(), 0xFF9B5DE5.toInt())) {
            val banner = assertNotNull(CoverColour.bannerFrom(IntArray(4_000) { colour }))
            val contrast = 1.05 / (CoverColour.luminance(banner) + 0.05)
            assertTrue(contrast >= 7.0, "white on ${Integer.toHexString(banner)} is only $contrast:1")
        }
        // All greys: no colour of its own, so the page takes its neutral.
        assertNull(CoverColour.bannerFrom(IntArray(4_000) { 0xFF808080.toInt() }))
    }
}
