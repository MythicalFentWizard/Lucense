package com.exo.musicplayer.data.net

import java.net.HttpURLConnection
import java.net.URL

/** One-line GET helper shared by the metadata and lyrics providers. */
object Http {

    private const val TAG = "Http"
    const val USER_AGENT = "Resonate/2.0 (Android music player)"

    fun get(url: String, headers: Map<String, String> = emptyMap()): String? = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    } catch (t: Throwable) {
        Log.warn(TAG, "GET failed: $url", t)
        null
    }
}
