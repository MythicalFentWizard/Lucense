package com.exo.musicplayer.desktop.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.exo.musicplayer.data.download.DownloadQuality
import com.exo.musicplayer.data.download.LinkResolver
import com.exo.musicplayer.data.download.ResolvedLink
import com.exo.musicplayer.data.library.DuplicateMatcher
import com.exo.musicplayer.data.lyrics.GeniusLyricsProvider
import com.exo.musicplayer.data.lyrics.LrcLibProvider
import com.exo.musicplayer.data.lyrics.LyricsFetch
import com.exo.musicplayer.data.lyrics.LyricsOvhProvider
import com.exo.musicplayer.data.lyrics.LyricsProviderChain
import com.exo.musicplayer.data.lyrics.NeteaseLyricsProvider
import com.exo.musicplayer.data.recognition.AudiusProvider
import com.exo.musicplayer.data.recognition.DeezerProvider
import com.exo.musicplayer.data.recognition.GeniusLyricSearch
import com.exo.musicplayer.data.recognition.GeniusMetadataProvider
import com.exo.musicplayer.data.recognition.ITunesProvider
import com.exo.musicplayer.data.recognition.InternetArchiveProvider
import com.exo.musicplayer.data.recognition.MetadataProviderChain
import com.exo.musicplayer.data.recognition.MusicBrainzProvider
import com.exo.musicplayer.data.recognition.MatchRanker
import com.exo.musicplayer.data.recognition.MusicMatch
import com.exo.musicplayer.data.recognition.NeteaseLyricSearch
import com.exo.musicplayer.data.recognition.RecognitionResult
import com.exo.musicplayer.data.recognition.ShazamClient
import com.exo.musicplayer.data.recognition.YouTubeSearchProvider
import com.exo.musicplayer.data.playlist.ImportResult
import com.exo.musicplayer.data.playlist.PlaylistEntry
import com.exo.musicplayer.data.playlist.PlaylistFile
import com.exo.musicplayer.data.weather.Affinity
import com.exo.musicplayer.data.weather.GeoPlace
import com.exo.musicplayer.data.weather.OpenMeteo
import com.exo.musicplayer.data.weather.WeatherAffinity
import com.exo.musicplayer.data.weather.WeatherCondition
import com.exo.musicplayer.data.weather.WeatherSnapshot
import com.exo.musicplayer.desktop.audio.AudioDevices
import com.exo.musicplayer.desktop.audio.DesktopAudioOutput
import com.exo.musicplayer.desktop.audio.MediaAudio
import com.exo.musicplayer.desktop.audio.SampleOutcome
import com.exo.musicplayer.desktop.audio.PlaybackEngine
import com.exo.musicplayer.desktop.download.DownloadProgress
import com.exo.musicplayer.desktop.download.ToolStatus
import com.exo.musicplayer.desktop.download.YtDlp
import com.exo.musicplayer.desktop.system.DuckKey
import com.exo.musicplayer.desktop.system.GlobalHotkey
import com.exo.musicplayer.desktop.library.DesktopTrack
import com.exo.musicplayer.desktop.library.FolderLibrary
import com.exo.musicplayer.desktop.ui.AccentChoice
import com.exo.musicplayer.desktop.ui.DesktopFxState
import com.exo.musicplayer.desktop.ui.Palette
import com.exo.musicplayer.desktop.ui.SidePanelKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** How the library table is ordered. */
enum class SortMode(val label: String) {
    ARTIST("Artist"),
    TITLE("Title"),
    ALBUM("Album"),
    DURATION("Duration"),
    PLAYS("Times played"),
    LISTEN_TIME("Time listened"),
    ADDED("Date added")
}

/** Progress of one of the bulk tools. */
data class BulkJob(
    val label: String = "",
    val running: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val current: String = "",
    val finishedNote: String? = null
) {
    val progress: Float get() = if (total > 0) done.toFloat() / total else 0f
}

/** One line in the download window's history. */
data class DownloadEntry(
    val id: Long,
    val target: String,
    var display: String,
    var percent: Float = 0f,
    var status: String = "Queued",
    var done: Boolean = false,
    var failed: Boolean = false,
    var files: List<File> = emptyList(),
    var note: String? = null
)

/** A duplicate set as the desktop sees it. */
data class DesktopDuplicateGroup(val keep: DesktopTrack, val remove: List<DesktopTrack>)

/**
 * Everything the Windows UI reads and acts on.
 *
 * A single observable state holder rather than thirty hoisted parameters. The
 * screens grew past the point where threading callbacks through the shell was
 * readable, and unlike Android there is no ViewModel to inherit — so this plays
 * the same role, owning the store, the playback engine and the provider chains,
 * and exposing plain Compose state.
 *
 * Every network and disk call goes through [io]; Compose state is only ever
 * written from the caller's (main) dispatcher.
 */
@Stable
class DesktopController(private val scope: CoroutineScope) {

    val settings = DesktopSettings()
    val store = DesktopStore(AppDirs.database)
    val engine = PlaybackEngine()

    init {
        Palette.use(AccentChoice.fromName(settings.accentName))
    }

    // ---- Providers ----------------------------------------------------------
    //
    // Seven catalogues, four lyric tiers. Identical to the Android build: the
    // implementations live in :shared, so neither platform has its own copy to
    // fall out of date.

    private val catalogue = MetadataProviderChain(
        listOf(
            ITunesProvider(),
            DeezerProvider(),
            MusicBrainzProvider(),
            AudiusProvider(),
            InternetArchiveProvider(),
            YouTubeSearchProvider(),
            GeniusMetadataProvider()
        )
    )

    private val lyricsChain = LyricsProviderChain(
        listOf(
            LrcLibProvider(),
            NeteaseLyricsProvider(),
            LyricsOvhProvider(),
            GeniusLyricsProvider()
        )
    )

    /**
     * Lyric-text search, kept separate from the catalogue chain.
     *
     * Only these two index the words themselves; sending a remembered line to
     * iTunes or MusicBrainz returns nothing, so mixing them in would only add
     * latency and empty rows.
     */
    private val lyricSearch = MetadataProviderChain(
        listOf(GeniusLyricSearch(), NeteaseLyricSearch())
    )

    private val shazam = ShazamClient()

    // ---- Library ------------------------------------------------------------

    var folders by mutableStateOf(settings.folders)
        private set
    var tracks by mutableStateOf<List<DesktopTrack>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set
    var scanned by mutableStateOf(0)
        private set
    var scanningName by mutableStateOf("")
        private set

    var query by mutableStateOf("")
    var sort by mutableStateOf(SortMode.ARTIST)
    var favourites by mutableStateOf<Set<String>>(emptySet())
        private set
    var favouritesOnly by mutableStateOf(false)

    private var listenTimes by mutableStateOf<Map<String, Long>>(emptyMap())
    private var playCounts by mutableStateOf<Map<String, Int>>(emptyMap())

    /**
     * The rows the table should show, after search, filter and sort.
     *
     * Derived rather than computed on read: the transport bar updates several
     * times a second, and without this the whole library would be filtered and
     * re-sorted on every one of those ticks.
     */
    val visibleTracks: List<DesktopTrack> get() = visibleTracksState.value

    private val visibleTracksState = derivedStateOf {
        val text = query.trim().lowercase(Locale.getDefault())
        val filtered = tracks.filter { track ->
            (!favouritesOnly || track.file.absolutePath in favourites) &&
                (text.isEmpty() ||
                    track.title.lowercase().contains(text) ||
                    track.displayArtist.lowercase().contains(text) ||
                    track.displayAlbum.lowercase().contains(text))
        }
        when (sort) {
            SortMode.ARTIST -> filtered.sortedWith(
                compareBy({ it.displayArtist.lowercase() }, { it.title.lowercase() })
            )
            SortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortMode.ALBUM -> filtered.sortedWith(
                compareBy({ it.displayAlbum.lowercase() }, { it.trackNumber ?: 0 })
            )
            SortMode.DURATION -> filtered.sortedByDescending { it.durationMs }
            SortMode.PLAYS -> filtered.sortedByDescending {
                playCounts[it.file.absolutePath] ?: 0
            }
            SortMode.LISTEN_TIME -> filtered.sortedByDescending {
                listenTimes[it.file.absolutePath] ?: 0L
            }
            SortMode.ADDED -> filtered.sortedByDescending { it.file.lastModified() }
        }
    }

    fun playCountOf(track: DesktopTrack): Int = playCounts[track.file.absolutePath] ?: 0

    fun listenedMsOf(track: DesktopTrack): Long = listenTimes[track.file.absolutePath] ?: 0L

    fun addFolder(dir: File) {
        folders = (folders + dir.absolutePath).distinct()
        settings.folders = folders
        rescan()
    }

    fun removeFolder(path: String) {
        folders = folders - path
        settings.folders = folders
        rescan()
    }

    fun rescan() {
        val roots = folders.map(::File).filter { it.isDirectory }
        if (roots.isEmpty()) {
            tracks = emptyList()
            scanning = false
            return
        }
        scope.launch {
            scanning = true
            scanned = 0
            val found = FolderLibrary.scan(roots) { count, name ->
                // Called from the IO dispatcher; only cheap value writes here.
                scanned = count
                scanningName = name
            }
            tracks = found
            scanning = false
            refreshAggregates()
        }
    }

    private suspend fun refreshAggregates() {
        val listen = io { store.listenTimeByPath() }
        val plays = io { store.playCountByPath() }
        val favs = io { store.favourites() }
        listenTimes = listen
        playCounts = plays
        favourites = favs
    }

    fun toggleFavourite(track: DesktopTrack) {
        val path = track.file.absolutePath
        val next = path !in favourites
        favourites = if (next) favourites + path else favourites - path
        scope.launch { io { store.setFavourite(path, next) } }
    }

    // ---- Playback -----------------------------------------------------------

    // Backed by explicit state objects rather than `by mutableStateOf` so the
    // setter can push straight to the audio thread: assigning is the whole API,
    // and there is no way to change one without the engine hearing about it.
    private val fxState = mutableStateOf(DesktopFxState())
    var fx: DesktopFxState
        get() = fxState.value
        set(value) {
            fxState.value = value
            engine.effects.speed = value.speed
            engine.effects.pitchSemitones = value.pitchSemitones
            engine.effects.reverb.enabled = value.reverbEnabled
            engine.effects.reverb.mix = value.reverbMix
            engine.effects.reverb.decay = value.reverbDecay
        }

    private val volumeState = mutableStateOf(settings.volume)
    var volume: Float
        get() = volumeState.value
        set(value) {
            volumeState.value = value
            settings.volume = value
            pushVolume()
        }

    /** True while the duck hotkey has the music turned down. */
    var ducked by mutableStateOf(false)
        private set

    private val duckEnabledState = mutableStateOf(settings.duckEnabled)
    var duckEnabled: Boolean
        get() = duckEnabledState.value
        set(value) {
            duckEnabledState.value = value
            settings.duckEnabled = value
            applyDuckBinding()
        }

    private val duckKeyState = mutableStateOf(DuckKey.fromName(settings.duckKeyName))
    var duckKey: DuckKey
        get() = duckKeyState.value
        set(value) {
            duckKeyState.value = value
            settings.duckKeyName = value.name
            applyDuckBinding()
        }

    private val duckPercentState = mutableStateOf(settings.duckPercent)
    var duckPercent: Int
        get() = duckPercentState.value
        set(value) {
            duckPercentState.value = value.coerceIn(10, 100)
            settings.duckPercent = duckPercentState.value
            if (ducked) pushVolume()
        }

    var duckError by mutableStateOf<String?>(null)
        private set

    private val hotkey = GlobalHotkey()

    /** Volume actually sent to the engine, after any ducking. */
    private fun pushVolume() {
        val factor = if (ducked) (100 - duckPercent) / 100f else 1f
        engine.setVolume(volume * factor)
    }

    private fun applyDuckBinding() {
        hotkey.unbind()
        duckError = null
        if (ducked) {
            ducked = false
            pushVolume()
        }
        if (!duckEnabled) return

        hotkey.bind(duckKey.virtualKey) {
            // Runs on the hotkey thread. Snapshot state is thread-safe, and the
            // engine reads volume from a volatile, so no hop to the UI thread is
            // needed - which matters, because the whole point is that this
            // responds while a game has focus and the UI is not being drawn.
            ducked = !ducked
            pushVolume()
        }
        duckError = hotkey.lastError
    }


    val outputs: List<DesktopAudioOutput> = AudioDevices.outputs()
    var selectedOutputs by mutableStateOf(
        settings.outputs.filter { name -> outputs.any { it.name == name } }
            .ifEmpty { listOf(outputs.first().name) }
    )
        private set

    /** The list playback walks through — whatever the user is currently looking at. */
    private var queue: List<DesktopTrack> = emptyList()

    /**
     * The play currently being timed.
     *
     * The row id only exists once the insert has landed, which is a dispatch
     * later than the play itself — so it is carried in a deferred rather than a
     * field. Skipping quickly through tracks used to close the wrong row.
     */
    private class OpenPlay(val path: String) {
        val rowId = CompletableDeferred<Long>()
    }

    private var openPlay: OpenPlay? = null

    /** Which docked panel is open, if any. */
    var sidePanel by mutableStateOf<SidePanelKind?>(null)

    var accent: AccentChoice
        get() = Palette.choice
        set(value) {
            Palette.use(value)
            settings.accentName = value.name
        }

    fun togglePanel(kind: SidePanelKind) {
        sidePanel = if (sidePanel == kind) null else kind
    }

    fun toggleOutput(output: DesktopAudioOutput) {
        selectedOutputs = if (output.name in selectedOutputs) {
            // Never end up with nothing selected — silence with no way back.
            (selectedOutputs - output.name).ifEmpty { listOf(outputs.first().name) }
        } else {
            selectedOutputs + output.name
        }
        settings.outputs = selectedOutputs
        engine.setOutputs(outputs.filter { it.name in selectedOutputs })
    }

    fun applyOutputs() {
        engine.setOutputs(outputs.filter { it.name in selectedOutputs })
    }

    fun play(track: DesktopTrack, from: List<DesktopTrack> = visibleTracks) {
        queue = from
        closeListenEvent()
        engine.play(track)
        openListenEvent(track)
        loadLyricsFor(track)
    }

    fun togglePlay() = engine.togglePlay()

    fun next() {
        val index = queue.indexOfFirst { it.file == engine.status.value.track?.file }
        if (index >= 0 && index < queue.lastIndex) play(queue[index + 1], queue)
    }

    fun previous() {
        val index = queue.indexOfFirst { it.file == engine.status.value.track?.file }
        if (index > 0) play(queue[index - 1], queue)
    }

    fun seekFraction(fraction: Float) {
        val duration = engine.status.value.durationMs
        if (duration > 0) engine.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    /** Called when a track finishes on its own. */
    fun advance() {
        closeListenEvent()
        next()
    }

    // ---- Listening history --------------------------------------------------

    private fun openListenEvent(track: DesktopTrack) {
        val open = OpenPlay(track.file.absolutePath)
        openPlay = open
        val group = weather?.condition?.name
        scope.launch { open.rowId.complete(io { store.startPlay(open.path, group) }) }
    }

    /** Writes how much of the outgoing track was actually heard. */
    fun closeListenEvent() {
        val open = openPlay ?: return
        openPlay = null
        val listened = engine.status.value.positionMs
        scope.launch {
            val id = open.rowId.await()
            if (id < 0) return@launch
            io { store.updateListened(id, listened) }
            if (listened >= DesktopStore.QUALIFYING_MS) refreshAggregates()
        }
    }

    // ---- Lyrics -------------------------------------------------------------

    var lyricsPlain by mutableStateOf<String?>(null)
        private set
    var lyricsSynced by mutableStateOf<String?>(null)
        private set
    var lyricsSource by mutableStateOf<String?>(null)
        private set
    var lyricsLoading by mutableStateOf(false)
        private set
    var lyricsNote by mutableStateOf<String?>(null)
        private set

    private fun loadLyricsFor(track: DesktopTrack) {
        lyricsPlain = null
        lyricsSynced = null
        lyricsSource = null
        lyricsNote = null
        scope.launch {
            val stored = io { store.lyricsFor(track.file.absolutePath) }
            if (stored != null) {
                lyricsPlain = stored.plain
                lyricsSynced = stored.synced
                lyricsSource = stored.source
            }
        }
    }

    fun fetchLyrics(track: DesktopTrack) {
        lyricsLoading = true
        lyricsNote = null
        scope.launch {
            val (result, provider) = lyricsChain.fetch(
                track.title,
                track.artist,
                track.album,
                track.durationMs
            )
            when (result) {
                is LyricsFetch.Found -> {
                    lyricsPlain = result.plain
                    lyricsSynced = result.synced
                    lyricsSource = provider
                    io {
                        store.saveLyrics(
                            track.file.absolutePath,
                            result.plain,
                            result.synced,
                            provider ?: "unknown"
                        )
                    }
                }
                LyricsFetch.Instrumental -> lyricsNote =
                    "Every service reports this as an instrumental."
                is LyricsFetch.Error -> lyricsNote = result.message
                LyricsFetch.NotFound -> lyricsNote =
                    "None of the four services had lyrics for this. You can paste them in yourself."
            }
            io { store.mark(track.file.absolutePath, "lyricsCheckedAt") }
            lyricsLoading = false
        }
    }

    fun saveManualLyrics(track: DesktopTrack, text: String) {
        val trimmed = text.trim()
        scope.launch {
            if (trimmed.isEmpty()) {
                io { store.deleteLyrics(track.file.absolutePath) }
                lyricsPlain = null
                lyricsSynced = null
                lyricsSource = null
            } else {
                // A pasted LRC is kept as synced so it still highlights in time.
                val looksTimed = Regex("""\[\d{1,3}:\d{1,2}""").containsMatchIn(trimmed)
                val plain = if (looksTimed) {
                    trimmed.lineSequence()
                        .map { it.replace(Regex("""\[[^]]*]"""), "").trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString("\n")
                } else {
                    trimmed
                }
                io {
                    store.saveLyrics(
                        track.file.absolutePath,
                        plain,
                        if (looksTimed) trimmed else null,
                        "Added by hand"
                    )
                    store.mark(track.file.absolutePath, "lyricsCheckedAt")
                }
                lyricsPlain = plain
                lyricsSynced = if (looksTimed) trimmed else null
                lyricsSource = "Added by hand"
            }
            lyricsNote = null
        }
    }

    // ---- Identify -----------------------------------------------------------

    var identifyQuery by mutableStateOf("")
    var identifyResults by mutableStateOf<List<MusicMatch>>(emptyList())
        private set
    var identifyBusy by mutableStateOf(false)
        private set
    var identifyStatus by mutableStateOf<String?>(null)
        private set
    var identifyTarget by mutableStateOf<DesktopTrack?>(null)
    var identifyMode by mutableStateOf(IdentifyMode.NAME)

    /** Set when the last attempt failed only because ffmpeg is missing. */
    var identifyNeedsFfmpeg by mutableStateOf(false)
        private set

    /**
     * Finds a song from a line of its lyrics.
     *
     * The reverse of the lyrics chain, and a genuinely different index: Genius
     * and NetEase both search lyric text, where the seven catalogues only match
     * titles and artists and would return nothing at all for a remembered line.
     */
    fun searchByLyrics(text: String = identifyQuery) {
        if (text.isBlank()) return
        identifyBusy = true
        identifyNeedsFfmpeg = false
        identifyStatus = "Searching lyrics..."
        scope.launch {
            val results = lyricSearch.searchAll(text, limitPer = 8)
            identifyResults = results
            identifyStatus = if (results.isEmpty()) {
                "No song matched those words. Try a longer or more distinctive line."
            } else {
                "${results.size} songs contain those words"
            }
            identifyBusy = false
        }
    }

    /**
     * Identifies any audio or video file on disk.
     *
     * Video included: ffmpeg pulls twelve seconds of mono out of the middle and
     * the existing fingerprint path takes it from there.
     */
    fun identifyMediaFile(file: File) {
        identifyTarget = null
        identifyBusy = true
        identifyNeedsFfmpeg = false
        identifyStatus = "Reading ${file.name}..."
        scope.launch { fingerprint(file, file.nameWithoutExtension, markPath = null) }
    }

    /**
     * Downloads the audio behind a link and identifies it.
     *
     * The same path a TikTok or Instagram link takes in the download window,
     * stopping at a temporary file rather than the library - yt-dlp handles well
     * over a thousand sites, so this is not limited to the obvious three.
     */
    fun identifyLink(url: String = identifyQuery) {
        val target = url.trim()
        if (target.isBlank()) return
        if (!tools.ready) {
            identifyStatus = "yt-dlp is missing from this installation - try reinstalling."
            return
        }
        identifyTarget = null
        identifyBusy = true
        identifyNeedsFfmpeg = false
        identifyStatus = "Fetching audio from that link..."

        scope.launch {
            val staging = io {
                File(AppDirs.tools, "identify-${System.currentTimeMillis()}").apply { mkdirs() }
            }
            try {
                val resolved = io { LinkResolver.resolve(target) }
                val fetchTarget = when (resolved) {
                    is ResolvedLink.Direct -> resolved.url
                    is ResolvedLink.Search -> LinkResolver.searchTarget(resolved.query)
                    is ResolvedLink.Unsupported -> {
                        identifyStatus = resolved.reason
                        identifyBusy = false
                        return@launch
                    }
                }

                val result = YtDlp.download(
                    target = fetchTarget,
                    destination = staging,
                    // No conversion: the fingerprinter only needs samples, and
                    // ffmpeg may not be installed.
                    toMp3 = false,
                    embedThumbnail = false,
                    wholePlaylist = false,
                    onProgress = { progress ->
                        progress.percent?.let {
                            identifyStatus = "Fetching audio... ${(it * 100).toInt()}%"
                        }
                    }
                )

                val file = result.getOrNull()?.firstOrNull()
                if (file == null) {
                    identifyStatus = result.exceptionOrNull()?.message
                        ?: "Couldn't get any audio from that link."
                    identifyBusy = false
                    return@launch
                }
                fingerprint(file, file.nameWithoutExtension, markPath = null)
            } finally {
                io { staging.deleteRecursively() }
            }
        }
    }

    /** Shared tail of every fingerprint route. */
    private suspend fun fingerprint(file: File, fallbackName: String, markPath: String?) {
        when (val outcome = MediaAudio.sample(file)) {
            SampleOutcome.NeedsFfmpeg -> {
                identifyNeedsFfmpeg = true
                identifyStatus = "That format needs ffmpeg - one click in the Download tab."
                identifyBusy = false
            }

            is SampleOutcome.Failed -> {
                identifyStatus = outcome.message
                identifyBusy = false
            }

            is SampleOutcome.Ok -> {
                identifyStatus = "Matching against Shazam..."
                when (val result = shazam.recognize(outcome.samples)) {
                    is RecognitionResult.Found -> {
                        identifyResults = result.matches
                        val best = result.matches.firstOrNull()
                        identifyStatus = "Identified: ${best?.display ?: fallbackName}"
                        identifyQuery = best?.display ?: fallbackName
                    }
                    RecognitionResult.NoMatch -> {
                        identifyStatus = "No fingerprint match. Searching by name instead..."
                        identifyMode = IdentifyMode.NAME
                        identifyQuery = fallbackName
                        identifyBusy = false
                        markPath?.let { io { store.mark(it, "fingerprintedAt") } }
                        searchCatalogues(fallbackName)
                        return
                    }
                    is RecognitionResult.Error -> identifyStatus = result.message
                }
                markPath?.let { io { store.mark(it, "fingerprintedAt") } }
                identifyBusy = false
            }
        }
    }

    fun searchCatalogues(text: String = identifyQuery) {
        if (text.isBlank()) return
        identifyBusy = true
        identifyNeedsFfmpeg = false
        identifyStatus = "Searching seven catalogues…"
        scope.launch {
            val found = catalogue.searchAll(text, limitPer = 8)
            // Providers each answer loosely and none of them can see the others,
            // so relevance is decided here rather than trusting whatever order
            // they came back in.
            val results = MatchRanker.rank(text, found)
            identifyResults = results
            val dropped = found.size - results.size
            identifyStatus = when {
                results.isEmpty() -> "Nothing came back. Try fewer words, or just the artist."
                dropped > 0 ->
                    "${results.size} matches from " +
                        "${results.map { it.provider }.distinct().size} services " +
                        "($dropped unrelated results hidden)"
                else ->
                    "${results.size} matches from " +
                        "${results.map { it.provider }.distinct().size} services"
            }
            identifyBusy = false
        }
    }

    /** Fingerprints a track in the library and asks Shazam what it is. */
    fun identifyFile(track: DesktopTrack) {
        identifyTarget = track
        identifyBusy = true
        identifyNeedsFfmpeg = false
        identifyStatus = "Fingerprinting ${track.title}..."
        scope.launch {
            fingerprint(track.file, track.title, markPath = track.file.absolutePath)
        }
    }

    /** Writes a chosen match onto a track: tags, and art if the match has any. */
    fun applyMatch(track: DesktopTrack, match: MusicMatch) {
        identifyBusy = true
        scope.launch {
            if (writeTags) {
                io {
                    TagWriter.write(
                        track.file,
                        match.title,
                        match.artist,
                        match.album,
                        match.releaseYear
                    )
                }
            }
            match.artworkUrl?.let { Covers.fetchAndStore(track, it, writeTags) }
            io { store.mark(track.file.absolutePath, "identifiedAt") }
            identifyStatus = "Applied \"${match.display}\" to ${track.file.name}."
            identifyBusy = false
            rescan()
        }
    }

    /** Mirrored into Compose state so toggling it redraws the switch. */
    private val writeTagsState = mutableStateOf(settings.writeTagsOnIdentify)
    var writeTags: Boolean
        get() = writeTagsState.value
        set(value) {
            writeTagsState.value = value
            settings.writeTagsOnIdentify = value
        }

    // ---- Bulk tools ---------------------------------------------------------

    var bulk by mutableStateOf(BulkJob())
        private set
    private var bulkJob: Job? = null

    fun cancelBulk() {
        bulkJob?.cancel()
        bulkJob = null
        bulk = bulk.copy(running = false, finishedNote = "Stopped.")
    }

    /**
     * @param redo when false, tracks already processed by this tool are skipped.
     *   Skips are counted and reported rather than silently dropped, so it is
     *   obvious why a run of 400 tracks finished in two seconds.
     */
    fun runBulk(kind: BulkKind, redo: Boolean) {
        if (bulk.running) return
        val column = kind.markColumn
        bulkJob = scope.launch {
            val already = if (redo) emptySet() else io { store.markedPaths(column) }
            val queue = tracks.filter { redo || it.file.absolutePath !in already }
            val skipped = tracks.size - queue.size

            bulk = BulkJob(
                label = kind.label,
                running = true,
                total = queue.size,
                skipped = skipped
            )

            var updated = 0
            var failed = 0
            for ((index, track) in queue.withIndex()) {
                bulk = bulk.copy(done = index, current = track.title)
                val ok = runCatching {
                    when (kind) {
                        BulkKind.COVERS -> bulkCover(track)
                        BulkKind.TAGS -> bulkTags(track)
                        BulkKind.LYRICS -> bulkLyrics(track)
                        BulkKind.IDENTIFY -> bulkIdentify(track)
                    }
                }.getOrDefault(false)
                if (ok) updated++ else failed++
                io { store.mark(track.file.absolutePath, column) }
                bulk = bulk.copy(updated = updated, failed = failed)
            }

            bulk = bulk.copy(
                running = false,
                done = queue.size,
                current = "",
                finishedNote = buildString {
                    append("$updated updated")
                    if (failed > 0) append(", $failed with nothing found")
                    if (skipped > 0) append(", $skipped skipped as already done")
                    append(".")
                }
            )
            bulkJob = null
            rescan()
        }
    }

    private suspend fun bulkCover(track: DesktopTrack): Boolean {
        if (io { Covers.hasLocalArt(track) }) return false
        val url = catalogue.searchForArtwork(track.searchQuery) ?: return false
        return Covers.fetchAndStore(track, url, writeTags)
    }

    private suspend fun bulkTags(track: DesktopTrack): Boolean {
        val (matches, _) = catalogue.search(track.searchQuery, limit = 1)
        val match = matches.firstOrNull() ?: return false
        return io {
            TagWriter.write(track.file, match.title, match.artist, match.album, match.releaseYear)
                .isSuccess
        }
    }

    private suspend fun bulkLyrics(track: DesktopTrack): Boolean {
        val (result, provider) = lyricsChain.fetch(
            track.title, track.artist, track.album, track.durationMs
        )
        if (result !is LyricsFetch.Found) return false
        io {
            store.saveLyrics(
                track.file.absolutePath, result.plain, result.synced, provider ?: "unknown"
            )
        }
        return true
    }

    private suspend fun bulkIdentify(track: DesktopTrack): Boolean {
        // Through MediaAudio rather than the plain sampler, so a bulk run also
        // covers m4a and video files sitting in the library.
        val outcome = MediaAudio.sample(track.file, track.durationMs)
        val samples = (outcome as? SampleOutcome.Ok)?.samples ?: return false
        val result = shazam.recognize(samples)
        val match = (result as? RecognitionResult.Found)?.matches?.firstOrNull() ?: return false
        if (writeTags) {
            io {
                TagWriter.write(
                    track.file, match.title, match.artist, match.album, match.releaseYear
                )
            }
        }
        match.artworkUrl?.let { Covers.fetchAndStore(track, it, writeTags) }
        return true
    }

    // ---- Playlists ----------------------------------------------------------

    var playlists by mutableStateOf<List<StoredPlaylist>>(emptyList())
        private set
    var openPlaylist by mutableStateOf<StoredPlaylist?>(null)
    var openPlaylistTracks by mutableStateOf<List<DesktopTrack>>(emptyList())
        private set

    fun refreshPlaylists() {
        scope.launch { playlists = io { store.playlists() } }
    }

    fun createPlaylist(name: String, initial: List<DesktopTrack> = emptyList()) {
        if (name.isBlank()) return
        scope.launch {
            val id = io { store.createPlaylist(name.trim()) }
            if (initial.isNotEmpty()) {
                io { store.addToPlaylist(id, initial.map { it.file.absolutePath }) }
            }
            playlists = io { store.playlists() }
        }
    }

    fun renamePlaylist(playlist: StoredPlaylist, name: String) {
        if (name.isBlank()) return
        scope.launch {
            io { store.renamePlaylist(playlist.id, name.trim()) }
            playlists = io { store.playlists() }
            if (openPlaylist?.id == playlist.id) openPlaylist = playlist.copy(name = name.trim())
        }
    }

    fun deletePlaylist(playlist: StoredPlaylist) {
        scope.launch {
            io { store.deletePlaylist(playlist.id) }
            if (openPlaylist?.id == playlist.id) openPlaylist = null
            playlists = io { store.playlists() }
        }
    }

    fun addToPlaylist(playlist: StoredPlaylist, items: List<DesktopTrack>) {
        scope.launch {
            io { store.addToPlaylist(playlist.id, items.map { it.file.absolutePath }) }
            playlists = io { store.playlists() }
            if (openPlaylist?.id == playlist.id) show(playlist)
        }
    }

    fun removeFromPlaylist(playlist: StoredPlaylist, track: DesktopTrack) {
        scope.launch {
            io { store.removeFromPlaylist(playlist.id, track.file.absolutePath) }
            playlists = io { store.playlists() }
            if (openPlaylist?.id == playlist.id) show(playlist)
        }
    }

    fun show(playlist: StoredPlaylist?) {
        openPlaylist = playlist
        if (playlist == null) {
            openPlaylistTracks = emptyList()
            return
        }
        scope.launch {
            val paths = io { store.playlistPaths(playlist.id) }
            val byPath = tracks.associateBy { it.file.absolutePath }
            openPlaylistTracks = paths.mapNotNull { byPath[it] }
        }
    }

    // ---- Sharing playlists ---------------------------------------------------

    var importResult by mutableStateOf<ImportResult<DesktopTrack>?>(null)
        private set
    var playlistNote by mutableStateOf<String?>(null)
        private set

    /** Writes a playlist out as text anyone can read, edit or send on. */
    fun exportPlaylist(playlist: StoredPlaylist, target: File) {
        scope.launch {
            val paths = io { store.playlistPaths(playlist.id) }
            val byPath = tracks.associateBy { it.file.absolutePath }
            val entries = paths.mapNotNull { path ->
                byPath[path]?.let {
                    PlaylistEntry(it.artist, it.title, it.durationMs)
                }
            }
            io { target.writeText(PlaylistFile.export(playlist.name, entries)) }
            playlistNote = "Exported ${entries.size} tracks to ${target.name}."
        }
    }

    /**
     * Reads a shared playlist and matches it against this library.
     *
     * Nothing is downloaded: the file names songs, and what is already here gets
     * linked up. Whatever is missing is reported so it can be found separately.
     */
    fun importPlaylist(source: File) {
        scope.launch {
            val parsed = io { PlaylistFile.parse(source.readText(), source.nameWithoutExtension) }
            if (parsed.entries.isEmpty()) {
                playlistNote = "That file had no tracks in it."
                return@launch
            }
            importResult = PlaylistFile.matchAgainst(
                playlist = parsed,
                library = tracks,
                artistOf = { it.artist },
                titleOf = { it.title },
                durationOf = { it.durationMs }
            )
            playlistNote = null
        }
    }

    /** Creates the playlist from a reviewed import. */
    fun confirmImport(result: ImportResult<DesktopTrack>) {
        scope.launch {
            val id = io { store.createPlaylist(result.name) }
            io { store.addToPlaylist(id, result.matched.map { it.second.file.absolutePath }) }
            playlists = io { store.playlists() }
            importResult = null
            playlistNote = buildString {
                append("Added ${result.matched.size} tracks as \"${result.name}\"")
                if (result.missing.isNotEmpty()) {
                    append("; ${result.missing.size} not in your library")
                }
                append(".")
            }
        }
    }

    fun dismissImport() {
        importResult = null
        playlistNote = null
    }

    // ---- Moods (weather) ----------------------------------------------------

    var weather by mutableStateOf<WeatherSnapshot?>(null)
        private set
    var weatherPlace by mutableStateOf(settings.weatherPlace)
        private set
    var weatherNote by mutableStateOf<String?>(null)
        private set
    var moodCondition by mutableStateOf<WeatherCondition?>(null)
    var moodTracks by mutableStateOf<List<Pair<DesktopTrack, Affinity>>>(emptyList())
        private set
    var moodHistorySize by mutableStateOf(0)
        private set
    var placeResults by mutableStateOf<List<GeoPlace>>(emptyList())
        private set

    fun refreshWeather(force: Boolean = false) {
        scope.launch {
            var latitude = settings.weatherLatitude
            var longitude = settings.weatherLongitude
            if (latitude.isNaN() || longitude.isNaN() || force && weatherPlace.isBlank()) {
                weatherNote = "Working out where you are from your IP address…"
                val place = OpenMeteo.locateByIp()
                if (place == null) {
                    weatherNote = "Couldn't work out your location. Set a city in Settings."
                    return@launch
                }
                latitude = place.latitude
                longitude = place.longitude
                settings.weatherLatitude = latitude
                settings.weatherLongitude = longitude
                if (settings.weatherPlace.isBlank()) {
                    settings.weatherPlace = place.display
                    weatherPlace = place.display
                }
            }
            val snapshot = OpenMeteo.current(latitude, longitude)
            if (snapshot == null) {
                weatherNote = "Open-Meteo didn't answer. Try again shortly."
            } else {
                weather = snapshot
                weatherNote = null
                if (moodCondition == null) moodCondition = snapshot.condition
                loadMood(moodCondition ?: snapshot.condition)
            }
        }
    }

    fun searchPlaces(text: String) {
        scope.launch { placeResults = OpenMeteo.geocode(text) }
    }

    fun usePlace(place: GeoPlace) {
        settings.weatherPlace = place.display
        settings.weatherLatitude = place.latitude
        settings.weatherLongitude = place.longitude
        weatherPlace = place.display
        placeResults = emptyList()
        refreshWeather()
    }

    fun useIpLocation() {
        settings.weatherPlace = ""
        settings.weatherLatitude = Double.NaN
        settings.weatherLongitude = Double.NaN
        weatherPlace = ""
        refreshWeather(force = true)
    }

    fun loadMood(condition: WeatherCondition) {
        moodCondition = condition
        scope.launch {
            val inCondition = io { store.playsPerTrackInCondition(condition.name) }
            val overall = io { store.playsPerTrackWithWeather() }
            val totalInCondition = io { store.totalPlaysInCondition(condition.name) }
            val totalWithWeather = io { store.totalPlaysWithWeather() }
            moodHistorySize = totalWithWeather

            val scored = WeatherAffinity.score(
                inCondition, overall, totalInCondition, totalWithWeather
            )
            // Scoring works in hashed ids; map them back to real files.
            val byKey = tracks.associateBy { DesktopStore.pathKey(it.file.absolutePath) }
            moodTracks = scored.mapNotNull { affinity ->
                byKey[affinity.trackId]?.let { it to affinity }
            }
        }
    }

    // ---- Stats --------------------------------------------------------------

    var statsTotalMs by mutableStateOf(0L)
        private set
    var statsPlays by mutableStateOf(0)
        private set
    var statsTracks by mutableStateOf(0)
        private set
    var statsTop by mutableStateOf<List<Pair<DesktopTrack, ListenRow>>>(emptyList())
        private set
    var statsByHour by mutableStateOf<Map<Int, Int>>(emptyMap())
        private set
    var statsByWeather by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    fun refreshStats() {
        scope.launch {
            statsTotalMs = io { store.totalListenedMs() }
            statsPlays = io { store.playCount() }
            statsTracks = io { store.distinctTracks() }
            statsByHour = io { store.playsByHour() }
            statsByWeather = io { store.playsByWeather() }
            val rows = io { store.topByListenTime(12) }
            val byPath = tracks.associateBy { it.file.absolutePath }
            statsTop = rows.mapNotNull { row -> byPath[row.path]?.let { it to row } }
        }
    }

    // ---- Downloads ----------------------------------------------------------

    var tools by mutableStateOf(ToolStatus())
        private set
    var toolNote by mutableStateOf<String?>(null)
        private set
    var toolProgress by mutableStateOf<Float?>(null)
        private set
    var downloadUrl by mutableStateOf("")
    var downloadToMp3 by mutableStateOf(true)
    var downloadEmbedArt by mutableStateOf(true)
    var downloadPlaylist by mutableStateOf(false)

    private val qualityState = mutableStateOf(
        DownloadQuality.fromName(settings.downloadQualityName)
    )
    var downloadQuality: DownloadQuality
        get() = qualityState.value
        set(value) {
            qualityState.value = value
            settings.downloadQualityName = value.name
        }
    var downloads by mutableStateOf<List<DownloadEntry>>(emptyList())
        private set
    var downloadLog by mutableStateOf<List<String>>(emptyList())
        private set

    fun refreshTools() {
        scope.launch {
            tools = io { YtDlp.status() }
            if (tools.ytDlp) tools = tools.copy(ytDlpVersion = YtDlp.version())
        }
    }

    fun updateYtDlp() {
        scope.launch {
            toolNote = "Updating yt-dlp…"
            val result = YtDlp.update { line -> downloadLog = (downloadLog + line).takeLast(200) }
            toolNote = result.fold(
                onSuccess = { "yt-dlp updated." },
                onFailure = { "Update failed: ${it.message}" }
            )
            refreshTools()
        }
    }

    /** Queues [rawUrl], resolving Spotify links to a searchable name first. */
    fun startDownload(rawUrl: String = downloadUrl) {
        val url = rawUrl.trim()
        if (url.isBlank()) return
        if (!tools.ready) {
            toolNote = "Install yt-dlp first — one click, about 12 MB."
            return
        }

        val entry = DownloadEntry(
            id = System.nanoTime(),
            target = url,
            display = url
        )
        downloads = downloads + entry
        downloadUrl = ""

        scope.launch {
            if (isSpotify(url)) {
                downloadWithSpotdl(entry.id, url)
                return@launch
            }

            val resolved = io { LinkResolver.resolve(url) }
            val target = when (resolved) {
                is ResolvedLink.Direct -> {
                    update(entry.id) { it.display = "${resolved.service} · $url" }
                    resolved.url
                }
                is ResolvedLink.Search -> {
                    update(entry.id) {
                        it.display = resolved.display
                        it.note = resolved.note
                    }
                    LinkResolver.searchTarget(resolved.query)
                }
                is ResolvedLink.Unsupported -> {
                    update(entry.id) {
                        it.failed = true
                        it.done = true
                        it.status = resolved.reason
                    }
                    return@launch
                }
            }

            update(entry.id) { it.status = "Starting…" }
            val destination = File(settings.downloadDir)
            val result = YtDlp.download(
                target = target,
                destination = destination,
                toMp3 = downloadToMp3 && tools.ffmpeg,
                embedThumbnail = downloadEmbedArt,
                wholePlaylist = downloadPlaylist,
                quality = downloadQuality,
                onProgress = { progress -> onDownloadProgress(entry.id, progress) }
            )

            result.fold(
                onSuccess = { files ->
                    update(entry.id) {
                        it.done = true
                        it.percent = 1f
                        it.files = files
                        it.status = if (files.isEmpty()) {
                            "Finished, but nothing was produced."
                        } else {
                            "Saved ${files.size} file${if (files.size == 1) "" else "s"}"
                        }
                    }
                    // If the download folder is in the library, the new files
                    // should appear without the user having to press rescan.
                    if (folders.any { destination.absolutePath.startsWith(it) }) rescan()
                },
                onFailure = { error ->
                    update(entry.id) {
                        it.done = true
                        it.failed = true
                        it.status = error.message ?: "Download failed."
                    }
                }
            )
        }
    }

    /**
     * Sends a search result to the downloader.
     *
     * Services that host audio (YouTube, Audius, the Internet Archive) carry a
     * real page URL. Pure metadata catalogues — iTunes, MusicBrainz, Genius —
     * do not, so the artist and title are handed to yt-dlp as a search instead,
     * which is the same fallback a Spotify link takes.
     */
    fun downloadMatch(match: MusicMatch) {
        if (match.drmProtected) {
            toolNote = "${match.provider} streams DRM-protected audio - there is no file " +
                "to download. Search by name to find it somewhere that hosts it."
            return
        }
        if (!tools.ready) {
            toolNote = "Install yt-dlp first — one click, about 12 MB."
            return
        }
        val direct = match.downloadUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
        if (direct != null) {
            startDownload(direct)
            return
        }

        val entry = DownloadEntry(
            id = System.nanoTime(),
            target = match.display,
            display = match.display,
            note = "${match.provider} has no downloadable file, so this searches " +
                "YouTube for the same track."
        )
        downloads = downloads + entry
        scope.launch {
            update(entry.id) { it.status = "Searching…" }
            val result = YtDlp.download(
                target = LinkResolver.searchTarget(match.display),
                destination = File(settings.downloadDir),
                toMp3 = downloadToMp3 && tools.ffmpeg,
                embedThumbnail = downloadEmbedArt,
                wholePlaylist = false,
                quality = downloadQuality,
                onProgress = { progress -> onDownloadProgress(entry.id, progress) }
            )
            result.fold(
                onSuccess = { files ->
                    update(entry.id) {
                        it.done = true
                        it.percent = 1f
                        it.files = files
                        it.status = "Saved ${files.size} file${if (files.size == 1) "" else "s"}"
                    }
                    rescan()
                },
                onFailure = { error ->
                    update(entry.id) {
                        it.done = true
                        it.failed = true
                        it.status = error.message ?: "Download failed."
                    }
                }
            )
        }
    }

    private fun isSpotify(url: String): Boolean =
        runCatching { java.net.URI(url).host.orEmpty().lowercase() }
            .getOrDefault("")
            .contains("spotify.com")

    /**
     * Hands a Spotify link to spotdl.
     *
     * spotdl reads Spotify's metadata and fetches the matching recording from
     * YouTube; Spotify's own audio is encrypted and no tool can download it. So
     * what arrives is a match rather than the Spotify file — usually the same
     * recording, occasionally a live take or a remaster. The note says so on the
     * row rather than leaving it to be discovered on listening.
     */
    private suspend fun downloadWithSpotdl(id: Long, url: String) {
        update(id) {
            it.status = "Reading Spotify…"
            it.note = "Spotify's audio is DRM protected, so spotdl looks the track up " +
                "and fetches the same recording from YouTube. Check it is the version " +
                "you wanted."
        }
        val result = YtDlp.downloadSpotify(
            url = url,
            destination = File(settings.downloadDir),
            toMp3 = downloadToMp3,
            quality = downloadQuality,
            onProgress = { progress -> onDownloadProgress(id, progress) }
        )
        result.fold(
            onSuccess = { files ->
                update(id) {
                    it.done = true
                    it.percent = 1f
                    it.files = files
                    it.status = if (files.isEmpty()) {
                        "Finished, but nothing was produced."
                    } else {
                        "Saved ${files.size} file${if (files.size == 1) "" else "s"}"
                    }
                }
                rescan()
            },
            onFailure = { error ->
                update(id) {
                    it.done = true
                    it.failed = true
                    it.status = error.message ?: "Download failed."
                }
            }
        )
    }

    private fun onDownloadProgress(id: Long, progress: DownloadProgress) {
        if (progress.line.isNotBlank()) {
            downloadLog = (downloadLog + progress.line).takeLast(200)
        }
        update(id) {
            progress.percent?.let { percent -> it.percent = percent }
            if (progress.line.startsWith("[ExtractAudio]")) it.status = "Converting…"
            else if (progress.percent != null) it.status = "Downloading"
        }
    }

    private fun update(id: Long, change: (DownloadEntry) -> Unit) {
        downloads = downloads.map { entry ->
            if (entry.id != id) entry else entry.copy().also(change)
        }
    }

    fun clearFinishedDownloads() {
        downloads = downloads.filterNot { it.done }
    }

    fun chooseDownloadDir(dir: File) {
        settings.downloadDir = dir.absolutePath
    }

    val downloadDir: String get() = settings.downloadDir

    // ---- Duplicates ---------------------------------------------------------

    var duplicates by mutableStateOf<List<DesktopDuplicateGroup>>(emptyList())
        private set
    var duplicatesScanning by mutableStateOf(false)
        private set
    var duplicatesNote by mutableStateOf<String?>(null)
        private set

    fun findDuplicates() {
        duplicatesScanning = true
        duplicatesNote = null
        scope.launch {
            val counts = playCounts
            val found = io {
                // Reading embedded art opens the file, so a comparator that
                // called it directly would re-read the same track O(log n) times.
                val hasArt = HashMap<String, Boolean>()
                DuplicateMatcher.group(
                    items = tracks,
                    artistOf = { it.artist },
                    titleOf = { it.title },
                    durationOf = { it.durationMs },
                    // Biggest file first as a stand-in for bitrate, then one
                    // with art, then the one actually played.
                    keeperOrder = compareByDescending<DesktopTrack> { it.sizeBytes }
                        .thenByDescending {
                            hasArt.getOrPut(it.file.absolutePath) {
                                Covers.hasLocalArt(it)
                            }
                        }
                        .thenByDescending { counts[it.file.absolutePath] ?: 0 }
                        .thenBy { it.file.lastModified() }
                ).map { DesktopDuplicateGroup(it.keep, it.remove) }
                    .sortedBy { it.keep.title.lowercase(Locale.ROOT) }
            }
            duplicates = found
            duplicatesScanning = false
            if (found.isEmpty()) duplicatesNote = "No duplicates found."
        }
    }

    /**
     * Sends the chosen copies to the Recycle Bin where the platform allows it.
     *
     * Deliberately not [File.delete]: these are the user's own files, sitting in
     * their own folders, and a wrong match should be recoverable.
     */
    fun removeDuplicates(groups: List<DesktopDuplicateGroup>) {
        scope.launch {
            val paths = groups.flatMap { it.remove }.map { it.file }
            val removed = io {
                paths.count { file ->
                    runCatching {
                        val desktop = java.awt.Desktop.getDesktop()
                        if (desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) {
                            desktop.moveToTrash(file)
                        } else {
                            file.delete()
                        }
                    }.getOrDefault(false)
                }
            }
            io { store.forgetPaths(paths.map { it.absolutePath }) }
            duplicates = emptyList()
            duplicatesNote = "Removed $removed file${if (removed == 1) "" else "s"}."
            rescan()
        }
    }

    // ---- Lifecycle ----------------------------------------------------------

    fun start() {
        applyOutputs()
        pushVolume()
        applyDuckBinding()
        if (folders.isNotEmpty()) rescan() else scope.launch { refreshAggregates() }
        refreshPlaylists()
        refreshTools()
        refreshWeather()
    }

    fun release() {
        hotkey.unbind()
        closeListenEvent()
        engine.release()
        store.close()
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}

/** How the Identify box interprets what you typed. */
enum class IdentifyMode(val label: String, val placeholder: String) {
    NAME("Name", "Artist and title, or whatever you can remember"),
    LYRICS("Lyrics", "A line you remember, however roughly"),
    LINK("Link", "TikTok, Instagram, YouTube, SoundCloud link")
}

/** The four bulk tools, and the mark each one records so reruns can skip. */
enum class BulkKind(val label: String, val markColumn: String) {
    COVERS("Covers", "artCheckedAt"),
    TAGS("Names & tags", "identifiedAt"),
    LYRICS("Lyrics", "lyricsCheckedAt"),
    IDENTIFY("Identify by sound", "fingerprintedAt")
}

/** What to send a catalogue when looking a track up. */
private val DesktopTrack.searchQuery: String
    get() = listOfNotNull(artist?.takeIf { it.isNotBlank() }, title).joinToString(" ")
