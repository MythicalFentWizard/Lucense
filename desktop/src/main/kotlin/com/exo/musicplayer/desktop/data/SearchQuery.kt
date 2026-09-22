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
    val unplayedOnly: Boolean = false
) {
    val isEmpty: Boolean
        get() = words.isEmpty() && artist.isEmpty() && album.isEmpty() && title.isEmpty() &&
            !favouritesOnly && !unplayedOnly

    fun matches(title: String, artist: String, album: String, favourite: Boolean, plays: Int): Boolean {
        if (favouritesOnly && !favourite) return false
        if (unplayedOnly && plays > 0) return false
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
                    value.isNotEmpty() -> words.add(value)
                }
            }
            return SearchQuery(words, artist, album, title, favourites, unplayed)
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
