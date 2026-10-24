package com.exo.musicplayer.data.discover

import com.exo.musicplayer.data.youtube.YouTubeVideo
import com.exo.musicplayer.data.youtube.YtDlpFlatSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One video the home page suggested, as far as it has been looked at. */
data class ForYouItem(
    val video: YouTubeVideo,
    val artist: String?,
    val title: String,
    /** The song's cover, where the catalogue has it; the video's thumbnail stands in otherwise. */
    val coverUrl: String? = null,
    /** True a song, false not one, null while the catalogue is still being asked. */
    val song: Boolean?,
    /** Why it is or isn't, in a few words. */
    val why: String
)

enum class ForYouStage { IDLE, READING, CHECKING, DONE }

data class ForYouState(
    val stage: ForYouStage = ForYouStage.IDLE,
    /** Every video suggested, in YouTube's order. */
    val items: List<ForYouItem> = emptyList(),
    val mixes: List<YtDlpFlatSearch.Mix> = emptyList(),
    /** There is no YouTube sign-in, so there is no home page to read. */
    val signedOut: Boolean = false,
    val problem: String? = null,
    /** When the page was last read, for "a minute ago". */
    val readAt: Long = 0L
) {
    val songs: List<ForYouItem> get() = items.filter { it.song == true }
    val others: List<ForYouItem> get() = items.filter { it.song == false }
    val checking: Int get() = items.count { it.song == null }
    val busy: Boolean get() = stage == ForYouStage.READING || stage == ForYouStage.CHECKING
}

/** A mix's own songs, once opened. */
data class MixState(
    val songs: List<ForYouItem> = emptyList(),
    val loading: Boolean = true,
    val problem: String? = null
)

/**
 * The songs among what YouTube suggests to the signed-in account: its home
 * page read through yt-dlp with the same sign-in the downloads use, every
 * video judged by [SongOrNot], and the doubtful ones checked against Deezer.
 *
 * Shared, so both apps sort the page the same way; each says how yt-dlp is
 * run ([read], [readMix]) and whether there is a sign-in ([signedIn]). Read
 * once per run unless asked again: the home page changes every time it is
 * opened, and a list that reshuffled itself whenever the tab was revisited
 * would lose whatever was being looked at.
 */
class YouTubeForYou(
    private val scope: CoroutineScope,
    private val discovery: Discovery,
    private val signedIn: () -> Boolean,
    /** The home page, or why it couldn't be read, in words for the page. */
    private val read: suspend () -> Pair<YtDlpFlatSearch.Feed?, String?>,
    /** A mix's videos, or why not. */
    private val readMix: suspend (String) -> Pair<List<YouTubeVideo>?, String?>
) {
    private val _state = MutableStateFlow(ForYouState())
    val state: StateFlow<ForYouState> = _state.asStateFlow()

    private val _mixes = MutableStateFlow<Map<String, MixState>>(emptyMap())
    val mixes: StateFlow<Map<String, MixState>> = _mixes.asStateFlow()

    private var job: Job? = null

    /** When the tab is shown: read if never read, or if a sign-in has appeared since. */
    fun open() {
        val now = _state.value
        if (now.stage == ForYouStage.IDLE || (now.signedOut && signedIn())) refresh()
    }

    /** Reads the home page again; YouTube suggests something new each time. */
    fun refresh() {
        job?.cancel()
        job = scope.launch {
            if (!signedIn()) {
                _state.value = ForYouState(stage = ForYouStage.DONE, signedOut = true)
                return@launch
            }
            // What was there stays on show while the page is read again.
            _state.update { it.copy(stage = ForYouStage.READING, problem = null, signedOut = false) }
            val (feed, error) = read()
            if (feed == null) {
                _state.update {
                    it.copy(stage = ForYouStage.DONE, problem = error ?: "YouTube's home page couldn't be read.")
                }
                return@launch
            }
            if (feed.videos.isEmpty() && feed.mixes.isEmpty()) {
                _state.value = ForYouState(stage = ForYouStage.DONE, problem = EMPTY, readAt = System.currentTimeMillis())
                return@launch
            }
            val judged = feed.videos.map { video ->
                val judgement = SongOrNot.judge(video)
                ForYouItem(
                    video = video,
                    artist = judgement.artist,
                    title = judgement.title,
                    song = when (judgement.verdict) {
                        SongOrNot.Verdict.SONG -> true
                        SongOrNot.Verdict.NOT -> false
                        SongOrNot.Verdict.MAYBE -> null
                    },
                    why = judgement.why
                ) to judgement.verdict
            }
            _mixes.value = emptyMap()
            _state.value = ForYouState(
                stage = ForYouStage.CHECKING,
                items = judged.map { it.first },
                mixes = feed.mixes,
                readAt = System.currentTimeMillis()
            )
            check(judged)
            _state.update { it.copy(stage = ForYouStage.DONE) }
        }
    }

    /**
     * Each song and each maybe asked of Deezer, in YouTube's order: a maybe
     * it knows is a song, one it doesn't isn't; a song it knows gets its
     * cover and the artist's name as written. Without Deezer the maybes are
     * left out, saying so.
     */
    private suspend fun check(judged: List<Pair<ForYouItem, SongOrNot.Verdict>>) {
        val asked = judged.filter { (item, verdict) -> verdict != SongOrNot.Verdict.NOT && item.artist != null }
        val reachable = asked.isEmpty() || discovery.reachable()
        judged.forEachIndexed { index, (item, verdict) ->
            if (verdict == SongOrNot.Verdict.NOT) return@forEachIndexed
            val artist = item.artist
            val match = if (reachable && artist != null) runCatching { discovery.song(artist, item.title) }.getOrNull() else null
            val checked = when {
                match != null -> item.copy(
                    song = true,
                    artist = match.artist.ifBlank { item.artist },
                    coverUrl = match.coverUrl,
                    why = if (verdict == SongOrNot.Verdict.MAYBE) "a song Deezer knows" else item.why
                )
                verdict == SongOrNot.Verdict.SONG -> item
                !reachable -> item.copy(song = false, why = "might be a song; Deezer couldn't be reached to check")
                else -> item.copy(song = false, why = "not a song Deezer knows")
            }
            _state.update { state -> state.copy(items = state.items.toMutableList().also { it[index] = checked }) }
        }
    }

    /** A mix's songs, read when it is opened; everything in a mix is taken to be a song. */
    fun openMix(mix: YtDlpFlatSearch.Mix) {
        val known = _mixes.value[mix.id]
        if (known != null && (known.loading || known.problem == null)) return
        _mixes.update { it + (mix.id to MixState()) }
        scope.launch {
            val (videos, error) = readMix(mix.url)
            _mixes.update {
                it + (
                    mix.id to if (videos.isNullOrEmpty()) {
                        MixState(loading = false, problem = error ?: "YouTube didn't give this mix's songs.")
                    } else {
                        MixState(
                            loading = false,
                            songs = videos.filterNot { video -> video.isShort }.map { video ->
                                val judgement = SongOrNot.judge(video)
                                ForYouItem(video, judgement.artist, judgement.title, song = true, why = "in the mix")
                            }
                        )
                    }
                    )
            }
        }
    }

    fun closeMix(mix: YtDlpFlatSearch.Mix) {
        _mixes.update { it - mix.id }
    }

    companion object {
        /** How far down the home page is read: about two screens of it. */
        const val LIMIT = 60

        const val EMPTY =
            "YouTube's home page came back empty. Either the sign-in has run out - check it on the " +
                "Download page - or the account's watch history is off, and then YouTube suggests nothing."

        const val SIGNED_OUT =
            "Sign in to YouTube to see what it suggests for you. Lucense reads the home page YouTube " +
                "shows that account, keeps the songs and leaves the rest. It's the same sign-in the " +
                "downloads use."
    }
}
