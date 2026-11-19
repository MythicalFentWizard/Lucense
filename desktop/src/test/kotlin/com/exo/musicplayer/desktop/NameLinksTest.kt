package com.exo.musicplayer.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import com.exo.musicplayer.desktop.data.CollectionKind
import com.exo.musicplayer.desktop.data.TrackGroups
import com.exo.musicplayer.desktop.library.DesktopTrack
import com.exo.musicplayer.desktop.ui.GroupActions
import com.exo.musicplayer.desktop.ui.GroupPage
import com.exo.musicplayer.desktop.ui.GroupView
import com.exo.musicplayer.desktop.ui.LucenseDesktopTheme
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An artist's or an album's name on a song goes to that artist's or album's page. */
@OptIn(ExperimentalTestApi::class)
class NameLinksTest {

    private val folder = Files.createTempDirectory("lucense-links").toFile().also { it.deleteOnExit() }

    private fun song(n: Int, artist: String?, album: String?) =
        DesktopTrack(File(folder, "$n.mp3"), "Song $n", artist, album, 181_000L, n, 2021, 1_000)

    private val songs = listOf(
        song(1, "Someone (feat. Other)", "An Album"),
        song(2, "Someone", "An Album"),
        song(3, "Other & Third", null)
    )

    @Test
    fun aSongsNamesAreTheKeysOfItsCards() {
        val album = TrackGroups.albums(songs).single()
        assertEquals(album.key, TrackGroups.albumGroupKey(songs[0]))
        assertEquals(album.key, TrackGroups.albumGroupKey(songs[1]))
        assertNull(TrackGroups.albumGroupKey(songs[2]))

        val artists = TrackGroups.artists(songs).map { it.key }.toSet()
        val linked = songs.flatMap { TrackGroups.splitArtists(it.artist) }.map(TrackGroups::artistGroupKey).toSet()
        assertEquals(artists, linked)
    }

    @Test
    fun anArtistInASongsCreditOpensTheirPageAndTheRestOfTheRowPlays() = runComposeUiTest {
        val album = TrackGroups.albums(songs).single()
        var opened: String? = null
        var played: DesktopTrack? = null
        setContent {
            LucenseDesktopTheme {
                GroupPage(
                    GroupView(album, CollectionKind.ALBUMS, emptyList(), emptyList(), null, false, false, false, emptyMap(), emptySet()),
                    actions(openArtist = { opened = it }, play = { played = it })
                )
            }
        }
        clickOn(songs[0], "Someone (feat. Other)", "Other")
        assertEquals("artist:other", opened)
        assertNull(played, "a click on the name opens the page and does not also play the song")

        clickOn(songs[0], "Someone (feat. Other)", "Someone")
        assertEquals("artist:someone", opened)
        assertNull(played)

        // Between the names is not a link: that is the row, which plays.
        clickOn(songs[0], "Someone (feat. Other)", "feat.")
        assertEquals(songs[0], played)
    }

    @Test
    fun anAlbumOnAnArtistsPageOpensTheAlbum() = runComposeUiTest {
        val artist = TrackGroups.artists(songs).first { it.key == "artist:someone" }
        val album = TrackGroups.albums(songs).single()
        var opened: String? = null
        var played: DesktopTrack? = null
        setContent {
            LucenseDesktopTheme {
                GroupPage(
                    GroupView(artist, CollectionKind.ARTISTS, emptyList(), emptyList(), null, false, false, false, emptyMap(), emptySet()),
                    actions(openAlbum = { opened = it }, play = { played = it })
                )
            }
        }
        // Without the album tiles, which open it too and would push the songs out of the window.
        clickOn(songs[1], "An Album", "An Album")
        assertEquals(album.key, opened)
        assertNull(played)
    }

    private fun actions(
        openArtist: (String) -> Unit = {},
        openAlbum: (String) -> Unit = {},
        play: (DesktopTrack) -> Unit = {}
    ) = GroupActions(
        play = { track, _ -> play(track) }, togglePlay = {}, setShuffle = {}, playNext = {},
        addToQueue = {}, toggleFavourite = {}, addToPlaylist = {}, edit = {}, reveal = {},
        openArtist = openArtist, openAlbum = openAlbum, back = {}, reorder = {}
    )

    /** Clicks the middle of [part] where it is written in [song]'s row, in the line reading [text], as the mouse would. */
    private fun ComposeUiTest.clickOn(song: DesktopTrack, text: String, part: String) {
        val node = onNode(hasText(text) and hasAnyAncestor(hasTestTag("song:" + song.file.absolutePath)), useUnmergedTree = true)
        val layout = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
        val at = text.indexOf(part)
        assertTrue(at >= 0, "\"$part\" in \"$text\"")
        val box = layout.first().getBoundingBox(at + part.length / 2)
        node.performMouseInput { click(box.center) }
        waitForIdle()
    }
}
