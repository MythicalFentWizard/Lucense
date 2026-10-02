package com.exo.musicplayer.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exo.musicplayer.BuildConfig
import com.exo.musicplayer.data.archive.ArchiveEntry
import com.exo.musicplayer.data.archive.MusicArchive
import com.exo.musicplayer.data.db.HourBucket
import com.exo.musicplayer.data.db.Lyrics
import com.exo.musicplayer.data.db.MusicDatabase
import com.exo.musicplayer.data.db.PlaylistSummary
import com.exo.musicplayer.data.db.SmartPlaylist
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.db.TrackListenTime
import com.exo.musicplayer.data.db.WeatherBucket
import com.exo.musicplayer.data.ingest.FolderScanner
import com.exo.musicplayer.data.ingest.ImportResult
import com.exo.musicplayer.data.library.AudioPrint
import com.exo.musicplayer.data.library.AutoPlaylist
import com.exo.musicplayer.data.library.DuplicateFinder
import com.exo.musicplayer.data.library.DuplicateGroup
import com.exo.musicplayer.data.library.LoudnessMeter
import com.exo.musicplayer.data.library.PhoneBackup
import com.exo.musicplayer.data.library.SearchQuery
import com.exo.musicplayer.data.library.TelegramName
import com.exo.musicplayer.data.lyrics.LrcParser
import com.exo.musicplayer.data.lyrics.LyricLine
import com.exo.musicplayer.data.lyrics.LyricsFetch
import com.exo.musicplayer.data.lyrics.LyricsTerm
import com.exo.musicplayer.data.lyrics.tidyLyrics
import com.exo.musicplayer.data.playlist.PlaylistEntry
import com.exo.musicplayer.data.playlist.PlaylistFile
import com.exo.musicplayer.data.prefs.GroupSort
import com.exo.musicplayer.data.prefs.LibrarySort
import com.exo.musicplayer.data.prefs.LibraryView
import com.exo.musicplayer.data.recognition.AudioSampler
import com.exo.musicplayer.data.recognition.AudiusProvider
import com.exo.musicplayer.data.recognition.DeezerProvider
import com.exo.musicplayer.data.recognition.GeniusMetadataProvider
import com.exo.musicplayer.data.recognition.ITunesProvider
import com.exo.musicplayer.data.recognition.MetadataProviderChain
import com.exo.musicplayer.data.recognition.MusicBrainzProvider
import com.exo.musicplayer.data.recognition.MusicMatch
import com.exo.musicplayer.data.recognition.RecognitionResult
import com.exo.musicplayer.data.recognition.YouTubeSearchProvider
import com.exo.musicplayer.data.update.Updates
import com.exo.musicplayer.data.weather.Affinity
import com.exo.musicplayer.data.weather.WeatherAffinity
import com.exo.musicplayer.data.weather.WeatherSnapshot
import com.exo.musicplayer.musicApp
import com.exo.musicplayer.playback.AudioFxState
import com.exo.musicplayer.playback.AudioOutput
import com.exo.musicplayer.playback.FxPreset
import com.exo.musicplayer.playback.InterruptionBehavior
import com.exo.musicplayer.playback.InterruptionState
import com.exo.musicplayer.playback.PlaybackState
import com.exo.musicplayer.playback.ReverbRoom
import com.exo.musicplayer.playback.Sleep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** What the Moods tab should be showing right now. */
sealed interface MoodState {
    data object NeedsPermission : MoodState
    data object Loading : MoodState
    data object Unavailable : MoodState
    data class Learning(val playsRecorded: Int, val playsNeeded: Int, val weather: WeatherSnapshot) :
        MoodState
    data class Ready(
        val weather: WeatherSnapshot,
        val tracks: List<Track>,
        val affinities: Map<Long, Affinity>
    ) : MoodState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.musicApp
    private val library = app.library
    private val playback = app.playback
    private val stats = app.stats
    private val weather = app.weather
    private val prefs = app.prefs

    private val started = SharingStarted.WhileSubscribed(5_000)

    // The same sources, in the same order, as Windows uses for covers and for
    // looking a song up by name: YouTube first, then the stores and free
    // catalogues, MusicBrainz last.
    private val lookup = MetadataProviderChain(
        listOf(
            YouTubeSearchProvider(),
            ITunesProvider(),
            DeezerProvider(),
            AudiusProvider(),
            GeniusMetadataProvider(),
            MusicBrainzProvider()
        )
    )

    /** The cover services, in their usual order, for the Settings choice. */
    val coverProviders: List<String> get() = lookup.labels

    // ---- The library, and how it's ordered ------------------------------------

    private val allTracks: StateFlow<List<Track>> = library.observeAllTracks()
        .stateIn(viewModelScope, started, emptyList())

    private val listenTotals: StateFlow<Map<Long, Long>> = stats.observeListenTotals()
        .map { rows -> rows.associate { it.trackId to it.totalMs } }
        .stateIn(viewModelScope, started, emptyMap())

    val sort: StateFlow<LibrarySort> = prefs.sort
    val view: StateFlow<LibraryView> = prefs.view
    val albumSort: StateFlow<GroupSort> = prefs.albumSort
    val artistSort: StateFlow<GroupSort> = prefs.artistSort
    val savedTab: StateFlow<String> = prefs.tab

    fun setSort(value: LibrarySort) = prefs.setSort(value)
    fun setView(value: LibraryView) = prefs.setView(value)
    fun setAlbumSort(value: GroupSort) = prefs.setAlbumSort(value)
    fun setArtistSort(value: GroupSort) = prefs.setArtistSort(value)
    fun rememberTab(name: String) = prefs.setTab(name)

    val tracks: StateFlow<List<Track>> = combine(allTracks, prefs.sort, listenTotals) { all, sort, listened ->
        withContext(Dispatchers.Default) { Sorting.tracks(all, sort, listened) }
    }.stateIn(viewModelScope, started, emptyList())

    val albums: StateFlow<List<TrackGroup>> = combine(allTracks, prefs.albumSort) { all, sort ->
        withContext(Dispatchers.Default) { Sorting.albums(all, sort) }
    }.stateIn(viewModelScope, started, emptyList())

    val artists: StateFlow<List<TrackGroup>> = combine(allTracks, prefs.artistSort) { all, sort ->
        withContext(Dispatchers.Default) { Sorting.artists(all, sort) }
    }.stateIn(viewModelScope, started, emptyList())

    private val _openGroup = MutableStateFlow<String?>(null)

    /** The album or artist opened from its card, if any; kept current as its songs change. */
    val openGroup: StateFlow<TrackGroup?> = combine(_openGroup, albums, artists, prefs.view) { key, a, b, view ->
        if (key == null) null else (if (view == LibraryView.ARTISTS) b else a).firstOrNull { it.key == key }
    }.stateIn(viewModelScope, started, null)

    fun openGroup(key: String?) { _openGroup.value = key }

    val favorites: StateFlow<List<Track>> = library.observeFavorites()
        .stateIn(viewModelScope, started, emptyList())

    val playlists: StateFlow<List<PlaylistSummary>> = library.observePlaylists()
        .stateIn(viewModelScope, started, emptyList())

    val libraryIsEmpty: StateFlow<Boolean> = allTracks
        .map { it.isEmpty() }
        .stateIn(viewModelScope, started, false)

    /** The lists worked out from the library and what's been played, as on Windows. */
    val autoLists: StateFlow<Map<AutoPlaylist, List<Track>>> = allTracks.map { all ->
        withContext(Dispatchers.Default) {
            val cap = AutoPlaylist.CAP
            mapOf(
                AutoPlaylist.RECENT to all.sortedByDescending { it.addedAt }.take(cap),
                AutoPlaylist.HISTORY to all.filter { it.lastPlayedAt != null }
                    .sortedByDescending { it.lastPlayedAt }.take(cap),
                AutoPlaylist.MOST_PLAYED to all.filter { it.playCount > 0 }
                    .sortedByDescending { it.playCount }.take(cap),
                AutoPlaylist.NEVER_PLAYED to all.filter { it.playCount == 0 },
                AutoPlaylist.TOP_RATED to all.filter { it.rating >= 4 }
                    .sortedWith(compareByDescending<Track> { it.rating }.thenBy { it.title.lowercase() }),
                AutoPlaylist.FAVOURITES to all.filter { it.isFavorite }.sortedBy { it.title.lowercase() }
            )
        }
    }.stateIn(viewModelScope, started, emptyMap())

    fun playAuto(kind: AutoPlaylist, shuffled: Boolean = false) {
        val list = autoLists.value[kind].orEmpty()
        if (list.isEmpty()) return
        playback.play(if (shuffled) list.shuffled() else list, 0)
    }

    // ---- Playback -------------------------------------------------------------

    val playbackState: StateFlow<PlaybackState> = playback.state

    // Position updates twice a second. Screens that only need identity or
    // play/pause subscribe to these instead, so a tick doesn't recompose lists.
    val currentTrackId: StateFlow<Long?> = playback.state
        .map { it.currentTrackId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, started, null)

    val isPlaying: StateFlow<Boolean> = playback.state
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, started, false)

    val currentTrack: StateFlow<Track?> = combine(currentTrackId, allTracks) { id, all ->
        all.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, started, null)

    /** The queue in the order it will play, current song included. */
    val queue: StateFlow<List<Track>> = combine(
        playback.state.map { it.queueTrackIds }.distinctUntilChanged(),
        allTracks
    ) { ids, all ->
        val byId = all.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }.stateIn(viewModelScope, started, emptyList())

    fun playFrom(list: List<Track>, index: Int) = playback.play(list, index)
    fun playAll() = playback.play(tracks.value, 0)

    fun shuffleAll() {
        val all = tracks.value
        if (all.isEmpty()) return
        playback.play(all.shuffled(), 0)
    }

    fun togglePlayPause() = playback.togglePlayPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekToFraction(fraction: Float) = playback.seekToFraction(fraction)
    fun toggleShuffle() = playback.toggleShuffle()
    fun cycleRepeat() = playback.cycleRepeat()
    fun playNext(track: Track) = playback.playNext(listOf(track))
    fun addToQueue(track: Track) = playback.addToQueue(listOf(track))
    fun jumpToQueueIndex(position: Int) = playback.jumpToQueueIndex(position)
    fun removeFromQueue(position: Int) = playback.removeFromQueue(position)
    fun moveInQueue(from: Int, to: Int) = playback.moveInQueue(from, to)
    fun clearQueue() = playback.clearQueue()

    // ---- Sleep timer, crossfade, levelling --------------------------------------

    val sleep: StateFlow<Sleep> = app.sleep.state
    fun sleepIn(minutes: Int) = app.sleep.setMinutes(minutes)
    fun sleepAtEndOfSong() = app.sleep.endOfSong()
    fun cancelSleep() = app.sleep.cancel()

    val crossfadeSeconds: StateFlow<Int> = prefs.crossfadeSeconds
    fun setCrossfadeSeconds(seconds: Int) = prefs.setCrossfadeSeconds(seconds)
    val levelling: StateFlow<Boolean> = prefs.levelling
    fun setLevelling(on: Boolean) = prefs.setLevelling(on)
    val sleepFade: StateFlow<Boolean> = prefs.sleepFade
    fun setSleepFade(on: Boolean) = prefs.setSleepFade(on)

    // ---- Search ---------------------------------------------------------------

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * The box understands the same filters as on Windows - artist:, album:,
     * genre:, rating:4+, year:2015-2020, plays:0, added:30d, fav:, quoted
     * phrases - and ignores accents, because it's the same parser.
     */
    val searchResults: StateFlow<List<Track>> = combine(_query.debounce(150), tracks) { text, all ->
        if (text.isBlank()) emptyList() else withContext(Dispatchers.Default) {
            val parsed = SearchQuery.of(text.trim())
            all.filter { library.matches(parsed, it) }
        }
    }.stateIn(viewModelScope, started, emptyList())

    val recentSearches: StateFlow<List<String>> = prefs.searches

    fun setQuery(value: String) { _query.value = value }
    fun rememberSearch() = prefs.rememberSearch(_query.value)
    fun forgetSearches() = prefs.forgetSearches()

    init {
        // Something typed and left alone for a moment, with results, is worth
        // offering again; a half-typed word that was deleted isn't.
        viewModelScope.launch {
            _query.debounce(1_500).collect { text ->
                if (text.isNotBlank() && searchResults.value.isNotEmpty()) prefs.rememberSearch(text)
            }
        }
    }

    // ---- Stats ----------------------------------------------------------------

    val totalListenedMs: StateFlow<Long> = stats.observeTotalListenedMs()
        .stateIn(viewModelScope, started, 0L)

    val totalPlays: StateFlow<Int> = stats.observePlayCount()
        .stateIn(viewModelScope, started, 0)

    val distinctTracksPlayed: StateFlow<Int> = stats.observeDistinctTracksPlayed()
        .stateIn(viewModelScope, started, 0)

    val topTracks: StateFlow<List<Pair<Track, TrackListenTime>>> =
        combine(stats.observeTopByListenTime(25), allTracks) { rows, all ->
            val byId = all.associateBy { it.id }
            rows.mapNotNull { row -> byId[row.trackId]?.let { it to row } }
        }.stateIn(viewModelScope, started, emptyList())

    val topThisWeek: StateFlow<List<Pair<Track, TrackListenTime>>> =
        combine(stats.observeTopThisWeek(10), allTracks) { rows, all ->
            val byId = all.associateBy { it.id }
            rows.mapNotNull { row -> byId[row.trackId]?.let { it to row } }
        }.stateIn(viewModelScope, started, emptyList())

    val listeningByHour: StateFlow<List<HourBucket>> = stats.observeByHour()
        .stateIn(viewModelScope, started, emptyList())

    val listeningByWeather: StateFlow<List<WeatherBucket>> = stats.observeByWeather()
        .stateIn(viewModelScope, started, emptyList())

    // ---- Moods (weather matching) ---------------------------------------------

    private val _mood = MutableStateFlow<MoodState>(MoodState.Loading)
    val mood: StateFlow<MoodState> = _mood.asStateFlow()

    /** Recomputed on demand: a network call and several aggregate queries. */
    fun refreshMood(force: Boolean = false) = viewModelScope.launch {
        if (!weather.hasLocationPermission()) {
            _mood.value = MoodState.NeedsPermission
            return@launch
        }
        _mood.value = MoodState.Loading

        val snapshot = runCatching { weather.currentWeather(force) }.getOrNull()
        if (snapshot == null) {
            _mood.value = MoodState.Unavailable
            return@launch
        }

        val recorded = runCatching { stats.weatherTaggedPlays() }.getOrDefault(0)
        if (recorded < WeatherAffinity.MIN_HISTORY) {
            _mood.value = MoodState.Learning(recorded, WeatherAffinity.MIN_HISTORY, snapshot)
            return@launch
        }

        val affinities = runCatching { stats.affinitiesFor(snapshot.condition) }
            .getOrDefault(emptyList())
        val ranked = library.tracksByIds(affinities.map { it.trackId })
        _mood.value = if (ranked.isEmpty()) {
            MoodState.Learning(recorded, WeatherAffinity.MIN_HISTORY, snapshot)
        } else {
            MoodState.Ready(snapshot, ranked, affinities.associateBy { it.trackId })
        }
    }

    fun playMood() {
        val ready = _mood.value as? MoodState.Ready ?: return
        if (ready.tracks.isNotEmpty()) playback.play(ready.tracks, 0)
    }

    // ---- Importing ------------------------------------------------------------

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun consumeNotice() { _notice.value = null }

    /** Live progress for a folder import. */
    data class BulkProgress(
        val done: Int = 0,
        val total: Int = 0,
        val added: Int = 0,
        val duplicates: Int = 0,
        val failed: Int = 0,
        val currentName: String? = null,
        val scanning: Boolean = false
    ) {
        val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    }

    private val _bulk = MutableStateFlow<BulkProgress?>(null)
    val bulk: StateFlow<BulkProgress?> = _bulk.asStateFlow()

    private var importJob: Job? = null

    fun cancelBulkImport() {
        importJob?.cancel()
        importJob = null
        _bulk.value = null
    }

    /**
     * Imports files chosen through the system picker. Same pipeline as a Telegram
     * share -- copied, hashed, tagged -- so files added this way behave
     * identically and deduplicate against songs already in the library.
     */
    fun importFromUris(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        var added = 0
        var duplicates = 0
        var failed = 0
        _bulk.value = BulkProgress(total = uris.size)
        for ((index, uri) in uris.withIndex()) {
            _bulk.value = BulkProgress(done = index, total = uris.size, added = added, duplicates = duplicates, failed = failed)
            when (app.importer.import(uri, sourceApp = "Files")) {
                is ImportResult.Imported -> added++
                is ImportResult.Duplicate -> duplicates++
                else -> failed++
            }
        }
        _bulk.value = null
        _notice.value = summarise(added, duplicates, failed)
    }

    /**
     * Bulk-imports every audio file under a folder the user granted access to.
     *
     * Safe to re-run: content hashing means already-imported songs are detected
     * as duplicates and skipped, so an interrupted import can simply be started
     * again rather than resumed.
     */
    fun importFolder(treeUri: Uri, label: String?) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _bulk.value = BulkProgress(scanning = true)
            val found = runCatching {
                FolderScanner.findAudio(getApplication(), treeUri)
            }.getOrDefault(emptyList())

            if (found.isEmpty()) {
                _bulk.value = null
                _notice.value = "No audio files found in that folder"
                return@launch
            }

            var added = 0
            var duplicates = 0
            var failed = 0
            _bulk.value = BulkProgress(total = found.size)

            for ((index, item) in found.withIndex()) {
                if (!isActive) return@launch
                _bulk.value = BulkProgress(
                    done = index,
                    total = found.size,
                    added = added,
                    duplicates = duplicates,
                    failed = failed,
                    currentName = item.name
                )
                when (app.importer.import(item.uri, sourceApp = label ?: "Folder")) {
                    is ImportResult.Imported -> added++
                    is ImportResult.Duplicate -> duplicates++
                    else -> failed++
                }
            }

            _bulk.value = null
            _notice.value = summarise(added, duplicates, failed)
        }
    }

    private fun summarise(added: Int, duplicates: Int, failed: Int): String = buildString {
        append(if (added == 1) "Added 1 song" else "Added $added songs")
        if (duplicates > 0) append(", $duplicates already in library")
        if (failed > 0) append(", $failed skipped")
    }

    // ---- Library tools ----------------------------------------------------------
    //
    // One job at a time, shown where it was started: the tools sheet, or Fix.

    private val _job = MutableStateFlow<ToolJob?>(null)
    val job: StateFlow<ToolJob?> = _job.asStateFlow()
    private var toolJob: Job? = null

    fun cancelJob() {
        toolJob?.cancel()
        toolJob = null
        _job.value = _job.value?.copy(running = false, current = "", note = "Stopped.")
    }

    fun dismissJob() {
        if (_job.value?.running != true) _job.value = null
    }

    /** How many songs each tool would visit without redo, for the tools sheet. */
    val toolCounts: StateFlow<Map<LibraryTool, Int>> = allTracks.map { all ->
        LibraryTool.entries.associateWith { tool -> all.count { needs(tool, it, names = true, tags = true) } }
    }.stateIn(viewModelScope, started, emptyMap())

    private fun fileStem(track: Track): String =
        (track.originalName ?: File(track.filePath).name).substringBeforeLast('.')

    /** Whether [tool] has something to do for [track] when not redoing everything. */
    private fun needs(tool: LibraryTool, track: Track, names: Boolean, tags: Boolean): Boolean = when (tool) {
        LibraryTool.NAMES_TAGS ->
            (names && (track.artist.isNullOrBlank() || track.title == fileStem(track))) ||
                (tags && (track.album.isNullOrBlank() || track.year == null || track.genre.isNullOrBlank()))
        LibraryTool.IDENTIFY -> track.identifiedAt == null
        LibraryTool.TELEGRAM ->
            track.durationMs <= 0L ||
                (track.artist.isNullOrBlank() && TelegramName.of(fileStem(track)) != null)
        LibraryTool.COVERS -> track.artCheckedAt == null
        LibraryTool.LYRICS -> track.lyricsCheckedAt == null
        LibraryTool.LEVELS -> track.levelDb == null
    }

    /** Runs one of the library tools over the whole library. */
    fun runTool(tool: LibraryTool, redo: Boolean, names: Boolean = true, tags: Boolean = true) {
        if (_job.value?.running == true) return
        val targets = allTracks.value.filter { redo || needs(tool, it, names, tags) }
        if (targets.isEmpty()) {
            _job.value = ToolJob(tool.label, running = false, note = "Nothing left to do. Tick Redo to run it again.")
            return
        }
        val workers = when (tool) {
            // Decoding audio, so a couple at most.
            LibraryTool.IDENTIFY -> 1
            LibraryTool.LEVELS -> 2
            else -> 5
        }
        runJob(tool.label, targets, workers, rescan = false) { track ->
            when (tool) {
                LibraryTool.NAMES_TAGS -> namesAndTags(track, names, tags)
                LibraryTool.IDENTIFY -> identify(track)
                LibraryTool.TELEGRAM -> telegram(track)
                LibraryTool.COVERS -> cover(track)
                LibraryTool.LYRICS -> lyricsFor(track, redo)
                LibraryTool.LEVELS -> level(track)
            }
        }
    }

    /**
     * Works through [targets] a few at a time, keeping [job] current. A song
     * that throws counts as nothing found rather than ending the run.
     */
    private fun runJob(
        label: String,
        targets: List<Track>,
        workers: Int,
        rescan: Boolean,
        finish: ((updated: Int, missed: Int) -> String)? = null,
        step: suspend (Track) -> Boolean
    ) {
        toolJob = viewModelScope.launch {
            var done = 0
            var updated = 0
            var missed = 0
            val inFlight = mutableListOf<String>()
            val queue = Channel<Track>(Channel.UNLIMITED)
            targets.forEach { queue.trySend(it) }
            queue.close()
            _job.value = ToolJob(label, total = targets.size)
            coroutineScope {
                repeat(workers) {
                    launch {
                        for (track in queue) {
                            inFlight += track.title
                            _job.value = _job.value?.copy(current = inFlight.joinToString("  ·  "))
                            val ok = try {
                                step(track)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                false
                            }
                            if (ok) updated++ else missed++
                            done++
                            inFlight -= track.title
                            _job.value = _job.value?.copy(done = done, current = inFlight.joinToString("  ·  "))
                        }
                    }
                }
            }
            _job.value = ToolJob(
                label,
                done = targets.size,
                total = targets.size,
                running = false,
                note = finish?.invoke(updated, missed) ?: buildString {
                    append("$updated updated")
                    if (missed > 0) append(", $missed with nothing found")
                    append(".")
                }
            )
            toolJob = null
        }
    }

    /** By name: the title and artist, or the album, year and genre, or both. */
    private suspend fun namesAndTags(track: Track, names: Boolean, tags: Boolean): Boolean {
        val details = lookup.findDetails(track.artist, track.title, giveUpMs = LOOKUP_GIVE_UP_MS) ?: return false
        val useful = (names && (details.title != null || details.artist != null)) ||
            (tags && (details.album != null || details.year != null || details.genre != null))
        if (!useful) return false
        library.applyDetails(
            track,
            title = details.title.takeIf { names },
            artist = details.artist.takeIf { names },
            album = details.album.takeIf { tags },
            year = details.year.takeIf { tags },
            genre = details.genre.takeIf { tags }
        )
        return true
    }

    private suspend fun identify(track: Track): Boolean {
        val match = listenTo(track)
        val applied = match != null && runCatching { library.applyMatch(track, match) }.isSuccess
        library.markIdentified(track.id)
        return applied
    }

    /**
     * Telegram's exports: a name from "Title   Artist" for a song with no
     * artist, and a length for one whose file never stated it. Seeking in
     * those works on the phone regardless; the player counts frames.
     */
    private suspend fun telegram(track: Track): Boolean {
        var mended = false
        val named = TelegramName.of(fileStem(track))
        if (named != null && track.artist.isNullOrBlank()) {
            library.applyDetails(track, title = named.title, artist = named.artist)
            mended = true
        }
        if (track.durationMs <= 0L) {
            val measured = withContext(Dispatchers.IO) {
                runCatching {
                    MediaMetadataRetriever().run {
                        try {
                            setDataSource(track.filePath)
                            extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        } finally {
                            release()
                        }
                    }
                }.getOrNull()
            }
            if (measured != null && measured > 0) {
                library.setDuration(track.id, measured)
                mended = true
            }
        }
        return mended
    }

    private suspend fun cover(track: Track): Boolean {
        val query = listOfNotNull(track.artist, track.title).joinToString(" ")
        val ok = runCatching {
            lookup.preferring(prefs.coverProvider.value.ifBlank { null }).findArtwork(query) { url ->
                library.updateArtwork(track, url).takeIf { it }
            }
        }.getOrNull() == true
        library.markArtChecked(track.id)
        return ok
    }

    private suspend fun lyricsFor(track: Track, force: Boolean): Boolean {
        val found = runCatching { app.lyrics.fetch(track, force = force) }.getOrNull() is LyricsFetch.Found
        library.markLyricsChecked(track.id)
        return found
    }

    private suspend fun level(track: Track): Boolean {
        val reading = withContext(Dispatchers.IO) { LoudnessMeter.measure(File(track.filePath)) } ?: return false
        library.setLevel(track.id, reading.dbfs, reading.peak)
        return true
    }

    /** Fingerprinting decodes audio, so however many songs are being fixed, one is heard at a time. */
    private val listening = Mutex()

    /** The song identified from its own audio. */
    private suspend fun listenTo(track: Track): MusicMatch? = listening.withLock {
        val samples = runCatching {
            AudioSampler.sampleMono16k(getApplication(), Uri.fromFile(File(track.filePath)))
        }.getOrNull() ?: return null
        (runCatching { app.shazam.recognize(samples) }.getOrNull() as? RecognitionResult.Found)
            ?.matches?.firstOrNull()
    }

    // ---- Fix, for the songs picked out ------------------------------------------

    private val _fixTargets = MutableStateFlow<List<Track>>(emptyList())
    val fixTargets: StateFlow<List<Track>> = _fixTargets.asStateFlow()

    fun fixSelection(visible: List<Track>) {
        _fixTargets.value = selectedTracks(visible)
    }

    fun fixOne(track: Track) {
        _fixTargets.value = listOf(track)
    }

    fun dismissFix() {
        _fixTargets.value = emptyList()
        dismissJob()
    }

    /**
     * Fetches whichever parts were ticked for the songs picked out.
     *
     * Unlike the tools this never skips a song for having been visited before:
     * picking songs out is the instruction to do them now. A song is looked up
     * by name when it has one worth searching for; one whose name is only an
     * id is identified by listening first, then looked up under the name that
     * turned out to be its own.
     */
    fun runFix(tags: Boolean, genres: Boolean, lyrics: Boolean) {
        val targets = _fixTargets.value
        if (_job.value?.running == true || targets.isEmpty() || !(tags || genres || lyrics)) return
        var tagged = 0
        var genred = 0
        var lyricked = 0
        var heard = 0
        runJob(
            FIX_LABEL, targets, workers = 4, rescan = false,
            finish = { _, missed ->
                val found = buildList {
                    if (tags) add("tags for $tagged")
                    if (genres) add("genres for $genred")
                    if (lyrics) add("lyrics for $lyricked")
                }
                buildString {
                    append("Found ${found.joinToString(", ")} of ${targets.size}.")
                    if (heard > 0) append(" $heard identified by listening.")
                    if (missed > 0) append(" Nothing at all for $missed.")
                }
            }
        ) { track ->
            var details = if (nameWorthSearching(track)) {
                lookup.findDetails(track.artist, track.title, giveUpMs = LOOKUP_GIVE_UP_MS)
            } else {
                null
            }
            var match: MusicMatch? = null
            if (details == null) {
                match = listenTo(track)
                if (match != null && (tags || genres)) {
                    details = lookup.findDetails(match.artist, match.title, giveUpMs = LOOKUP_GIVE_UP_MS)
                }
            }
            if (match != null) heard++
            val title = details?.title ?: match?.title
            val artist = details?.artist ?: match?.artist
            val album = details?.album ?: match?.album
            val year = details?.year ?: match?.releaseYear
            val genre = details?.genre ?: match?.genre

            val haveTags = tags && (title != null || artist != null || album != null || year != null)
            val haveGenre = genres && genre != null
            if (haveTags || haveGenre) {
                library.applyDetails(
                    track,
                    title = title.takeIf { tags },
                    artist = artist.takeIf { tags },
                    album = album.takeIf { tags },
                    year = year.takeIf { tags },
                    genre = genre.takeIf { genres }
                )
                if (haveTags) tagged++
                if (haveGenre) genred++
            }
            // Looked for under the song's real name whenever one was just found,
            // whether or not that name was wanted in the library.
            val gotLyrics = lyrics && runCatching {
                app.lyrics.fetch(track.copy(title = title ?: track.title, artist = artist ?: track.artist), force = true)
            }.getOrNull() is LyricsFetch.Found
            if (gotLyrics) lyricked++
            haveTags || haveGenre || gotLyrics
        }
    }

    /**
     * Whether searching by the song's name stands a chance. An artist makes it
     * worth a try. Without one, a title of nothing but digits and the letters
     * a to f is an id - `9239d7ef-aeb9-45d2-…` - which a search only ever
     * matches by accident.
     */
    private fun nameWorthSearching(track: Track): Boolean {
        if (!track.artist.isNullOrBlank()) return true
        val name = track.title.trim()
        if (name.isEmpty()) return false
        val digits = name.count { it.isDigit() }
        val wordLetters = name.count { it.isLetter() && it.lowercaseChar() !in 'a'..'f' }
        return wordLetters > 0 || digits < 4
    }

    // ---- Duplicates -----------------------------------------------------------

    private val _duplicates = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicates: StateFlow<List<DuplicateGroup>> = _duplicates.asStateFlow()

    private val _duplicateScanning = MutableStateFlow(false)
    val duplicateScanning: StateFlow<Boolean> = _duplicateScanning.asStateFlow()

    private val _duplicateListening = MutableStateFlow<Pair<Int, Int>?>(null)
    /** Songs listened to so far and how many need it, while a scan is listening. */
    val duplicateListening: StateFlow<Pair<Int, Int>?> = _duplicateListening.asStateFlow()

    private val printDao by lazy { MusicDatabase.get(getApplication()).trackPrintDao() }

    /**
     * Finds the same recording stored twice, by how it sounds. Each song is
     * listened to once - thirty seconds from its middle - and the print kept,
     * so a second scan only listens to songs added since.
     */
    fun scanDuplicates() = viewModelScope.launch {
        if (_duplicateScanning.value) return@launch
        _duplicateScanning.value = true
        val songs = library.allTracks()
        val prints = HashMap<Long, IntArray>()
        printDao.all().forEach { stored -> AudioPrint.decode(stored.data)?.let { prints[stored.trackId] = it } }
        val todo = songs.filter { it.id !in prints }
        if (todo.isNotEmpty()) {
            _duplicateListening.value = 0 to todo.size
            var done = 0
            val work = Channel<Track>(Channel.UNLIMITED)
            todo.forEach { work.trySend(it) }
            work.close()
            coroutineScope {
                // Two at a time: each one decodes audio, and a phone has the
                // cores for two without the rest of the app stuttering.
                repeat(2) {
                    launch {
                        for (track in work) {
                            val print = withContext(Dispatchers.Default) {
                                runCatching {
                                    AudioSampler.sampleMono16k(
                                        getApplication(), Uri.fromFile(File(track.filePath)), AudioPrint.SECONDS
                                    )?.let { AudioPrint.of(it, 16_000) }?.takeIf { it.isNotEmpty() }
                                }.getOrNull()
                            }
                            if (print != null) {
                                prints[track.id] = print
                                printDao.save(com.exo.musicplayer.data.db.TrackPrint(track.id, AudioPrint.encode(print)))
                            }
                            done++
                            _duplicateListening.value = done to todo.size
                        }
                    }
                }
            }
            _duplicateListening.value = null
        }
        _duplicates.value = withContext(Dispatchers.Default) { DuplicateFinder.find(songs, prints) }
        _duplicateScanning.value = false
    }

    fun clearDuplicates() { _duplicates.value = emptyList() }

    /** Deletes the chosen copies: database rows, audio files and orphaned art. */
    fun removeDuplicates(tracks: List<Track>) = viewModelScope.launch {
        var removed = 0
        for (track in tracks) {
            playback.evictTrack(track.id)
            runCatching { library.deleteTrack(track) }.onSuccess { removed++ }
        }
        _duplicates.value = emptyList()
        _notice.value = if (removed == 1) "Removed 1 duplicate" else "Removed $removed duplicates"
    }

    // ---- Interruption behaviour -------------------------------------------------

    val interruption: StateFlow<InterruptionState> = app.interruption.state

    fun setInterruptionBehavior(behavior: InterruptionBehavior) =
        app.interruption.setBehavior(behavior)

    fun setPlayDuringCalls(enabled: Boolean) = app.interruption.setPlayDuringCalls(enabled)

    // ---- Audio output -----------------------------------------------------------

    val audioOutputs: StateFlow<List<AudioOutput>> = app.audioOutputs.outputs
    val selectedOutputs: StateFlow<Set<String>> = app.audioOutputs.selectedKeys
    val mirrorOutputs: StateFlow<Boolean> = app.audioOutputs.mirrorEnabled

    fun pickOutput(key: String) {
        if (app.audioOutputs.mirrorEnabled.value) {
            app.audioOutputs.toggle(key)
        } else {
            app.audioOutputs.select(key)
        }
    }

    fun setMirrorOutputs(enabled: Boolean) {
        app.audioOutputs.setMirrorEnabled(enabled)
        if (!enabled) {
            // Collapse a multi-selection back to a single output.
            app.audioOutputs.selectedKeys.value.firstOrNull()
                ?.let { app.audioOutputs.select(it) }
        }
    }

    // ---- Audio effects ----------------------------------------------------------

    val audioFx: StateFlow<AudioFxState> = app.audioFx.state
    val fxRemembered: StateFlow<Boolean> = app.audioFx.remembered

    fun applyFxPreset(preset: FxPreset) = app.audioFx.applyPreset(preset)
    fun setFxSpeed(value: Float) = app.audioFx.setSpeed(value)
    fun setFxPitch(semitones: Float) = app.audioFx.setPitch(semitones)
    fun setReverbEnabled(enabled: Boolean) = app.audioFx.setReverbEnabled(enabled)
    fun setReverbRoom(room: ReverbRoom) = app.audioFx.setReverbRoom(room)
    fun setReverbAmount(amount: Float) = app.audioFx.setReverbAmount(amount)
    fun setEqEnabled(enabled: Boolean) = app.audioFx.setEqEnabled(enabled)
    fun setEqBand(band: Int, db: Float) = app.audioFx.setEqBand(band, db)
    fun flattenEq() = app.audioFx.flattenEq()
    fun resetFx() = app.audioFx.reset()

    fun rememberFxForSong() {
        app.audioFx.rememberForCurrent()
        currentTrack.value?.let { _notice.value = "These effects will come back with \"${it.title}\"." }
    }

    fun forgetFxForSong() {
        app.audioFx.forgetForCurrent()
        currentTrack.value?.let { _notice.value = "\"${it.title}\" plays with the usual effects again." }
    }

    // ---- Lyrics -----------------------------------------------------------------

    /** Lyrics for whatever is playing, re-queried as the track changes. */
    val currentLyrics: StateFlow<Lyrics?> = currentTrackId
        .flatMapLatest { id -> if (id == null) flowOf(null) else app.lyrics.observe(id) }
        // Lyrics saved before the Genius leftovers were stripped get the same treatment.
        .map { saved -> saved?.let { it.copy(plainText = it.plainText?.let(::tidyLyrics)) } }
        .stateIn(viewModelScope, started, null)

    val currentLyricLines: StateFlow<List<LyricLine>> = currentLyrics
        .map { lyrics -> lyrics?.syncedText?.let(LrcParser::parse).orEmpty() }
        .stateIn(viewModelScope, started, emptyList())

    private val _lyricsBusy = MutableStateFlow(false)
    val lyricsBusy: StateFlow<Boolean> = _lyricsBusy.asStateFlow()

    private val _lyricsMessage = MutableStateFlow<String?>(null)
    val lyricsMessage: StateFlow<String?> = _lyricsMessage.asStateFlow()

    init {
        // A message about one song's lyrics means nothing on the next song.
        viewModelScope.launch { currentTrackId.collect { _lyricsMessage.value = null } }
    }

    fun fetchLyrics(force: Boolean = false) {
        val track = currentTrack.value ?: return
        _lyricsBusy.value = true
        _lyricsMessage.value = null
        viewModelScope.launch {
            when (val result = runCatching { app.lyrics.fetch(track, force) }.getOrNull()) {
                is LyricsFetch.Found -> Unit                    // the flow updates the UI
                is LyricsFetch.Instrumental ->
                    _lyricsMessage.value = "This track is marked as instrumental."
                is LyricsFetch.Error -> _lyricsMessage.value = result.message
                else -> _lyricsMessage.value =
                    "No lyrics found. You can add them yourself below."
            }
            _lyricsBusy.value = false
        }
    }

    fun saveLyrics(text: String) {
        val track = currentTrack.value ?: return
        viewModelScope.launch {
            app.lyrics.saveManual(track.id, text)
            _lyricsMessage.value = null
            _notice.value = if (text.isBlank()) "Lyrics removed" else "Lyrics saved"
        }
    }

    fun deleteLyrics() {
        val track = currentTrack.value ?: return
        viewModelScope.launch {
            app.lyrics.delete(track.id)
            _notice.value = "Lyrics removed"
        }
    }

    val lyricsTerm: StateFlow<LyricsTerm> = prefs.lyricsTerm
    val lyricsPattern: StateFlow<String> = prefs.lyricsPattern
    fun setLyricsTerm(term: LyricsTerm) = prefs.setLyricsTerm(term)
    fun setLyricsPattern(pattern: String) = prefs.setLyricsPattern(pattern)

    val coverProvider: StateFlow<String> = prefs.coverProvider
    fun setCoverProvider(label: String) = prefs.setCoverProvider(label)

    // ---- Identify one song by listening, and choose the match -------------------

    private val _tagTarget = MutableStateFlow<Track?>(null)
    val tagTarget: StateFlow<Track?> = _tagTarget.asStateFlow()

    private val _tagBusy = MutableStateFlow(false)
    val tagBusy: StateFlow<Boolean> = _tagBusy.asStateFlow()

    private val _tagResult = MutableStateFlow<RecognitionResult?>(null)
    val tagResult: StateFlow<RecognitionResult?> = _tagResult.asStateFlow()

    /**
     * Identifies a track already in the library by listening to its own file —
     * the fix for Telegram imports that arrive as "audio_2026_03_12.mp3" with no
     * artist and no cover.
     */
    fun startFixTags(track: Track) {
        _tagTarget.value = track
        _tagResult.value = null
        _tagBusy.value = true
        viewModelScope.launch {
            val samples = runCatching {
                AudioSampler.sampleMono16k(getApplication(), Uri.fromFile(File(track.filePath)))
            }.getOrNull()

            _tagResult.value = if (samples == null) {
                RecognitionResult.Error("Couldn't read the audio for this track.")
            } else {
                runCatching { app.shazam.recognize(samples) }
                    .getOrElse { RecognitionResult.Error(it.message ?: "Identification failed.") }
            }
            _tagBusy.value = false
        }
    }

    fun applyTagMatch(match: MusicMatch) {
        val track = _tagTarget.value ?: return
        viewModelScope.launch {
            runCatching { library.applyMatch(track, match) }
            _notice.value = "Details updated"
            dismissFixTags()
        }
    }

    fun dismissFixTags() {
        _tagTarget.value = null
        _tagResult.value = null
        _tagBusy.value = false
    }

    // ---- Library actions ----------------------------------------------------------

    fun toggleFavorite(track: Track) = viewModelScope.launch {
        library.setFavorite(track.id, !track.isFavorite)
    }

    fun setRating(track: Track, stars: Int) = viewModelScope.launch {
        library.setRating(track.id, stars)
    }

    private val _rateTarget = MutableStateFlow<Track?>(null)
    val rateTarget: StateFlow<Track?> = _rateTarget.asStateFlow()
    fun rate(track: Track?) { _rateTarget.value = track }

    fun deleteTrack(track: Track) = viewModelScope.launch {
        playback.evictTrack(track.id)
        library.deleteTrack(track)
    }

    /** Puts a song's details back to whatever its own file says. */
    fun revertTrackToFile(track: Track) {
        _editTarget.value = null
        viewModelScope.launch {
            val back = library.revertToFile(track)
            _notice.value = if (back == null) {
                "That file is not where it used to be."
            } else {
                "\"${back.title}\" is back to what the file says."
            }
        }
    }

    private val _editTarget = MutableStateFlow<Track?>(null)
    val editTarget: StateFlow<Track?> = _editTarget.asStateFlow()

    fun editTrack(track: Track) { _editTarget.value = track }

    fun dismissEdit() { _editTarget.value = null }

    fun saveTrackDetails(
        track: Track,
        title: String,
        artist: String,
        album: String,
        year: Int?,
        genre: String
    ) {
        _editTarget.value = null
        viewModelScope.launch {
            runCatching { library.saveDetails(track, title, artist, album, year, genre) }
        }
    }

    /** The songs the edit-many dialog is working on. */
    private val _editMany = MutableStateFlow<List<Track>>(emptyList())
    val editMany: StateFlow<List<Track>> = _editMany.asStateFlow()

    fun editSelection(visible: List<Track>) { _editMany.value = selectedTracks(visible) }
    fun dismissEditMany() { _editMany.value = emptyList() }

    /**
     * Sets whichever fields were filled in on every song being edited. A field
     * left blank is left alone rather than cleared: the point of editing thirty
     * songs at once is to set one thing about them.
     */
    fun applyToMany(artist: String, album: String, year: String, genre: String) {
        val targets = _editMany.value
        _editMany.value = emptyList()
        clearSelection()
        val newYear = year.trim().toIntOrNull()
        viewModelScope.launch {
            targets.forEach { track ->
                library.applyDetails(
                    track,
                    artist = artist.takeIf { it.isNotBlank() },
                    album = album.takeIf { it.isNotBlank() },
                    year = newYear,
                    genre = genre.takeIf { it.isNotBlank() }
                )
            }
            _notice.value = "Updated ${targets.size} ${if (targets.size == 1) "song" else "songs"}."
        }
    }

    // ---- Playlists ----------------------------------------------------------------

    private val _openPlaylistId = MutableStateFlow<Long?>(null)

    val openPlaylist: StateFlow<PlaylistSummary?> =
        combine(_openPlaylistId, playlists) { id, all -> all.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, started, null)

    val openPlaylistTracks: StateFlow<List<Track>> = _openPlaylistId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else library.observePlaylistTracks(id)
        }
        .stateIn(viewModelScope, started, emptyList())

    fun showPlaylist(playlistId: Long?) { _openPlaylistId.value = playlistId }

    fun createPlaylist(name: String, seedTrackIds: List<Long> = emptyList()) =
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            val id = runCatching { library.createPlaylist(name.trim()) }.getOrNull()
            if (id == null) {
                _notice.value = "There's already a playlist called \"${name.trim()}\"."
                return@launch
            }
            if (seedTrackIds.isNotEmpty()) library.addToPlaylist(id, seedTrackIds)
            _notice.value = if (seedTrackIds.isEmpty()) "Created \"${name.trim()}\"." else
                "Created \"${name.trim()}\" with ${seedTrackIds.size} ${if (seedTrackIds.size == 1) "song" else "songs"}."
        }

    fun renamePlaylist(playlistId: Long, name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        runCatching { library.renamePlaylist(playlistId, name.trim()) }
            .onFailure { _notice.value = "There's already a playlist called \"${name.trim()}\"." }
    }

    fun addToPlaylist(playlistId: Long, trackId: Long) = viewModelScope.launch {
        library.addToPlaylist(playlistId, listOf(trackId))
        _notice.value = "Added to the playlist."
    }

    fun removeFromPlaylist(playlistId: Long, trackId: Long) = viewModelScope.launch {
        library.removeFromPlaylist(playlistId, trackId)
    }

    fun deletePlaylist(playlistId: Long) = viewModelScope.launch {
        library.deletePlaylist(playlistId)
    }

    fun playPlaylist(playlistId: Long, startIndex: Int = 0, shuffled: Boolean = false) = viewModelScope.launch {
        val items = library.playlistTracksOnce(playlistId)
        if (items.isNotEmpty()) playback.play(if (shuffled) items.shuffled() else items, startIndex)
    }

    // ---- Genres and rules ---------------------------------------------------------

    init {
        // A library imported before the app read genres has none. One bounded
        // pass fills them in, and writes an empty genre for files that carry
        // none so the same songs are not re-read on every launch.
        viewModelScope.launch { runCatching { library.fillMissingGenres() } }
    }

    val smartPlaylists: StateFlow<List<SmartPlaylist>> = library.observeSmartPlaylists()
        .stateIn(viewModelScope, started, emptyList())

    /** How many songs each smart list holds right now, kept current as the library changes. */
    val smartCounts: StateFlow<Map<Long, Int>> = combine(smartPlaylists, allTracks) { lists, all ->
        withContext(Dispatchers.Default) {
            lists.associate { list ->
                val parsed = SearchQuery.of(list.rule)
                list.id to all.count { library.matches(parsed, it) }
            }
        }
    }.stateIn(viewModelScope, started, emptyMap())

    private val _genrePrompt = MutableStateFlow("")
    val genrePrompt: StateFlow<String> = _genrePrompt.asStateFlow()

    /** How many songs the prompt picks out, as it's typed. */
    val promptCount: StateFlow<Int> = combine(_genrePrompt.debounce(200), allTracks) { prompt, _ ->
        if (prompt.isBlank()) 0 else library.promptTracks(prompt).size
    }.stateIn(viewModelScope, started, 0)

    fun setGenrePrompt(text: String) { _genrePrompt.value = text }

    /** The genres in the library, most songs first, for the chips under the prompt. */
    val genres: StateFlow<List<Pair<String, Int>>> = allTracks
        .mapLatest { runCatching { library.genres() }.getOrDefault(emptyList()) }
        .stateIn(viewModelScope, started, emptyList())

    fun playPrompt() {
        val prompt = _genrePrompt.value
        viewModelScope.launch {
            val list = library.promptTracks(prompt)
            if (list.isEmpty()) {
                _notice.value = "Nothing matches \"${prompt.trim()}\"."
            } else {
                playFrom(list, 0)
            }
        }
    }

    /** Freezes what a prompt matches right now into an ordinary playlist. */
    fun savePromptAsPlaylist() {
        val prompt = _genrePrompt.value.trim()
        viewModelScope.launch {
            val list = library.promptTracks(prompt)
            if (list.isEmpty()) {
                _notice.value = "Nothing matches \"$prompt\"."
                return@launch
            }
            val id = runCatching { library.createPlaylist(prompt.replaceFirstChar { it.uppercase() }) }.getOrNull()
            if (id == null) {
                _notice.value = "There's already a playlist called \"$prompt\"."
                return@launch
            }
            library.addToPlaylist(id, list.map { it.id })
            _notice.value = "Saved ${list.size} songs as a playlist."
        }
    }

    fun createSmartPlaylist(name: String, rule: String) {
        viewModelScope.launch {
            if (rule.isBlank()) {
                _notice.value = "A smart list needs a rule — try rating:4+ or year:2015-2020."
                return@launch
            }
            library.createSmartPlaylist(name.ifBlank { rule.replaceFirstChar { it.uppercase() } }, rule)
            _notice.value = "\"${name.ifBlank { rule }}\" holds ${library.smartTracks(rule).size} songs."
        }
    }

    fun deleteSmartPlaylist(id: Long) {
        viewModelScope.launch { library.deleteSmartPlaylist(id) }
    }

    fun playSmartPlaylist(list: SmartPlaylist) {
        viewModelScope.launch {
            val songs = library.smartTracks(list.rule)
            if (songs.isEmpty()) {
                _notice.value = "\"${list.name}\" matches nothing right now."
            } else {
                playFrom(songs, 0)
            }
        }
    }

    // ---- Zip and ship ---------------------------------------------------------------
    //
    // Packs the library or a playlist into one file, reports where it landed,
    // and can hand it straight to another app. Written into the app's own
    // external files directory, which needs no storage permission on any API
    // level and is still a real path a file manager can reach.

    data class ArchiveState(
        val running: Boolean = false,
        val fraction: Float = 0f,
        val current: String = "",
        val note: String? = null,
        val file: File? = null
    )

    private val _archive = MutableStateFlow(ArchiveState())
    val archive: StateFlow<ArchiveState> = _archive.asStateFlow()

    @Volatile private var archiveCancelled = false

    fun zipLibrary() = startArchive("Library") { library.allTracks() }

    fun zipPlaylist(playlist: PlaylistSummary) =
        startArchive(playlist.name) { library.playlistTracksOnce(playlist.id) }

    fun zipSelected(visible: List<Track>) {
        val chosen = selectedTracks(visible)
        clearSelection()
        startArchive("Selection") { chosen }
    }

    private fun startArchive(label: String, load: suspend () -> List<Track>) {
        if (_archive.value.running) return
        archiveCancelled = false
        _archive.value = ArchiveState(running = true)

        viewModelScope.launch {
            val chosen = runCatching { load() }.getOrDefault(emptyList())
            if (chosen.isEmpty()) {
                _archive.value = ArchiveState(note = "Nothing to archive.")
                return@launch
            }

            val context = getApplication<Application>()
            val root = context.getExternalFilesDir(null) ?: context.filesDir
            val stamp = java.time.LocalDate.now().toString()
            val destination = File(
                File(root, "archives"),
                MusicArchive.safeName("Lucense $label $stamp") + ".zip"
            )

            // Numbered so a playlist keeps its order once unpacked, and named
            // with the artist so the folder is navigable rather than a wall of
            // identical-looking files.
            val digits = chosen.size.toString().length
            val entries = chosen.mapIndexed { index, track ->
                val file = File(track.filePath)
                val number = (index + 1).toString().padStart(digits, '0')
                val stem = listOfNotNull(
                    track.artist?.takeIf { it.isNotBlank() }, track.title
                ).joinToString(" - ")
                ArchiveEntry(file, "$number ${MusicArchive.safeName(stem)}.${file.extension}")
            }

            val result = withContext(Dispatchers.IO) {
                MusicArchive.zip(
                    entries = entries,
                    destination = destination,
                    onProgress = { progress ->
                        _archive.value = _archive.value.copy(
                            fraction = progress.fraction,
                            current = progress.currentName
                        )
                    },
                    shouldContinue = { !archiveCancelled }
                )
            }

            _archive.value = result.fold(
                onSuccess = { done ->
                    ArchiveState(
                        note = buildString {
                            append("${done.included} tracks, ")
                            append("%.1f MB".format(done.bytes / 1_048_576.0))
                            if (done.skipped.isNotEmpty()) {
                                append(" - ${done.skipped.size} missing from storage")
                            }
                        },
                        file = done.file
                    )
                },
                onFailure = {
                    ArchiveState(note = it.message ?: "Couldn't build the archive.")
                }
            )
        }
    }

    fun cancelArchive() {
        archiveCancelled = true
        _archive.value = ArchiveState(note = "Cancelled.")
    }

    fun dismissArchive() { _archive.value = ArchiveState() }

    // ---- Selecting several tracks ---------------------------------------------------
    //
    // Held as a set of ids rather than of Tracks: the list is re-queried
    // constantly as playback counts and favourites change, so holding entities
    // would keep stale copies and break equality against the fresh ones.

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    fun toggleSelected(trackId: Long) {
        _selectedIds.value = _selectedIds.value.let {
            if (trackId in it) it - trackId else it + trackId
        }
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun selectAll(tracks: List<Track>) {
        _selectedIds.value = tracks.map { it.id }.toSet()
    }

    /** Resolves the selection to tracks, in the order they appear on screen. */
    fun selectedTracks(visible: List<Track>): List<Track> {
        val chosen = _selectedIds.value
        return visible.filter { it.id in chosen }
    }

    fun playSelected(visible: List<Track>) {
        val chosen = selectedTracks(visible)
        clearSelection()
        if (chosen.isNotEmpty()) playback.play(chosen, 0)
    }

    fun playNextSelected(visible: List<Track>) {
        val chosen = selectedTracks(visible)
        clearSelection()
        playback.playNext(chosen)
        if (chosen.isNotEmpty()) _notice.value = "${chosen.size} ${if (chosen.size == 1) "song" else "songs"} up next."
    }

    fun queueSelected(visible: List<Track>) {
        val chosen = selectedTracks(visible)
        clearSelection()
        playback.addToQueue(chosen)
        if (chosen.isNotEmpty()) _notice.value = "Added ${chosen.size} to the queue."
    }

    fun favoriteSelected(visible: List<Track>) = viewModelScope.launch {
        val chosen = selectedTracks(visible)
        // One decision for the whole selection: if any are not favourites,
        // favourite everything. Toggling each independently would leave a
        // mixed selection mixed, which is never what was meant.
        val makeFavorite = chosen.any { !it.isFavorite }
        library.setFavorite(chosen.filter { it.isFavorite != makeFavorite }.map { it.id }, makeFavorite)
        clearSelection()
    }

    fun revertSelected(visible: List<Track>) = viewModelScope.launch {
        val chosen = selectedTracks(visible)
        clearSelection()
        val back = chosen.count { library.revertToFile(it) != null }
        _notice.value = "$back ${if (back == 1) "song is" else "songs are"} back to what the file says."
    }

    fun addSelectedToPlaylist(playlistId: Long, chosen: List<Track>) =
        viewModelScope.launch {
            library.addToPlaylist(playlistId, chosen.map { it.id })
            clearSelection()
            _notice.value = "Added ${chosen.size} to the playlist."
        }

    fun deleteSelected(visible: List<Track>) = viewModelScope.launch {
        for (track in selectedTracks(visible)) {
            playback.evictTrack(track.id)
            runCatching { library.deleteTrack(track) }
        }
        clearSelection()
    }

    // ---- Sharing playlists ------------------------------------------------------------
    //
    // Plain text so a playlist can be sent to someone the way any other file is
    // sent. Nothing is uploaded, no account is involved, and an import matches
    // against what is already on the device rather than fetching anything.

    private val _importResult =
        MutableStateFlow<com.exo.musicplayer.data.playlist.ImportResult<Track>?>(null)
    val importResult: StateFlow<com.exo.musicplayer.data.playlist.ImportResult<Track>?> =
        _importResult.asStateFlow()

    fun exportPlaylist(playlist: PlaylistSummary, into: (String, String) -> Boolean) {
        viewModelScope.launch {
            val items = library.playlistTracksOnce(playlist.id)
            val entries = items.map {
                PlaylistEntry(it.artist, it.title, it.durationMs)
            }
            val text = PlaylistFile.export(playlist.name, entries)
            _notice.value = if (into(playlist.name, text)) {
                "Exported ${entries.size} tracks."
            } else {
                "Couldn't write that file."
            }
        }
    }

    fun importPlaylist(text: String, fallbackName: String) {
        viewModelScope.launch {
            val parsed = PlaylistFile.parse(text, fallbackName)
            if (parsed.entries.isEmpty()) {
                _notice.value = "That file had no tracks in it."
                return@launch
            }
            _importResult.value = PlaylistFile.matchAgainst(
                playlist = parsed,
                library = library.allTracks(),
                artistOf = { it.artist },
                titleOf = { it.title },
                durationOf = { it.durationMs }
            )
        }
    }

    fun confirmImport(result: com.exo.musicplayer.data.playlist.ImportResult<Track>) {
        viewModelScope.launch {
            val id = runCatching { library.createPlaylist(result.name) }.getOrNull()
                ?: return@launch
            library.addToPlaylist(id, result.matched.map { it.second.id })
            _importResult.value = null
            _notice.value = buildString {
                append("Added ${result.matched.size} tracks as \"${result.name}\"")
                if (result.missing.isNotEmpty()) {
                    append("; ${result.missing.size} not on this device")
                }
                append(".")
            }
        }
    }

    fun dismissImport() {
        _importResult.value = null
    }

    // ---- Backups ------------------------------------------------------------------------

    private val backups by lazy { PhoneBackup(MusicDatabase.get(getApplication())) }

    /** Builds the backup text and hands it to [write], which saves it where the user chose. */
    fun backUp(write: (String) -> Boolean) = viewModelScope.launch {
        val text = backups.write()
        _notice.value = if (write(text)) "Backup saved." else "Couldn't write that file."
    }

    fun restore(text: String) = viewModelScope.launch {
        val outcome = backups.restore(text)
        _notice.value = outcome?.let { "Restored ${it.summary}." }
            ?: "That isn't a Lucense phone backup."
    }

    // ---- Updates ----------------------------------------------------------------------------

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    fun checkForUpdate() {
        if (_update.value == UpdateState.Checking) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            val newest = withContext(Dispatchers.IO) { runCatching { Updates.newest() }.getOrNull() }
            _update.value = when {
                newest == null -> UpdateState.Failed
                Updates.isNewer(newest.version, BuildConfig.VERSION_NAME) ->
                    UpdateState.Available(newest.version, newest.url)
                else -> UpdateState.UpToDate(BuildConfig.VERSION_NAME)
            }
        }
    }

    init {
        // Once a launch, quietly, as on Windows.
        checkForUpdate()
    }

    companion object {
        const val FIX_LABEL = "Fix"
        private const val LOOKUP_GIVE_UP_MS = 10_000L
    }
}
