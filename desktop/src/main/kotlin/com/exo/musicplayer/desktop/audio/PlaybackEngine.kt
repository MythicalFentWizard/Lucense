package com.exo.musicplayer.desktop.audio

import com.exo.musicplayer.data.audio.SpectrumAnalyser
import com.exo.musicplayer.desktop.library.DesktopTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

data class PlaybackStatus(
    val track: DesktopTrack? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    /** Set once the file has been played to its end, and only then. */
    val ended: Boolean = false
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * The Windows playback engine.
 *
 * One decode feeds every output. A dedicated thread pulls PCM from [Decoder],
 * runs it through [EffectChain], and writes the *same* buffer to each open
 * line — so playing to several devices costs one decode, and every device is
 * sample-identical by construction rather than by periodic resynchronisation.
 * That is the piece Android could not do properly.
 *
 * Writes to secondary devices are best-effort: a stalled or unplugged endpoint
 * is dropped rather than allowed to block the device actually being listened to.
 */
class PlaybackEngine {

    /** Position updates coarser than this are invisible on screen. */
    private val PUBLISH_INTERVAL_MS = 250L

    private val _status = MutableStateFlow(PlaybackStatus())
    val status: StateFlow<PlaybackStatus> = _status.asStateFlow()

    val effects = EffectChain()

    /** Band levels for the mixer display; idle unless something is showing it. */
    val spectrum = SpectrumAnalyser()

    /** The bass as it reaches the speakers, for the reactive backgrounds. */
    val beat = BeatTap()

    /**
     * What this track is multiplied by so it sits at the same loudness as the
     * rest. One when nothing has been measured, or when levelling is off.
     */
    @Volatile var trackGain = 1f

    private var worker: Thread? = null
    private var lines = listOf<SourceDataLine>()
    private var outputs = listOf<DesktopAudioOutput>()

    @Volatile private var playing = false
    @Volatile private var stopRequested = false
    @Volatile private var seekToMs: Long? = null
    @Volatile private var outputsDirty = false
    @Volatile private var framesPlayed = 0L

    /** Where a prepared track will start from when play is pressed. */
    @Volatile private var pendingStartMs = 0L
    private var lastPublished = -1L

    /**
     * The song to run into when this one ends, kept fed from outside.
     *
     * The old shape was: the track ends, the worker exits, the lines close, the
     * controller starts the next one and the lines open again. Every one of
     * those steps is silence, and together they are the gap between tracks.
     * Knowing what comes next lets the same thread carry straight on through
     * the same open lines.
     */
    @Volatile private var upNext: DesktopTrack? = null

    /** What the next song is multiplied by, the way [trackGain] is for this one. */
    @Volatile private var upNextGain = 1f

    /** Milliseconds the next song overlaps this one; 0 is a clean handover. */
    @Volatile var crossfadeMs: Int = 0

    /**
     * Called on the playback thread when the engine moves on by itself.
     *
     * Whoever listens must not block: this is the thread feeding the speakers.
     */
    var onAdvanced: ((finished: DesktopTrack, started: DesktopTrack) -> Unit)? = null

    fun setUpNext(track: DesktopTrack?, gain: Float) {
        upNext = track
        upNextGain = gain
    }

    /** Devices to play through. The first is primary; the rest are mirrors. */
    fun setOutputs(selected: List<DesktopAudioOutput>) {
        outputs = selected
        // Flagged rather than applied here: opening and closing lines from the
        // UI thread while the playback thread is writing to them is a race.
        // The worker picks this up on its next pass.
        outputsDirty = true
    }

    fun play(track: DesktopTrack, startMs: Long = 0L) {
        stop()
        stopRequested = false
        playing = true
        val from = startMs.coerceAtLeast(0L)
        framesPlayed = from * AudioDevices.FORMAT.sampleRate.toInt() / 1000
        pendingStartMs = 0L
        lastPublished = -1
        effects.reset()
        spectrum.reset()
        beat.reset()
        _status.value = PlaybackStatus(
            track = track,
            playing = true,
            positionMs = from,
            durationMs = track.durationMs
        )

        // Above normal priority: a playback thread that loses its time slice to
        // the UI thread is an audible gap, and it does very little per wake-up.
        worker = thread(name = "lucense-playback", isDaemon = true, priority = Thread.MAX_PRIORITY) {
            run(track, framesPlayed)
        }
    }

    /**
     * Shows [track] paused at [positionMs] without opening it, which is how the
     * app comes back to what was playing when it was last closed. Pressing play
     * carries on from there.
     */
    fun prepare(track: DesktopTrack, positionMs: Long) {
        stop()
        pendingStartMs = positionMs.coerceAtLeast(0L)
        _status.value = PlaybackStatus(
            track = track,
            playing = false,
            positionMs = pendingStartMs,
            durationMs = track.durationMs
        )
    }

    fun togglePlay() {
        val track = _status.value.track ?: return
        // Nothing is open yet - the track is only being shown, as it is after a
        // restart - so this press starts it where it left off.
        if (worker?.isAlive != true) {
            play(track, pendingStartMs)
            return
        }
        playing = !playing
        _status.value = _status.value.copy(playing = playing)
    }

    fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0)
        // Nothing open yet - a song restored at startup and not played - so
        // there is no playback thread to hand the seek to. It becomes where
        // play starts from instead, or pressing play would undo it.
        if (worker?.isAlive == true) seekToMs = target else pendingStartMs = target
        // Said at once rather than when the playback thread next gets round to
        // it. Until then everything watching the position would show the old
        // spot and then jump forward, which on the seek bar read as it bugging
        // in and out.
        _status.value = _status.value.copy(positionMs = target)
    }

    fun stop() {
        stopRequested = true
        playing = false
        spectrum.reset()
        worker?.join(600)
        worker = null
        closeLines()
        _status.value = _status.value.copy(playing = false)
    }

    fun setVolume(value: Float) { effects.volume = value.coerceIn(0f, 1f) }

    private fun run(track: DesktopTrack, startFrame: Long) {
        var current = track
        var decoder: Decoder? = null
        // The song being faded in, while the one above is still going.
        var incomingTrack: DesktopTrack? = null
        var incoming: Decoder? = null
        var fadeFrames = 0L
        var fadeLength = 0L
        // Whether the decoder ran out with nothing to follow it, as opposed to
        // being stopped or failing: the difference between the queue being
        // finished and playback merely ending here.
        var reachedEnd = false
        try {
            decoder = Decoder.open(current.file, startFrame)
            reopenLines()
            if (lines.isEmpty()) {
                _status.value = _status.value.copy(
                    playing = false,
                    error = "No audio output could be opened."
                )
                return
            }

            val rate = AudioDevices.FORMAT.sampleRate.toInt()
            var duration = if (current.durationMs > 0) current.durationMs else -1L

            while (!stopRequested) {
                if (outputsDirty) {
                    outputsDirty = false
                    reopenLines()
                }

                // Handled before the pause check, so a seek made while paused
                // moves the position straight away rather than on resume.
                seekToMs?.let { target ->
                    seekToMs = null
                    // A jump abandons any overlap in progress: the song that was
                    // being faded in is no longer the one about to arrive.
                    runCatching { incoming?.close() }
                    incoming = null
                    incomingTrack = null
                    decoder = seek(decoder, current, target * rate / 1000)
                    framesPlayed = decoder?.positionFrames ?: 0L
                    effects.reset()
                    spectrum.reset()
                    beat.reset()
                    // Drops what is already queued at the old position. Without
                    // it, up to a fifth of a second of the old spot still plays
                    // after the jump, which is what makes a seek feel sluggish.
                    lines.forEach { runCatching { it.flush() } }
                    publishPosition(duration, rate, force = true)
                }

                if (!playing) { Thread.sleep(40); continue }

                // Open the next song early enough to overlap by the chosen
                // amount. Only possible where this one's length is known -
                // without that there is no telling when the end is coming.
                val overlap = crossfadeMs.toLong()
                if (incoming == null && overlap > 0 && duration > 0) {
                    val leftMs = duration - framesPlayed * 1000 / rate
                    val follow = upNext
                    if (follow != null && leftMs in 1..overlap) {
                        val opened = runCatching { Decoder.open(follow.file, 0L) }.getOrNull()
                        if (opened != null) {
                            incoming = opened
                            incomingTrack = follow
                            fadeFrames = 0L
                            fadeLength = (overlap * rate / 1000).coerceAtLeast(1L)
                        }
                    }
                }

                val raw = decoder?.read(4096)
                if (raw == null) {
                    // This song is done. Carry on into the next one on this
                    // same thread, through these same open lines: closing them
                    // and opening them again is exactly what the gap was.
                    var started = incomingTrack
                    var handover = incoming
                    if (handover == null) {
                        val follow = upNext
                        if (follow != null) {
                            handover = runCatching { Decoder.open(follow.file, 0L) }.getOrNull()
                            started = follow
                        }
                    }
                    if (handover == null || started == null) {
                        reachedEnd = true
                        break
                    }
                    runCatching { decoder?.close() }
                    val finished = current
                    decoder = handover
                    current = started
                    incoming = null
                    incomingTrack = null
                    trackGain = upNextGain
                    // Where the new song already is, which after an overlap is
                    // however much of it has been playing underneath.
                    framesPlayed = handover.positionFrames
                    lastPublished = -1
                    duration = if (current.durationMs > 0) current.durationMs else -1L
                    _status.value = PlaybackStatus(
                        track = current,
                        playing = true,
                        positionMs = framesPlayed * 1000 / rate,
                        durationMs = current.durationMs
                    )
                    runCatching { onAdvanced?.invoke(finished, current) }
                    continue
                }

                // Before the effects rather than after: the meter, the reverb
                // and the limiter downstream should all see the level that is
                // actually going out.
                val gain = trackGain
                if (gain < 0.999f || gain > 1.001f) {
                    for (i in raw.indices) raw[i] *= gain
                }

                incoming?.let { second ->
                    val other = second.read(raw.size / 2)
                    if (other == null) {
                        // The next song turned out shorter than the overlap.
                        // Stop mixing and let the changeover do the rest.
                        runCatching { second.close() }
                        incoming = null
                        incomingTrack = null
                    } else {
                        fadeFrames += mixFade(raw, other, upNextGain, fadeFrames, fadeLength)
                    }
                }

                val processed = effects.process(raw)
                framesPlayed += raw.size / 2
                if (processed.isEmpty()) continue
                // After the chain, so speed, pitch and reverb are all visible
                // in the meter rather than it showing the untouched source.
                spectrum.feed(processed)
                beat.feed(processed, queuedFrames(), rate)

                writeToAll(toBytes(processed))
                publishPosition(duration, rate)
            }
        } catch (t: Throwable) {
            _status.value = _status.value.copy(
                playing = false,
                error = t.message ?: "Playback failed."
            )
        } finally {
            runCatching { decoder?.close() }
            runCatching { incoming?.close() }
            if (!stopRequested) {
                _status.value = _status.value.copy(playing = false, ended = reachedEnd)
            }
        }
    }

    /**
     * Moves playback to [frame], reopening the file only when that is behind the
     * decoder.
     *
     * Forward is the common case, a click a little further along, and carrying on
     * from where the decoder already is avoids passing over the start of the file
     * again. A compressed stream cannot go backwards, so that reopens.
     */
    private fun seek(current: Decoder?, track: DesktopTrack, frame: Long): Decoder? {
        if (current != null && frame >= current.positionFrames) {
            current.skip(frame - current.positionFrames)
            return current
        }
        runCatching { current?.close() }
        return Decoder.open(track.file, frame)
    }

    /**
     * Published four times a second rather than once per buffer. Every emission
     * recomposes the transport bar and repaints a frame, and at about eleven
     * buffers a second that was an order of magnitude more redrawing than a 3px
     * progress bar can show.
     */
    private fun publishPosition(duration: Long, rate: Int, force: Boolean = false) {
        // A seek is waiting to be carried out: this frame count is about to be
        // replaced, and saying it now would undo what seekTo already said.
        if (!force && seekToMs != null) return
        val positionMs = framesPlayed * 1000 / rate
        val slot = positionMs / PUBLISH_INTERVAL_MS
        if (!force && slot == lastPublished) return
        lastPublished = slot
        _status.value = _status.value.copy(
            positionMs = positionMs,
            // Unknown stays unknown. Reporting the position as the length made
            // the seek bar scale against a moving target, so its middle landed
            // near the start, and made every pause look like the end of a track.
            durationMs = if (duration > 0) duration else 0L
        )
    }

    /** Frames handed to the primary line that it has not played yet. */
    private fun queuedFrames(): Int = runCatching {
        val line = lines.firstOrNull() ?: return 0
        (line.bufferSize - line.available()) / line.format.frameSize
    }.getOrDefault(0)

    private fun writeToAll(bytes: ByteArray) {
        val current = lines
        if (current.isEmpty()) return
        // The primary write blocks, which is what paces playback.
        runCatching { current[0].write(bytes, 0, bytes.size) }
        for (i in 1 until current.size) {
            // Mirrors must never stall the primary, so they get whatever the
            // line can take right now and drop the rest.
            runCatching {
                val room = current[i].available()
                if (room > 0) current[i].write(bytes, 0, minOf(room, bytes.size))
            }
        }
    }

    /**
     * Scratch buffer for PCM conversion, reused across the whole track.
     *
     * Allocating one per buffer meant roughly eleven short-lived 16 KB arrays a
     * second for as long as anything was playing — enough steady garbage to keep
     * the heap growing for no reason. Only the playback thread touches it.
     */
    private var pcmScratch = ByteArray(0)

    private fun toBytes(samples: FloatArray): ByteArray {
        val needed = samples.size * 2
        if (pcmScratch.size != needed) pcmScratch = ByteArray(needed)
        val out = pcmScratch
        for (i in samples.indices) {
            val clamped = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            out[i * 2] = (clamped and 0xFF).toByte()
            out[i * 2 + 1] = ((clamped shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun reopenLines() {
        closeLines()
        val targets = outputs.ifEmpty { listOf(DesktopAudioOutput("System default", null)) }
        lines = targets.mapNotNull { AudioDevices.openLine(it) }
    }

    private fun closeLines() {
        lines.forEach {
            runCatching { it.drain() }
            runCatching { it.stop() }
            runCatching { it.close() }
        }
        lines = emptyList()
    }

    fun release() {
        stop()
    }
}

/**
 * Mixes [incoming] into [out] across an overlap, and says how many frames of it
 * were used.
 *
 * Equal power rather than a straight line: two uncorrelated songs at half
 * volume each are quieter than either at full volume, so a linear fade dips
 * audibly in the middle. Cosine and sine keep the sum of squares at one.
 *
 * The level is worked out once at each end of the buffer and interpolated
 * across it, rather than a cosine per sample - a buffer is about a tenth of a
 * second, which is far too coarse a step to hear a seam in, and this is the
 * thread feeding the speakers.
 */
internal fun mixFade(
    out: FloatArray,
    incoming: FloatArray,
    incomingGain: Float,
    fadeFrames: Long,
    fadeLength: Long
): Int {
    val count = minOf(out.size, incoming.size)
    if (count <= 0 || fadeLength <= 0L) return 0
    val half = (PI / 2).toFloat()
    val from = (fadeFrames.toFloat() / fadeLength).coerceIn(0f, 1f)
    val to = ((fadeFrames + count / 2).toFloat() / fadeLength).coerceIn(0f, 1f)
    val outFrom = cos(from * half)
    val outTo = cos(to * half)
    val inFrom = sin(from * half)
    val inTo = sin(to * half)
    for (i in 0 until count) {
        val along = if (count > 1) i.toFloat() / (count - 1) else 1f
        val fading = outFrom + (outTo - outFrom) * along
        val rising = inFrom + (inTo - inFrom) * along
        out[i] = out[i] * fading + incoming[i] * incomingGain * rising
    }
    return count / 2
}
