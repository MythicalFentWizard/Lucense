package com.exo.musicplayer.ui.recognition

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exo.musicplayer.data.recognition.AudioSampler
import com.exo.musicplayer.data.recognition.AudiusProvider
import com.exo.musicplayer.data.recognition.DeezerProvider
import com.exo.musicplayer.data.recognition.GeniusMetadataProvider
import com.exo.musicplayer.data.recognition.InternetArchiveProvider
import com.exo.musicplayer.data.recognition.YouTubeSearchProvider
import com.exo.musicplayer.data.recognition.ITunesProvider
import com.exo.musicplayer.data.download.DownloadOutcome
import com.exo.musicplayer.data.download.DownloadQuality
import com.exo.musicplayer.data.download.YtDlpDownloader
import com.exo.musicplayer.data.recognition.GeniusLyricSearch
import com.exo.musicplayer.data.recognition.MatchRanker
import com.exo.musicplayer.data.recognition.MetadataProviderChain
import com.exo.musicplayer.data.recognition.NeteaseLyricSearch
import com.exo.musicplayer.data.recognition.MusicBrainzProvider
import com.exo.musicplayer.data.recognition.RecognitionResult
import com.exo.musicplayer.data.recognition.ShazamClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecognitionStage(val message: String) {
    IDLE(""),
    FETCHING("Fetching the audio…"),
    EXTRACTING("Reading the audio…"),
    IDENTIFYING("Listening…"),
    SEARCHING("Searching…")
}

class RecognitionViewModel(application: Application) : AndroidViewModel(application) {

    private val shazam = ShazamClient()

    /**
     * Only built when a link is actually identified.
     *
     * Constructing it is cheap, but the first download unpacks a Python runtime,
     * and nobody who never pastes a link should pay for that.
     */
    private val downloader by lazy { YtDlpDownloader(application) }
    // Every keyless service, queried in parallel. YouTube last only in
    // declaration order; results are interleaved so each service shows up.
    private val search = MetadataProviderChain(
        listOf(
            ITunesProvider(),
            DeezerProvider(),
            MusicBrainzProvider(),
            AudiusProvider(),
            InternetArchiveProvider(),
            GeniusMetadataProvider(),
            YouTubeSearchProvider()
        )
    )

    /**
     * Lyric-text search, kept apart from the catalogue chain.
     *
     * Only these two index the words of a song; sending a remembered line to
     * iTunes or MusicBrainz returns nothing, so mixing them in would add
     * latency and empty rows and nothing else.
     */
    private val lyricSearch = MetadataProviderChain(
        listOf(GeniusLyricSearch(), NeteaseLyricSearch())
    )

    private val _mode = MutableStateFlow(SearchMode.NAME)
    val mode: StateFlow<SearchMode> = _mode.asStateFlow()

    private val _stage = MutableStateFlow(RecognitionStage.IDLE)
    val stage: StateFlow<RecognitionStage> = _stage.asStateFlow()

    private val _result = MutableStateFlow<RecognitionResult?>(null)
    val result: StateFlow<RecognitionResult?> = _result.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sourceLabel = MutableStateFlow<String?>(null)
    val sourceLabel: StateFlow<String?> = _sourceLabel.asStateFlow()

    fun setQuery(value: String) { _query.value = value }

    fun setMode(value: SearchMode) { _mode.value = value }

    /** Runs whichever search the current mode calls for. */
    fun runSearch() {
        when (_mode.value) {
            SearchMode.NAME -> searchByName()
            SearchMode.LYRICS -> searchByLyrics()
            SearchMode.LINK -> identifyLink()
        }
    }

    /**
     * Pulls the audio behind a link down and fingerprints it.
     *
     * The file is a means to an end, so it goes to the cache directory and is
     * deleted as soon as the fingerprint is taken — identifying a song should
     * not quietly fill the device with videos.
     *
     * Fetched at the lowest quality on purpose: this needs twelve seconds of
     * recognisable audio, not a keepable copy, and on a phone the smaller
     * download is the difference between a few seconds and a wait.
     */
    fun identifyLink(url: String = _query.value) {
        val target = url.trim()
        if (target.isEmpty()) return
        _sourceLabel.value = null
        viewModelScope.launch {
            _result.value = null
            _stage.value = RecognitionStage.FETCHING

            val outcome = runCatching {
                downloader.downloadAudio(target, DownloadQuality.LOW) { _, _, _ -> }
            }.getOrElse { DownloadOutcome.Failed(it.message ?: "Couldn't fetch that link.") }

            when (outcome) {
                is DownloadOutcome.NotReady -> {
                    _stage.value = RecognitionStage.IDLE
                    _result.value = RecognitionResult.Error(
                        "The downloader isn't ready yet. Open the download window once " +
                            "so it can finish setting itself up, then try again."
                    )
                }

                is DownloadOutcome.Failed -> {
                    _stage.value = RecognitionStage.IDLE
                    _result.value = RecognitionResult.Error(outcome.message)
                }

                is DownloadOutcome.Done -> {
                    try {
                        _stage.value = RecognitionStage.EXTRACTING
                        val samples = runCatching {
                            AudioSampler.sampleMono16k(
                                getApplication(),
                                Uri.fromFile(outcome.file)
                            )
                        }.getOrNull()

                        if (samples == null) {
                            _result.value = RecognitionResult.Error(
                                "Downloaded the audio but couldn't decode it."
                            )
                        } else {
                            _stage.value = RecognitionStage.IDENTIFYING
                            _result.value = runCatching { shazam.recognize(samples) }
                                .getOrElse {
                                    RecognitionResult.Error(
                                        it.message ?: "Identification failed."
                                    )
                                }
                        }
                    } finally {
                        // The whole working directory, not just the file, since
                        // yt-dlp may have left thumbnails beside it.
                        runCatching {
                            outcome.file.parentFile?.deleteRecursively()
                                ?: outcome.file.delete()
                        }
                        _stage.value = RecognitionStage.IDLE
                    }
                }
            }
        }
    }

    fun clearResult() {
        _result.value = null
        _sourceLabel.value = null
    }

    /** Fingerprints a snippet of [uri] and asks Shazam what it is. */
    fun identify(uri: Uri, label: String?) {
        _sourceLabel.value = label
        viewModelScope.launch {
            _result.value = null
            _stage.value = RecognitionStage.EXTRACTING
            val samples = runCatching {
                AudioSampler.sampleMono16k(getApplication(), uri)
            }.getOrNull()

            if (samples == null) {
                _stage.value = RecognitionStage.IDLE
                _result.value = RecognitionResult.Error(
                    "Couldn't read any audio from that file. If it's a video, it may " +
                        "have no audio track or use a codec this device can't decode."
                )
                return@launch
            }

            _stage.value = RecognitionStage.IDENTIFYING
            _result.value = runCatching { shazam.recognize(samples) }
                .getOrElse { RecognitionResult.Error(it.message ?: "Identification failed.") }
            _stage.value = RecognitionStage.IDLE
        }
    }

    fun searchByName() {
        val text = _query.value.trim()
        if (text.isEmpty()) return
        _sourceLabel.value = null
        viewModelScope.launch {
            _result.value = null
            _stage.value = RecognitionStage.SEARCHING
            _result.value = runCatching {
                val found = search.searchAll(text)
                // Each service answers loosely and none can see the others, so
                // relevance is decided here rather than trusting the order they
                // happened to come back in. Without this a search for one song
                // returns another that merely shares a couple of words.
                val matches = MatchRanker.rank(text, found)
                val dropped = found.size - matches.size
                _sourceLabel.value = buildString {
                    append(
                        matches.map { it.provider }.distinct()
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                    )
                    if (dropped > 0) append("  — $dropped unrelated hidden")
                }.takeIf { it.isNotBlank() }
                if (matches.isEmpty()) RecognitionResult.NoMatch
                else RecognitionResult.Found(matches)
            }.getOrElse { RecognitionResult.Error(it.message ?: "Search failed.") }
            _stage.value = RecognitionStage.IDLE
        }
    }

    /**
     * Finds a song from a line of its lyrics.
     *
     * Not ranked: the query is the words of the song, not its name, so scoring
     * the query against titles would throw away every correct answer.
     */
    fun searchByLyrics() {
        val text = _query.value.trim()
        if (text.isEmpty()) return
        _sourceLabel.value = null
        viewModelScope.launch {
            _result.value = null
            _stage.value = RecognitionStage.SEARCHING
            _result.value = runCatching {
                val matches = lyricSearch.searchAll(text, limitPer = 8)
                _sourceLabel.value = matches.map { it.provider }.distinct()
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .takeIf { it.isNotBlank() }
                if (matches.isEmpty()) RecognitionResult.NoMatch
                else RecognitionResult.Found(matches)
            }.getOrElse { RecognitionResult.Error(it.message ?: "Search failed.") }
            _stage.value = RecognitionStage.IDLE
        }
    }
}

/** How the Identify box reads what was typed. */
enum class SearchMode(val label: String, val hint: String) {
    NAME("Name", "Artist and title, in any order"),
    LYRICS("Lyrics", "A line you remember, however roughly"),
    LINK("Link", "TikTok, Instagram, YouTube link")
}
