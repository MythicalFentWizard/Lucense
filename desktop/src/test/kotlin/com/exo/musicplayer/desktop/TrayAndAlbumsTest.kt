package com.exo.musicplayer.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.data.lyrics.LyricsTranslator
import com.exo.musicplayer.data.lyrics.SavedTranslations
import com.exo.musicplayer.desktop.audio.PlaybackStatus
import com.exo.musicplayer.desktop.data.CollectionKind
import com.exo.musicplayer.desktop.data.DesktopStore
import com.exo.musicplayer.desktop.data.TrackGroup
import com.exo.musicplayer.desktop.data.TrackGroups
import com.exo.musicplayer.desktop.library.DesktopTrack
import com.exo.musicplayer.desktop.system.SingleInstance
import com.exo.musicplayer.desktop.ui.GroupActions
import com.exo.musicplayer.desktop.ui.GroupPage
import com.exo.musicplayer.desktop.ui.GroupView
import com.exo.musicplayer.desktop.ui.LucenseDesktopTheme
import com.exo.musicplayer.desktop.ui.MiniPlayerCard
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrayAndAlbumsTest {

    private val folder = Files.createTempDirectory("lucense-test").toFile().also { it.deleteOnExit() }

    private fun song(n: Int, number: Int? = n) =
        DesktopTrack(File(folder, "$n.mp3"), "Song $n", "Someone", "An Album", 181_000L + n, number, 2021, 1_000)

    // ---- Translations ----

    @Test
    fun aTranslationComesBackAndRemembersBeingHidden() {
        val saved = SavedTranslations(File(folder, "translations"))
        val lyrics = listOf("Hola", "", "  que tal ")
        assertNull(saved.get(lyrics, "en"))
        saved.put(lyrics, "en", LyricsTranslator.Translation(listOf("Hello", "", "how are you"), "es"))

        val back = assertNotNull(saved.get(lyrics, "en"))
        assertEquals(listOf("Hello", "", "how are you"), back.lines)
        assertEquals("es", back.from)
        assertTrue(back.shown)
        // Spacing at the ends of a line is the same lyrics; another language is another translation.
        assertNotNull(saved.get(listOf(" Hola", "", "que tal"), "en"))
        assertNull(saved.get(lyrics, "de"))
        // Other lyrics: not this translation.
        assertNull(saved.get(lyrics + "otra", "en"))

        saved.setShown(lyrics, "en", false)
        assertFalse(assertNotNull(saved.get(lyrics, "en")).shown)
        assertEquals(listOf("Hello", "", "how are you"), saved.get(lyrics, "en")?.lines)
        // Nothing half-written left lying about.
        assertTrue(File(folder, "translations").listFiles()!!.none { it.name.endsWith(".part") })
    }

    /** Asks Google itself, so only with LUCENSE_NET set. */
    @Test
    fun onlyTheLinesInAnotherLanguageAreTranslated() = kotlinx.coroutines.runBlocking {
        if (System.getenv("LUCENSE_NET") == null) return@runBlocking
        val lines = listOf("I walk the city at night", "", "Nobody knows the way I feel", "Je ne sais pas pourquoi",
            "I keep on running down the road", "Every window has a story", "La vie est belle ce soir")
        val back = assertNotNull(LyricsTranslator.translate(lines, "en"))
        assertEquals(List(5) { "" }, back.lines.filterIndexed { i, _ -> i != 3 && i != 6 })
        assertTrue(back.lines[3].isNotBlank() && back.lines[6].isNotBlank(), back.lines.toString())
        assertEquals("fr", back.from)
        // All English: nothing to show, which the apps call "already in your language".
        assertTrue(assertNotNull(LyricsTranslator.translate(lines.filterIndexed { i, _ -> i != 3 && i != 6 }, "en")).lines.all { it.isBlank() })
        println("translated: " + back.lines)
    }

    // ---- Albums in an order of their own ----

    @Test
    fun anAlbumKeepsTheOrderItWasDraggedInto() {
        val songs = listOf(song(1), song(2), song(3), song(4, number = null))
        val album = TrackGroups.albums(songs).single()
        assertEquals(listOf(1, 2, 3, 4), album.tracks.map { it.title.last().digitToInt() })

        val dragged = listOf(songs[2], songs[0], songs[1]).map { it.file.absolutePath } +
            File(folder, "gone.mp3").absolutePath
        // Dragged order first, then a song added since, in the album's own order; one gone is passed over.
        assertEquals(listOf(3, 1, 2, 4), TrackGroups.inOrder(album, dragged).tracks.map { it.title.last().digitToInt() })

        // Saved and read back, the album's key - with the NUL in it - intact.
        val store = DesktopStore(File(folder, "test.db"))
        store.saveAlbumOrder(album.key, dragged)
        assertTrue('\u0000' in album.key)
        assertEquals(mapOf(album.key to dragged), store.albumOrders())
        store.deleteAlbumOrder(album.key)
        assertTrue(store.albumOrders().isEmpty())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun draggingAHandleMovesTheSongAndAClickStillPlays() = runComposeUiTest {
        val songs = (1..5).map { song(it) }
        val group = TrackGroup("album:test", "An Album", "Someone", songs)
        var reordered: List<DesktopTrack>? = null
        var played: DesktopTrack? = null
        setContent {
            LucenseDesktopTheme {
                GroupPage(
                    GroupView(group, CollectionKind.ALBUMS, emptyList(), emptyList(), null, false, false, false, emptyMap(), emptySet()),
                    GroupActions(
                        play = { track, _ -> played = track }, togglePlay = {}, setShuffle = {}, playNext = {},
                        addToQueue = {}, toggleFavourite = {}, addToPlaylist = {}, edit = {}, reveal = {},
                        openArtist = {}, openAlbum = {}, back = {}, reorder = { reordered = it }
                    )
                )
            }
        }
        val first = songs[0].file.absolutePath
        val row = onNodeWithTag("song:$first").fetchSemanticsNode().size.height.toFloat()
        // Down two rows, a step at a time, as a hand moves a mouse.
        onNodeWithTag("handle:$first", useUnmergedTree = true).performMouseInput {
            moveTo(center)
            press()
            repeat(20) { moveBy(Offset(0f, row * 2f / 20)) }
            release()
        }
        waitForIdle()
        assertEquals(listOf(2, 3, 1, 4, 5), reordered?.map { it.title.last().digitToInt() })

        onNodeWithTag("song:" + songs[3].file.absolutePath).performClick()
        waitForIdle()
        assertEquals(songs[3], played)
    }

    // ---- The mini player ----

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theMiniPlayerShowsTheSongAndItsButtonsWork() = runComposeUiTest {
        var paused = 0
        var next = 0
        setContent {
            LucenseDesktopTheme {
                Box(Modifier.size(392.dp, 128.dp)) {
                    MiniPlayerCard(
                        status = PlaybackStatus(song(7), playing = true, positionMs = 61_000, durationMs = 181_007),
                        onSeek = {}, onPrevious = {}, onPlayPause = { paused++ }, onNext = { next++ },
                        onExpand = {}, onClose = {}
                    )
                }
            }
        }
        onNodeWithText("Song 7").assertExists()
        onNodeWithText("1:01 / 3:01").assertExists()
        onNodeWithTag("mini:play").performClick()
        onNodeWithTag("mini:next").performClick()
        waitForIdle()
        assertEquals(1, paused)
        assertEquals(1, next)
        System.getenv("LUCENSE_SHOTS")?.let { out ->
            File(out).mkdirs()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "mini-player.png"))
        }
    }

    // ---- One Lucense at a time ----

    @Test
    fun aSecondStartHandsItsFilesToTheFirst() {
        val instance = File(folder, "instance")
        // Nobody running: this one carries on.
        assertFalse(SingleInstance.handOver(arrayOf("x.mp3"), instance))

        val arrived = CountDownLatch(1)
        var got: List<String>? = null
        val server = assertNotNull(SingleInstance.listen(instance) { got = it; arrived.countDown() })
        try {
            assertTrue(SingleInstance.handOver(arrayOf("C:\\Music\\one.mp3", "D:\\two words.flac"), instance))
            assertTrue(arrived.await(5, TimeUnit.SECONDS))
            assertEquals(listOf("C:\\Music\\one.mp3", "D:\\two words.flac"), got)

            // Someone without the token is turned away.
            val forged = File(folder, "forged")
            forged.writeText(instance.readLines()[0] + "\nnot-the-token\n")
            assertFalse(SingleInstance.handOver(arrayOf("evil.mp3"), forged))
        } finally {
            server.close()
        }
        // Gone without tidying up: its file is left, its port is closed, and the next start goes ahead.
        assertFalse(SingleInstance.handOver(arrayOf(), instance))
    }
}
