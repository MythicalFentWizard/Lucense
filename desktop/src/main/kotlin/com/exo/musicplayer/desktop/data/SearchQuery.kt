package com.exo.musicplayer.desktop.data

import java.text.Normalizer
import java.util.Locale

/**
 * What was typed into the search box.
 *
 * Plain words match the title, the artist or the album, as they always did.
 * A word of the form `artist:proz` narrows to that field, `fav:` to the ones
 * you hearted and `unplayed:` to the ones you never started. Quotes hold a
 * phrase together: `artist:"tyler, the creator"`.
 *
 * Everything is compared with accents stripped, so "Proz" finds "Pröz" - a
 * library full of names nobody can type on a plain keyboard is the normal case
 * here, not the exception.
 */
data class SearchQuery(
    val words: List<String> = emptyList(),
    val artist: List<String> = emptyList(),
    val album: List<String> = emptyList(),
    val title: List<String> = emptyList(),
    val favouritesOnly: Boolean = false,
    val unplayedOnly: Boolean = false,
    val ratingFrom: Int? = null,
    val ratingTo: Int? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val playsFrom: Int? = null,
    val playsTo: Int? = null,
    /** Added to the library within this many days. */
    val addedWithinDays: Int? = null
) {
    val isEmpty: Boolean
        get() = words.isEmpty() && artist.isEmpty() && album.isEmpty() && title.isEmpty() &&
            !favouritesOnly && !unplayedOnly && ratingFrom == null && ratingTo == null &&
            yearFrom == null && yearTo == null && playsFrom == null && playsTo == null &&
            addedWithinDays == null

    fun matches(
        title: String,
        artist: String,
        album: String,
        favourite: Boolean,
        plays: Int,
        rating: Int = 0,
        year: Int? = null,
        addedAt: Long = 0L
    ): Boolean {
        if (favouritesOnly && !favourite) return false
        if (unplayedOnly && plays > 0) return false
        ratingFrom?.let { if (rating < it) return false }
        ratingTo?.let { if (rating > it) return false }
        playsFrom?.let { if (plays < it) return false }
        playsTo?.let { if (plays > it) return false }
        if (yearFrom != null || yearTo != null) {
            val actual = year ?: return false
            yearFrom?.let { if (actual < it) return false }
            yearTo?.let { if (actual > it) return false }
        }
        addedWithinDays?.let {
            if (addedAt <= 0L) return false
            if (System.currentTimeMillis() - addedAt > it * 86_400_000L) return false
        }
        val foldedTitle = fold(title)
        val foldedArtist = fold(artist)
        val foldedAlbum = fold(album)
        if (this.artist.any { !foldedArtist.contains(it) }) return false
        if (this.album.any { !foldedAlbum.contains(it) }) return false
        if (this.title.any { !foldedTitle.contains(it) }) return false
        return words.all {
            foldedTitle.contains(it) || foldedArtist.contains(it) || foldedAlbum.contains(it)
        }
    }

    companion object {
        private val MARKS = Regex("\\p{M}+")

        /** Lowercase, and without the accents that make a name unsearchable. */
        fun fold(text: String): String =
            MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFKD), "").lowercase(Locale.ROOT)

        fun of(text: String): SearchQuery {
            val words = mutableListOf<String>()
            val artist = mutableListOf<String>()
            val album = mutableListOf<String>()
            val title = mutableListOf<String>()
            var favourites = false
            var unplayed = false
            var ratingFrom: Int? = null
            var ratingTo: Int? = null
            var yearFrom: Int? = null
            var yearTo: Int? = null
            var playsFrom: Int? = null
            var playsTo: Int? = null
            var addedWithin: Int? = null

            for (token in tokenise(text)) {
                val colon = token.indexOf(':')
                val field = if (colon > 0) token.take(colon).lowercase(Locale.ROOT) else ""
                val value = if (colon > 0) fold(token.drop(colon + 1).trim('"')) else fold(token)
                when {
                    field == "artist" || field == "by" -> if (value.isNotEmpty()) artist.add(value)
                    field == "album" -> if (value.isNotEmpty()) album.add(value)
                    field == "title" || field == "track" -> if (value.isNotEmpty()) title.add(value)
                    field == "fav" || field == "loved" -> favourites = value != "no"
                    field == "unplayed" || field == "new" -> unplayed = value != "no"
                    field == "rating" || field == "stars" -> span(value)?.let {
                        ratingFrom = it.first
                        ratingTo = it.second
                    }
                    field == "year" -> span(value)?.let {
                        yearFrom = it.first
                        yearTo = it.second
                    }
                    field == "plays" || field == "played" -> span(value)?.let {
                        playsFrom = it.first
                        playsTo = it.second
                    }
                    field == "added" -> addedWithin = value.trimEnd('d').trim().toIntOrNull()
                    value.isNotEmpty() -> words.add(value)
                }
            }
            return SearchQuery(
                words, artist, album, title, favourites, unplayed,
                ratingFrom, ratingTo, yearFrom, yearTo, playsFrom, playsTo, addedWithin
            )
        }

        /**
         * A number written as a bound: `4` exactly, `4+` or `>=4` at least,
         * `<3` below, `2010-2015` between. Null when it is not a number at all,
         * which leaves that rule out rather than matching nothing.
         */
        internal fun span(value: String): Pair<Int?, Int?>? {
            val text = value.trim()
            if (text.isEmpty()) return null
            return when {
                text.endsWith("+") -> text.dropLast(1).toIntOrNull()?.let { it to null }
                text.startsWith(">=") -> text.drop(2).toIntOrNull()?.let { it to null }
                text.startsWith("<=") -> text.drop(2).toIntOrNull()?.let { null to it }
                text.startsWith(">") -> text.drop(1).toIntOrNull()?.let { (it + 1) to null }
                text.startsWith("<") -> text.drop(1).toIntOrNull()?.let { null to (it - 1) }
                text.contains('-') && text.indexOf('-') > 0 -> {
                    val halves = text.split('-', limit = 2)
                    val low = halves[0].trim().toIntOrNull()
                    val high = halves[1].trim().toIntOrNull()
                    if (low == null && high == null) null else low to high
                }
                else -> text.toIntOrNull()?.let { it to it }
            }
        }

        /** Splits on spaces, except inside quotes. */
        private fun tokenise(text: String): List<String> {
            val out = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            for (character in text) {
                when {
                    character == '"' -> {
                        quoted = !quoted
                        current.append(character)
                    }
                    character.isWhitespace() && !quoted -> {
                        if (current.isNotEmpty()) out.add(current.toString())
                        current.clear()
                    }
                    else -> current.append(character)
                }
            }
            if (current.isNotEmpty()) out.add(current.toString())
            return out
        }
    }
}
