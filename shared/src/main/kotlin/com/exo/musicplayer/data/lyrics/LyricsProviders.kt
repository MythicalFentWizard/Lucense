package com.exo.musicplayer.data.lyrics

import com.exo.musicplayer.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One place lyrics can come from. */
interface LyricsProvider {
    val label: String
    suspend fun fetch(title: String, artist: String?, album: String?, durationMs: Long): LyricsFetch
}

/**
 * Tries each provider in turn until one returns lyrics.
 *
 * Ordered by what they give back, not by popularity: LRCLIB first because it is
 * the only one of the three that reliably carries *timed* LRC, then NetEase
 * which often does, then lyrics.ovh which is plain text but has broad Western
 * coverage. A provider erroring out is treated the same as finding nothing, so
 * one service being down never blocks the rest.
 */
class LyricsProviderChain(
    private val providers: List<LyricsProvider>
) {
    suspend fun fetch(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long
    ): Pair<LyricsFetch, String?> {
        var sawInstrumental = false
        for (provider in providers) {
            val result = runCatching { provider.fetch(title, artist, album, durationMs) }
                .getOrElse { LyricsFetch.NotFound }
            when (result) {
                is LyricsFetch.Found -> return result to provider.label
                is LyricsFetch.Instrumental -> sawInstrumental = true
                else -> Unit
            }
        }
        return (if (sawInstrumental) LyricsFetch.Instrumental else LyricsFetch.NotFound) to null
    }
}

/** Primary: free, no key, and the best source of synced LRC. */
class LrcLibProvider(private val client: LrcLibClient = LrcLibClient()) : LyricsProvider {
    override val label = "LRCLIB"
    override suspend fun fetch(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long
    ): LyricsFetch = client.fetch(title, artist, album, durationMs)
}

/**
 * Secondary: NetEase Cloud Music. Free and often has timed lyrics, including
 * for tracks Western databases miss entirely.
 */
class NeteaseLyricsProvider : LyricsProvider {

    override val label = "NetEase"

    override suspend fun fetch(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long
    ): LyricsFetch = withContext(Dispatchers.IO) {
        val query = listOfNotNull(artist?.takeIf { it.isNotBlank() }, title).joinToString(" ")
        val headers = mapOf("Referer" to "https://music.163.com/")

        val searchBody = Http.get(
            "https://music.163.com/api/search/get?s=${encodeQuery(query)}&type=1&limit=5",
            headers
        ) ?: return@withContext LyricsFetch.NotFound

        val songs = runCatching {
            JSONObject(searchBody).optJSONObject("result")?.optJSONArray("songs")
        }.getOrNull() ?: return@withContext LyricsFetch.NotFound
        if (songs.length() == 0) return@withContext LyricsFetch.NotFound

        // Prefer the candidate whose duration is closest to ours.
        var bestId = -1L
        var bestDelta = Long.MAX_VALUE
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val id = song.optLong("id", -1L)
            if (id <= 0) continue
            val delta = if (durationMs > 0) {
                kotlin.math.abs(song.optLong("duration", 0L) - durationMs)
            } else {
                0L
            }
            if (delta < bestDelta) { bestDelta = delta; bestId = id }
        }
        if (bestId <= 0) return@withContext LyricsFetch.NotFound

        val lyricBody = Http.get(
            "https://music.163.com/api/song/lyric?id=$bestId&lv=1&kv=1&tv=-1",
            headers
        ) ?: return@withContext LyricsFetch.NotFound

        val root = runCatching { JSONObject(lyricBody) }.getOrNull()
            ?: return@withContext LyricsFetch.NotFound
        if (root.optBoolean("nolyric", false)) return@withContext LyricsFetch.Instrumental

        val synced = root.optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
            ?: return@withContext LyricsFetch.NotFound
        // NetEase always ships LRC; derive the plain form from it.
        val plain = synced.lineSequence()
            .map { it.replace(Regex("""\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?]"""), "").trim() }
            .filter { it.isNotEmpty() && !Regex("""^\[[a-zA-Z#]+:.*]$""").matches(it) }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }

        LyricsFetch.Found(plain, synced)
    }
}

/** Tertiary: plain text only, but good coverage of mainstream Western tracks. */
class LyricsOvhProvider : LyricsProvider {

    override val label = "lyrics.ovh"

    override suspend fun fetch(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long
    ): LyricsFetch = withContext(Dispatchers.IO) {
        if (artist.isNullOrBlank()) return@withContext LyricsFetch.NotFound
        val body = Http.get(
            "https://api.lyrics.ovh/v1/${encodeQuery(artist)}/${encodeQuery(title)}"
        ) ?: return@withContext LyricsFetch.NotFound

        val text = runCatching { JSONObject(body).optString("lyrics") }.getOrNull()
            ?.replace("\r\n", "\n")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext LyricsFetch.NotFound

        LyricsFetch.Found(text, null)
    }
}

private fun encodeQuery(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8")
