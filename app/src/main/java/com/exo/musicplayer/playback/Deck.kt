package com.exo.musicplayer.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import java.nio.ByteBuffer

/**
 * One player and everything attached to it.
 *
 * There are two of these so that a crossfade can be a real overlap: the next
 * song starts on the other deck underneath the one finishing, and the media
 * session is handed across. With crossfade off only one is ever used, and
 * songs follow each other gaplessly on it.
 */
@UnstableApi
class Deck(context: Context, sinks: List<TeeAudioProcessor.AudioBufferSink>) {

    /** Whether this deck is the one the session is showing; only that one feeds the taps. */
    @Volatile
    var live: Boolean = false

    val tone = ToneProcessor()

    val player: ExoPlayer

    /** Effects that hang off the player's audio session, reverb chiefly. */
    val fx = AudioFxController()

    // Each part of the app that wants the music quieter owns one of these, and
    // the player hears their product. Before, the audio-focus fade wrote the
    // volume directly, which anything else touching it would have fought with.
    var levelVolume = 1f
        set(value) { field = value; applyVolume() }
    var focusVolume = 1f
        set(value) { field = value; applyVolume() }
    var sleepVolume = 1f
        set(value) { field = value; applyVolume() }
    var fadeVolume = 1f
        set(value) { field = value; applyVolume() }

    init {
        val gated = sinks.map { sink -> GatedSink(sink) { live } }
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(
                    arrayOf<AudioProcessor>(tone) + gated.map { TeeAudioProcessor(it) }
                )
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
        // Constant-bitrate seeking makes an MP3 with no header seekable and gives
        // it a length - Telegram's exports often arrive like that, and without
        // this they showed 0:00 and the seek bar did nothing.
        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        player = ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context, extractors))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ false
            )
            // Pause instead of blaring out of the speaker when headphones are pulled.
            .setHandleAudioBecomingNoisy(true)
            // Keeps the processor running with the screen off. Without it some
            // phones let playback stutter or stop once the screen went dark.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    private fun applyVolume() {
        player.volume = (levelVolume * focusVolume * sleepVolume * fadeVolume).coerceIn(0f, 1f)
    }

    fun release() {
        fx.release()
        player.release()
    }

    /** Passes buffers on only while [open] says so. */
    private class GatedSink(
        private val sink: TeeAudioProcessor.AudioBufferSink,
        private val open: () -> Boolean
    ) : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) =
            sink.flush(sampleRateHz, channelCount, encoding)

        override fun handleBuffer(buffer: ByteBuffer) {
            if (open()) sink.handleBuffer(buffer)
        }
    }
}
