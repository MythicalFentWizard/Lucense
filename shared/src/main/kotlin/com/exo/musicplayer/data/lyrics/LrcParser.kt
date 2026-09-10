package com.exo.musicplayer.data.lyrics

/** One timed line of an LRC file. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Parses LRC-format lyrics.
 *
 * Handles the two things real files actually do beyond the basic format: several
 * timestamps sharing one line (a repeated chorus is written once), and the
 * `[ar:]`/`[ti:]` metadata tags, which must not be rendered as lyrics.
 */
object LrcParser {

    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val METADATA = Regex("""^\[[a-zA-Z#]+:.*]$""")

    fun parse(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()

        for (raw in lrc.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || METADATA.matches(line)) continue

            val stamps = TIMESTAMP.findAll(line).toList()
            if (stamps.isEmpty()) continue

            val text = line.substring(stamps.last().range.last + 1).trim()
            for (stamp in stamps) {
                val minutes = stamp.groupValues[1].toLongOrNull() ?: continue
                val seconds = stamp.groupValues[2].toLongOrNull() ?: continue
                val fraction = stamp.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                lines += LyricLine(minutes * 60_000 + seconds * 1_000 + millis, text)
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    /**
     * Index of the line that should be highlighted at [positionMs], or -1 before
     * the first line starts.
     */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
