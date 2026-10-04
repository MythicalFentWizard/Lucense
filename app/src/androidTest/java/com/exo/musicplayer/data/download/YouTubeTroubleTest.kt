package com.exo.musicplayer.data.download

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exo.musicplayer.data.youtube.YtDlpFlatSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** What counts as YouTube refusing a connection, what a playlist link is, and how one is read. */
@RunWith(AndroidJUnit4::class)
class YouTubeTroubleTest {

    @Test
    fun aRefusalIsAboutTheConnectionNotTheVideo() {
        assertTrue(YouTubeTrouble.refused("ERROR: [youtube] DND2L_q2gSQ: Sign in to confirm you’re not a bot."))
        assertTrue(YouTubeTrouble.refused("ERROR: unable to download video data: HTTP Error 403: Forbidden"))
        assertTrue(YouTubeTrouble.refused("ERROR: HTTP Error 429: Too Many Requests"))
        assertFalse(YouTubeTrouble.refused("ERROR: [youtube] abc: Video unavailable"))
        // Age-restricted is that one video; the next will download.
        assertFalse(YouTubeTrouble.refused("ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate"))
        assertFalse(YouTubeTrouble.refused(null))
    }

    @Test
    fun aFailureIsSaidInPlainWords() {
        val refused = YouTubeTrouble.explain("ERROR: [youtube] x: Sign in to confirm you’re not a bot. Use --cookies-from-browser")
        assertTrue(refused.startsWith("YouTube refused this connection."))
        // Three reasons, not one: it used to tell someone with no VPN to change VPN server.
        assertTrue(refused.contains("a lot of downloads in a row") && refused.contains("VPN") && refused.contains("out of date"))
        assertEquals(
            "YouTube only plays this video for signed-in adults.",
            YouTubeTrouble.explain("ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate. Use --cookies-from-browser or --cookies")
        )
        assertTrue(YouTubeTrouble.explain("ERROR: [youtube] abc: Video unavailable").startsWith("YouTube says this video is unavailable"))
        assertEquals("Requested format is not available", YouTubeTrouble.explain("ERROR: [youtube] abc: Requested format is not available"))
        assertEquals("The download failed.", YouTubeTrouble.explain(null))
    }

    @Test
    fun aPlaylistLinkIsToldFromAVideoInsideOne() {
        assertTrue(LinkResolver.isYouTubePlaylist("https://www.youtube.com/playlist?list=PLZqsyBiYZFQ2_M3Jh", wholeList = false))
        assertTrue(LinkResolver.isYouTubePlaylist("https://music.youtube.com/playlist?list=OLAK5uy_abc", wholeList = false))
        val inside = "https://www.youtube.com/watch?v=dudPOUWOuKI&list=PL123abc&index=4"
        assertFalse(LinkResolver.isYouTubePlaylist(inside, wholeList = false))
        assertTrue(LinkResolver.isYouTubePlaylist(inside, wholeList = true))
        assertFalse(LinkResolver.isYouTubePlaylist("https://www.youtube.com/watch?v=dudPOUWOuKI", wholeList = true))
        assertFalse(LinkResolver.isYouTubePlaylist("https://soundcloud.com/someone/sets/an-album?list=1", wholeList = true))
    }

    @Test
    fun aPlaylistIsReadWithoutItsRemovedVideos() {
        val json = """
            {"title": "Morningside", "entries": [
              {"id": "aaaaaaaaaaa", "title": "Fazerdaze - Lucky Girl", "channel": "Fazerdaze", "duration": 190.0, "ie_key": "Youtube"},
              {"id": "bbbbbbbbbbb", "title": "[Private video]", "ie_key": "Youtube"},
              {"id": "ccccccccccc", "title": "[Deleted video]", "ie_key": "Youtube"},
              {"id": "eeeeeeeeeee", "title": null, "ie_key": "Youtube"},
              {"id": "ddddddddddd", "title": "Fazerdaze - Jennifer", "uploader": "Fazerdaze", "duration": 201, "ie_key": "Youtube"}
            ]}
        """.trimIndent()
        val playlist = YtDlpFlatSearch.parsePlaylist(json)!!
        assertEquals("Morningside", playlist.title)
        assertEquals(listOf("Fazerdaze - Lucky Girl", "Fazerdaze - Jennifer"), playlist.songs.map { it.title })
        assertEquals(3, playlist.gone)
        assertEquals("https://www.youtube.com/watch?v=aaaaaaaaaaa", playlist.songs.first().watchUrl)
        assertEquals(201, playlist.songs.last().durationSeconds)
        assertNull(YtDlpFlatSearch.parsePlaylist("not json"))
    }
}
