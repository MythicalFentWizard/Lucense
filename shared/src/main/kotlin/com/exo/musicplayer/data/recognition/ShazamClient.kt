package com.exo.musicplayer.data.recognition

import com.exo.musicplayer.data.net.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.UUID

/**
 * Sends a [ShazamSignature] to Shazam's recognition endpoint.
 *
 * No API key and no account: the request carries only the fingerprint. This is
 * the same approach the open-source SongRec client uses, and it is worth being
 * clear that the endpoint is undocumented and unofficial — it can change or
 * start refusing requests at any time, which the UI surfaces rather than hides.
 *
 * Only frequency peaks leave the machine. The audio never does.
 */
class ShazamClient {

    suspend fun recognize(samples16k: FloatArray): RecognitionResult =
        withContext(Dispatchers.IO) {
            if (samples16k.isEmpty()) {
                return@withContext RecognitionResult.Error("No audio to identify.")
            }

            val signature = runCatching { ShazamSignature.generate(samples16k) }
                .getOrElse {
                    Log.warn(TAG, "Fingerprinting failed", it)
                    return@withContext RecognitionResult.Error("Could not fingerprint the audio.")
                }

            val uri = DATA_URI_PREFIX + Base64.getEncoder().encodeToString(signature)
            val timestamp = System.currentTimeMillis()
            val body = JSONObject().apply {
                put("geolocation", JSONObject().apply {
                    put("altitude", 300)
                    put("latitude", 45)
                    put("longitude", 2)
                })
                put("signature", JSONObject().apply {
                    put(
                        "samplems",
                        (samples16k.size.toFloat() / ShazamSignature.SAMPLE_RATE * 1000).toInt()
                    )
                    put("timestamp", timestamp.toInt())
                    put("uri", uri)
                })
                put("timestamp", timestamp.toInt())
                put("timezone", "Europe/Paris")
            }.toString()

            val url = URL(
                "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/" +
                    "${UUID.randomUUID().toString().uppercase()}/${UUID.randomUUID()}" +
                    "?sync=true&webv3=true&sampling=true&connected=" +
                    "&shazamapiversion=v3&sharehub=true&video=v3"
            )

            val connection = try {
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Content-Language", "en_US")
                    setRequestProperty("User-Agent", USER_AGENT)
                }
            } catch (t: Throwable) {
                return@withContext RecognitionResult.Error("Couldn't reach Shazam.")
            }

            try {
                connection.outputStream.use { it.write(body.toByteArray()) }
                val code = connection.responseCode
                if (code == 429) {
                    return@withContext RecognitionResult.Error(
                        "Shazam is rate-limiting this network. Try again in a few minutes."
                    )
                }
                val text = if (code in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                if (text.isBlank()) {
                    return@withContext RecognitionResult.Error("Empty response (HTTP $code).")
                }
                parse(text)
            } catch (t: Throwable) {
                Log.warn(TAG, "recognize failed", t)
                RecognitionResult.Error(t.message ?: "Identification failed.")
            } finally {
                connection.disconnect()
            }
        }

    private fun parse(body: String): RecognitionResult = try {
        val root = JSONObject(body)
        val track = root.optJSONObject("track")
        if (track == null) {
            RecognitionResult.NoMatch
        } else {
            val title = track.optString("title").takeIf { it.isNotBlank() }
            if (title == null) {
                RecognitionResult.NoMatch
            } else {
                RecognitionResult.Found(listOf(track.toMatch(title)))
            }
        }
    } catch (t: Throwable) {
        RecognitionResult.Error("Could not read Shazam's response.")
    }

    private fun JSONObject.toMatch(title: String): MusicMatch {
        val artist = optString("subtitle").takeIf { it.isNotBlank() }
        val artwork = optJSONObject("images")?.let { images ->
            images.optString("coverarthq").takeIf { it.isNotBlank() }
                ?: images.optString("coverart").takeIf { it.isNotBlank() }
        }

        // Album and year live in a free-form "metadata" list inside sections.
        var album: String? = null
        var year: Int? = null
        optJSONArray("sections")?.let { sections ->
            for (i in 0 until sections.length()) {
                val metadata = sections.optJSONObject(i)?.optJSONArray("metadata") ?: continue
                for (j in 0 until metadata.length()) {
                    val entry = metadata.optJSONObject(j) ?: continue
                    when (entry.optString("title")) {
                        "Album" -> album = entry.optString("text").takeIf { it.isNotBlank() }
                        "Released" -> year = entry.optString("text").takeIf { it.isNotBlank() }
                            ?.takeLast(4)?.toIntOrNull()
                    }
                }
            }
        }

        return MusicMatch(
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artwork,
            releaseYear = year,
            source = MusicMatch.Source.FINGERPRINT
        )
    }

    private companion object {
        const val TAG = "ShazamClient"
        const val DATA_URI_PREFIX = "data:audio/vnd.shazam.sig;base64,"
        const val USER_AGENT =
            "Dalvik/2.1.0 (Linux; U; Android 13; Pixel 7 Build/TQ2A.230505.002)"
    }
}
