package com.exo.musicplayer.desktop.audio

import com.exo.musicplayer.data.youtube.PreviewLabel
import com.exo.musicplayer.data.youtube.PreviewState
import com.exo.musicplayer.desktop.data.NetworkProxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.sound.sampled.SourceDataLine

/**
 * Plays a preview without downloading it: a YouTube result, a song's
 * half-minute clip from Discover, or the song an artist or a genre is heard by.
 *
 * Kept entirely separate from [PlaybackEngine] rather than added to it, for
 * three reasons. A preview should not join the queue or replace what is playing
 * once it ends; it should not inherit the equaliser and nightcore settings,
 * because the point is to hear what the file actually sounds like; and it has no
 * seekable duration, since it is a pipe rather than a file. Sharing the engine
 * would mean weakening all three of its guarantees for the sake of a feature
 * that only has to answer "is this the right song?".
 *
 * The audio arrives as Opus in a WebM container, which Java Sound cannot open at
 * all — no service provider on the classpath handles it. ffmpeg does, and it
 * already ships with the app for the downloader, so it is used as a decoder
 * here: it is handed the signed URL and writes raw PCM to stdout in exactly the
 * format the output line wants, so nothing has to be converted afterwards.
 *
 * Nothing is written to disk. A preview that left a file behind would be a
 * download, and there is already a button for that.
 *
 * One of these serves every page, so only one preview plays at a time and the
 * small player in the corner can pause or close it from anywhere.
 */
class PreviewPlayer(private val ffmpegPath: () -> java.io.File) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    private var job: Job? = null

    @Volatile
    private var process: Process? = null

    /** The line the preview is playing through, for pausing it where it is. */
    @Volatile
    private var line: SourceDataLine? = null

    /** Set while paused: the writer waits rather than feeding a stopped line. */
    @Volatile
    private var held = false

    /** How to find the stream again, for playing a finished preview a second time. */
    private var again: (() -> Unit)? = null

    /**
     * Starts previewing what [key] names, or acts on it if it is already the
     * one: a click on what is playing stops it, a click on what is paused
     * carries on. [label] is shown at once; [found] replaces it once the
     * stream has been found, for an artist or a genre, where which song it is
     * isn't known until then.
     */
    fun toggle(
        key: String,
        label: PreviewLabel? = null,
        found: () -> PreviewLabel? = { null },
        resolve: suspend (String) -> String?
    ) {
        val current = _state.value
        if (current.videoId == key) {
            if (current.paused) pauseOrResume() else stop()
            return
        }
        start(key, label, found, resolve)
    }

    private fun start(key: String, label: PreviewLabel?, found: () -> PreviewLabel?, resolve: suspend (String) -> String?) {
        again = { start(key, label, found, resolve) }
        val previous = job
        // Announced at once, so the small player says what is coming while
        // the old preview is still being torn down.
        _state.value = PreviewState(videoId = key, loading = true, label = label)
        job = scope.launch {
            // The old preview is gone before the new one starts, so two
            // ffmpeg processes never write to the line at once.
            previous?.cancelAndJoin()
            killProcess()

            val ffmpeg = ffmpegPath()
            if (!ffmpeg.isFile) {
                _state.value = PreviewState(
                    videoId = key,
                    error = "ffmpeg is missing, so previews cannot be decoded.",
                    label = label
                )
                return@launch
            }

            val url = try {
                resolve(key)
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (_: Throwable) {
                null
            }
            // Another preview may have been asked for while this one was being found.
            if (_state.value.videoId != key) return@launch
            val named = found() ?: label
            if (url == null) {
                _state.value = PreviewState(
                    videoId = key,
                    error = "Couldn't get a playable stream for that.",
                    label = named
                )
                return@launch
            }

            _state.value = PreviewState(videoId = key, loading = false, playing = true, label = named)
            try {
                stream(ffmpeg, url, key)
            } catch (stopped: CancellationException) {
                // Being stopped is not a failure. Reporting it as one put an
                // error back on screen a moment after the preview was closed.
                throw stopped
            } catch (failure: Throwable) {
                if (_state.value.videoId == key) {
                    _state.value = PreviewState(videoId = key, error = failure.message, label = named)
                }
            }
        }
    }

    private suspend fun stream(ffmpeg: java.io.File, url: String, key: String) =
        withContext(Dispatchers.IO) {
            val format = AudioDevices.FORMAT
            // ffmpeg cannot speak SOCKS, and its own HTTP proxy option would be a
            // second setting to keep in step. So behind a proxy the stream is
            // fetched here, along the same route as everything else, and fed to
            // ffmpeg on stdin; without one ffmpeg reads the URL itself as before.
            val proxied = NetworkProxy.urlFor(url) != null
            val started = ProcessBuilder(
                buildList {
                    add(ffmpeg.absolutePath)
                    addAll(listOf("-hide_banner", "-loglevel", "error"))
                    if (proxied) {
                        addAll(listOf("-i", "pipe:0"))
                    } else {
                        // Keeps a stalled connection from hanging the preview forever.
                        addAll(listOf("-rw_timeout", "15000000", "-i", url))
                    }
                    addAll(
                        listOf(
                            "-f", "s16le",
                            "-ac", format.channels.toString(),
                            "-ar", format.sampleRate.toInt().toString(),
                            "pipe:1"
                        )
                    )
                }
            ).redirectErrorStream(false).start()
            process = started

            if (proxied) {
                Thread {
                    runCatching {
                        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15_000
                            readTimeout = 15_000
                        }
                        connection.inputStream.use { input ->
                            started.outputStream.use { output -> input.copyTo(output, 1 shl 16) }
                        }
                    }
                    runCatching { started.outputStream.close() }
                }.apply { isDaemon = true; start() }
            }

            // stderr is drained on its own thread: ffmpeg blocks once the pipe
            // buffer fills, and a blocked encoder looks exactly like a hang.
            val drain = Thread { runCatching { started.errorStream.use { it.readBytes() } } }
                .apply { isDaemon = true; start() }

            val out = AudioDevices.openLine(null)
            if (out == null) {
                started.destroy()
                _state.value = PreviewState(videoId = key, error = "No audio output.", label = _state.value.label)
                return@withContext
            }
            line = out
            held = false

            var finished = false
            try {
                val buffer = ByteArray(format.frameSize * 4096)
                var totalBytes = 0L
                val bytesPerSecond = format.sampleRate.toInt() * format.frameSize

                while (true) {
                    val read = started.inputStream.read(buffer)
                    if (read <= 0) break
                    // A stopped line doesn't block a write, it returns short:
                    // left to itself the rest of the clip would be read and
                    // thrown away in a second. So a pause is waited out here,
                    // with ffmpeg waiting behind it, and what a write didn't
                    // take is offered again.
                    var offset = 0
                    while (offset < read && isActive) {
                        while (held && isActive) Thread.sleep(40)
                        if (!isActive) break
                        offset += out.write(buffer, offset, read - offset)
                    }
                    totalBytes += read

                    val seconds = (totalBytes / bytesPerSecond).toInt()
                    val current = _state.value
                    // Only republished when the whole second ticks over, so a
                    // preview does not recompose the list forty times a second.
                    if (current.videoId == key && current.secondsPlayed != seconds) {
                        _state.value = current.copy(secondsPlayed = seconds)
                    }
                }
                out.drain()
                // The stream running out is the end; ffmpeg being killed, by a
                // stop or by the next preview, reads the same from here but
                // leaves a different exit code.
                finished = isActive &&
                    runCatching { started.waitFor(2, TimeUnit.SECONDS) && started.exitValue() == 0 }.getOrDefault(false)
            } finally {
                line = null
                runCatching { out.stop() }
                runCatching { out.close() }
                runCatching { started.destroy() }
                runCatching { drain.join(500) }
                process = null
                // Left alone if something else has already taken over, so a
                // finished preview cannot touch the state of the next one.
                val current = _state.value
                if (current.videoId == key) {
                    // Played to the end: it stays in the small player, to be
                    // played again or closed. Anything else was a stop.
                    _state.value = if (finished) {
                        current.copy(playing = false, paused = true, ended = true, secondsPlayed = 0)
                    } else {
                        PreviewState()
                    }
                }
            }
        }

    /** Holds what is playing, or carries on with what is held. Does nothing while a stream is still being found. */
    fun pauseOrResume() {
        val now = _state.value
        if (now.videoId == null || now.loading || now.error != null) return
        when {
            now.ended -> again?.invoke()
            now.playing -> {
                held = true
                runCatching { line?.stop() }
                _state.value = now.copy(playing = false, paused = true)
            }
            now.paused -> {
                runCatching { line?.start() }
                held = false
                _state.value = now.copy(playing = true, paused = false)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        again = null
        killProcess()
        _state.value = PreviewState()
    }

    private fun killProcess() {
        // A paused preview has its writer waiting; let it go so it can finish.
        held = false
        runCatching { line?.flush() }
        runCatching { process?.destroy() }
        process = null
    }

    fun release() {
        stop()
        runCatching { scope.coroutineContext[Job]?.cancel() }
    }
}
