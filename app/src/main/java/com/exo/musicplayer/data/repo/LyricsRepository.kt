package com.exo.musicplayer.data.repo

import android.content.Context
import com.exo.musicplayer.data.db.Lyrics
import com.exo.musicplayer.data.db.MusicDatabase
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.lyrics.GeniusLyricsProvider
import com.exo.musicplayer.data.lyrics.LrcLibProvider
import com.exo.musicplayer.data.lyrics.LyricsOvhProvider
import com.exo.musicplayer.data.lyrics.LyricsProviderChain
import com.exo.musicplayer.data.lyrics.NeteaseLyricsProvider
import com.exo.musicplayer.data.lyrics.LyricsFetch
import kotlinx.coroutines.flow.Flow

class LyricsRepository(context: Context) {

    private val dao = MusicDatabase.get(context.applicationContext).lyricsDao()
    // Ordered by what they return: LRCLIB has the most timed LRC, NetEase
    // often does and reaches non-Western catalogue, lyrics.ovh is plain text
    // with broad mainstream coverage.
    // Tiers 1-3 can return timed LRC, which drives the line highlighting.
    // Genius is tier 4 and plain text only, so it is consulted last: reaching
    // it means having words without timings rather than no words at all.
    private val chain = LyricsProviderChain(
        listOf(
            LrcLibProvider(),
            NeteaseLyricsProvider(),
            LyricsOvhProvider(),
            GeniusLyricsProvider()
        )
    )

    fun observe(trackId: Long): Flow<Lyrics?> = dao.observe(trackId)

    suspend fun find(trackId: Long): Lyrics? = dao.find(trackId)

    /**
     * Looks up lyrics online and stores them.
     *
     * Lyrics the user typed themselves are never replaced by a fetch: they were
     * entered precisely because the automatic lookup was wrong or empty, so
     * silently overwriting them would undo deliberate work. [force] still won't
     * clobber a manual entry — deleting it is the explicit way to start over.
     */
    suspend fun fetch(track: Track, force: Boolean = false): LyricsFetch {
        val existing = dao.find(track.id)
        if (existing != null && existing.isManual) {
            return LyricsFetch.Found(existing.plainText, existing.syncedText)
        }
        if (existing != null && !force) {
            return LyricsFetch.Found(existing.plainText, existing.syncedText)
        }

        val (result, provider) = chain.fetch(
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs
        )
        if (result is LyricsFetch.Found) {
            dao.upsert(
                Lyrics(
                    trackId = track.id,
                    plainText = result.plain,
                    syncedText = result.synced,
                    source = provider ?: Lyrics.SOURCE_FETCHED
                )
            )
        }
        return result
    }

    /** Stores lyrics the user typed or pasted in. */
    suspend fun saveManual(trackId: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            dao.delete(trackId)
            return
        }
        // Pasted text may already be in LRC format; keep the stamps if so.
        val looksSynced = Regex("""\[\d{1,3}:\d{2}""").containsMatchIn(trimmed)
        dao.upsert(
            Lyrics(
                trackId = trackId,
                plainText = if (looksSynced) stripStamps(trimmed) else trimmed,
                syncedText = if (looksSynced) trimmed else null,
                source = Lyrics.SOURCE_MANUAL
            )
        )
    }

    suspend fun delete(trackId: Long) = dao.delete(trackId)

    private fun stripStamps(lrc: String): String = lrc.lineSequence()
        .map { it.replace(Regex("""\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?]"""), "").trim() }
        .filter { it.isNotEmpty() && !Regex("""^\[[a-zA-Z#]+:.*]$""").matches(it) }
        .joinToString("\n")
}
