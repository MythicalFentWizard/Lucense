package com.exo.musicplayer.desktop.audio

import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** A song's loudness over time, and where playback is in it. */
interface SongShape {
    /** Loudness of each 10 ms slice of the track, as far as it has been read. */
    val slices: FloatArray

    /** How many of [slices] are filled in. */
    val filled: Int

    /** The loudest slice read so far, to scale the rest against. */
    val loudest: Float

    /** Whether there is a track to draw. */
    val active: Boolean

    /** The playback position in milliseconds, moving smoothly. */
    fun positionMs(): Double
}

/**
 * The playing track's loudness, read ahead of playback, and a smooth clock for
 * where playback is.
 *
 * The Waves background draws the song as a graph scrolling past a "now" line,
 * what has played to one side and what is coming to the other. For the part
 * still to come to be known, the file is read once in the background - a
 * hundred loudness values a second, each the RMS of 10 ms - starting when the
 * track does and filling in as it goes; recent tracks are remembered. The
 * engine only reports its position four times a second, so between reports the
 * clock runs on at the playback speed and each report nudges it rather than
 * snapping it, which would make the graph step. A seek, or pausing, sets it
 * outright.
 *
 * Nothing is read unless something calls [sync], which only the Waves
 * background does.
 */
class SongGraph(private val engine: PlaybackEngine) : SongShape {

    @Volatile override var slices: FloatArray = FloatArray(0)
        private set
    @Volatile override var filled: Int = 0
        private set
    @Volatile override var loudest: Float = 0f
        private set
    override val active: Boolean get() = trackPath != null

    @Volatile private var trackPath: String? = null
    private var reader: Thread? = null
    private var lastStatus: PlaybackStatus? = null

    private val remembered = object : LinkedHashMap<String, Reading>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Reading>?) = size > 12
    }

    private class Reading(val slices: FloatArray, val filled: Int, val loudest: Float)

    @Volatile private var anchorMs = 0.0
    @Volatile private var anchorNanos = System.nanoTime()
    @Volatile private var playing = false

    /** Takes in the engine's latest report; cheap to call every frame. */
    fun sync() {
        val status = engine.status.value
        if (status === lastStatus) return
        lastStatus = status

        val path = status.track?.file?.absolutePath
        if (path != trackPath) {
            remember()
            trackPath = path
            read(status)
        }

        val reported = status.positionMs.toDouble()
        val predicted = positionMs()
        val now = System.nanoTime()
        anchorMs = if (!status.playing || !playing || abs(predicted - reported) > 400.0) {
            reported
        } else {
            predicted + (reported - predicted) * 0.2
        }
        anchorNanos = now
        playing = status.playing
    }

    override fun positionMs(): Double {
        if (!playing) return anchorMs
        return anchorMs + (System.nanoTime() - anchorNanos) / 1e6 * engine.effects.speed
    }

    private fun remember() {
        val path = trackPath ?: return
        if (filled > 0) synchronized(remembered) { remembered[path] = Reading(slices, filled, loudest) }
    }

    private fun read(status: PlaybackStatus) {
        reader?.interrupt()
        reader = null
        val track = status.track
        if (track == null) {
            slices = FloatArray(0)
            filled = 0
            loudest = 0f
            return
        }
        val known = synchronized(remembered) { remembered[track.file.absolutePath] }
        val size = ((max(track.durationMs, 1_000L) / SLICE_MS) + 100).toInt()
        if (known != null && known.filled >= known.slices.size - 100) {
            slices = known.slices
            filled = known.filled
            loudest = known.loudest
            return
        }
        val target = FloatArray(size)
        slices = target
        filled = 0
        loudest = 0f
        reader = thread(name = "resonate-song-graph", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            runCatching {
                Decoder.open(track.file).use { decoder ->
                    var slice = 0
                    var sum = 0.0
                    var count = 0
                    while (slice < target.size && !Thread.currentThread().isInterrupted) {
                        val chunk = decoder.read(FRAMES_PER_SLICE * 10) ?: break
                        var i = 0
                        while (i + 1 < chunk.size) {
                            val mono = (chunk[i] + chunk[i + 1]) * 0.5f
                            sum += mono * mono
                            if (++count == FRAMES_PER_SLICE) {
                                if (slice < target.size) {
                                    val value = sqrt(sum / count).toFloat()
                                    target[slice] = value
                                    if (value > loudest) loudest = value
                                }
                                slice++
                                sum = 0.0
                                count = 0
                            }
                            i += 2
                        }
                        filled = slice.coerceAtMost(target.size)
                    }
                }
            }
        }
    }

    private companion object {
        const val SLICE_MS = 10L
        const val FRAMES_PER_SLICE = 441
    }
}
