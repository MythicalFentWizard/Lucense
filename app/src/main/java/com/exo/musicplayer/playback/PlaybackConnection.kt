package com.exo.musicplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.exo.musicplayer.data.db.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackState(
    val isConnected: Boolean = false,
    val currentTrackId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** Track ids in the order they will play - the shuffled order when shuffle is on. */
    val queueTrackIds: List<Long> = emptyList(),
    /** Where the song playing sits in [queueTrackIds]. */
    val queueIndex: Int = -1,
    /** When this was read, on the uptime clock, so the position can be carried forward between reads. */
    val readAt: Long = android.os.SystemClock.elapsedRealtime()
) {
    /** The position now, carried forward from when it was read while playing. */
    fun positionNow(): Long =
        if (isPlaying) positionMs + (android.os.SystemClock.elapsedRealtime() - readAt) else positionMs

    val hasNext: Boolean get() = queueIndex in 0 until queueTrackIds.lastIndex || repeatMode == Player.REPEAT_MODE_ALL
    val hasPrevious: Boolean get() = queueIndex > 0
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * The UI's handle on the player.
 *
 * State comes from a [MediaController] bound to [PlaybackService], republished
 * as a [StateFlow] Compose can collect; it follows the session across a
 * crossfade, when the session's player changes underneath it.
 *
 * Commands go to the player through the service's [PlayerHost], which can
 * shape the play order. A command given before the service is up - tapping a
 * song the moment the app opens - is held and carried out once it is, where it
 * used to be silently dropped.
 *
 * Lives on the Application so playback state survives the Activity, and so the
 * share-import screen can start playback without opening the main UI.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackConnection(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: () -> PlayerHost?,
    /** Called when the player moves to a new track. */
    private val onTrackStarted: (Long) -> Unit,
    /** Wall-clock milliseconds actually spent playing since the last tick. */
    private val onElapsed: (Long) -> Unit = {},
    /** Called when playback stops, so pending listening time can be persisted. */
    private val onPlaybackPaused: () -> Unit = {}
) {

    private var controller: MediaController? = null
    private var connecting = false
    private var ticker: Job? = null
    private val pending = mutableListOf<(ExoPlayer) -> Unit>()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.trackId()?.let(onTrackStarted)
            publish()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startTicker() else stopTicker()
            publish()
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    // The service went away - Android stopped it while paused.
                    // The next command reconnects, and the service restores
                    // where it was when it comes back.
                    if (this@PlaybackConnection.controller === controller) {
                        this@PlaybackConnection.controller = null
                        stopTicker()
                        publish()
                    }
                }
            })
            .buildAsync()
        future.addListener(
            {
                connecting = false
                controller = runCatching { future.get() }.getOrNull()?.also {
                    it.addListener(listener)
                }
                publish()
                if (controller?.isPlaying == true) startTicker()
                onHostReady()
            },
            androidx.core.content.ContextCompat.getMainExecutor(context)
        )
    }

    /** Called when the service comes up: anything asked for meanwhile happens now. */
    fun onHostReady() {
        val player = host()?.player ?: return
        val waiting = pending.toList()
        pending.clear()
        waiting.forEach { it(player) }
        publish()
    }

    /** Runs [action] on the player now, or as soon as the service is up. */
    private fun withPlayer(action: (ExoPlayer) -> Unit) {
        val player = host()?.player
        if (player != null) {
            action(player)
            publish()
        } else {
            pending += action
            connect()
        }
    }

    // ---- Commands ----

    /** Replaces the queue with [tracks] and starts at [startIndex]. */
    fun play(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem() }
        withPlayer { QueueOps.play(it, items, startIndex) }
        tracks.getOrNull(startIndex)?.id?.let(onTrackStarted)
    }

    fun togglePlayPause() = withPlayer { player ->
        when {
            player.isPlaying -> player.pause()
            player.mediaItemCount == 0 -> host()?.restoreIfEmpty()
            else -> {
                // A queue that ran out starts again from its first song.
                if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition(0)
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
    }

    fun play() = withPlayer { player ->
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun pause() = withPlayer { it.pause() }

    fun next() = withPlayer { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }

    /** Restarts the song when more than four seconds in, as on Windows, and goes back otherwise. */
    fun previous() = withPlayer { player ->
        if (player.currentPosition > RESTART_THRESHOLD_MS || !player.hasPreviousMediaItem()) {
            player.seekTo(0)
        } else {
            player.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) = withPlayer { it.seekTo(positionMs.coerceAtLeast(0L)) }

    /** Stops a second short of the end, so a drag to the end doesn't skip the song. */
    fun seekToFraction(fraction: Float) = withPlayer { player ->
        val duration = player.duration
        if (duration > 0) {
            val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
            player.seekTo(target.coerceAtMost((duration - 1_000L).coerceAtLeast(0L)))
        }
    }

    fun toggleShuffle() = withPlayer { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withPlayer { player ->
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun addToQueue(tracks: List<Track>) {
        val items = tracks.map { it.toMediaItem() }
        withPlayer { QueueOps.addToQueue(it, items) }
    }

    fun playNext(tracks: List<Track>) {
        val items = tracks.map { it.toMediaItem() }
        withPlayer { QueueOps.playNext(it, items) }
    }

    /** Positions are in the play order, as the Up next list shows it. */
    fun moveInQueue(from: Int, to: Int) = withPlayer { QueueOps.move(it, from, to) }

    fun removeFromQueue(position: Int) = withPlayer { QueueOps.remove(it, position) }

    fun clearQueue() = withPlayer { QueueOps.clearUpcoming(it) }

    fun jumpToQueueIndex(position: Int) = withPlayer { QueueOps.jumpTo(it, position) }

    /** Drops a deleted track from the queue so playback never points at a missing file. */
    fun evictTrack(trackId: Long) = withPlayer { player ->
        for (index in player.mediaItemCount - 1 downTo 0) {
            if (player.getMediaItemAt(index).trackId() == trackId) {
                player.removeMediaItem(index)
            }
        }
    }

    // ---- Internals ----

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            // Wall clock rather than player position: seeking around a track
            // must not be counted as time spent listening to it.
            var last = System.currentTimeMillis()
            while (true) {
                delay(POSITION_POLL_MS)
                val now = System.currentTimeMillis()
                controller?.takeIf { it.isPlaying }?.let { playing ->
                    // A song restored at start-up was loaded before this
                    // controller connected, so no transition ever announced
                    // it; name the song with every tick so its listening
                    // still counts. The recorder ignores a repeat of the same.
                    playing.currentMediaItem?.trackId()?.let(onTrackStarted)
                    onElapsed(now - last)
                }
                last = now
                publish()
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
        onPlaybackPaused()
    }

    private var lastTimeline: Any? = null
    private var lastShuffle = false
    private var cachedQueue: Pair<List<Long>, List<Int>> = emptyList<Long>() to emptyList()

    /**
     * The queue in play order. Rebuilt only when the timeline object or the
     * shuffle setting has changed: a position tick twice a second can't
     * reorder it, and walking a long queue on every tick was measurable.
     */
    private fun queueOf(player: Player): Pair<List<Long>, List<Int>> {
        val timeline = player.currentTimeline
        if (timeline === lastTimeline && player.shuffleModeEnabled == lastShuffle) return cachedQueue
        val order = QueueOps.order(player)
        cachedQueue = order.map { player.getMediaItemAt(it).trackId() ?: -1L } to order
        lastTimeline = timeline
        lastShuffle = player.shuffleModeEnabled
        return cachedQueue
    }

    private fun publish() {
        val player = controller
        if (player == null) {
            _state.value = _state.value.copy(isConnected = false, isPlaying = false)
            lastTimeline = null
            return
        }
        val (ids, order) = queueOf(player)
        _state.value = PlaybackState(
            isConnected = true,
            currentTrackId = player.currentMediaItem?.trackId(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queueTrackIds = ids,
            queueIndex = order.indexOf(player.currentMediaItemIndex)
        )
    }

    private companion object {
        const val POSITION_POLL_MS = 500L
        const val RESTART_THRESHOLD_MS = 4_000L
    }
}
