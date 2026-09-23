package com.exo.musicplayer.desktop.library

import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Says when something in the library's folders has changed on disk.
 *
 * Songs arrive in those folders from everywhere - a download finishing, a
 * Telegram export dropped in by hand, a file renamed in Explorer - and the
 * library only ever noticed when it was told to look again.
 *
 * Changes come in bursts: copying one file produces several events, and a
 * download in progress produces a steady stream of them. Nothing is reported
 * until the folder has been quiet for [SETTLE_MS], so a rescan happens once
 * after the copying stops rather than repeatedly while it goes on.
 *
 * Windows reports changes per directory, so every folder under a root is
 * registered, down to [MAX_DEPTH]. A folder created after that is not watched
 * until the next time watching is set up - the files in it still arrive with
 * the next rescan.
 */
class FolderWatcher(private val onChanged: () -> Unit) {

    private var worker: Thread? = null
    private var service: WatchService? = null
    private val running = AtomicBoolean(false)

    /** True while a watch is actually in place. */
    @Volatile var watching: Boolean = false
        private set

    fun watch(roots: List<File>) {
        stop()
        val folders = roots.filter { it.isDirectory }
        if (folders.isEmpty()) return
        val watcher = runCatching { FileSystems.getDefault().newWatchService() }.getOrNull() ?: return
        service = watcher
        running.set(true)
        worker = thread(name = "lucense-folder-watch", isDaemon = true) {
            folders.forEach { register(watcher, it, 0) }
            watching = true
            var waiting = false
            var last = 0L
            try {
                while (running.get()) {
                    val key = watcher.poll(500, TimeUnit.MILLISECONDS)
                    if (key != null) {
                        val real = key.pollEvents().any { it.kind() != StandardWatchEventKinds.OVERFLOW }
                        if (real) {
                            waiting = true
                            last = System.currentTimeMillis()
                        }
                        key.reset()
                    }
                    if (waiting && System.currentTimeMillis() - last > SETTLE_MS) {
                        waiting = false
                        runCatching { onChanged() }
                    }
                }
            } catch (_: InterruptedException) {
                // Falls through to closing below.
            } catch (_: ClosedWatchServiceException) {
                // stop() closed it from underneath; that is how it ends.
            } finally {
                watching = false
                runCatching { watcher.close() }
            }
        }
    }

    private fun register(watcher: WatchService, dir: File, depth: Int) {
        if (depth > MAX_DEPTH) return
        runCatching {
            dir.toPath().register(
                watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )
        }
        dir.listFiles()?.forEach { if (it.isDirectory) register(watcher, it, depth + 1) }
    }

    fun stop() {
        running.set(false)
        runCatching { service?.close() }
        service = null
        worker?.let { runCatching { it.join(600) } }
        worker = null
        watching = false
    }

    private companion object {
        /** How long the folders must be quiet before a change counts as finished. */
        const val SETTLE_MS = 2_500L
        const val MAX_DEPTH = 6
    }
}
