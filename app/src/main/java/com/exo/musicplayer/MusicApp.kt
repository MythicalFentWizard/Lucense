package com.exo.musicplayer

import android.app.Application
import android.content.Context
import com.exo.musicplayer.data.ingest.TrackImporter
import com.exo.musicplayer.data.repo.LibraryRepository
import com.exo.musicplayer.data.recognition.ShazamClient
import com.exo.musicplayer.data.repo.LyricsRepository
import com.exo.musicplayer.data.repo.StatsRepository
import com.exo.musicplayer.data.weather.WeatherRepository
import com.exo.musicplayer.playback.AudioFxSettings
import com.exo.musicplayer.playback.AudioOutputRepository
import com.exo.musicplayer.playback.InterruptionSettings
import com.exo.musicplayer.playback.ListeningRecorder
import com.exo.musicplayer.ui.theme.ThemeSettings
import com.exo.musicplayer.playback.PlaybackConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Poor-man's DI. The app has a handful of long-lived collaborators, so a
 * container on the Application beats pulling in a framework.
 */
class MusicApp : Application() {

    /**
     * Main-dispatched on purpose: PlaybackConnection drives a MediaController,
     * and Media3 throws if a controller is touched off its application thread.
     * Everything genuinely blocking (file copies, Room writes) switches
     * dispatcher itself.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Splash is per process launch, not per Activity, so switching back to
     *  the app does not replay it. */
    var splashShown: Boolean = false

    val library: LibraryRepository by lazy { LibraryRepository(this) }
    val importer: TrackImporter by lazy { TrackImporter(this) }
    val stats: StatsRepository by lazy { StatsRepository(this) }
    val weather: WeatherRepository by lazy { WeatherRepository(this) }
    val shazam: ShazamClient by lazy { ShazamClient() }
    val lyrics: LyricsRepository by lazy { LyricsRepository(this) }
    val themeSettings: ThemeSettings by lazy { ThemeSettings(this) }
    val audioFx: AudioFxSettings by lazy { AudioFxSettings(this) }
    val audioOutputs: AudioOutputRepository by lazy { AudioOutputRepository(this) }
    val interruption: InterruptionSettings by lazy { InterruptionSettings(this) }

    private val recorder: ListeningRecorder by lazy {
        ListeningRecorder(appScope, stats, library, weather)
    }

    val playback: PlaybackConnection by lazy {
        PlaybackConnection(
            context = this,
            scope = appScope,
            onTrackStarted = { trackId -> recorder.onTrackStarted(trackId) },
            onElapsed = { deltaMs -> recorder.onElapsed(deltaMs) },
            onPlaybackPaused = { recorder.flush() }
        )
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // Storage the user cleared behind our back shouldn't leave ghost rows.
            library.pruneMissingFiles()
            importer.cleanupOrphans()
        }
    }
}

val Context.musicApp: MusicApp
    get() = applicationContext as MusicApp
