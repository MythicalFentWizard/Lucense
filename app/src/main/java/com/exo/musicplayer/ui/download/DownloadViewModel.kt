package com.exo.musicplayer.ui.download

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exo.musicplayer.data.download.DownloadOutcome
import com.exo.musicplayer.data.download.DownloadQuality
import com.exo.musicplayer.data.download.LinkResolver
import com.exo.musicplayer.data.download.ResolvedLink
import com.exo.musicplayer.data.download.YtDlpDownloader
import com.exo.musicplayer.data.ingest.ImportResult
import com.exo.musicplayer.data.youtube.AndroidYouTubeBackend
import com.exo.musicplayer.data.youtube.PipedYouTubeBackend
import com.exo.musicplayer.data.youtube.SoundCloudFallback
import com.exo.musicplayer.data.youtube.YouTubeFormat
import com.exo.musicplayer.data.youtube.YouTubeLinkFinder
import com.exo.musicplayer.data.youtube.YouTubeSearch
import com.exo.musicplayer.musicApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadUiState(
    val busy: Boolean = false,
    val stage: String = "",
    val percent: Float = 0f,
    val etaSeconds: Long = 0L,
    val title: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
    /** Shown while downloading: which YouTube video was picked, or the Spotify caveat. */
    val note: String? = null,
    /**
     * Whether "update yt-dlp and retry" is worth suggesting. It is after a failed
     * download, where a stale yt-dlp is the usual cause. It is not after "no
     * suitable video", where updating would change nothing.
     */
    val offerUpdate: Boolean = true,
    /** Which song of a multi-select download this is, counting from 1. */
    val queuePosition: Int = 0,
    val queueSize: Int = 0
)

/** One song to download: a real link, or a name to find on YouTube first. */
data class DownloadRequest(
    val downloadUrl: String?,
    val artist: String?,
    val title: String,
    val durationMs: Long?
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val downloader = YtDlpDownloader(application)
    private val importer = application.musicApp.importer

    /**
     * Turns a song name into one real YouTube link.
     *
     * yt-dlp is only ever handed a link. It used to be given
     * `ytsearch1:artist title`, which failed here before yt-dlp even ran, since
     * the link check refused anything that was not http. And where a search
     * target does run, it takes YouTube's first hit, which for real searches is
     * routinely a sped-up or slowed re-upload rather than the song.
     */
    private val search = YouTubeSearch(listOf(PipedYouTubeBackend(), AndroidYouTubeBackend(downloader)))
    private val finder = YouTubeLinkFinder(search)

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _quality = MutableStateFlow(DownloadQuality.DEFAULT)
    val quality: StateFlow<DownloadQuality> = _quality.asStateFlow()

    private var job: Job? = null

    /**
     * What a "Find & download" from Identify is looking for.
     *
     * Kept apart from the text box so the catalogue's artist, title and
     * duration reach the finder intact, instead of being guessed back out of
     * the joined-up text shown in the box.
     */
    private var pendingWanted: YouTubeLinkFinder.Wanted? = null

    fun setUrl(value: String) {
        // Kept exactly as typed: trimming every keystroke ate the space after
        // each word, so "svard crash" could only ever be typed as "svardcrash".
        // It's trimmed when the download starts.
        _url.value = value
        // Typing over a prefilled search makes the text the request.
        pendingWanted = null
    }

    fun setQuality(value: DownloadQuality) { _quality.value = value }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = DownloadUiState(message = "Cancelled", isError = false)
    }

    fun update() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = DownloadUiState(busy = true, stage = "Updating yt-dlp…")
            val message = downloader.update()
            _state.value = DownloadUiState(
                message = message,
                isError = message.startsWith("Update failed")
            )
        }
    }

    /**
     * Starts a download from an Identify result.
     *
     * Services that host audio (YouTube, Audius, the Internet Archive) carry a
     * real link and go straight through. Everything else is found on YouTube
     * first, using the match's own artist, title and length, and yt-dlp is given
     * the link that was found.
     */
    fun downloadMatch(downloadUrl: String?, artist: String?, title: String, durationMs: Long?) {
        val (input, wanted) = DownloadRequest(downloadUrl, artist, title, durationMs).toInput()
        pendingWanted = wanted
        _url.value = input
        download()
    }

    fun download() {
        val input = _url.value.trim()
        if (input.isBlank()) return
        val wanted = pendingWanted?.takeIf { it.query == input }

        job?.cancel()
        job = viewModelScope.launch {
            _state.value = DownloadUiState(busy = true, stage = "Starting yt-dlp…")
            if (!ensureReady()) return@launch

            _state.value = when (val outcome = downloadOne(input, wanted)) {
                is ItemOutcome.Added -> {
                    finishInput()
                    DownloadUiState(message = "Added \"${outcome.title}\"")
                }
                ItemOutcome.Duplicate -> {
                    finishInput()
                    DownloadUiState(message = "Already in your library")
                }
                is ItemOutcome.Failed -> DownloadUiState(
                    message = outcome.message,
                    isError = true,
                    offerUpdate = outcome.offerUpdate
                )
            }
        }
    }

    /**
     * Downloads several songs picked together in Identify, one after another.
     *
     * In sequence rather than at once: each download starts a Python runtime and
     * an ffmpeg conversion, and several of those side by side on a phone are
     * slower overall and hotter than the same work queued. One song that cannot
     * be found or fails does not stop the rest; the summary says which.
     */
    fun downloadAll(requests: List<DownloadRequest>) {
        if (requests.isEmpty()) return
        if (requests.size == 1) {
            requests.first().let { downloadMatch(it.downloadUrl, it.artist, it.title, it.durationMs) }
            return
        }

        job?.cancel()
        job = viewModelScope.launch {
            _state.value = DownloadUiState(
                busy = true,
                stage = "Starting yt-dlp…",
                queuePosition = 1,
                queueSize = requests.size
            )
            if (!ensureReady()) return@launch

            var added = 0
            var duplicates = 0
            val failed = mutableListOf<String>()

            requests.forEachIndexed { index, request ->
                val (input, wanted) = request.toInput()
                _url.value = input
                _state.value = DownloadUiState(
                    busy = true,
                    stage = "Starting…",
                    queuePosition = index + 1,
                    queueSize = requests.size
                )
                when (val outcome = downloadOne(input, wanted)) {
                    is ItemOutcome.Added -> added++
                    ItemOutcome.Duplicate -> duplicates++
                    is ItemOutcome.Failed -> failed += "${request.title} (${outcome.message.take(80)})"
                }
            }

            finishInput()
            _state.value = DownloadUiState(
                message = buildString {
                    append("Added $added of ${requests.size}")
                    if (duplicates > 0) append(", $duplicates already in your library")
                    if (failed.isNotEmpty()) append(". Not downloaded: ${failed.joinToString("; ")}")
                },
                isError = added + duplicates == 0,
                offerUpdate = false
            )
        }
    }

    private suspend fun ensureReady(): Boolean {
        // First run unpacks the Python runtime, which takes a few seconds.
        if (downloader.ensureReady()) return true
        _state.value = DownloadUiState(
            message = downloader.lastInitError
                ?: "yt-dlp couldn't start. This build ships arm64 libraries only.",
            isError = true
        )
        return false
    }

    private fun finishInput() {
        _url.value = ""
        pendingWanted = null
    }

    private sealed interface ItemOutcome {
        data class Added(val title: String) : ItemOutcome
        data object Duplicate : ItemOutcome
        data class Failed(val message: String, val offerUpdate: Boolean = true) : ItemOutcome
    }

    /** Finds, downloads and imports one song, keeping the queue position in the state. */
    private suspend fun downloadOne(input: String, wanted: YouTubeLinkFinder.Wanted?): ItemOutcome {
        // The song being fetched, in case YouTube refuses this connection and
        // it has to be looked for elsewhere. Worked out only if that happens.
        var song: suspend () -> SoundCloudFallback.Lookup? = { null }
        val picked: Picked = if (downloader.looksLikeUrl(input)) {
            _state.value = _state.value.copy(stage = "Reading the link…")
            when (val resolved = downloader.resolve(input)) {
                is ResolvedLink.Direct -> {
                    song = { SoundCloudFallback.lookUp(resolved.url, search, downloader.lookupProxy) }
                    Picked(resolved.url, downloader.peekTitle(resolved.url), null)
                }

                is ResolvedLink.Search ->
                    // Spotify: its audio can't be fetched, so the same track is
                    // found on YouTube and that link is what downloads.
                    when (val found = findLink(YouTubeLinkFinder.Wanted(title = resolved.query))) {
                        is LinkResult.Ok -> {
                            song = { found.picked.lookup }
                            found.picked.copy(
                                note = listOfNotNull(resolved.note, found.picked.note).joinToString("\n")
                            )
                        }
                        is LinkResult.None -> return ItemOutcome.Failed(found.message, found.offerUpdate)
                    }

                is ResolvedLink.Unsupported -> return ItemOutcome.Failed(resolved.reason, offerUpdate = false)
            }
        } else {
            // Not a link: an artist and song name, typed or sent from Identify. A
            // leftover "ytsearch1:" prefix is accepted and ignored.
            val text = input.replace(SEARCH_PREFIX, "").trim()
            when (val found = findLink(wanted ?: YouTubeLinkFinder.Wanted(title = text))) {
                is LinkResult.Ok -> {
                    song = { found.picked.lookup }
                    // The box shows the link yt-dlp is actually fetching.
                    _url.value = found.picked.link
                    found.picked
                }
                is LinkResult.None -> return ItemOutcome.Failed(found.message, found.offerUpdate)
            }
        }

        _state.value = _state.value.copy(
            stage = "Downloading…",
            title = picked.title,
            note = picked.note,
            percent = 0f,
            etaSeconds = 0L
        )

        val progress: (Float, Long, String) -> Unit = { percent, eta, line ->
            _state.value = _state.value.copy(
                percent = percent.coerceIn(0f, 100f),
                etaSeconds = eta,
                stage = if (line.contains("ExtractAudio", true) || line.contains("ffmpeg", true)) {
                    "Converting to mp3…"
                } else {
                    "Downloading…"
                }
            )
        }
        var outcome = downloader.downloadAudio(picked.link, _quality.value, progress)
        // Whatever stopped YouTube - a refused VPN address, a removed or
        // region-blocked video - the same song may be on SoundCloud.
        val failed = outcome as? DownloadOutcome.Failed
        if (failed != null && LinkResolver.isYouTube(picked.link)) {
            val refused = failed.refusedByYouTube
            outcome = fromSoundCloud(song(), refused, progress)
                ?: return ItemOutcome.Failed(
                    SoundCloudFallback.notFound(refused, failed.message),
                    offerUpdate = !refused
                )
        }

        return when (outcome) {
            is DownloadOutcome.Done -> {
                _state.value = _state.value.copy(stage = "Adding to library…", percent = 100f)
                // Same pipeline as a Telegram share: hashed, deduplicated, tagged.
                val result = importer.import(Uri.fromFile(outcome.file), sourceApp = "Download")
                // The cache copy is redundant once the importer has its own.
                runCatching { outcome.file.parentFile?.deleteRecursively() }
                when (result) {
                    is ImportResult.Imported -> ItemOutcome.Added(result.track.title)
                    is ImportResult.Duplicate -> ItemOutcome.Duplicate
                    else -> ItemOutcome.Failed("Downloaded, but the file couldn't be imported.", offerUpdate = false)
                }
            }
            is DownloadOutcome.Failed -> ItemOutcome.Failed(outcome.message)
            DownloadOutcome.NotReady -> ItemOutcome.Failed(downloader.lastInitError ?: "yt-dlp is not available.")
        }
    }

    /**
     * The same song from SoundCloud, when YouTube has refused this connection -
     * usually a VPN address it has flagged. Null when SoundCloud has no upload
     * that is the song, so nothing wrong is saved.
     */
    private suspend fun fromSoundCloud(
        lookup: SoundCloudFallback.Lookup?,
        refused: Boolean,
        progress: (Float, Long, String) -> Unit
    ): DownloadOutcome? {
        if (lookup == null || lookup.wanted.query.isBlank()) return null
        _state.value = _state.value.copy(
            stage = SoundCloudFallback.searching(refused),
            percent = 0f,
            etaSeconds = 0L
        )
        val found = downloader.searchSoundCloud(SoundCloudFallback.searchQuery(lookup.wanted))
        val upload = SoundCloudFallback.pick(lookup, found) ?: return null
        _state.value = _state.value.copy(
            stage = "Downloading…",
            title = upload.title,
            note = SoundCloudFallback.note(upload, refused)
        )
        return downloader.downloadAudio(upload.watchUrl, _quality.value, progress)
    }

    /**
     * The link yt-dlp will be given, and what to show about it. [lookup] is the
     * song it stands for, kept for looking elsewhere if YouTube refuses.
     */
    private data class Picked(
        val link: String,
        val title: String?,
        val note: String?,
        val lookup: SoundCloudFallback.Lookup? = null
    )

    private sealed interface LinkResult {
        data class Ok(val picked: Picked) : LinkResult
        data class None(val message: String, val offerUpdate: Boolean) : LinkResult
    }

    /**
     * Finds the YouTube video that is the song, or explains why none was used.
     *
     * When nothing suitable turns up, nothing is downloaded. Saving a slowed
     * edit or a cover instead would look like success and be wrong.
     */
    private suspend fun findLink(wanted: YouTubeLinkFinder.Wanted): LinkResult {
        _state.value = _state.value.copy(stage = "Finding \"${wanted.query}\" on YouTube…")
        return when (val outcome = finder.find(wanted)) {
            is YouTubeLinkFinder.Outcome.Found -> {
                val video = outcome.pick.video
                LinkResult.Ok(
                    Picked(
                        link = video.watchUrl,
                        title = video.title,
                        note = buildString {
                            append("Found on YouTube: ${video.channel}")
                            video.durationSeconds?.let { append(" · ${YouTubeFormat.duration(it)}") }
                            video.viewCount?.let { append(" · ${YouTubeFormat.views(it)}") }
                        },
                        // What was asked for, not the video's title; the video's
                        // length only as a loose bound, as it includes intros.
                        lookup = SoundCloudFallback.Lookup(wanted, video.durationSeconds)
                    )
                )
            }
            // With no results at all a stale yt-dlp is plausible; with results
            // that were all edits, updating would change nothing.
            is YouTubeLinkFinder.Outcome.NothingSuitable ->
                LinkResult.None(outcome.message, offerUpdate = outcome.closest == null)
        }
    }

    private fun DownloadRequest.toInput(): Pair<String, YouTubeLinkFinder.Wanted?> {
        val direct = downloadUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
        if (direct != null) return direct to null
        val wanted = YouTubeLinkFinder.Wanted(title = title, artist = artist, durationMs = durationMs)
        return wanted.query to wanted
    }

    private companion object {
        val SEARCH_PREFIX = Regex("""^ytsearch\d*:""", RegexOption.IGNORE_CASE)
    }
}
