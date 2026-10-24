package com.exo.musicplayer.desktop.download

import com.exo.musicplayer.data.download.DownloadQuality
import com.exo.musicplayer.desktop.data.AppDirs
import com.exo.musicplayer.desktop.data.NetworkProxy
import com.exo.musicplayer.desktop.data.ToolPaths
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * What yt-dlp read about a YouTube video, kept while YouTube's addresses last.
 *
 * Reading a video is the slow part of playing or downloading it: its page,
 * its player, YouTube's challenge - several seconds, and it used to be done
 * afresh for the preview, again for the download, again to stream it. Now it
 * is done once and kept (YouTube's addresses last about six hours). A preview
 * plays from what was kept, and a download hands it to yt-dlp
 * (`--load-info-json`), which then goes straight to fetching the audio.
 *
 * Your YouTube reads its first songs ahead ([lookAhead]), a couple at a time,
 * so most of them start as soon as they are clicked. A click on one not read
 * yet reads it at once rather than waiting its turn.
 */
object YouTubeAhead {

    /** One video, read: yt-dlp's whole answer on disk, and the address it picked to play. */
    class Found(val id: String, val info: File, val url: String, val expiresAt: Long, val readAt: Long) {
        val fresh: Boolean get() = System.currentTimeMillis() < expiresAt - MARGIN_MS && info.isFile
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dir: File by lazy {
        // What an earlier run kept is gone with it: nothing remembers which address goes with which video.
        File(AppDirs.root, "youtube-ahead").also { folder ->
            folder.deleteRecursively()
            folder.mkdirs()
        }
    }

    private val found = ConcurrentHashMap<String, Found>()
    private val reading = ConcurrentHashMap<String, CompletableDeferred<Found?>>()

    /** Waiting to be read ahead, the most wanted first. */
    private val waiting = ArrayDeque<String>()
    private var workers = 0

    /** The video a YouTube watch address is for, or null for anything else. */
    fun videoId(page: String): String? =
        WATCH.find(page)?.groupValues?.let { it[1].ifEmpty { it[2] } }?.takeIf { it.isNotEmpty() }

    /** What was kept for [id], while it is still good. */
    fun kept(id: String): Found? {
        val known = found[id] ?: return null
        if (known.fresh) return known
        forget(id)
        return null
    }

    /** Drops what was kept for [id]; the next look reads it again. */
    fun forget(id: String) {
        found.remove(id)?.info?.delete()
    }

    /** Drops what was kept for [id] if it was read more than [ms] ago; whether it did. */
    fun forgetIfOlder(id: String, ms: Long): Boolean {
        val known = found[id] ?: return false
        if (System.currentTimeMillis() - known.readAt < ms) return false
        forget(id)
        return true
    }

    /** The address to play [id] from: kept, being read, or read now. */
    suspend fun audioUrl(id: String, quality: DownloadQuality): String? = find(id, quality)?.url

    /** [id], read: what was kept, what is being read, or read now, ahead of anything waiting. */
    suspend fun find(id: String, quality: DownloadQuality): Found? {
        kept(id)?.let { return it }
        synchronized(waiting) { waiting.remove(id) }
        // Waited on here rather than read here: a preview given up on stops waiting at once,
        // and the reading finishes all the same, for the next click.
        return start(listOf(id), quality).getValue(id).await()
    }

    /**
     * Reads [ids] ahead, the first first, two yt-dlp at a time. Put before
     * whatever was waiting already, which is what was wanted earlier.
     */
    fun lookAhead(ids: List<String>, quality: DownloadQuality) {
        synchronized(waiting) {
            val wanted = ids.filter { kept(it) == null && !reading.containsKey(it) }
            waiting.removeAll(wanted.toSet())
            wanted.asReversed().forEach { waiting.addFirst(it) }
            while (waiting.size > MAX_WAITING) waiting.removeLast()
            while (workers < WORKERS && waiting.isNotEmpty()) {
                workers++
                scope.launch { work(quality) }
            }
        }
    }

    private suspend fun work(quality: DownloadQuality) {
        try {
            while (true) {
                val batch = synchronized(waiting) {
                    buildList {
                        while (size < BATCH && waiting.isNotEmpty()) {
                            val next = waiting.removeFirst()
                            if (kept(next) == null && !reading.containsKey(next)) add(next)
                        }
                    }
                }
                if (batch.isEmpty()) {
                    if (synchronized(waiting) { waiting.isEmpty() }) return
                    continue
                }
                start(batch, quality).values.forEach { it.await() }
            }
        } finally {
            synchronized(waiting) { workers-- }
        }
    }

    /**
     * Starts one yt-dlp reading those of [ids] that aren't kept or being read
     * already, and hands back an answer to wait on for each of [ids].
     */
    private fun start(ids: List<String>, quality: DownloadQuality): Map<String, Deferred<Found?>> {
        val answers = LinkedHashMap<String, Deferred<Found?>>()
        val mine = mutableListOf<String>()
        for (id in ids) {
            val known = kept(id)
            if (known != null) {
                answers[id] = CompletableDeferred(known)
                continue
            }
            val answer = CompletableDeferred<Found?>()
            val already = reading.putIfAbsent(id, answer)
            answers[id] = already ?: answer
            if (already == null) mine += id
        }
        if (mine.isNotEmpty()) scope.launch { read(mine, quality) }
        return answers
    }

    /** Runs yt-dlp over [ids], keeping each answer as it comes; what it couldn't read is answered null. */
    private fun read(ids: List<String>, quality: DownloadQuality) {
        try {
            val exe = ToolPaths.ytDlp
            if (!exe.isFile) return
            val process = ProcessBuilder(
                buildList {
                    add(exe.absolutePath)
                    // One JSON document a line, a line a video, each as soon as it is read.
                    addAll(listOf("-j", "-f", quality.formatSelector, "--no-playlist", "--no-warnings", "--ignore-config", "--no-abort-on-error"))
                    addAll(ytDlpJsEngine() + NetworkProxy.ytDlpArgs() + YouTubeSignIn.ytDlpArgs() + YT_DLP_NETWORK)
                    ids.forEach { add("https://www.youtube.com/watch?v=$it") }
                }
            ).redirectErrorStream(false).also(NetworkProxy::configure).start()
            Thread { runCatching { process.errorStream.use { it.readBytes() } } }.apply { isDaemon = true; start() }
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    val answer = runCatching { keep(line) }.getOrNull() ?: return@forEach
                    found[answer.id] = answer
                    reading.remove(answer.id)?.complete(answer)
                }
            }
            process.waitFor()
        } catch (_: Exception) {
            // Answered below, as not read.
        } finally {
            ids.forEach { id -> reading.remove(id)?.complete(null) }
        }
    }

    /** One line of yt-dlp's answer, kept on disk; null when it names no playable address. */
    private fun keep(line: String): Found? {
        if (!line.startsWith("{")) return null
        val json = JSONObject(line)
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val url = json.optString("url").takeIf { it.startsWith("http") } ?: return null
        val now = System.currentTimeMillis()
        val expires = EXPIRES.find(url)?.groupValues?.get(1)?.toLongOrNull()?.times(1000) ?: (now + UNKNOWN_EXPIRY_MS)
        val file = File(dir, "$id.json")
        file.writeText(line, Charsets.UTF_8)
        return Found(id, file, url, expires, now)
    }

    private val WATCH = Regex("""^https?://(?:www\.|m\.|music\.)?youtube\.com/watch\?(?:.*&)?v=([A-Za-z0-9_-]{11})|^https?://youtu\.be/([A-Za-z0-9_-]{11})""")
    private val EXPIRES = Regex("""[?&/]expire[=/](\d{9,11})""")

    /** Two yt-dlp at a time, a few videos each: the second half of a batch shares the first's start. */
    private const val WORKERS = 2
    private const val BATCH = 3
    private const val MAX_WAITING = 24

    /** Given up on this long before YouTube says it ends: a download may take a while. */
    private const val MARGIN_MS = 30L * 60 * 1000
    private const val UNKNOWN_EXPIRY_MS = 2L * 60 * 60 * 1000
}
