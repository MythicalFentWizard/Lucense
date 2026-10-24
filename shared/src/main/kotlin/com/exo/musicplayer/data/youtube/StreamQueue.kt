package com.exo.musicplayer.data.youtube

import com.exo.musicplayer.data.playlist.ParsedPlaylist
import java.util.concurrent.ConcurrentHashMap

/**
 * Streaming: songs played online one after another without downloading them,
 * after Riff's streaming (github.com/rootscripts/riff, MIT).
 *
 * It goes through the preview player and its small player rather than the
 * library's: what is streamed isn't in the library, mustn't land in the queue
 * or the play counts, and goes when it is closed. The small player gains a
 * next and a previous, and says how far down the list it is.
 */
data class StreamItem(
    val title: String,
    val artist: String? = null,
    /**
     * The page it plays from - a YouTube video, a SoundCloud track. Null when
     * only its name is known (a song from a Spotify list), and it is found on
     * YouTube when its turn comes, the way a download of it would be.
     */
    val link: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null
)

data class StreamQueue(
    val name: String,
    val items: List<StreamItem>,
    val index: Int = 0,
    /** Tells one list's keys from another's, so a new list never mistakes the old one's ending for its own. */
    val id: Long = System.nanoTime()
) {
    val current: StreamItem? get() = items.getOrNull(index)
    val hasNext: Boolean get() = index + 1 < items.size
    val hasPrevious: Boolean get() = index > 0

    /** The preview key for the song at [at]. */
    fun key(at: Int = index): String = "stream:$id:$at"

    /** Whether a preview key is one of this list's. */
    fun owns(key: String?): Boolean = key?.startsWith("stream:$id:") == true

    /** What the small player says: the song, and its place in the list. */
    fun label(at: Int = index): PreviewLabel {
        val item = items[at]
        return PreviewLabel(
            item.title,
            listOfNotNull(item.artist?.takeIf { it.isNotBlank() }, "${at + 1} of ${items.size}").joinToString(" · "),
            item.artworkUrl
        )
    }

    companion object {
        fun ofVideos(name: String, videos: List<YouTubeVideo>, start: Int = 0): StreamQueue = StreamQueue(
            name,
            videos.map {
                StreamItem(
                    title = it.title,
                    artist = it.channel.removeSuffix(" - Topic").takeUnless { c -> c == "Unknown channel" },
                    link = it.watchUrl,
                    durationMs = (it.durationSeconds ?: 0) * 1000L,
                    artworkUrl = it.thumbnail(YouTubeVideo.ThumbSize.SMALL)
                )
            },
            index = start.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
        )

        fun ofPlaylist(playlist: ParsedPlaylist): StreamQueue = StreamQueue(
            playlist.name,
            playlist.entries.map { StreamItem(it.title, it.artist, it.link, it.durationMs) }
        )
    }
}

/**
 * Where a [StreamItem] plays from. Finding that takes seconds - yt-dlp signs
 * the address, and a song known only by name is searched for first - so the
 * next song is looked up while this one plays, and its address kept for a
 * while (YouTube's last about six hours; an hour is well inside that).
 */
class StreamResolver(
    /** The audio's own address, from a page: yt-dlp's `-g`. */
    private val audioUrl: suspend (page: String) -> String?,
    private val finder: YouTubeLinkFinder
) {
    private val found = ConcurrentHashMap<String, Pair<String, Long>>()

    suspend fun resolve(item: StreamItem): String? {
        val key = listOf(item.link.orEmpty(), item.artist.orEmpty(), item.title).joinToString("\u0001")
        found[key]?.let { (url, at) -> if (System.currentTimeMillis() - at < KEEP_MS) return url }
        val page = item.link ?: run {
            val wanted = YouTubeLinkFinder.Wanted(
                title = item.title,
                artist = item.artist,
                durationMs = item.durationMs.takeIf { it > 0 }
            )
            (finder.find(wanted) as? YouTubeLinkFinder.Outcome.Found)?.pick?.video?.watchUrl
        } ?: return null
        val url = audioUrl(page) ?: return null
        found[key] = url to System.currentTimeMillis()
        return url
    }

    private companion object {
        const val KEEP_MS = 60 * 60 * 1000L
    }
}
