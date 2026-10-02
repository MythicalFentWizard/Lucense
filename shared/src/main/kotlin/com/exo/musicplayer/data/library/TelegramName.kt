package com.exo.musicplayer.data.library

/** A "Title   Artist" filename, which is the shape Telegram's exports arrive in. */
data class TelegramName(val title: String, val artist: String) {
    companion object {
        /** Two or more spaces: one space is part of a title, not a separator. */
        private val SEPARATOR = Regex("""\s{2,}""")

        fun of(name: String): TelegramName? {
            val parts = name.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 2) return null
            return TelegramName(parts.first(), parts.drop(1).joinToString(", "))
        }
    }
}
