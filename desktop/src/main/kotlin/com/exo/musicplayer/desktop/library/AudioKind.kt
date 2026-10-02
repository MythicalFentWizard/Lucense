package com.exo.musicplayer.desktop.library

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.io.RandomAccessFile

/**
 * What a music file really is, read from its first bytes rather than its name.
 *
 * Files do turn up named for the wrong format: M4A audio saved as `.mp3`, for
 * one, which is what nine songs in a real library were. Going by the name, the
 * tag reader hunted for MP3 frames, found none and gave up, so the song showed
 * its file name, no artist, and a length counted out of noise - and every edit
 * failed the same way, because writing a tag starts by reading the file. Read
 * as what they are, all nine had proper tags the whole time.
 *
 * The name still wins whenever the contents agree with it or say nothing
 * clear: this only steps in when the two plainly disagree.
 */
object AudioKind {

    /**
     * Each format's own name, and the other names of it the tag reader knows.
     * A name it doesn't know - an MP4 called `.aac` - is no use to keep, so a
     * file under one is read by the format's own name instead.
     */
    private val FAMILIES = mapOf(
        "mp3" to setOf("mp3"),
        "m4a" to setOf("m4a", "mp4", "m4b", "m4p"),
        "flac" to setOf("flac"),
        "ogg" to setOf("ogg", "oga", "opus"),
        "wav" to setOf("wav"),
        "aif" to setOf("aif", "aiff", "aifc")
    )

    /** The extension [file]'s contents call for: its own, unless they disagree. */
    fun of(file: File): String {
        val named = file.extension.lowercase()
        val real = runCatching { sniff(file) }.getOrNull() ?: return named
        return if (named in FAMILIES.getValue(real)) named else real
    }

    /** The file's tags and header, read as whatever it really is. */
    fun read(file: File): AudioFile = AudioFileIO.readAs(file, of(file))

    private fun sniff(file: File): String? = RandomAccessFile(file, "r").use { input ->
        val head = ByteArray(12)
        if (input.read(head) < 12) return null
        // An ID3 tag says nothing about what follows it - FLAC and AAC files
        // carry them too - so look past it at the audio itself.
        if (head.startsWith(0, "ID3")) {
            val size = ((head[6].toInt() and 0x7f) shl 21) or ((head[7].toInt() and 0x7f) shl 14) or
                ((head[8].toInt() and 0x7f) shl 7) or (head[9].toInt() and 0x7f)
            val after = 10L + size
            if (after + 12 > input.length()) return null
            input.seek(after)
            val next = ByteArray(12)
            if (input.read(next) < 12) return null
            return kindOf(next)
        }
        kindOf(head)
    }

    private fun kindOf(head: ByteArray): String? {
        val first = head[0].toInt() and 0xFF
        val second = head[1].toInt() and 0xFF
        return when {
            head.startsWith(4, "ftyp") -> "m4a"
            head.startsWith(0, "fLaC") -> "flac"
            head.startsWith(0, "OggS") -> "ogg"
            head.startsWith(0, "RIFF") && head.startsWith(8, "WAVE") -> "wav"
            head.startsWith(0, "FORM") && (head.startsWith(8, "AIFF") || head.startsWith(8, "AIFC")) -> "aif"
            // An MPEG audio frame: eleven sync bits, then a layer other than
            // 00. Layer 00 is ADTS, which is AAC with no container at all.
            first == 0xFF && (second and 0xE0) == 0xE0 && ((second shr 1) and 3) != 0 -> "mp3"
            else -> null
        }
    }

    private fun ByteArray.startsWith(at: Int, text: String): Boolean =
        size >= at + text.length && text.indices.all { this[at + it] == text[it].code.toByte() }
}
