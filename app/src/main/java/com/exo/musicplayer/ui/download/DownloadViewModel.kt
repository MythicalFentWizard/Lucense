package com.exo.musicplayer.ui.download

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exo.musicplayer.data.download.DownloadOutcome
import com.exo.musicplayer.data.download.DownloadQuality
import com.exo.musicplayer.data.download.ResolvedLink
import com.exo.musicplayer.data.download.YtDlpDownloader
import com.exo.musicplayer.data.ingest.ImportResult
import com.exo.musicplayer.data.youtube.AndroidYouTubeBackend
import com.exo.musicplayer.data.youtube.PipedYouTubeBackend
import com.exo.musicplayer.data.youtube.YouTubeFormat
import com.exo.musicplayer.data.youtube.YouTubeLinkFinder
import com.exo.musicplayer.data.youtube.YouTubeSearch
import com.exo.musicplayer.musicApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val offerUpdate: Boolean = true
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val downloader = YtDlpDownloader(application)
    private val importer = application.musicApp.importer

    /**
     * Turns a song name into one real YouTube link.
     *
     * yt-dlp is only ever handed a link now. It used to be given
     * `ytsearch1:artist title`, which failed here before yt-dlp even ran, since
     * the link check refused anything that was not http. And where a search
     * target does run, it takes YouTube's first hit, which for real searches is
     * routinely a sped-up or slowed re-upload rather than the song.
     */
    private val finder = YouTubeLinkFinder(
        YouTubeSearch(listOf(PipedYouTubeBackend(), AndroidYouTubeBackend(downloader)))
    )

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
        _url.value = value.trim()
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
        val direct = downloadUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
        if (direct != null) {
            pendingWanted = null
            _url.value = direct
        } else {
            val wanted = YouTubeLinkFinder.Wanted(title = title, artist = artist, durationMs = durationMs)
            pendingWanted = wanted
            _url.value = wanted.query
        }
        download()
    }

    fun download() {
        val input = _url.value.trim()
        if (input.isBlank()) return
        val wanted = pendingWanted?.takeIf { it.query == input }

        job?.cancel()
        job = viewModelScope.launch {
            _state.value = DownloadUiState(busy = true, stage = "Starting yt-dlp…")

            // First run unpacks the Python runtime, which takes a few seconds.
            if (!downloader.ensureReady()) {
                _state.value = DownloadUiState(
                    message = downloader.lastInitError
                        ?: "yt-dlp couldn't start. This build ships arm64 libraries only.",
                    isError = true
                )
                return@launch
            }

            val picked: Picked = if (downloader.looksLikeUrl(input)) {
                _state.value = _state.value.copy(stage = "Reading the link…")
                when (val resolved = downloader.resolve(input)) {
                    is ResolvedLink.Direct ->
                        Picked(resolved.url, downloader.peekTitle(resolved.url), null)

                    is ResolvedLink.Search -> {
                        // Spotify: its audio can't be fetched, so the same track
                        // is found on YouTube and that link is what downloads.
                        val found = findLink(YouTubeLinkFinder.Wanted(title = resolved.query))
                            ?: return@launch
                        found.copy(note = listOfNotNull(resolved.note, found.note).joinToString("\n"))
                    }

                    is ResolvedLink.Unsupported -> {
                        _state.value = DownloadUiState(
                            message = resolved.reason,
                            isError = true,
                            offerUpdate = false
                        )
                        return@launch
                    }
                }
            } else {
                // Not a link: an artist and song name, typed or sent from
                // Identify. A leftover "ytsearch1:" prefix is accepted and
                // ignored, so text pasted from before this change still works.
                val text = input.replace(SEARCH_PREFIX, "").trim()
                val found = findLink(wanted ?: YouTubeLinkFinder.Wanted(title = text))
                    ?: return@launch
                // The box shows the link yt-dlp is actually fetching.
                _url.value = found.link
                found
            }

            _state.value = _state.value.copy(
                stage = "Downloading…",
                title = picked.title,
                note = picked.note
            )

            val outcome = downloader.downloadAudio(picked.link, _quality.value) { percent, eta, line ->
                _state.value = _state.value.copy(
                    percent = percent.coerceIn(0f, 100f),
                    etaSeconds = eta,
                    stage = if (line.contains("ExtractAudio", true) ||
                        line.contains("ffmpeg", true)
                    ) {
                        "Converting to mp3…"
                    } else {
                        "Downloading…"
                    }
                )
            }

            when (outcome) {
                is DownloadOutcome.Done -> {
                    _state.value = _state.value.copy(stage = "Adding to library…", percent = 100f)
                    // Same pipeline as a Telegram share: hashed, deduplicated, tagged.
                    val result = importer.import(
                        Uri.fromFile(outcome.file),
                        sourceApp = "Download"
                    )
                    // The cache copy is redundant once the importer has its own.
                    runCatching { outcome.file.parentFile?.deleteRecursively() }

                    _state.value = when (result) {
                        is ImportResult.Imported ->
                            DownloadUiState(message = "Added \"${result.track.title}\"")
                        is ImportResult.Duplicate ->
                            DownloadUiState(message = "Already in your library")
                        else ->
                            DownloadUiState(
                                message = "Downloaded, but the file couldn't be imported.",
                                isError = true
                            )
                    }
                    _url.value = ""
                    pendingWanted = null
                }

                is DownloadOutcome.Failed ->
                    _state.value = DownloadUiState(message = outcome.message, isError = true)

                DownloadOutcome.NotReady ->
                    _state.value = DownloadUiState(
                        message = downloader.lastInitError ?: "yt-dlp is not available.",
                        isError = true
                    )
            }
        }
    }

    /** The link yt-dlp will be given, and what to show about it. */
    private data class Picked(val link: String, val title: String?, val note: String?)

    /**
     * Finds the YouTube video that is the song, or explains why none was used.
     *
     * When nothing suitable turns up, nothing is downloaded. Saving a slowed
     * edit or a cover instead would look like success and be wrong.
     */
    private suspend fun findLink(wanted: YouTubeLinkFinder.Wanted): Picked? {
        _state.value = _state.value.copy(stage = "Finding \"${wanted.query}\" on YouTube…")
        return when (val outcome = finder.find(wanted)) {
            is YouTubeLinkFinder.Outcome.Found -> {
                val video = outcome.pick.video
                Picked(
                    link = video.watchUrl,
                    title = video.title,
                    note = buildString {
                        append("Found on YouTube: ${video.channel}")
                        video.durationSeconds?.let { append(" · ${YouTubeFormat.duration(it)}") }
                        video.viewCount?.let { append(" · ${YouTubeFormat.views(it)}") }
                    }
                )
            }

            is YouTubeLinkFinder.Outcome.NothingSuitable -> {
                _state.value = DownloadUiState(
                    message = outcome.message,
                    isError = true,
                    // With no results at all a stale yt-dlp is plausible; with
                    // results that were all edits, updating would change nothing.
                    offerUpdate = outcome.closest == null
                )
                null
            }
        }
    }

    private companion object {
        val SEARCH_PREFIX = Regex("""^ytsearch\d*:""", RegexOption.IGNORE_CASE)
    }
}
