package com.exo.musicplayer.data.discover

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exo.musicplayer.data.youtube.YouTubeLinkFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The parts of Discover that are decisions rather than look-ups, on names as they really come. */
@RunWith(AndroidJUnit4::class)
class DiscoveryTest {

    private val genres = listOf(
        "blackgaze", "digicore", "emocore", "hard rock", "hardcore punk", "j-core", "metal", "metalcore",
        "nu metal", "rap", "rap rock", "rock", "shoegaze", "trap"
    )

    @Test
    fun aTypedWordFindsTheGenreOfThatNameFirst() {
        val found = Discovery.searchGenres(genres, "rap")
        assertEquals("rap", found.first())
        assertEquals("rap rock", found[1])
        // "trap" only contains it, so it comes after those that start with it.
        assertEquals("trap", found.last())
        assertFalse("rock" in found)
    }

    @Test
    fun aFamilyWordFindsTheGenresBuiltOnIt() {
        val core = Discovery.searchGenres(genres, "core")
        assertEquals(setOf("digicore", "emocore", "hardcore punk", "j-core", "metalcore"), core.toSet())
        assertEquals(genres, Discovery.searchGenres(genres, "  "))
    }

    @Test
    fun namesCompareWithoutAccentsCaseOrPunctuation() {
        assertEquals("svard", Discovery.plain("SVÄRD"))
        assertEquals("tyler the creator", Discovery.plain("Tyler, The Creator"))
        assertEquals(listOf("kets4eki", "asteria"), Discovery.split("kets4eki, asteria"))
        assertEquals(listOf("CG5", "Dagames"), Discovery.split("CG5 & Dagames"))
    }

    @Test
    fun ownedIsJudgedWithoutTheBracketedExtras(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val browser = DiscoverBrowser(scope, Discovery(null))
            browser.setLibrary(
                listOf(
                    LibrarySong("Mexico", "Alestorm", "Folk Metal"),
                    LibrarySong("Dracula", "Tame Impala, JENNIE", null)
                )
            )
            val library = withTimeout(5_000) { browser.library.first { it.titles.isNotEmpty() } }
            fun song(title: String, artist: String) = DiscoverTrack(1, title, artist, null, 0, null, null, 0)
            assertTrue(library.owns(song("Mexico", "Alestorm")))
            assertTrue(library.owns(song("Mexico (Remastered 2021)", "Alestorm")))
            assertTrue(library.owns(song("Dracula (with JENNIE)", "Tame Impala")))
            // The same title by someone else is a different song.
            assertFalse(library.owns(song("Mexico", "Butthole Surfers")))
            assertFalse(library.owns(song("Drink", "Alestorm")))
            assertEquals(listOf("Alestorm", "Tame Impala, JENNIE"), library.artists)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun wordsReadAsTheyShould() {
        assertEquals("2,209", DiscoverWords.grouped(2209))
        assertEquals("1 fan", DiscoverWords.fans(1))
        assertEquals("58K fans", DiscoverWords.fans(58_420))
        assertEquals("1.9M fans", DiscoverWords.fans(1_900_000))
        assertEquals("18 Sep 2020", DiscoverWords.date("2020-09-18"))
        assertEquals("out 1 Jan 2999", DiscoverWords.date("2999-01-01"))
        assertNull(DiscoverWords.date(""))
        assertEquals("Download the other 3", DiscoverWords.downloadAll(3, 8))
        assertEquals("Download all 8", DiscoverWords.downloadAll(8, 8))
        assertEquals("Nothing left to download", DiscoverWords.downloadAll(0, 8))
    }

    @Test
    fun anEmptyYouTubeSearchIsAskedAgainInOtherWords() {
        val named = YouTubeLinkFinder.Wanted(title = "Kambua", artist = "Bonga")
        assertEquals("Bonga Kambua", named.query)
        assertEquals(listOf("Kambua Bonga", "Bonga Kambua audio"), named.otherQueries)
        assertEquals(listOf("svard crash audio"), YouTubeLinkFinder.Wanted(title = "svard crash").otherQueries)
    }
}
