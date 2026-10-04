package com.exo.musicplayer.data.discover

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How Discover's numbers, dates and kinds are worded, the same on the phone and on Windows. */
object DiscoverWords {

    /** 2208 as "2,208". */
    fun grouped(number: Int): String = String.format(Locale.US, "%,d", number)

    /** 12345 as "12K", 1900000 as "1.9M": fan counts. */
    fun compact(number: Int): String = when {
        number >= 1_000_000 -> String.format(Locale.US, "%.1fM", number / 1_000_000.0)
        number >= 10_000 -> "${number / 1000}K"
        number >= 1_000 -> String.format(Locale.US, "%.1fK", number / 1000.0)
        else -> number.toString()
    }

    fun fans(count: Int): String = "${compact(count)} ${if (count == 1) "fan" else "fans"}"

    /** Deezer's "single", "ep", "album" as a word to show. */
    fun kind(kind: String): String = when (kind.lowercase(Locale.ROOT)) {
        "single" -> "Single"
        "ep" -> "EP"
        "compile", "compilation" -> "Compilation"
        else -> "Album"
    }

    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** "2026-09-18" as "18 Sep 2026", and as "out 18 Sep 2026" while that is still ahead. */
    fun date(date: String): String? {
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        val text = day.format(DAY_MONTH_YEAR)
        return if (day.isAfter(LocalDate.now())) "out $text" else text
    }

    /** "Swervedriver · Album · 2 Oct 2026". */
    fun release(release: DiscoverRelease, withArtist: Boolean): String = listOfNotNull(
        release.artist.takeIf { withArtist && it.isNotBlank() },
        kind(release.kind),
        date(release.date)
    ).joinToString(" · ")

    /** What "Download all" should say for a release with [wanted] of [all] songs still to fetch. */
    fun downloadAll(wanted: Int, all: Int): String = when {
        wanted == 0 -> "Nothing left to download"
        wanted == all && all == 1 -> "Download"
        wanted == all -> "Download all $wanted"
        else -> "Download the other $wanted"
    }

    const val UNREACHABLE_HOME =
        "Deezer couldn't be reached, so new releases can't be looked up. It is blocked in some " +
            "countries; with a VPN on, try again. Genres still work."
}
