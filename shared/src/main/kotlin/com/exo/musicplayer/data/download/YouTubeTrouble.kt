package com.exo.musicplayer.data.download

import com.exo.musicplayer.data.net.Http
import org.json.JSONObject
import java.net.Proxy
import java.net.URLEncoder

/** What YouTube says when it won't give a song, in words that mean something outside a terminal. */
object YouTubeTrouble {

    /**
     * How yt-dlp reports YouTube refusing the connection rather than the
     * video. "Sign in to confirm your age" is not one of them: that is about
     * the video, and the next one will download.
     */
    private val REFUSALS = listOf("not a bot", "HTTP Error 403", "HTTP Error 429", "Too Many Requests")

    fun refused(line: String?): Boolean =
        line != null && REFUSALS.any { line.contains(it, ignoreCase = true) }

    /** Why YouTube refuses a connection, and what helps. */
    const val WHY_REFUSED =
        "YouTube does that after a lot of downloads in a row, to VPN addresses it has flagged, and " +
            "when yt-dlp is out of date. Wait an hour, update yt-dlp, or try without the VPN or on " +
            "another server."

    /** What to tell someone whose YouTube download failed, from yt-dlp's error line. */
    fun explain(said: String?): String {
        val line = said?.trim().orEmpty()
        return when {
            refused(line) -> "YouTube refused this connection. $WHY_REFUSED"
            line.contains("Video unavailable", ignoreCase = true) ->
                "YouTube says this video is unavailable: removed, private, or not offered to this connection."
            else -> reason(line).ifBlank { "The download failed." }
        }
    }

    /**
     * yt-dlp's error line without its "ERROR: [youtube] abc123:" prefix or its
     * advice about command-line cookies, which means nothing in an app.
     */
    fun reason(line: String): String {
        val said = line.replace(ERROR_PREFIX, "").replace(COOKIE_ADVICE, "").trim()
        return if (said.contains("confirm your age", ignoreCase = true)) {
            "YouTube only plays this video for signed-in adults."
        } else {
            said
        }
    }

    /**
     * A YouTube link's title from its oEmbed card: one light request instead
     * of a full extraction. Blocking; call it off the main thread.
     */
    fun title(link: String, proxy: Proxy? = null): String? {
        val url = "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(link, "UTF-8")
        val json = Http.get(url, proxy = proxy) ?: return null
        return runCatching { JSONObject(json).optString("title").takeIf { it.isNotBlank() } }.getOrNull()
    }

    private val ERROR_PREFIX = Regex("""^\s*ERROR:\s*(\[[^\]]+]\s*)?([\w-]+:\s*)?""")
    private val COOKIE_ADVICE = Regex("""\s*Use --cookies.*$""")
}
