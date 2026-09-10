package com.exo.musicplayer.data.download

import com.exo.musicplayer.data.net.Http

/** What a pasted link turns into before yt-dlp sees it. */
sealed interface ResolvedLink {
    /** yt-dlp can fetch this URL directly. */
    data class Direct(val url: String, val service: String) : ResolvedLink

    /**
     * The service can't be downloaded from, but its metadata was readable, so
     * the track is looked up by name instead.
     */
    data class Search(val query: String, val display: String, val note: String) : ResolvedLink

    data class Unsupported(val reason: String) : ResolvedLink
}

/**
 * Works out how a pasted link should be handled.
 *
 * yt-dlp downloads YouTube and SoundCloud natively — SoundCloud has had a
 * maintained extractor for years. Spotify has none, and cannot: its audio is
 * Widevine-protected and never served as a plain stream. Tools advertising
 * "Spotify downloads" are all doing what this does — reading Spotify's metadata
 * and fetching the matching recording from somewhere that isn't Spotify.
 *
 * That distinction is surfaced to the user rather than hidden, because the
 * result is a *match*, not the Spotify file: usually the same recording, but
 * occasionally a live take, a remaster or a cover.
 */
object LinkResolver {

    private val OG_TITLE = Regex("""<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']""")
    private val OG_DESCRIPTION =
        Regex("""<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']+)["']""")

    fun resolve(rawUrl: String): ResolvedLink {
        val url = rawUrl.trim()
        if (!url.startsWith("http", ignoreCase = true)) {
            return ResolvedLink.Unsupported("That doesn't look like a link.")
        }
        val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }
            .getOrDefault("")

        return when {
            host.contains("spotify.com") -> resolveSpotify(url)
            host.contains("soundcloud.com") -> ResolvedLink.Direct(url, "SoundCloud")
            host.contains("youtube.com") || host.contains("youtu.be") ->
                ResolvedLink.Direct(url, "YouTube")
            host.contains("bandcamp.com") -> ResolvedLink.Direct(url, "Bandcamp")
            // yt-dlp supports well over a thousand sites; anything unrecognised
            // is worth handing over rather than refusing up front.
            else -> ResolvedLink.Direct(url, "link")
        }
    }

    private fun resolveSpotify(url: String): ResolvedLink {
        val page = Http.get(
            url,
            mapOf(
                "User-Agent" to BROWSER_AGENT,
                "Accept-Language" to "en"
            )
        ) ?: return ResolvedLink.Unsupported(
            "Couldn't read that Spotify page. Spotify itself can't be downloaded from — " +
                "its audio is DRM protected — so the track has to be looked up by name."
        )

        val title = OG_TITLE.find(page)?.groupValues?.get(1)?.let(::unescape)
            ?: return ResolvedLink.Unsupported(
                "Couldn't read the track name from that Spotify link."
            )

        // "Artist · Album · Song · 1987" — the first segment is the artist.
        val description = OG_DESCRIPTION.find(page)?.groupValues?.get(1)?.let(::unescape).orEmpty()
        val artist = description.split('·').firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        val query = listOfNotNull(artist, title).joinToString(" ")
        val display = if (artist != null) "$artist — $title" else title

        return ResolvedLink.Search(
            query = query,
            display = display,
            note = "Spotify can't be downloaded from directly, so this searches for " +
                "the same track elsewhere. Check the result is the version you wanted."
        )
    }

    /** yt-dlp treats this prefix as "search and take the first result". */
    fun searchTarget(query: String): String = "ytsearch1:$query"

    private fun unescape(text: String): String = text
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()

    private const val BROWSER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"
}
