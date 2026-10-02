package com.exo.musicplayer

import android.app.Application
import android.content.Context
import android.os.StrictMode
import androidx.media3.common.util.UnstableApi
import com.exo.musicplayer.data.prefs.AppPrefs
import com.exo.musicplayer.playback.PlayerHost
import com.exo.musicplayer.playback.SleepTimer
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
@androidx.annotation.OptIn(UnstableApi::class)
class MusicApp : Application() {

    /**
     * Main-dispatched on purpose: PlaybackConnection drives a MediaController,
     * and Media3 throws if a controller is touched off its application thread.
     * Everything genuinely blocking (file copies, Room writes) switches
     * dispatcher itself.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val library: LibraryRepository by lazy { LibraryRepository(this) }
    val importer: TrackImporter by lazy { TrackImporter(this) }
    val stats: StatsRepository by lazy { StatsRepository(this) }
    val weather: WeatherRepository by lazy { WeatherRepository(this) }
    val shazam: ShazamClient by lazy { ShazamClient() }
    val lyrics: LyricsRepository by lazy { LyricsRepository(this, prefs) }
    val themeSettings: ThemeSettings by lazy { ThemeSettings(this) }
    val audioFx: AudioFxSettings by lazy { AudioFxSettings(this) }
    val audioOutputs: AudioOutputRepository by lazy { AudioOutputRepository(this) }
    val interruption: InterruptionSettings by lazy { InterruptionSettings(this) }
    val prefs: AppPrefs by lazy { AppPrefs(this) }
    val sleep: SleepTimer by lazy { SleepTimer() }

    /** The running player service, while there is one. */
    private var host: PlayerHost? = null

    fun attachHost(service: PlayerHost) {
        host = service
        playback.onHostReady()
    }

    fun detachHost(service: PlayerHost) {
        if (host === service) host = null
    }

    private val recorder: ListeningRecorder by lazy {
        ListeningRecorder(appScope, stats, library, weather)
    }

    val playback: PlaybackConnection by lazy {
        PlaybackConnection(
            context = this,
            scope = appScope,
            host = { host },
            onTrackStarted = { trackId -> recorder.onTrackStarted(trackId) },
            onElapsed = { deltaMs -> recorder.onElapsed(deltaMs) },
            onPlaybackPaused = { recorder.flush() }
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Development builds name any disk or network work done on the main
        // thread, and anything leaked, in the log. Releases don't carry this.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects()
                    .detectActivityLeaks().penaltyLog().build()
            )
        }
        // Bound straight away, so the service is up - and has put back what
        // was playing last time - before anything on screen asks for it.
        playback.connect()
        appScope.launch {
            // Storage the user cleared behind our back shouldn't leave ghost rows.
            library.pruneMissingFiles()
            importer.cleanupOrphans()
        }
    }
}

val Context.musicApp: MusicApp
    get() = applicationContext as MusicApp
