package com.exo.musicplayer.desktop

import com.exo.musicplayer.desktop.data.AppDirs
import com.exo.musicplayer.desktop.download.OwnSignIn
import com.exo.musicplayer.desktop.system.DiscordPresence
import com.exo.musicplayer.desktop.system.Mpris
import com.exo.musicplayer.desktop.system.Platform
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Linux build's own pieces, against the real thing where there is one:
 * MPRIS through playerctl, the sign-in window through the browsers installed,
 * Discord through a socket where Discord would listen. Skipped elsewhere.
 */
class LinuxTest {

    private fun linux() = assumeTrue(Platform.isLinux, "Linux only")

    /** Runs [command]; its output, trimmed, or null when it couldn't run. */
    private fun run(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText().trim()
        if (!process.waitFor(15, TimeUnit.SECONDS)) process.destroyForcibly()
        out
    }.getOrNull()

    private fun eventually(what: String, timeoutMs: Long = 10_000, check: () -> Boolean) {
        val until = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < until) {
            if (check()) return
            Thread.sleep(200)
        }
        throw AssertionError("Not within ${timeoutMs / 1000} s: $what")
    }

    @Test
    fun `the data folder is where Linux keeps one`() {
        linux()
        val xdg = System.getenv("XDG_DATA_HOME")
        assertNotNull(xdg, "the test task sets XDG_DATA_HOME")
        assertEquals(File(xdg, "Lucense").absolutePath, AppDirs.root.absolutePath)
    }

    @Test
    fun `the desktop sees Lucense through MPRIS`() {
        linux()
        assumeTrue(System.getenv("DBUS_SESSION_BUS_ADDRESS") != null, "needs a session bus")
        assumeTrue(Platform.onPath("playerctl") != null, "needs playerctl")
        val calls = Collections.synchronizedList(mutableListOf<String>())
        var position = 12_000L
        val mpris = Mpris(object : Mpris.Actions {
            override fun playPause() { calls += "playPause" }
            override fun play() { calls += "play" }
            override fun pause() { calls += "pause" }
            override fun next() { calls += "next" }
            override fun previous() { calls += "previous" }
            override fun stop() { calls += "stop" }
            override fun seekTo(ms: Long) { calls += "seekTo $ms"; position = ms }
            override fun positionMs(): Long = position
        })
        assertTrue(mpris.start(), "announced on the session bus")
        try {
            mpris.update(
                Mpris.NowPlaying(
                    key = "/music/Alan Walker - Faded.mp3", title = "Faded", artist = "Alan Walker",
                    album = "Different World", lengthMs = 212_000, playing = true, artUrl = "file:///tmp/cover.png"
                ),
                position
            )
            eventually("playerctl lists Lucense") { run("playerctl", "-l").orEmpty().lines().any { it.startsWith("lucense") } }
            assertEquals("Faded", run("playerctl", "-p", "lucense", "metadata", "xesam:title"))
            assertEquals("Alan Walker", run("playerctl", "-p", "lucense", "metadata", "xesam:artist"))
            assertEquals("Different World", run("playerctl", "-p", "lucense", "metadata", "xesam:album"))
            assertEquals("212000000", run("playerctl", "-p", "lucense", "metadata", "mpris:length"))
            assertEquals("file:///tmp/cover.png", run("playerctl", "-p", "lucense", "metadata", "mpris:artUrl"))
            assertEquals("Playing", run("playerctl", "-p", "lucense", "status"))
            assertEquals("12.000000", run("playerctl", "-p", "lucense", "position"))

            run("playerctl", "-p", "lucense", "play-pause")
            run("playerctl", "-p", "lucense", "next")
            run("playerctl", "-p", "lucense", "previous")
            run("playerctl", "-p", "lucense", "position", "30")
            eventually("the four calls arrive") { calls.size >= 4 }
            assertEquals(listOf("playPause", "next", "previous", "seekTo 30000"), calls.toList())

            mpris.update(Mpris.NowPlaying(key = "/music/Alan Walker - Faded.mp3", title = "Faded", playing = false, lengthMs = 212_000), position)
            eventually("paused shows") { run("playerctl", "-p", "lucense", "status") == "Paused" }
            mpris.update(Mpris.NowPlaying(), 0)
            eventually("stopped shows") { run("playerctl", "-p", "lucense", "status") == "Stopped" }
        } finally {
            mpris.stop()
        }
        eventually("gone once stopped") { run("playerctl", "-l").orEmpty().lines().none { it.startsWith("lucense") } }
    }

    @Test
    fun `Discord gets the song over its Unix socket`() {
        linux()
        val socket = File("/tmp/discord-ipc-0")
        assumeTrue(!socket.exists(), "a Discord is already listening here")
        val frames = Collections.synchronizedList(mutableListOf<String>())
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(socket.toPath())) }
        val serving = Thread {
            runCatching {
                server.accept().use { client ->
                    while (true) {
                        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                        while (header.hasRemaining()) if (client.read(header) < 0) return@use
                        header.flip()
                        val op = header.int
                        val body = ByteBuffer.allocate(header.int)
                        while (body.hasRemaining()) if (client.read(body) < 0) return@use
                        frames += "$op " + String(body.array(), Charsets.UTF_8)
                        val reply = if (op == 0) "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\",\"data\":{}}" else "{\"cmd\":\"SET_ACTIVITY\",\"evt\":null,\"data\":{}}"
                        client.write(frame(1, reply))
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        val presence = DiscordPresence()
        try {
            presence.start("123456789012345678")
            eventually("connected") { presence.connected }
            presence.show("Faded", "Alan Walker", System.currentTimeMillis(), System.currentTimeMillis() + 212_000, playing = true)
            eventually("the song is sent") { frames.any { it.startsWith("1 ") && "Faded" in it && "Alan Walker" in it } }
            assertTrue(frames.first().startsWith("0 ") && "123456789012345678" in frames.first(), "the handshake names the application")
        } finally {
            presence.stop()
            server.close()
            serving.interrupt()
            socket.delete()
        }
    }

    private fun frame(op: Int, json: String): ByteBuffer {
        val body = json.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(8 + body.size).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.size).put(body).flip()
    }

    /** A page on this machine that sets a cookie: what the sign-in window is checked against, instead of Google. */
    private fun cookiePage(value: String, hits: AtomicInteger): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                hits.incrementAndGet()
                exchange.responseHeaders.add("Set-Cookie", "lucensecheck=$value; Max-Age=86400; Path=/")
                val body = "<html><title>check</title><body>ok</body></html>".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

    private fun running(profile: File): Int = File("/proc").listFiles { f -> f.name.all(Char::isDigit) }.orEmpty().count { folder ->
        runCatching { File(folder, "cmdline").readText() }.getOrDefault("").contains(profile.absolutePath)
    }

    @Test
    fun `a browser is found to sign in with`() {
        linux()
        val browser = OwnSignIn.browser()
        println("Sign in here would use: $browser")
        assertNotNull(browser)
    }

    @Test
    fun `the Chrome sign-in window has no debugging port and hands over its cookies`() = runBlocking {
        linux()
        assumeTrue(System.getenv("DISPLAY") != null, "needs a display")
        val exe = listOf("google-chrome", "google-chrome-stable", "chromium", "chromium-browser").firstNotNullOfOrNull { Platform.onPath(it) }
        assumeTrue(exe != null, "needs Chrome or Chromium")
        val profile = File(System.getProperty("java.io.tmpdir"), "lucense-chrome-${System.nanoTime()}").apply { mkdirs() }
        val value = "v${System.nanoTime()}"
        val hits = AtomicInteger()
        val page = cookiePage(value, hits)
        try {
            val window = OwnSignIn.PlainWindow.open(exe!!, profile, "http://127.0.0.1:${page.address.port}/", null, offscreen = true)
            assertTrue(window.open(), "the window runs with its own profile")
            withTimeoutOrNull(30_000) { while (hits.get() == 0) delay(200) }
            assertTrue(hits.get() > 0, "it loads the page")
            delay(1_500)
            assertTrue(!File(profile, "DevToolsActivePort").exists(), "no debugging connection is open")
            window.closeGently()
            assertTrue(!window.open(), "Done closes it")

            val reader = OwnSignIn.ChromiumWindow.open(exe, profile, "about:blank", null, hidden = true)
            val cookies = reader.cookies().orEmpty()
            reader.close()
            val found = cookies.firstOrNull { "\tlucensecheck\t" in it }
            assertTrue(found?.endsWith("\t$value") == true, "the reader finds the cookie: ${found ?: "${cookies.size} cookies, not it"}")
            delay(1_000)
            assertEquals(0, running(profile), "nothing is left running")
        } finally {
            page.stop(0)
            profile.deleteRecursively()
        }
    }

    @Test
    fun `the Firefox sign-in window hands over its cookies`() = runBlocking {
        linux()
        assumeTrue(System.getenv("DISPLAY") != null, "needs a display")
        val exe = Platform.onPath("firefox")
        assumeTrue(exe != null, "needs Firefox")
        val profile = File(System.getProperty("java.io.tmpdir"), "lucense-firefox-${System.nanoTime()}").apply { mkdirs() }
        val value = "v${System.nanoTime()}"
        val hits = AtomicInteger()
        val page = cookiePage(value, hits)
        try {
            val window = OwnSignIn.FirefoxWindow.open(exe!!, profile, "http://127.0.0.1:${page.address.port}/", null)
            withTimeoutOrNull(60_000) { while (hits.get() == 0) delay(200) }
            assertTrue(hits.get() > 0, "it loads the page")
            assertTrue(window.open(), "the window is there")
            // Firefox writes its cookies down a moment after it gets them.
            var found: String? = null
            withTimeoutOrNull(30_000) {
                while (found == null) {
                    found = window.cookies().orEmpty().firstOrNull { "\tlucensecheck\t" in it }
                    if (found == null) delay(500)
                }
            }
            assertTrue(found?.endsWith("\t$value") == true, "the cookie is read from its profile: $found")
            window.close()
            delay(1_000)
            assertEquals(0, running(profile), "nothing is left running")
        } finally {
            page.stop(0)
            profile.deleteRecursively()
        }
    }
}
