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
 * One thread owns the pipe, and every frame written is followed by reading the
 * answer to it. That matters more than it looks. Windows serialises the two
 * directions of a synchronous file handle, so a second thread sitting in a
 * blocking read stops writes to the same handle dead - the handshake gets out,
 * every song after it hangs in the write for ever, and nothing looks wrong from
 * the outside. Reading each reply on the thread that wrote the frame avoids
 * that, and has the happy side effect that Discord can tell us when it has
 * refused something.
 *
 * Everything here is best effort. Discord may not be running, may be a browser
 * tab, or may refuse the id; in each case it says so through [note] rather than
 * getting in the way of playing music.
 */
class DiscordPresence(private val onState: () -> Unit = {}) {

    private class Showing(
        val title: String,
        val artist: String,
        val startedAt: Long,
        val endsAt: Long,
        val playing: Boolean
    )

    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    @Volatile private var pipe: RandomAccessFile? = null

    @Volatile private var wanted: Showing? = null
    private var sentAt = 0L
    private var lastSent: String? = null

    /** True once Discord has accepted the handshake. */
    @Volatile var connected: Boolean = false
        private set

    /** Why it isn't showing, in words the settings panel can put on screen. */
    @Volatile var note: String? = null
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
            close()
        }
    }

    fun stop() {
        running.set(false)
        val thread = worker
        worker = null
        thread?.let { runCatching { it.join(1_500) } }
        // If it never woke up it is parked waiting on a reply that is not
        // coming; closing the handle is what frees it.
        close()
        report(false, null)
    }

    /** What should be on screen; the worker sends it when Discord will take it. */
    fun show(title: String?, artist: String?, startedAt: Long, endsAt: Long, playing: Boolean) {
        wanted = if (title.isNullOrBlank()) {
            null
        } else {
            Showing(title, artist.orEmpty().ifBlank { "Unknown artist" }, startedAt, endsAt, playing)
        }
    }

    private fun open(applicationId: String) {
        var sawPipe = false
        for (index in 0..9) {
            val path = File("""\\.\pipe\discord-ipc-""" + index)
            val opened = runCatching { RandomAccessFile(path, "rw") }.getOrNull() ?: continue
            sawPipe = true
            val hello = "{" + quoted("v") + ":1," + quoted("client_id") + ":" + quoted(applicationId) + "}"
            val reply = runCatching {
                write(opened, 0, hello)
                read(opened)
            }.getOrNull()
            if (reply == null) {
                runCatching { opened.close() }
                continue
            }
            errorIn(reply)?.let {
                runCatching { opened.close() }
                report(false, "Discord turned down the application ID: $it")
                return
            }
            pipe = opened
            lastSent = null
            sentAt = 0L
            report(true, null)
            return
        }
        report(false, if (sawPipe) "Discord answered, but not in a way this understands." else null)
    }

    private fun push() {
        val target = wanted
        val now = System.currentTimeMillis()
        val payload = if (target == null || !target.playing) null else activity(target)
        if (payload == lastSent) return
        // Discord throttles updates; sending faster than this just gets dropped.
        if (now - sentAt < MIN_GAP_MS) return
        val handle = pipe ?: return
        val frame = if (payload == null) clearFrame() else setFrame(payload)
        val reply = runCatching {
            write(handle, 1, frame)
            read(handle)
        }.getOrNull()
        if (reply == null) {
            close()
            report(false, "Lost the connection to Discord; trying again shortly.")
            return
        }
        lastSent = payload
        sentAt = now
        report(true, errorIn(reply)?.let { "Discord turned down the song: $it" })
    }

    private fun clear() {
        val handle = pipe ?: return
        runCatching {
            write(handle, 1, clearFrame())
            read(handle)
        }
    }

    private fun close() {
        val handle = pipe
        pipe = null
        lastSent = null
        runCatching { handle?.close() }
    }

    private fun report(connected: Boolean, note: String?) {
        if (this.connected == connected && this.note == note) return
        this.connected = connected
        this.note = note
        runCatching { onState() }
    }

    /**
     * Type 2 is "Listening to", which is what a music player ought to say. The
     * two timestamps give the profile a progress bar rather than a stopwatch.
     */
    private fun activity(showing: Showing): String = buildString {
        append("{").append(quoted("type")).append(":2,")
        // Discord rejects a one character line, which a song really can have.
        append(quoted("details")).append(":").append(quoted(showing.title.take(120).padEnd(2))).append(",")
        append(quoted("state")).append(":").append(quoted("by " + showing.artist.take(120))).append(",")
        append(quoted("timestamps")).append(":{").append(quoted("start")).append(":")
        append(showing.startedAt / 1000)
        if (showing.endsAt > showing.startedAt) {
            append(",").append(quoted("end")).append(":").append(showing.endsAt / 1000)
        }
        append("},")
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

    private fun read(handle: RandomAccessFile): String {
        val header = ByteArray(8)
        handle.readFully(header)
        val length = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        require(length in 1..(1 shl 20)) { "a reply of $length bytes" }
        val body = ByteArray(length)
        handle.readFully(body)
        return String(body, Charsets.UTF_8)
    }

    /** The message out of an error reply, or null if Discord was happy. */
    private fun errorIn(reply: String): String? {
        if (!reply.contains("\"evt\":\"ERROR\"")) return null
        val key = "\"message\":\""
        val at = reply.indexOf(key)
        if (at < 0) return "no reason given"
        val end = reply.indexOf('"', at + key.length)
        return if (end < 0) "no reason given" else reply.substring(at + key.length, end)
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
