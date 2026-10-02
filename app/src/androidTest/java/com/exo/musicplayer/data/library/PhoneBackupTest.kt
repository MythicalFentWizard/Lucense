package com.exo.musicplayer.data.library

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.exo.musicplayer.data.db.MusicDatabase
import com.exo.musicplayer.data.db.PlayEvent
import com.exo.musicplayer.data.db.Playlist
import com.exo.musicplayer.data.db.SmartPlaylist
import com.exo.musicplayer.data.db.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A backup taken on one phone, restored on another that has the same songs:
 * everything comes back, nothing is doubled by restoring twice, and a large
 * listening history restores in reasonable time.
 */
@RunWith(AndroidJUnit4::class)
class PhoneBackupTest {

    private lateinit var from: MusicDatabase
    private lateinit var to: MusicDatabase

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        from = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java).build()
        to = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java).build()
    }

    @After
    fun close() {
        from.close()
        to.close()
    }

    private fun song(n: Int) = Track(
        filePath = "/music/$n.m4a",
        contentHash = "hash-$n",
        title = "Song $n",
        artist = "Artist ${n % 40}"
    )

    @Test
    fun restoresEverythingOnceAndQuickly() = runBlocking {
        val songs = 1_000
        val ids = (0 until songs).map { from.trackDao().insert(song(it)) }
        ids.forEachIndexed { i, id ->
            if (i % 7 == 0) from.trackDao().setFavorite(id, true)
            if (i % 5 == 0) from.trackDao().setRating(id, 1 + i % 5)
            repeat(i % 20) {
                from.playEventDao().insert(PlayEvent(trackId = id, startedAt = 1L, listenedMs = 60_000L, hourOfDay = 9))
                from.trackDao().markPlayed(id, 1L)
            }
        }
        val list = from.playlistDao().insertPlaylist(Playlist(name = "Mix"))
        from.playlistDao().appendTracks(list, ids.take(50))
        from.smartPlaylistDao().insert(SmartPlaylist(name = "Loved", rule = "rating:4+"))

        val text = PhoneBackup(from).write()

        // The other phone has the same songs, untouched.
        (0 until songs).forEach { to.trackDao().insert(song(it)) }
        val started = System.nanoTime()
        val outcome = PhoneBackup(to).restore(text)!!
        val ms = (System.nanoTime() - started) / 1_000_000
        Log.i("PhoneBackupTest", "restored $outcome in $ms ms")

        val expectedPlays = (0 until songs).sumOf { it % 20 }
        assertEquals((0 until songs).count { it % 7 == 0 }, outcome.favourites)
        assertEquals((0 until songs).count { it % 5 == 0 }, outcome.ratings)
        assertEquals(expectedPlays, outcome.plays)
        assertEquals(1, outcome.playlists)
        assertEquals(0, outcome.missing)

        val restored = to.trackDao().allOnce().associateBy { it.contentHash }
        from.trackDao().allOnce().forEach { original ->
            val copy = restored.getValue(original.contentHash)
            assertEquals(original.isFavorite, copy.isFavorite)
            assertEquals(original.rating, copy.rating)
            assertEquals(original.playCount, copy.playCount)
        }
        val lists = to.playlistDao().observeSummaries().first()
        assertEquals(listOf("Mix"), lists.map { it.name })
        assertEquals(50, to.playlistDao().tracksOnce(lists.single().id).size)
        assertEquals(listOf("Loved"), to.smartPlaylistDao().allOnce().map { it.name })

        // Restoring again adds nothing: it only ever fills in what's missing.
        val again = PhoneBackup(to).restore(text)!!
        assertEquals(0, again.favourites + again.ratings + again.plays + again.playlists)

        assertTrue("restore of $expectedPlays plays took $ms ms", ms < 15_000)
    }
}
