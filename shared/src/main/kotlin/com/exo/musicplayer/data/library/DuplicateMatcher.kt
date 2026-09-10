package com.exo.musicplayer.data.library

import java.util.Locale
import kotlin.math.abs

/**
 * The "is this the same song twice?" heuristic, independent of storage.
 *
 * Android matches Room entities and Windows matches files on disk, but the
 * judgement is identical, and it is the part worth getting right: a wrong guess
 * costs someone a file they cannot get back. Keeping one implementation means
 * the two platforms cannot quietly drift apart on what counts as a duplicate.
 */
object DuplicateMatcher {

    /** Tracks whose lengths differ by more than this are treated as different. */
    const val DURATION_TOLERANCE_MS = 5_000L

    private val NOISE = Regex(
        """\((?:official|official video|official audio|lyrics|lyric video|audio|hd|hq|"""
            + """remaster(?:ed)?(?:\s+\d{4})?|explicit|clean|visualizer)\)""",
        RegexOption.IGNORE_CASE
    )
    private val BRACKETS = Regex("""\[[^\]]*]""")
    private val FEATURED = Regex("""\b(feat\.?|ft\.?|featuring)\b.*""", RegexOption.IGNORE_CASE)
    private val NON_ALPHANUMERIC = Regex("""[^a-z0-9]+""")
    private val RUNS_OF_SPACE = Regex("""\s+""")

    /**
     * Reduces a title to something comparable: "Song (Official Video)" and
     * "Song feat. Someone" both collapse to "song".
     */
    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.lowercase(Locale.ROOT)
            .replace(NOISE, " ")
            .replace(BRACKETS, " ")
            .replace(FEATURED, " ")
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
            .replace(RUNS_OF_SPACE, " ")
    }

    /** One duplicate set: the copy worth keeping, and the rest. */
    data class Group<T>(val keep: T, val remove: List<T>)

    /**
     * @param keeperOrder sorted ascending; the first item survives.
     */
    fun <T> group(
        items: List<T>,
        artistOf: (T) -> String?,
        titleOf: (T) -> String?,
        durationOf: (T) -> Long,
        keeperOrder: Comparator<T>
    ): List<Group<T>> {
        val buckets = items
            .groupBy { "${normalize(artistOf(it))}|${normalize(titleOf(it))}" }
            .filterKeys { it != "|" }

        return buckets.values.flatMap { candidates ->
            if (candidates.size < 2) return@flatMap emptyList()

            // Within a title match, split further by length so a short edit and
            // a full version are not treated as copies of each other.
            val byLength = mutableListOf<MutableList<T>>()
            for (item in candidates.sortedBy(durationOf)) {
                val length = durationOf(item)
                val slot = byLength.firstOrNull { existing ->
                    val reference = durationOf(existing.first())
                    reference <= 0 || length <= 0 ||
                        abs(reference - length) <= DURATION_TOLERANCE_MS
                }
                if (slot == null) byLength += mutableListOf(item) else slot += item
            }

            byLength.filter { it.size > 1 }.map { found ->
                val ordered = found.sortedWith(keeperOrder)
                Group(keep = ordered.first(), remove = ordered.drop(1))
            }
        }
    }
}
