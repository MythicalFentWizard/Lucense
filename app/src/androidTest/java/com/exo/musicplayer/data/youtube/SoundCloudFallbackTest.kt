package com.exo.musicplayer.data.youtube

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The SoundCloud fallback's choices, on the results SoundCloud actually gave
 * for these songs through a VPN exit YouTube was refusing.
 */
@RunWith(AndroidJUnit4::class)
class SoundCloudFallbackTest {

    private fun upload(title: String, uploader: String, seconds: Int, plays: Long) = YouTubeVideo(
        id = title, title = title, channel = uploader, durationSeconds = seconds, viewCount = plays,
        source = SoundCloudFallback.LABEL, pageUrl = "https://soundcloud.com/x/${title.hashCode()}"
    )

    // "svard crash" on SoundCloud, first four results as they came back.
    private val svard = listOf(
        upload("Crash - Svard Slowed and Reverb", "bugs under your skin", 221, 33),
        upload("crash", "svard", 179, 24_381),
        upload("sugar crash remix", "8shard", 97, 17_796),
        upload("CRASH (INTERLUDE)", "JKillaX \$Kard", 63, 16)
    )

    @Test
    fun picksTheArtistsOwnUploadOverTheSlowedOne() {
        val lookup = SoundCloudFallback.Lookup(YouTubeLinkFinder.Wanted(title = "crash", artist = "svard"), 188)
        assertEquals("crash", SoundCloudFallback.pick(lookup, svard)?.title)
    }

    @Test
    fun refusesASnippetOfTheSong() {
        // The artist's own 40-second preview, titled just the song's name.
        val snippet = listOf(upload("crash", "svard", 40, 30_000))
        val lookup = SoundCloudFallback.Lookup(YouTubeLinkFinder.Wanted(title = "crash", artist = "svard"), 188)
        assertNull(SoundCloudFallback.pick(lookup, snippet))
        // Without the video's length nothing tells the snippet apart, which is
        // why the length is looked up for links too.
        val unknown = SoundCloudFallback.Lookup(lookup.wanted, null)
        assertEquals("crash", SoundCloudFallback.pick(unknown, snippet)?.title)
    }

    @Test
    fun refusesAnHourLongLoop() {
        val loop = listOf(upload("crash", "svard", 3_600, 5_000))
        val lookup = SoundCloudFallback.Lookup(YouTubeLinkFinder.Wanted(title = "crash", artist = "svard"), 188)
        assertNull(SoundCloudFallback.pick(lookup, loop))
    }

    @Test
    fun findsNothingRatherThanADifferentSong() {
        val lookup = SoundCloudFallback.Lookup(YouTubeLinkFinder.Wanted(title = "Government Lies", artist = "Bishops Green"))
        assertNull(SoundCloudFallback.pick(lookup, svard))
    }

    @Test
    fun searchesWithoutTheVideoDressing() {
        assertEquals(
            "Fazerdaze Little Uneasy",
            SoundCloudFallback.searchQuery(YouTubeLinkFinder.Wanted(title = "Little Uneasy (Official Video)", artist = "Fazerdaze"))
        )
        assertEquals(
            "Eminem Rap God",
            SoundCloudFallback.searchQuery(YouTubeLinkFinder.Wanted(title = "Rap God Official Audio", artist = "Eminem"))
        )
        assertEquals("Childhood", SoundCloudFallback.searchQuery(YouTubeLinkFinder.Wanted(title = "Childhood [HD]")))
    }

    @Test
    fun readsVideoIdsFromEveryLinkShape() {
        assertEquals("szvFmW_oxeY", SoundCloudFallback.videoId("https://www.youtube.com/watch?v=szvFmW_oxeY"))
        assertEquals("szvFmW_oxeY", SoundCloudFallback.videoId("https://youtu.be/szvFmW_oxeY?si=abc"))
        assertEquals("szvFmW_oxeY", SoundCloudFallback.videoId("https://music.youtube.com/watch?v=szvFmW_oxeY&list=RD"))
        assertEquals("szvFmW_oxeY", SoundCloudFallback.videoId("https://www.youtube.com/shorts/szvFmW_oxeY"))
        assertNull(SoundCloudFallback.videoId("https://soundcloud.com/svard/crash"))
    }

    @Test
    fun tellsARefusedConnectionFromABrokenVideo() {
        assertTrue(SoundCloudFallback.refusedByYouTube("ERROR: [youtube] DND2L_q2gSQ: Sign in to confirm you’re not a bot."))
        assertTrue(SoundCloudFallback.refusedByYouTube("ERROR: unable to download video data: HTTP Error 403: Forbidden"))
        assertFalse(SoundCloudFallback.refusedByYouTube("ERROR: [youtube] abc: Video unavailable"))
        assertFalse(SoundCloudFallback.refusedByYouTube(null))
    }
}
