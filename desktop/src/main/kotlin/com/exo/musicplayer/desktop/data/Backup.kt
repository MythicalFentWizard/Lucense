package com.exo.musicplayer.desktop.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What Lucense knows that the files themselves do not: which songs are
 * favourites, what has been played and how much of it, and the playlists.
 *
 * All of that lives in one database in one folder, which is fine until the
 * folder goes. A backup is a plain text file, one record per line, tab
 * separated - readable, diffable, and needing nothing to parse it. Paths may
 * contain spaces, but not tabs or newlines, so the format holds.
 *
 * Settings are written down too, for reference, but restoring does not apply
 * them: half-applying a theme or a folder list while the app is running is
 * worse than not doing it at all.
 */
object Backup {

    private const val HEADER = "lucense-backup"

    /** What the header said before the app was renamed. */
    private const val WAS = "resonate-backup"
    private const val VERSION = 1

    class Snapshot(
        val takenAt: Long,
        val favourites: List<String>,
        val plays: Map<String, Int>,
        val listened: Map<String, Long>,
        val playlists: List<SavedList>,
        val settings: Map<String, String>
    ) {
        val entries: Int get() = favourites.size + plays.size + playlists.sumOf { it.paths.size }
    }

    class SavedList(val name: String, val paths: List<String>)

    val folder: File by lazy { File(AppDirs.root, "backups").also { it.mkdirs() } }

    /** Newest first, which is the order anyone restoring wants them in. */
    fun existing(): List<File> =
        folder.listFiles()?.filter { it.isFile && it.extension == "txt" }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun take(store: DesktopStore, settings: DesktopSettings): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.ROOT).format(Date())
        val file = File(folder, "backup $stamp.txt")
        val text = buildString {
            appendLine("$HEADER\t$VERSION")
            appendLine("taken\t${System.currentTimeMillis()}")
            store.favourites().forEach { appendLine("fav\t$it") }
            store.playCountByPath().forEach { (path, count) -> appendLine("play\t$path\t$count") }
            store.listenTimeByPath().forEach { (path, ms) -> appendLine("listen\t$path\t$ms") }
            store.playlists().forEach { playlist ->
                appendLine("playlist\t${playlist.name}")
                store.playlistPaths(playlist.id).forEach { appendLine("track\t$it") }
            }
            settings.all().forEach { (key, value) ->
                if (!value.contains('\t') && !value.contains('\n')) appendLine("setting\t$key\t$value")
            }
        }
        file.writeText(text)
        return file
    }

    fun read(file: File): Snapshot? = runCatching {
        val lines = file.readLines()
        // Anything written before the rename carries the old marker and is
        // otherwise identical; refusing it would cost somebody their history.
        val marker = lines.firstOrNull()
        if (marker?.startsWith(HEADER) != true && marker?.startsWith(WAS) != true) return null
        var takenAt = file.lastModified()
        val favourites = mutableListOf<String>()
        val plays = mutableMapOf<String, Int>()
        val listened = mutableMapOf<String, Long>()
        val playlists = mutableListOf<SavedList>()
        val settings = mutableMapOf<String, String>()
        var current: MutableList<String>? = null
        var currentName: String? = null

        fun closePlaylist() {
            val name = currentName ?: return
            playlists.add(SavedList(name, current.orEmpty().toList()))
            currentName = null
            current = null
        }

        for (line in lines.drop(1)) {
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "taken" -> takenAt = parts.getOrNull(1)?.toLongOrNull() ?: takenAt
                "fav" -> parts.getOrNull(1)?.let { favourites.add(it) }
                "play" -> {
                    val path = parts.getOrNull(1)
                    val count = parts.getOrNull(2)?.toIntOrNull()
                    if (path != null && count != null) plays[path] = count
                }
                "listen" -> {
                    val path = parts.getOrNull(1)
                    val ms = parts.getOrNull(2)?.toLongOrNull()
                    if (path != null && ms != null) listened[path] = ms
                }
                "playlist" -> {
                    closePlaylist()
                    currentName = parts.getOrNull(1)
                    current = mutableListOf()
                }
                "track" -> parts.getOrNull(1)?.let { current?.add(it) }
                "setting" -> {
                    val key = parts.getOrNull(1)
                    val value = parts.getOrNull(2)
                    if (key != null && value != null) settings[key] = value
                }
            }
        }
        closePlaylist()
        Snapshot(takenAt, favourites, plays, listened, playlists, settings)
    }.getOrNull()

    /**
     * Puts a snapshot back. Favourites and playlists return as they were; the
     * play history returns as counts and listening time, but stamped now rather
     * than when it happened, so the times of day in Stats are not restored.
     * Playlists that already exist by name are left alone rather than doubled.
     */
    fun restore(store: DesktopStore, snapshot: Snapshot): String {
        snapshot.favourites.forEach { store.setFavourite(it, true) }

        val already = store.playlists().map { it.name }.toSet()
        var lists = 0
        snapshot.playlists.forEach { saved ->
            if (saved.name !in already && saved.paths.isNotEmpty()) {
                val id = store.createPlaylist(saved.name)
                store.addToPlaylist(id, saved.paths)
                lists++
            }
        }

        val counted = store.playCountByPath()
        var plays = 0
        snapshot.plays.forEach { (path, count) ->
            val missing = count - (counted[path] ?: 0)
            if (missing > 0) {
                val each = (snapshot.listened[path] ?: 0L) / count
                repeat(missing) {
                    val id = store.startPlay(path, null)
                    if (each > 0) store.updateListened(id, each)
                    plays++
                }
            }
        }
        return "${snapshot.favourites.size} favourites, $lists playlists, $plays plays"
    }
}
