package com.exo.musicplayer.data.youtube

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Plays a preview without downloading it: a YouTube result, a song's
 * half-minute clip from Discover, or the song an artist or a genre is heard by.
 *
 * ExoPlayer cannot open a YouTube watch page, but it plays the signed
 * googlevideo.com URL behind one perfectly well: the audio is Opus in a WebM
 * container, which is a format Android has decoded natively since long before
 * this app's minimum API level. So a preview needs no temporary file and no
 * ffmpeg pass, only the URL, and getting that is the slow part.
 *
 * A second player rather than the library's own, for the same reasons the
 * Windows build keeps them apart: a preview must not join the queue, must not
 * survive as "now playing" once it ends, and must not inherit the equaliser,
 * because the point of previewing is to hear what the recording actually
 * sounds like. It also means stopping a preview cannot disturb whatever the
 * user had queued up.
 *
 * One of these serves every page, so only one preview plays at a time and the
 * small player at the bottom of the screen can pause or close it from anywhere.
 */
class YouTubePreviewPlayer(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    /**
     * Created on first use and kept afterwards.
     *
     * ExoPlayer must be built and driven from the thread that created it, and
     * everything here runs on the main dispatcher, so that is where it lives.
     * Building one costs a few milliseconds, which is not worth paying for
     * anyone who never taps a preview.
     */
    private var player: ExoPlayer? = null

    private var job: Job? = null
    private var ticker: Job? = null

    /**
     * Starts previewing what [key] names, or acts on it if it is already the
     * one: a tap on what is playing stops it, a tap on what is paused carries
     * on. [label] is shown at once; [found] replaces it once the stream has
     * been found, for an artist or a genre, where which song it is isn't
     * known until then.
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
        job?.cancel()
        ticker?.cancel()
        releasePlayback()

        _state.value = PreviewState(videoId = key, loading = true, label = label)

        job = scope.launch {
            val url = runCatching { resolve(key) }.getOrNull()

            // The row may have been tapped again, or another one started, while
            // the URL was being signed - that takes seconds, not milliseconds.
            if (_state.value.videoId != key) return@launch
            val named = found() ?: label

            if (url == null) {
                _state.value = PreviewState(
                    videoId = key,
                    error = "Couldn't get a playable stream. yt-dlp may need updating.",
                    label = named
                )
                return@launch
            }

            val active = player ?: ExoPlayer.Builder(context).build().also { built ->
                built.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState != Player.STATE_ENDED) return
                        // Left in place, wound back, rather than cleared: the
                        // small player stays until it is closed, and play
                        // starts it again.
                        built.pause()
                        built.seekTo(0)
                        val now = _state.value
                        if (now.videoId != null) {
                            _state.value = now.copy(playing = false, paused = true, ended = true, secondsPlayed = 0)
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        val now = _state.value
                        _state.value = PreviewState(
                            videoId = now.videoId,
                            // Signed URLs expire, and a stale one fails here
                            // rather than at resolution time.
                            error = "Playback failed: ${error.errorCodeName}",
                            label = now.label
                        )
                    }
                })
                player = built
            }

            active.setMediaItem(MediaItem.fromUri(url))
            active.prepare()
            active.play()

            _state.value = PreviewState(videoId = key, loading = false, playing = true, label = named)

            ticker = scope.launch {
                while (true) {
                    delay(500)
                    val now = _state.value
                    if (now.videoId != key) break
                    if (!now.playing) continue
                    val seconds = (active.currentPosition / 1000).toInt()
                    if (now.secondsPlayed != seconds) {
                        _state.value = now.copy(secondsPlayed = seconds)
                    }
                }
            }
        }
    }

    /** Holds what is playing, or carries on with what is held. Does nothing while a stream is still being found. */
    fun pauseOrResume() {
        val now = _state.value
        val active = player ?: return
        if (now.videoId == null || now.loading || now.error != null) return
        if (now.playing) {
            active.pause()
            _state.value = now.copy(playing = false, paused = true)
        } else if (now.paused) {
            active.play()
            _state.value = now.copy(playing = true, paused = false, ended = false)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        ticker?.cancel()
        ticker = null
        releasePlayback()
        _state.value = PreviewState()
    }

    private fun releasePlayback() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.clearMediaItems() }
        }
    }

    /** Called from the view model's onCleared; the player outlives no screen. */
    fun release() {
        stop()
        runCatching { player?.release() }
        player = null
    }
}
