package com.exo.musicplayer.desktop.system

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * What Discord shows other people while music is playing.
 *
 * Discord listens on a named pipe on the same machine - nothing is sent over
 * the network by Resonate, and a proxy or VPN makes no difference to it. The
 * protocol is a four byte opcode, a four byte length and then JSON: opcode 0
 * to say hello with the application's id, opcode 1 to set what is showing.
 *
 * Everything here is best effort. Discord may not be running, may be a browser
 * tab, or may refuse the id; in each case this quietly does nothing rather than
 * getting in the way of playing music.
 */
class DiscordPresence {

    private class Showing(
        val title: String,
        val artist: String,
        val startedAt: Long,
        val playing: Boolean
    )

    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var pipe: RandomAccessFile? = null

    @Volatile private var wanted: Showing? = null
    @Volatile private var sentAt = 0L
    @Volatile private var lastSent: String? = null

    /** True once Discord has accepted the handshake. */
    @Volatile var connected: Boolean = false
        private set

    fun start(applicationId: String) {
        stop()
        if (applicationId.isBlank()) return
        running.set(true)
        worker = thread(name = "resonate-discord", isDaemon = true) {
            var nextTry = 0L
            while (running.get()) {
                if (pipe == null && System.currentTimeMillis() > nextTry) {
                    nextTry = System.currentTimeMillis() + RETRY_MS
                    open(applicationId)
                }
                if (pipe != null) push()
                Thread.sleep(500)
            }
            clear()
            runCatching { pipe?.close() }
            pipe = null
            connected = false
        }
    }

    fun stop() {
        running.set(false)
        worker?.let { runCatching { it.join(1_500) } }
        worker = null
    }

    /** What should be on screen; the worker sends it when Discord will take it. */
    fun show(title: String?, artist: String?, startedAt: Long, playing: Boolean) {
        wanted = if (title.isNullOrBlank()) {
            null
        } else {
            Showing(title, artist.orEmpty().ifBlank { "Unknown artist" }, startedAt, playing)
        }
    }

    private fun open(applicationId: String) {
        for (index in 0..9) {
            val path = File("\\\\.\\pipe\\discord-ipc-" + index)
            val opened = runCatching { RandomAccessFile(path, "rw") }.getOrNull() ?: continue
            val hello = "{" + quoted("v") + ":1," + quoted("client_id") + ":" + quoted(applicationId) + "}"
            val ok = runCatching { write(opened, 0, hello) }.isSuccess
            if (!ok) {
                runCatching { opened.close() }
                continue
            }
            pipe = opened
            connected = true
            // Discord answers every frame. Nothing here needs the answers, but
            // something has to read them or the pipe eventually fills up.
            thread(name = "resonate-discord-drain", isDaemon = true) {
                val header = ByteArray(8)
                while (running.get() && pipe === opened) {
                    val read = runCatching { opened.read(header) }.getOrNull() ?: break
                    if (read < 8) break
                    val length = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    if (length <= 0 || length > 1 shl 20) break
                    val body = ByteArray(length)
                    if (runCatching { opened.readFully(body) }.isFailure) break
                }
            }
            return
        }
    }

    private fun push() {
        val target = wanted
        val now = System.currentTimeMillis()
        val payload = if (target == null || !target.playing) null else activity(target)
        if (payload == lastSent) return
        // Discord throttles updates; sending faster than this just gets dropped.
        if (now - sentAt < MIN_GAP_MS) return
        val frame = if (payload == null) clearFrame() else setFrame(payload)
        val handle = pipe ?: return
        val sent = runCatching { write(handle, 1, frame) }.isSuccess
        if (!sent) {
            runCatching { handle.close() }
            pipe = null
            connected = false
            lastSent = null
            return
        }
        lastSent = payload
        sentAt = now
    }

    private fun clear() {
        val handle = pipe ?: return
        runCatching { write(handle, 1, clearFrame()) }
    }

    private fun activity(showing: Showing): String = buildString {
        append("{")
        append(quoted("details")).append(":").append(quoted(showing.title.take(120))).append(",")
        append(quoted("state")).append(":").append(quoted("by " + showing.artist.take(120))).append(",")
        append(quoted("timestamps")).append(":{").append(quoted("start")).append(":")
        append(showing.startedAt / 1000).append("},")
        append(quoted("assets")).append(":{")
        append(quoted("large_image")).append(":").append(quoted(ASSET)).append(",")
        append(quoted("large_text")).append(":").append(quoted("Resonate"))
        append("}}")
    }

    private fun setFrame(activity: String): String =
        "{" + quoted("cmd") + ":" + quoted("SET_ACTIVITY") + "," +
            quoted("nonce") + ":" + quoted(System.nanoTime().toString()) + "," +
            quoted("args") + ":{" + quoted("pid") + ":" + ProcessHandle.current().pid() + "," +
            quoted("activity") + ":" + activity + "}}"

    private fun clearFrame(): String =
        "{" + quoted("cmd") + ":" + quoted("SET_ACTIVITY") + "," +
            quoted("nonce") + ":" + quoted(System.nanoTime().toString()) + "," +
            quoted("args") + ":{" + quoted("pid") + ":" + ProcessHandle.current().pid() + "}}"

    private fun write(handle: RandomAccessFile, opcode: Int, json: String) {
        val body = json.toByteArray(Charsets.UTF_8)
        val frame = ByteBuffer.allocate(8 + body.size).order(ByteOrder.LITTLE_ENDIAN)
        frame.putInt(opcode)
        frame.putInt(body.size)
        frame.put(body)
        handle.write(frame.array())
    }

    /** A JSON string, with the few characters that would break one escaped. */
    private fun quoted(text: String): String = buildString {
        append('"')
        text.forEach { character ->
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character == '\n' -> append("\\n")
                character == '\r' -> append("\\r")
                character == '\t' -> append("\\t")
                character.code < 0x20 -> append(String.format("\\u%04x", character.code))
                else -> append(character)
            }
        }
        append('"')
    }

    private companion object {
        /** The image uploaded to the Discord application under this name. */
        const val ASSET = "resonate"
        const val MIN_GAP_MS = 4_000L
        const val RETRY_MS = 20_000L
    }
}
