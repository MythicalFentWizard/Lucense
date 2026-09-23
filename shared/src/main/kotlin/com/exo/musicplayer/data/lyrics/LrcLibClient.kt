package com.exo.musicplayer.data.lyrics

import com.exo.musicplayer.data.net.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** What a lyrics lookup returned. */
sealed interface LyricsFetch {
    data class Found(val plain: String?, val synced: String?) : LyricsFetch
    data object NotFound : LyricsFetch
    data object Instrumental : LyricsFetch
    data class Error(val message: String) : LyricsFetch
}

/**
 * Lyrics from LRCLIB.
 *
 * Free, no account, no key, and it returns time-stamped LRC alongside plain
 * text, which is what makes line-by-line highlighting possible. LRCLIB asks
 * clients to identify themselves in the User-Agent, so this one does.
 */
class LrcLibClient {

    /**
     * Tries the exact-match endpoint first — it takes a duration and so avoids
     * matching a different recording of the same song — then falls back to a
     * fuzzy search.
     */
    suspend fun fetch(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long
    ): LyricsFetch = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext LyricsFetch.NotFound

        val durationSeconds = (durationMs / 1000).toInt()
        val exact = buildString {
            append("https://lrclib.net/api/get")
            append("?track_name=").append(encodeQuery(title))
            if (!artist.isNullOrBlank()) append("&artist_name=").append(encodeQuery(artist))
            if (!album.isNullOrBlank()) append("&album_name=").append(encodeQuery(album))
            if (durationSeconds > 0) append("&duration=").append(durationSeconds)
        }

        when (val direct = request(exact)) {
            is Response.Ok -> parseObject(JSONObject(direct.body))?.let { return@withContext it }
            is Response.NotFound -> Unit
            is Response.Failed -> return@withContext LyricsFetch.Error(direct.message)
        }

        // Fall back to search: tags from a Telegram import are often wrong or
        // missing, so an exact lookup failing does not mean there are no lyrics.
        val query = listOfNotNull(artist?.takeIf { it.isNotBlank() }, title).joinToString(" ")
        val searchUrl = "https://lrclib.net/api/search?q=${encodeQuery(query)}"
        when (val found = request(searchUrl)) {
            is Response.Ok -> {
                val array = runCatching { JSONArray(found.body) }.getOrNull()
                    ?: return@withContext LyricsFetch.NotFound
                if (array.length() == 0) return@withContext LyricsFetch.NotFound

                val best = pickBest(array, durationSeconds)
                    ?: return@withContext LyricsFetch.NotFound
                parseObject(best) ?: LyricsFetch.NotFound
            }
            is Response.NotFound -> LyricsFetch.NotFound
            is Response.Failed -> LyricsFetch.Error(found.message)
        }
    }

    /** Prefers a result whose duration is close to ours, then one with timings. */
    private fun pickBest(array: JSONArray, durationSeconds: Int): JSONObject? {
        var best: JSONObject? = null
        var bestScore = Int.MIN_VALUE
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val itemDuration = item.optDouble("duration", -1.0).toInt()
            var score = 0
            if (durationSeconds > 0 && itemDuration > 0) {
                val delta = kotlin.math.abs(itemDuration - durationSeconds)
                score += when {
                    delta <= 2 -> 100
                    delta <= 5 -> 60
                    delta <= 15 -> 20
                    else -> -delta
                }
            }
            if (!item.optString("syncedLyrics").isNullOrBlank()) score += 30
            if (!item.optString("plainLyrics").isNullOrBlank()) score += 10
            if (score > bestScore) {
                bestScore = score
                best = item
            }
        }
        return best
    }

    private fun parseObject(json: JSONObject): LyricsFetch? {
        if (json.optBoolean("instrumental", false)) return LyricsFetch.Instrumental
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() }
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
        if (plain == null && synced == null) return null
        return LyricsFetch.Found(plain, synced)
    }

    private sealed interface Response {
        data class Ok(val body: String) : Response
        data object NotFound : Response
        data class Failed(val message: String) : Response
    }

    private fun request(url: String): Response = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            when (val code = connection.responseCode) {
                in 200..299 ->
                    Response.Ok(connection.inputStream.bufferedReader().use { it.readText() })
                404 -> Response.NotFound
                else -> Response.Failed("Lyrics service returned HTTP $code.")
            }
        } finally {
            connection.disconnect()
        }
    } catch (t: Throwable) {
        Log.warn(TAG, "Lyrics request failed", t)
        Response.Failed("Couldn't reach the lyrics service.")
    }

    private companion object {
        const val TAG = "LrcLibClient"
        const val USER_AGENT = "Lucense/1.6 (Android music player)"
    }
}

private fun encodeQuery(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8")
