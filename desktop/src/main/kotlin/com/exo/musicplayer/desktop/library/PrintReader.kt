package com.exo.musicplayer.desktop.library

import com.exo.musicplayer.data.library.AudioPrint
import com.exo.musicplayer.desktop.audio.Decoder
import java.io.File

/** Reads the sound print of a file: thirty seconds from its middle, decoded and reduced. */
object PrintReader {

    private const val RATE = 44_100

    /** The print, or null when the file can't be decoded or is too short to say anything. */
    fun read(file: File, durationMs: Long): IntArray? = runCatching {
        val startFrame = AudioPrint.windowStartMs(durationMs) * RATE / 1_000
        val wanted = AudioPrint.SECONDS * RATE
        val mono = FloatArray(wanted)
        var filled = 0
        Decoder.open(file, startFrame).use { decoder ->
            while (filled < wanted) {
                val block = decoder.read(8_192) ?: break
                if (block.isEmpty()) break
                // The decoder hands out interleaved stereo; the print wants mono.
                var i = 0
                while (i + 1 < block.size && filled < wanted) {
                    mono[filled++] = (block[i] + block[i + 1]) * 0.5f
                    i += 2
                }
            }
        }
        AudioPrint.of(mono.copyOf(filled), RATE).takeIf { it.isNotEmpty() }
    }.getOrNull()
}
