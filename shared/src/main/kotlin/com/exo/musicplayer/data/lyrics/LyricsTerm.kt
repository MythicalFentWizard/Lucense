package com.exo.musicplayer.data.lyrics

/**
 * What gets searched for when lyrics are fetched.
 *
 * A file downloaded from YouTube is called "Song (Official Music Video) [4K]",
 * and no lyrics service has heard of that song. Which tidy-up helps depends
 * entirely on where a library came from, so this is a setting rather than a
 * guess made on everyone's behalf. Shared, so the phone and the PC search the
 * same way for the same setting.
 */
enum class LyricsTerm(val label: String, val note: String) {
    ARTIST_TITLE("Artist and title", "The tags exactly as they are. Right for a tidy library."),
    CLEAN_TITLE(
        "Tidied title",
        "Artist and title, with \"(Official Video)\", \"[HD]\", \"feat. ...\" and the like removed."
    ),
    TITLE_ONLY("Title only", "For files whose artist tag is wrong, or missing."),
    FILENAME("File name", "For files with no useful tags at all."),
    CUSTOM("Custom", "Your own pattern from {artist}, {title}, {album} and {file}.");

    /**
     * The title and artist to search with under this term. [pattern] is only
     * read for [CUSTOM]; [fileName] is the name without its extension.
     */
    fun query(
        artist: String?,
        title: String,
        album: String?,
        fileName: String,
        pattern: String
    ): Pair<String, String?> = when (this) {
        ARTIST_TITLE -> title to artist
        CLEAN_TITLE -> clean(title) to artist
        TITLE_ONLY -> clean(title) to null
        FILENAME -> clean(fileName) to null
        CUSTOM -> fill(pattern, artist, title, album, fileName) to null
    }

    companion object {
        fun of(name: String?): LyricsTerm = entries.firstOrNull { it.name == name } ?: ARTIST_TITLE

        private val NOISE = Regex(
            """\s*[(\[][^)\]]*\b(official|video|audio|lyrics?|hd|hq|4k|mv|remaster(ed)?|""" +
                """visuali[sz]er|explicit|full song|with lyrics)\b[^)\]]*[)\]]""",
            RegexOption.IGNORE_CASE
        )
        private val FEATURING = Regex(
            """\s*[(\[]?\s*\b(feat|ft|featuring)\b\.?\s[^)\]]*[)\]]?""",
            RegexOption.IGNORE_CASE
        )
        private val SPACES = Regex("""\s+""")

        /** Strips the decoration a downloaded file carries in its title. */
        fun clean(text: String): String {
            val stripped = text.replace(NOISE, "").replace(FEATURING, "")
            val tidy = stripped.replace(SPACES, " ").trim().trim('-', '_', ' ')
            // Never hand back nothing: a title that was all decoration is
            // still a better search than an empty string.
            return tidy.ifBlank { text.trim() }
        }

        fun fill(pattern: String, artist: String?, title: String, album: String?, fileName: String): String =
            pattern
                .replace("{artist}", artist.orEmpty())
                .replace("{title}", title)
                .replace("{album}", album.orEmpty())
                .replace("{file}", fileName)
                .replace(SPACES, " ")
                .trim()
    }
}
