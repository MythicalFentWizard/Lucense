package com.exo.musicplayer.data.library

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * How loud a song is, measured the way Windows measures it: RMS over the first
 * ninety seconds of every channel, in decibels below full scale, plus the
 * loudest single sample. The same numbers on both, so levelling brings a song
 * to the same loudness on the phone as on the PC.
 */
object LoudnessMeter {

    class Reading(val dbfs: Float, val peak: Float)

    private const val SECONDS = 90
    private const val TIMEOUT_US = 10_000L

    fun measure(file: File): Reading? = runCatching {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            } ?: return@runCatching null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@runCatching null
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var float = false
            var squares = 0.0
            var samples = 0L
            var peak = 0f
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone && samples < SECONDS.toLong() * rate * channels) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val read = extractor.readSampleData(buffer, 0)
                        if (read < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, read, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        rate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        float = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            out.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            buffer.order(ByteOrder.nativeOrder())
                            if (float) {
                                while (buffer.remaining() >= 4) {
                                    val value = buffer.getFloat()
                                    squares += value.toDouble() * value
                                    if (abs(value) > peak) peak = abs(value)
                                    samples++
                                }
                            } else {
                                while (buffer.remaining() >= 2) {
                                    val value = buffer.getShort() / 32_768f
                                    squares += value.toDouble() * value
                                    if (abs(value) > peak) peak = abs(value)
                                    samples++
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            // A second of audio at the least, or the number means nothing.
            if (samples < rate.toLong() * channels) return@runCatching null
            val rms = sqrt(squares / samples).toFloat()
            if (rms <= 1e-6f) return@runCatching null
            Reading(20f * log10(rms), peak)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }.getOrNull()
}
