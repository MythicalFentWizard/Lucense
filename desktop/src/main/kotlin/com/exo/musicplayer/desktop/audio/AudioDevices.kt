package com.exo.musicplayer.desktop.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine

/** A Windows audio endpoint. */
data class DesktopAudioOutput(
    val name: String,
    val mixerInfo: Mixer.Info?
) {
    val isDefault: Boolean get() = mixerInfo == null
}

/**
 * Output enumeration and line opening.
 *
 * Worth contrasting with Android: there, the audio policy can silently override
 * a per-track device choice, which is why simultaneous multi-device output had
 * to ship as "experimental". On Windows each mixer is an independent endpoint
 * that can be opened directly, so playing to several devices at once is a
 * first-class capability rather than a workaround.
 */
object AudioDevices {

    /** Everything is decoded to this before it reaches a device. */
    val FORMAT: AudioFormat = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        44100f, 16, 2, 4, 44100f, false
    )

    fun outputs(): List<DesktopAudioOutput> {
        val found = AudioSystem.getMixerInfo().mapNotNull { info ->
            val mixer = runCatching { AudioSystem.getMixer(info) }.getOrNull()
                ?: return@mapNotNull null
            val canPlay = mixer.sourceLineInfo.any { lineInfo ->
                lineInfo is DataLine.Info &&
                    SourceDataLine::class.java.isAssignableFrom(lineInfo.lineClass)
            }
            if (!canPlay) return@mapNotNull null
            DesktopAudioOutput(info.name.trim().ifBlank { info.description }, info)
        }.distinctBy { it.name }

        return listOf(DesktopAudioOutput("System default", null)) + found
    }

    fun openLine(output: DesktopAudioOutput?): SourceDataLine? = runCatching {
        val info = DataLine.Info(SourceDataLine::class.java, FORMAT)
        val line = if (output?.mixerInfo == null) {
            AudioSystem.getLine(info) as SourceDataLine
        } else {
            AudioSystem.getMixer(output.mixerInfo).getLine(info) as SourceDataLine
        }
        // Generous buffer: an underrun is far more audible than added latency.
        line.open(FORMAT, FORMAT.frameSize * 8192)
        line.start()
        line
    }.getOrNull()
}
