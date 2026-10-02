package com.exo.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.exo.musicplayer.MainActivity
import com.exo.musicplayer.MusicApp
import com.exo.musicplayer.data.db.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** What the rest of the app reaches the player through, while the service is up. */
@UnstableApi
interface PlayerHost {
    /** The player the session is showing - the one heard, or fading in. */
    val player: ExoPlayer

    /** Puts whatever was playing last back, paused, if nothing is loaded. */
    fun restoreIfEmpty()
}

/**
 * Hosts the players and the MediaSession. Because this is a
 * MediaSessionService, Media3 puts up the media notification and wires the
 * lockscreen and Bluetooth controls for us, and playback keeps running when the
 * UI goes away.
 *
 * What it adds on top, all of which used to be missing on the phone:
 *  - the queue, its order and the position survive the service being stopped,
 *    and come back paused where they were - including from the media controls
 *    after Android has stopped the app entirely;
 *  - crossfade, as a real overlap on a second player;
 *  - volume levelling, the sleep timer, and effects kept for one song.
 */
@UnstableApi
class PlaybackService : MediaSessionService(), PlayerHost {

    private lateinit var app: MusicApp
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val spectrum = SpectrumTap(Spectrum.analyser)
    private val mirror = MultiDeviceTap()

    private lateinit var decks: Pair<Deck, Deck>
    private lateinit var active: Deck
    private val standby: Deck get() = if (active === decks.first) decks.second else decks.first

    private var mediaSession: MediaSession? = null
    private var focus: AudioFocusController? = null
    private var outputs: MultiOutputController? = null
    private lateinit var memory: PlaybackMemory

    /** Every track by id, kept current, for levels and for restoring. */
    private var library: Map<Long, Track> = emptyMap()

    override val player: ExoPlayer get() = active.player

    override fun onCreate() {
        super.onCreate()
        app = application as MusicApp
        memory = PlaybackMemory(this)

        decks = Deck(this, listOf(mirror, spectrum)) to Deck(this, listOf(mirror, spectrum))
        active = decks.first
        active.live = true
        decks.first.player.addListener(deckListener(decks.first))
        decks.second.player.addListener(deckListener(decks.second))

        mediaSession = MediaSession.Builder(this, active.player)
            .setSessionActivity(openAppIntent())
            .setCallback(SessionCallback())
            .build()

        // Focus is handled here rather than by ExoPlayer: a call starting must
        // silence playback outright, while starting playback during a call
        // should be allowed quietly. See AudioFocusController.
        focus = AudioFocusController(
            context = this,
            scope = serviceScope,
            settingsProvider = { app.interruption.state.value },
            onVolume = { level -> decks.first.focusVolume = level; decks.second.focusVolume = level }
        ).also { it.attach(active.player) }

        outputs = MultiOutputController(mirror)
        serviceScope.launch {
            // Re-pin whenever the choice changes or a device connects.
            combine(
                app.audioOutputs.selectedKeys,
                app.audioOutputs.mirrorEnabled,
                app.audioOutputs.outputs
            ) { _, mirrorOn, _ -> mirrorOn }.collect { mirrorOn ->
                outputs?.apply(active.player, app.audioOutputs.selectedDevices(), mirrorOn)
            }
        }

        // The UI writes the same settings objects; both live in this process,
        // so effects follow a slider without a round trip through the session.
        serviceScope.launch { app.audioFx.state.collect { applyFx(active) } }
        serviceScope.launch { app.prefs.levelling.collect { applyTone(decks.first); applyTone(decks.second) } }
        serviceScope.launch {
            app.library.observeAllTracks().collect { tracks ->
                library = tracks.associateBy { it.id }
                applyTone(decks.first)
                applyTone(decks.second)
            }
        }
        serviceScope.launch { app.sleep.state.collect { runSleep(it) } }

        app.attachHost(this)
        restoreIfEmpty()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Swiping the app away should not kill audio that is still playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        remember()
        val current = mediaSession?.player
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        remember()
        app.detachHost(this)
        serviceScope.cancel()
        focus?.release()
        focus = null
        outputs?.release()
        outputs = null
        mediaSession?.release()
        mediaSession = null
        decks.first.release()
        decks.second.release()
        super.onDestroy()
    }

    // ---- Remembering and restoring -------------------------------------------

    private var restoring = false

    override fun restoreIfEmpty() {
        if (restoring || active.player.mediaItemCount > 0) return
        val saved = memory.load() ?: return
        restoring = true
        serviceScope.launch {
            val items = itemsFor(saved)
            restoring = false
            // Something may have started playing while the library was read;
            // that wins over what was there last time.
            if (items == null || active.player.mediaItemCount > 0) return@launch
            val (mediaItems, index, order) = items
            val p = active.player
            p.setMediaItems(mediaItems, index, saved.positionMs)
            p.repeatMode = saved.repeatMode
            p.shuffleModeEnabled = saved.shuffle
            if (saved.shuffle) QueueOps.setOrder(p, order)
            p.prepare()
        }
    }

    /** The saved queue as media items, minus songs deleted since, with the index and order remapped. */
    private suspend fun itemsFor(saved: PlaybackMemory.Saved): Triple<List<MediaItem>, Int, List<Int>>? {
        val tracks = app.library.tracksByIds(saved.trackIds).associateBy { it.id }
        val kept = saved.trackIds.indices.filter { tracks[saved.trackIds[it]] != null }
        if (kept.isEmpty()) return null
        val newIndexOf = kept.withIndex().associate { (newIndex, oldIndex) -> oldIndex to newIndex }
        val items = kept.map { tracks.getValue(saved.trackIds[it]).toMediaItem() }
        val index = newIndexOf[saved.index] ?: 0
        val order = saved.order.mapNotNull { newIndexOf[it] }
        return Triple(items, index, order)
    }

    private var saveJob: Job? = null

    /** Saves soon rather than now, so a burst of events costs one write. */
    private fun rememberSoon() {
        saveJob?.cancel()
        saveJob = serviceScope.launch {
            delay(400)
            remember()
        }
    }

    private fun remember() {
        val p = active.player
        if (p.mediaItemCount == 0) return
        val ids = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).trackId() ?: -1L }
        if (ids.any { it < 0 }) return
        memory.save(
            PlaybackMemory.Saved(
                trackIds = ids,
                order = QueueOps.order(p),
                index = p.currentMediaItemIndex,
                positionMs = p.currentPosition.coerceAtLeast(0L),
                shuffle = p.shuffleModeEnabled,
                repeatMode = p.repeatMode
            )
        )
    }

    // ---- Each deck --------------------------------------------------------------

    private fun deckListener(deck: Deck) = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            applyTone(deck)
            if (deck === active) {
                app.audioFx.onTrackChanged(mediaItem?.trackId())
                rememberSoon()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (deck === active && shuffleModeEnabled) QueueOps.shuffleAroundCurrent(deck.player)
            if (deck === active) rememberSoon()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (deck !== active) return
            if (isPlaying) startWatching() else {
                rememberSoon()
                // Pausing in the middle of a crossfade silences both songs.
                if (!deck.player.playWhenReady) finishFadeNow()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (deck === active && !playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
            ) {
                // The end-of-song sleep timer has done its job.
                deck.player.pauseAtEndOfMediaItems = false
                if (app.sleep.state.value == Sleep.EndOfSong) app.sleep.cancel()
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (deck !== active) return
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_REPEAT_MODE_CHANGED
                )
            ) {
                rememberSoon()
            }
        }
    }

    private var watcher: Job? = null

    /**
     * While music plays: save the position every few seconds, and start a
     * crossfade when the song gets close enough to its end.
     */
    private fun startWatching() {
        if (watcher?.isActive == true) return
        watcher = serviceScope.launch {
            var sinceSave = 0L
            while (isActive) {
                delay(WATCH_MS)
                if (!active.player.isPlaying) break
                sinceSave += WATCH_MS
                if (sinceSave >= SAVE_EVERY_MS) {
                    sinceSave = 0
                    remember()
                }
                maybeCrossfade()
            }
        }
    }

    // ---- Effects, the equalizer and levelling ----------------------------------

    private fun applyFx(deck: Deck) {
        val state = app.audioFx.state.value
        deck.fx.apply(deck.player, state)
        applyTone(deck)
    }

    private fun applyTone(deck: Deck) {
        val state = app.audioFx.state.value
        val id = deck.player.currentMediaItem?.trackId()
        val gain = if (app.prefs.levelling.value) levelGain(library[id]) else 1f
        deck.tone.set(state.eqEnabled, state.eqGains, gain)
    }

    /**
     * Towards -14 dBFS, never more than twelve decibels either way, and never
     * so far that the loudest sample is pushed into clipping - the same rule
     * as Windows, so a song is as loud on the phone as it is there.
     */
    private fun levelGain(track: Track?): Float {
        val db = track?.levelDb ?: return 1f
        val wanted = (TARGET_DBFS - db).coerceIn(-MOST_DB, MOST_DB)
        val gain = 10f.pow(wanted / 20f)
        val peak = track.levelPeak ?: 0f
        val headroom = if (peak > 0.001f) 0.99f / peak else gain
        return max(0.05f, min(gain, headroom))
    }

    // ---- Crossfade ------------------------------------------------------------

    private var fading: Job? = null
    private var fadingOut: Deck? = null

    private fun maybeCrossfade() {
        val seconds = app.prefs.crossfadeSeconds.value
        if (seconds <= 0 || fading != null) return
        val p = active.player
        if (!p.isPlaying || p.repeatMode == Player.REPEAT_MODE_ONE) return
        if (app.sleep.state.value == Sleep.EndOfSong) return
        val next = p.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return
        val duration = p.duration
        val overlap = seconds * 1000L
        // A song shorter than twice the overlap would be mostly fade.
        if (duration == C.TIME_UNSET || duration < overlap * 2 + 5_000) return
        val remaining = duration - p.currentPosition
        if (remaining > overlap) return
        startCrossfade(next, remaining.coerceIn(1_000, overlap))
    }

    /**
     * Starts the next song on the other deck, hands the session to it, and
     * swaps the two over [lengthMs] with an equal-power curve so the middle of
     * the overlap doesn't sag.
     */
    private fun startCrossfade(nextIndex: Int, lengthMs: Long) {
        val from = active
        val to = standby
        val source = from.player
        val items = (0 until source.mediaItemCount).map { source.getMediaItemAt(it) }
        val order = QueueOps.order(source)

        to.fadeVolume = 0f
        to.player.setMediaItems(items, nextIndex, 0L)
        to.player.repeatMode = source.repeatMode
        to.player.shuffleModeEnabled = source.shuffleModeEnabled
        if (source.shuffleModeEnabled) QueueOps.setOrder(to.player, order)
        to.player.playbackParameters = source.playbackParameters
        to.player.prepare()
        to.player.play()

        // From here the new song is the one shown and controlled.
        active = to
        to.live = true
        from.live = false
        mediaSession?.player = to.player
        focus?.attach(to.player)
        outputs?.apply(to.player, app.audioOutputs.selectedDevices(), app.audioOutputs.mirrorEnabled.value)
        applyFx(to)
        app.audioFx.onTrackChanged(to.player.currentMediaItem?.trackId())
        rememberSoon()

        fadingOut = from
        fading = serviceScope.launch {
            val steps = (lengthMs / FADE_STEP_MS).coerceAtLeast(1)
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                from.fadeVolume = cos(t * PI / 2).toFloat()
                to.fadeVolume = sin(t * PI / 2).toFloat()
                delay(FADE_STEP_MS)
            }
            finishFadeNow()
        }
        startWatching()
    }

    /** Ends a crossfade at once: the outgoing song stops, the incoming one is at full level. */
    private fun finishFadeNow() {
        val out = fadingOut ?: return
        fading?.cancel()
        fading = null
        fadingOut = null
        out.player.pause()
        out.player.clearMediaItems()
        out.fadeVolume = 1f
        active.fadeVolume = 1f
    }

    // ---- Sleep timer ------------------------------------------------------------

    private var sleepJob: Job? = null

    private fun runSleep(sleep: Sleep) {
        sleepJob?.cancel()
        decks.first.sleepVolume = 1f
        decks.second.sleepVolume = 1f
        active.player.pauseAtEndOfMediaItems = sleep == Sleep.EndOfSong
        if (sleep !is Sleep.At) return
        sleepJob = serviceScope.launch {
            val fade = if (app.prefs.sleepFade.value) SleepTimer.FADE_MS else 0L
            val fadeFrom = sleep.endsAtMs - fade
            delay((fadeFrom - System.currentTimeMillis()).coerceAtLeast(0))
            val start = System.currentTimeMillis()
            val length = (sleep.endsAtMs - start).coerceAtLeast(1)
            while (fade > 0 && isActive) {
                val t = ((System.currentTimeMillis() - start).toFloat() / length).coerceIn(0f, 1f)
                active.sleepVolume = 1f - t
                if (t >= 1f) break
                delay(FADE_STEP_MS)
            }
            finishFadeNow()
            active.player.pause()
            remember()
            active.sleepVolume = 1f
            app.sleep.cancel()
        }
    }

    // ---- The session ------------------------------------------------------------

    private inner class SessionCallback : MediaSession.Callback {
        /**
         * The media controls asking to carry on after Android has stopped the
         * app - the play button in the notification shade, a headset button.
         * Answered from the same memory a normal start restores from.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val saved = memory.load()
            if (saved == null) {
                future.setException(UnsupportedOperationException("Nothing to resume"))
                return future
            }
            serviceScope.launch {
                val items = itemsFor(saved)
                if (items == null) {
                    future.setException(UnsupportedOperationException("Nothing to resume"))
                } else {
                    future.set(MediaSession.MediaItemsWithStartPosition(items.first, items.second, saved.positionMs))
                }
            }
            return future
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private companion object {
        const val WATCH_MS = 250L
        const val SAVE_EVERY_MS = 5_000L
        const val FADE_STEP_MS = 50L
        const val TARGET_DBFS = -14f
        const val MOST_DB = 12f
    }
}
