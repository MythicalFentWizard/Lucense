package com.exo.musicplayer.desktop.download

import com.exo.musicplayer.desktop.data.AppDirs
import java.io.File

/**
 * Which videos have been downloaded, and to where.
 *
 * Kept for playlists: asking for the same one again, after YouTube refused
 * half of it or after a few songs were added to it, fetches only what is
 * missing. A song counts as had only while its file is still where it was
 * saved, so one that was deleted or moved is simply downloaded again.
 *
 * A line per download in a text file beside the database. Appended to, never
 * rewritten, so a crash part-way through a playlist loses nothing.
 */
object DownloadArchive {

    private val file: File get() = File(AppDirs.root, "downloaded.tsv")

    private val known: MutableMap<String, String> by lazy {
        val map = HashMap<String, String>()
        runCatching {
            file.takeIf { it.isFile }?.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) map[line.substring(0, tab)] = line.substring(tab + 1)
            }
        }
        map
    }

    /** The file a video was saved as, if it is still there. */
    @Synchronized
    fun fileFor(videoId: String): File? = known[videoId]?.let(::File)?.takeIf { it.isFile }

    @Synchronized
    fun add(videoId: String, saved: File) {
        known[videoId] = saved.absolutePath
        runCatching { file.appendText("$videoId\t${saved.absolutePath}\n") }
    }
}
