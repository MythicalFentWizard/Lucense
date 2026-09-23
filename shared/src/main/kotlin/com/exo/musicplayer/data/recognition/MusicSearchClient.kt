package com.exo.musicplayer.data.recognition

import com.exo.musicplayer.data.net.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Search-by-name against the iTunes Search API.
 *
 * Chosen because it needs no account, no key and no rate-limit registration, and
 * it was the one music endpoint reachable from this machine *without* the proxy.
 * It also returns cover art, which is what most Telegram imports are missing.
 */
class MusicSearchClient {

    suspend fun search(query: String, limit: Int = 20): RecognitionResult =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext RecognitionResult.NoMatch

            val url = URL(
                "https://itunes.apple.com/search" +
                    "?term=${encodeQuery(trimmed)}&media=music&entity=song&limit=$limit"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "GET"
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext RecognitionResult.Error(
                        "Search failed (HTTP ${connection.responseCode})."
                    )
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val results = JSONObject(body).optJSONArray("results")
                    ?: return@withContext RecognitionResult.NoMatch

                val matches = (0 until results.length()).mapNotNull { index ->
                    results.optJSONObject(index)?.toMatch()
                }
                if (matches.isEmpty()) RecognitionResult.NoMatch
                else RecognitionResult.Found(matches)
            } catch (t: Throwable) {
                Log.warn(TAG, "Search failed", t)
                RecognitionResult.Error(t.message ?: "Search failed.")
            } finally {
                connection.disconnect()
            }
        }

    private fun JSONObject.toMatch(): MusicMatch? {
        val title = optString("trackName").takeIf { it.isNotBlank() } ?: return null
        return MusicMatch(
            title = title,
            artist = optString("artistName").takeIf { it.isNotBlank() },
            album = optString("collectionName").takeIf { it.isNotBlank() },
            genre = optString("primaryGenreName").takeIf { it.isNotBlank() },
            // The API hands back a 100px thumbnail; ask for something usable.
            artworkUrl = optString("artworkUrl100")
                .takeIf { it.isNotBlank() }
                ?.replace("100x100bb", "400x400bb"),
            releaseYear = optString("releaseDate").take(4).toIntOrNull(),
            durationMs = optLong("trackTimeMillis", 0L).takeIf { it > 0L },
            source = MusicMatch.Source.SEARCH,
            drmProtected = true
        )
    }

    private companion object {
        const val TAG = "MusicSearchClient"
    }
}

private fun encodeQuery(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8")
