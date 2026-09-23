package com.exo.musicplayer.desktop.audio

import com.exo.musicplayer.data.recognition.ShazamSignature
import com.exo.musicplayer.desktop.data.ToolPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.sound.sampled.AudioSystem

/** What came back from trying to get fingerprintable audio out of a file. */
sealed interface SampleOutcome {
    data class Ok(val samples: FloatArray) : SampleOutcome
    /** The file needs ffmpeg and the bundled copy is missing or unrunnable. */
    data object NeedsFfmpeg : SampleOutcome
    data class Failed(val message: String) : SampleOutcome
}

/**
 * Getting identifiable audio out of things Java Sound can't open.
 *
 * Java Sound handles MP3, Vorbis, FLAC, WAV and AIFF through the service
 * providers on the classpath — which covers a music library but not a video
 * someone recorded, or an Opus voice note. ffmpeg covers the rest, and it ships
 * with the app for the downloader's sake, so this reuses that binary rather than
 * adding a second one.
 *
 * ffmpeg is asked to produce exactly what the fingerprinter wants — 12 seconds
 * of mono 16 kHz PCM from the middle of the recording — so nothing has to be
 * decoded, downmixed or resampled afterwards. Extracting the whole file first
 * would mean writing hundreds of megabytes to disk to read twelve seconds of it.
 */
object MediaAudio {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "3g2", "flv", "wmv",
        "ts", "m2ts", "mts", "mpg", "mpeg", "ogv", "asf", "rm", "rmvb", "divx"
    )

    /** Formats ffmpeg is needed for even though they are audio. */
    private val FFMPEG_ONLY_AUDIO = setOf(
        "m4a", "aac", "opus", "wma", "amr", "ape", "wv", "mka", "alac", "dsf", "aiff"
    )

    private val ffmpeg: File get() = ToolPaths.ffmpeg

    fun ffmpegAvailable(): Boolean = ffmpeg.isFile

    fun isVideo(file: File): Boolean = file.extension.lowercase() in VIDEO_EXTENSIONS

    /** Whether Java Sound is likely to refuse this on its own. */
    fun needsFfmpeg(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in VIDEO_EXTENSIONS || extension in FFMPEG_ONLY_AUDIO
    }

    /**
     * Samples any media file, whether or not Java Sound can read it.
     *
     * Java Sound is tried first even for formats on the ffmpeg list — a `.aiff`
     * or a mislabelled extension may well open natively, and skipping a process
     * launch is worth one cheap attempt.
     */
    suspend fun sample(file: File, knownDurationMs: Long = 0L): SampleOutcome =
        withContext(Dispatchers.IO) {
            if (!file.isFile) return@withContext SampleOutcome.Failed("That file is gone.")

            if (!needsFfmpeg(file)) {
                val native = runCatching {
                    FileSampler.sampleMono16k(file, knownDurationMs)
                }.getOrNull()
                if (native != null) return@withContext SampleOutcome.Ok(native)
            }

            if (!ffmpegAvailable()) return@withContext SampleOutcome.NeedsFfmpeg

            val duration = knownDurationMs.takeIf { it > 0 } ?: probeDurationMs(file)
            extractWindow(file, duration).fold(
                onSuccess = { SampleOutcome.Ok(it) },
                onFailure = {
                    SampleOutcome.Failed(it.message ?: "Couldn't read audio from that file.")
                }
            )
        }

    private val DURATION = Regex("""Duration:\s*(\d+):(\d{2}):(\d{2})\.(\d{1,2})""")

    /**
     * Length in milliseconds, or 0 if it can't be read.
     *
     * Parsed out of ffmpeg's own banner rather than calling ffprobe, which is a
     * near-identical 139 MB binary to ship for one number. ffmpeg exits non-zero
     * when given no output file, which is expected here — the header it prints
     * on the way out is the whole point.
     */
    fun probeDurationMs(file: File): Long {
        if (!ffmpeg.isFile) return 0L
        return runCatching {
            val process = ProcessBuilder(
                ffmpeg.absolutePath, "-hide_banner", "-i", file.absolutePath
            ).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()

            val match = DURATION.find(text) ?: return 0L
            val (hours, minutes, seconds, fraction) = match.destructured
            val centis = fraction.padEnd(2, '0').take(2).toLong()
            hours.toLong() * 3_600_000 + minutes.toLong() * 60_000 +
                seconds.toLong() * 1_000 + centis * 10
        }.getOrDefault(0L)
    }

    private fun extractWindow(file: File, durationMs: Long): Result<FloatArray> = runCatching {
        val seconds = ShazamSignature.WINDOW_SECONDS
        // Same rule as the audio path: start halfway in, because intros are
        // often silence, a logo sting or speech, none of which fingerprint.
        val startSeconds = if (durationMs > seconds * 1000L) {
            ((durationMs / 2000.0)).coerceAtMost((durationMs / 1000.0) - seconds)
        } else {
            0.0
        }

        val output = File.createTempFile("lucense-identify", ".wav")
        try {
            val process = ProcessBuilder(
                ffmpeg.absolutePath,
                "-hide_banner", "-loglevel", "error",
                // -ss before -i seeks by keyframe without decoding what precedes.
                "-ss", "%.3f".format(startSeconds),
                "-i", file.absolutePath,
                "-t", seconds.toString(),
                "-vn",
                "-ac", "1",
                "-ar", ShazamSignature.SAMPLE_RATE.toString(),
                "-f", "wav",
                "-y", output.absolutePath
            ).redirectErrorStream(true).start()

            val log = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            check(code == 0 && output.length() > 1024) {
                log.lineSequence().lastOrNull { it.isNotBlank() }
                    ?: "ffmpeg couldn't find an audio track in that file."
            }
            readMono16k(output)
        } finally {
            output.delete()
        }
    }

    /** ffmpeg wrote plain 16-bit PCM, so this is a straight byte-to-float read. */
    private fun readMono16k(wav: File): FloatArray {
        AudioSystem.getAudioInputStream(wav).use { stream ->
            val bytes = stream.readBytes()
            val samples = FloatArray(bytes.size / 2)
            for (i in samples.indices) {
                val low = bytes[i * 2].toInt() and 0xFF
                val high = bytes[i * 2 + 1].toInt()
                samples[i] = ((high shl 8) or low) / 32768f
            }
            return samples
        }
    }
}
