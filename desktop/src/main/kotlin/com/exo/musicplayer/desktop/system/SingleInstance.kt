package com.exo.musicplayer.desktop.system

import com.exo.musicplayer.desktop.data.AppDirs
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * One Lucense at a time.
 *
 * Started again - from its shortcut, or by opening a song with it - while one
 * is already running, perhaps out of sight in the tray, the new one hands the
 * files it was given to the one running and quits, and the one running comes
 * to the front and plays them.
 *
 * The running one listens on the loopback address only, on a port written in
 * Lucense's own folder together with a random token that a caller must
 * repeat, so nothing else on the computer can hand it files.
 */
object SingleInstance {

    private val file: File get() = File(AppDirs.root, "instance")

    /** True when a Lucense already running took [args] and came to the front: this one should quit. */
    fun handOver(args: Array<String>): Boolean = handOver(args, file)

    internal fun handOver(args: Array<String>, file: File): Boolean {
        val lines = runCatching { file.readLines() }.getOrNull() ?: return false
        val port = lines.getOrNull(0)?.trim()?.toIntOrNull() ?: return false
        val token = lines.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return runCatching {
            Socket().use { socket ->
                // Gone without tidying up, its port closed: nobody answers, and this one carries on.
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_MS)
                socket.soTimeout = ANSWER_MS
                val out = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                out.write(token + "\n")
                args.forEach { out.write(it.replace('\n', ' ').replace('\r', ' ') + "\n") }
                out.write("\n")
                out.flush()
                socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine() == "ok"
            }
        }.getOrDefault(false)
    }

    /**
     * Starts listening for later starts. [onArrive] is given the files each
     * one was opened with - none for a plain start - on the UI thread.
     */
    fun listen(onArrive: (List<String>) -> Unit) {
        listen(file, onArrive)
    }

    /** Listens with its port and token written to [file]; the server, so a test can close it. */
    internal fun listen(file: File, onArrive: (List<String>) -> Unit): ServerSocket? {
        val server = runCatching { ServerSocket(0, 8, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return null
        val token = UUID.randomUUID().toString()
        runCatching { file.writeText("${server.localPort}\n$token\n") }
        thread(isDaemon = true, name = "lucense-single-instance") {
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    socket.use {
                        it.soTimeout = ANSWER_MS
                        val reader = it.getInputStream().bufferedReader(Charsets.UTF_8)
                        if (reader.readLine() != token) return@use
                        val paths = generateSequence { reader.readLine() }.takeWhile { line -> line.isNotEmpty() }.toList()
                        it.getOutputStream().write("ok\n".toByteArray())
                        it.getOutputStream().flush()
                        SwingUtilities.invokeLater { onArrive(paths) }
                    }
                }
            }
        }
        return server
    }

    private const val CONNECT_MS = 500
    private const val ANSWER_MS = 3_000
}
