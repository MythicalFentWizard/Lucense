package com.exo.musicplayer.data.discover

import com.exo.musicplayer.data.youtube.YouTubeVideo
import java.text.Normalizer
import java.util.Locale

/**
 * Whether a video YouTube suggested is a song, and if it is, whose and called
 * what - from what the home page says of it, without opening it.
 *
 * YouTube marks its music in ways a listing shows:
 *
 *  - **"- Topic" channels** are YouTube Music's own: every video on one is a
 *    track, named after the artist.
 *  - **VEVO channels** are the labels' artist channels.
 *  - **Titles** carry it: "(Official Video)", "[Lyrics]", "Visualizer",
 *    "(slowed + reverb)", "feat.", "Artist - Song".
 *
 * Against it: Shorts and live streams, anything under 45 seconds or longer
 * than a song, and titles that say reaction, podcast, trailer, gameplay and
 * the like. What is left - "Artist - Song" with nothing else to go on, or
 * just a song's length - is [Verdict.MAYBE]: worth asking a music catalogue
 * whether that artist has that song (see [Discovery.song]).
 */
object SongOrNot {

    enum class Verdict { SONG, MAYBE, NOT }

    data class Judgement(
        val verdict: Verdict,
        /** The artist, where one can be told; for a MAYBE, the one to ask the catalogue about. */
        val artist: String?,
        /** The title without the parts that only make sense on YouTube. */
        val title: String,
        /** Why, in a few words for the page. */
        val why: String
    )

    fun judge(video: YouTubeVideo): Judgement {
        // Decorated letters - "𝗕𝗔𝗜𝗟𝗔 𝗟𝗘𝗡𝗧𝗢", popular with edit channels - read as the plain ones.
        val raw = Normalizer.normalize(video.title, Normalizer.Form.NFKC).trim()
        val channel = Normalizer.normalize(video.channel, Normalizer.Form.NFKC).trim()
        val seconds = video.durationSeconds
        val names = names(raw, channel)
        val title = names.title
        val artist = names.artist

        fun song(why: String) = Judgement(Verdict.SONG, artist, title, why)
        fun maybe(why: String) = Judgement(Verdict.MAYBE, artist, title, why)
        fun not(why: String) = Judgement(Verdict.NOT, artist, title, why)

        // YouTube Music's own tracks are songs whatever they are called.
        if (channel.endsWith(TOPIC)) {
            return when {
                video.isShort -> not("a Short")
                seconds != null && seconds > MAX_TRACK_SECONDS -> not("longer than a song")
                else -> song("a YouTube Music track")
            }
        }
        if (video.isShort) return not("a Short")
        if (seconds != null && seconds < MIN_SECONDS) return not("under 45 seconds")
        if (seconds != null && seconds > MAX_SECONDS) return not("longer than a song")
        NOT_MUSIC.find(raw)?.let { return not("says \"${it.value.trim().lowercase(Locale.ROOT)}\"") }

        return when {
            VEVO.containsMatchIn(channel) -> song("from the artist's VEVO channel")
            names.promo -> song("an official video, audio or lyric video")
            EDIT.containsMatchIn(raw) -> song("a remix or an edit")
            FEAT.containsMatchIn(raw) -> song("credits a featured artist")
            names.split -> maybe("titled \"artist - song\"")
            seconds != null && seconds in MAYBE_FROM..MAYBE_TO -> maybe("as long as a song")
            else -> not("nothing about it says song")
        }
    }

    private class Names(val artist: String?, val title: String, val promo: Boolean, val split: Boolean)

    /**
     * The artist and the song from a title and a channel. "Artist - Song" and
     * K-pop's "Artist 'Song'" are taken apart; otherwise the channel is the
     * artist. Brackets that only say what kind of upload it is go.
     */
    private fun names(raw: String, channel: String): Names {
        var promo = false
        // "Song | Official Video", "Song // Lyrics": a trailing part that is only about the upload.
        var title = raw.split(SEGMENT).let { parts ->
            val kept = parts.filterIndexed { index, part -> index == 0 || !PROMO_WORD.containsMatchIn(part).also { if (it) promo = true } }
            kept.joinToString(" | ")
        }
        title = BRACKET.replace(title) { match ->
            if (PROMO_WORD.containsMatchIn(match.groupValues[1]) && !EDIT.containsMatchIn(match.groupValues[1])) {
                promo = true
                " "
            } else {
                match.value
            }
        }
        if (PROMO_BARE.containsMatchIn(title)) {
            promo = true
            title = PROMO_BARE.replace(title, " ")
        }
        // Emoji and hashtags are decoration, not the song's name.
        title = title.replace(HASHTAG, " ").replace(EMOJI, " ")
        title = title.replace(SPACES, " ").trim().trim('-', '–', '—', '|', ' ')

        val fromChannel = channel.removeSuffix(TOPIC).replace(VEVO, "").replace(OFFICIAL, "").trim()
            .takeIf { it.isNotEmpty() && it != "Unknown channel" }
        // "Artist - Song", usually; edit channels also write "Song (slowed) - DJ Name"
        // and "Song - SLOWED - DJ Name". A part that only names the edit goes
        // back onto the song, and the parts swap when the song is plainly first.
        val parts = title.split(DASH).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2 && parts[0].length <= 80) {
            val edits = parts.filter { EDIT_ONLY.matches(it) }
            val named = parts.filterNot { EDIT_ONLY.matches(it) }
            if (named.size >= 2) {
                var artist = named[0]
                var song = named.drop(1).joinToString(" - ")
                if ((EDIT.containsMatchIn(artist) && !EDIT.containsMatchIn(song)) || ARTIST_PREFIX.containsMatchIn(song)) {
                    artist = song.also { song = artist }
                }
                if (edits.isNotEmpty()) song += edits.joinToString(" ", prefix = " (", postfix = ")")
                return Names(artist, song, promo, split = true)
            }
        }
        if (promo) {
            QUOTED.find(title)?.let { parts ->
                return Names(parts.groupValues[1].trim(), parts.groupValues[2].trim(), promo, split = true)
            }
        }
        return Names(fromChannel, title.ifEmpty { raw }, promo, split = false)
    }

    private const val TOPIC = " - Topic"
    private const val MIN_SECONDS = 45
    private const val MAX_SECONDS = 15 * 60
    private const val MAX_TRACK_SECONDS = 30 * 60
    private const val MAYBE_FROM = 90
    private const val MAYBE_TO = 7 * 60

    private val I = setOf(RegexOption.IGNORE_CASE)

    private val VEVO = Regex("""\s*VEVO$""", I)
    private val OFFICIAL = Regex("""\s+(official|officiel|oficial)$""", I)
    private val SPACES = Regex("""\s+""")
    private val HASHTAG = Regex("""#[\p{L}\p{N}_]+""")
    private val EMOJI = Regex("""[\p{So}\x{FE0F}\x{200D}\x{1F3FB}-\x{1F3FF}]+""")
    private val SEGMENT = Regex("""\s+(?:\||//)\s+""")
    private val BRACKET = Regex("""\s*[(\[【「『]([^)\]】」』]*)[)\]】」』]""")

    /** Words that, in brackets or after a bar, only say what kind of upload it is. */
    private val PROMO_WORD = Regex(
        """\b(official|lyrics?|audio|video|visuali[sz]er|mv|m/v|clip|hd|hq|4k|explicit|videoclip|oficial|officiel|color coded)\b""",
        I
    )

    /** The same, standing in the title without brackets. */
    private val PROMO_BARE = Regex(
        """\b(official\s+(music\s+)?(video|audio|visuali[sz]er|lyric\s+video|mv)|lyric\s+video|music\s+video|clip\s+officiel|video\s+oficial|m/?v)\b""",
        I
    )

    private val EDIT = Regex(
        """\b(slowed|reverb|sped\s*up|speed\s*up|nightcore|daycore|remix|bass\s*boosted|8d|mashup|instrumental|acapella|a\s*cappella|cover|phonk|jersey\s*club|bootleg|type\s*beat|lo-?fi)\b""",
        I
    )

    private val FEAT = Regex("""(\bft\.|\bfeat\b\.?|\bfeaturing\b|\bprod\.?\s+by\b)""", I)

    /** Titles that say it is something other than a song. */
    private val NOT_MUSIC = Regex(
        """(#shorts\b|\bpodcast\b|\bepisode\s*\d|\bep\.?\s*\d+\b|\breact(s|ion|ions|ing)?\b|\breview\b|\btrailer\b|\bteaser\b|""" +
            """\bgameplay\b|\bwalkthrough\b|\blet'?s\s+play\b|\bplaythrough\b|\btutorial\b|\bhow\s+to\b|\bvlog\b|""" +
            """\bunboxing\b|\bexplained\b|\bdocumentary\b|\binterview\b|\bhighlights\b|\bfull\s+match\b|""" +
            """\bcompilation\b|\bspeedrun\b|\btier\s+list\b|\blive\s*stream\b|\basmr\b|\btop\s+\d+\b|""" +
            """\bbehind\s+the\s+scenes\b|\bmaking\s+of\b|\bq\s*&\s*a\b|\bfull\s+album\b|\bfull\s+concert\b)""",
        I
    )

    private val DASH = Regex("""\s+[-–—]\s+""")

    /** A part of a title that is only the name of an edit: "SLOWED", "super slowed + reverb". */
    private val EDIT_ONLY = Regex(
        """(?:(?:super|ultra)\s+)?(?:slowed|sped\s*up|speed\s*up|reverb|nightcore|daycore|bass\s*boosted)(?:\s*\+\s*reverb)?""",
        I
    )

    /** How a DJ or an MC is credited, which makes that part the artist. */
    private val ARTIST_PREFIX = Regex("""^(dj|mc)\s""", I)
    private val QUOTED = Regex("""^(.{1,60}?)\s*['‘“"「『]([^'’”"」』]{1,100})['’”"」』]""")
}
