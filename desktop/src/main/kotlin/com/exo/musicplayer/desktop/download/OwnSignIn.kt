package com.exo.musicplayer.desktop.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * YouTube signed in to once, in a browser window of Lucense's own.
 *
 * A browser's own sign-in won't do for long: YouTube cycles a session's
 * cookies while it is used, and copies taken from a browser in daily use stop
 * working soon after. A session nothing else touches isn't cycled. So this
 * opens a browser from the Chromium family - Edge, which every Windows has,
 * or Chrome or Brave - with a profile of its own, at YouTube's sign-in. Once
 * the account is signed in, its cookies are read over the browser's local
 * debugging connection (Chrome-family browsers encrypt them on disk so no
 * other program can), the window is closed and its profile deleted. What is
 * left is Lucense's copy, used only by yt-dlp.
 *
 * The browser is started as it would be by hand, with a debugging port on
 * this PC only and nothing driving its pages, so YouTube's sign-in sees an
 * ordinary browser.
 */
object OwnSignIn {

    sealed interface Outcome {
        /** Signed in and kept. */
        data object Kept : Outcome
        /** The window was closed before signing in, or Stop was pressed. */
        data object Stopped : Outcome
        data class Failed(val why: String) : Outcome
    }

    /** The Chromium-family browser to sign in with: Edge first, since every Windows has it. */
    fun browser(): File? {
        val programs = listOfNotNull(System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles"), System.getenv("LOCALAPPDATA"))
        val candidates = listOf(
            "Microsoft/Edge/Application/msedge.exe",
            "Google/Chrome/Application/chrome.exe",
            "BraveSoftware/Brave-Browser/Application/brave.exe",
            "Vivaldi/Application/vivaldi.exe"
        )
        return candidates.firstNotNullOfOrNull { path -> programs.map { File(it, path) }.firstOrNull { it.isFile } }
    }

    @Volatile private var stopRequested = false

    /** Ends a sign-in under way: the window closes and nothing is kept. */
    fun stop() {
        stopRequested = true
    }

    /**
     * Opens the window at YouTube's sign-in and waits for the account to be
     * signed in, then keeps its cookies as [YouTubeSignIn]'s. [proxy] is the
     * route Lucense goes by, so the sign-in comes from the same address as the
     * downloads; [onStatus] hears what is happening.
     */
    suspend fun signIn(proxy: String?, onStatus: (String) -> Unit): Outcome = withContext(Dispatchers.IO) {
        stopRequested = false
        val exe = browser() ?: return@withContext Outcome.Failed(
            "No Edge, Chrome or Brave was found to sign in with. Use Firefox or a cookies.txt file instead."
        )
        val profile = YouTubeSignIn.windowProfile
        profile.deleteRecursively()
        profile.mkdirs()
        val window = runCatching { Window.open(exe, profile, SIGN_IN_URL, proxy, headless = false) }.getOrElse {
            profile.deleteRecursively()
            return@withContext Outcome.Failed("${exe.nameWithoutExtension} didn't open: ${it.message}")
        }
        try {
            onStatus("A ${browserName(exe)} window opened. Sign in to YouTube there; it closes by itself once you have.")
            waitForSignIn(window, onStatus)
        } finally {
            window.close()
            // The profile held the session too; only Lucense's copy is left.
            repeat(10) {
                if (!profile.exists() || profile.deleteRecursively()) return@repeat
                delay(500)
            }
        }
    }

    private suspend fun waitForSignIn(window: Window, onStatus: (String) -> Unit): Outcome {
        val deadline = System.currentTimeMillis() + GIVE_UP_MS
        var withoutPages = 0
        while (true) {
            if (stopRequested || !window.alive) return Outcome.Stopped
            if (System.currentTimeMillis() > deadline) {
                return Outcome.Failed("Nobody signed in for half an hour, so the window was closed.")
            }
            val cookies = window.cookies()
            if (cookies != null && signedIn(cookies)) {
                onStatus("Signed in. Keeping the sign-in…")
                // YouTube sets its last cookies a moment after the account's.
                delay(SETTLE_MS)
                val final = window.cookies() ?: cookies
                return if (YouTubeSignIn.keepLines(netscape(final), YouTubeSignIn.Source.OWN)) {
                    Outcome.Kept
                } else {
                    Outcome.Failed("The sign-in finished without YouTube's cookies. Try again.")
                }
            }
            // Every tab closed, though the browser may linger in the background.
            withoutPages = if (window.pages() == 0) withoutPages + 1 else 0
            if (withoutPages >= 2) return Outcome.Stopped
            delay(POLL_MS)
        }
    }

    private fun browserName(exe: File): String = when (exe.nameWithoutExtension.lowercase()) {
        "msedge" -> "Microsoft Edge"
        "chrome" -> "Chrome"
        "brave" -> "Brave"
        "vivaldi" -> "Vivaldi"
        else -> "browser"
    }

    /** Signed in to YouTube, not only to Google: YouTube's own copy of the account cookie is there. */
    private fun signedIn(cookies: JSONArray): Boolean = (0 until cookies.length()).any { index ->
        val cookie = cookies.optJSONObject(index) ?: return@any false
        cookie.optString("domain").endsWith("youtube.com") &&
            cookie.optString("name") in setOf("SAPISID", "__Secure-3PAPISID")
    }

    /** The browser's cookies as cookies.txt lines, the format yt-dlp reads. */
    fun netscape(cookies: JSONArray): List<String> = (0 until cookies.length()).mapNotNull { index ->
        val cookie = cookies.optJSONObject(index) ?: return@mapNotNull null
        val domain = cookie.optString("domain").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val expires = cookie.optDouble("expires", -1.0)
        listOf(
            (if (cookie.optBoolean("httpOnly")) "#HttpOnly_" else "") + domain,
            if (domain.startsWith(".")) "TRUE" else "FALSE",
            cookie.optString("path", "/"),
            if (cookie.optBoolean("secure")) "TRUE" else "FALSE",
            if (cookie.optBoolean("session") || expires <= 0) "0" else expires.toLong().toString(),
            cookie.optString("name"),
            cookie.optString("value")
        ).joinToString("\t")
    }

    /**
     * A browser started with a profile of its own and a debugging port, and the
     * connection to it. The port is the browser's own pick, read from the file
     * it writes into the profile, so nothing else on the PC is in the way.
     */
    class Window private constructor(
        private val process: Process,
        private val socket: WebSocket,
        private val replies: ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>
    ) {
        private val ids = AtomicInteger()

        val alive: Boolean get() = process.isAlive

        /** Every cookie the browser holds, or null when it didn't answer. */
        suspend fun cookies(): JSONArray? = call("Storage.getCookies")?.optJSONObject("result")?.optJSONArray("cookies")

        /** How many tabs are open. */
        suspend fun pages(): Int = call("Target.getTargets")?.optJSONObject("result")?.optJSONArray("targetInfos")
            ?.let { infos -> (0 until infos.length()).count { infos.optJSONObject(it)?.optString("type") == "page" } } ?: 0

        private suspend fun call(method: String, params: JSONObject = JSONObject()): JSONObject? {
            if (!process.isAlive) return null
            val id = ids.incrementAndGet()
            val reply = CompletableDeferred<JSONObject>()
            replies[id] = reply
            return runCatching {
                socket.sendText(JSONObject().put("id", id).put("method", method).put("params", params).toString(), true).await()
                withTimeoutOrNull(CALL_TIMEOUT_MS) { reply.await() }
            }.getOrNull().also { replies.remove(id) }
        }

        /** Closes the browser, asking first and insisting after. */
        suspend fun close() {
            runCatching { withTimeoutOrNull(3_000) { call("Browser.close") } }
            runCatching { socket.abort() }
            withContext(Dispatchers.IO) {
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.descendants().forEach { it.destroyForcibly() }
                    process.destroyForcibly()
                    process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
        }

        companion object {
            /** Starts [exe] on [url] with [profile]; [headless] only for checking this works without a window. */
            suspend fun open(exe: File, profile: File, url: String, proxy: String?, headless: Boolean): Window {
                val command = buildList {
                    add(exe.absolutePath)
                    add("--user-data-dir=${profile.absolutePath}")
                    add("--remote-debugging-port=0")
                    add("--no-first-run")
                    add("--no-default-browser-check")
                    add("--disable-sync")
                    if (headless) add("--headless=new")
                    // Chrome takes socks5:// where yt-dlp says socks5h://; the name is resolved through it either way.
                    proxy?.let { add("--proxy-server=" + it.replace("socks5h://", "socks5://").replace("socks4a://", "socks4://")) }
                    add("--new-window")
                    add(url)
                }
                val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                try {
                    // The browser writes the port it chose into the profile once it listens.
                    val portFile = File(profile, "DevToolsActivePort")
                    val port = withTimeoutOrNull(STARTUP_MS) {
                        while (true) {
                            portFile.takeIf { it.isFile }?.readLines()?.firstOrNull()?.trim()?.toIntOrNull()?.let { return@withTimeoutOrNull it }
                            if (!process.isAlive) error("it closed straight away")
                            delay(200)
                        }
                        @Suppress("UNREACHABLE_CODE")
                        null
                    } ?: error("it didn't start in time")
                    // Straight to this PC: Lucense's proxy is for the internet, not for localhost.
                    val version = withContext(Dispatchers.IO) {
                        URL("http://127.0.0.1:$port/json/version").openConnection(Proxy.NO_PROXY).getInputStream().use { it.readBytes().decodeToString() }
                    }
                    val address = JSONObject(version).getString("webSocketDebuggerUrl")
                    val replies = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
                    val socket = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build()
                        .newWebSocketBuilder()
                        .buildAsync(URI(address), Replies(replies))
                        .await()
                    return Window(process, socket, replies)
                } catch (failure: Throwable) {
                    process.descendants().forEach { it.destroyForcibly() }
                    process.destroyForcibly()
                    throw failure
                }
            }
        }
    }

    /** Collects the browser's answers, a message at a time, and hands each to whoever asked. */
    private class Replies(private val waiting: ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>) : WebSocket.Listener {
        private val partial = StringBuilder()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            partial.append(data)
            if (last) {
                val message = runCatching { JSONObject(partial.toString()) }.getOrNull()
                partial.setLength(0)
                message?.optInt("id", -1)?.takeIf { it >= 0 }?.let { waiting.remove(it)?.complete(message) }
            }
            webSocket.request(1)
            return null
        }
    }

    /** YouTube's sign-in, landing back on YouTube: the same address the phone's sign-in uses. */
    const val SIGN_IN_URL =
        "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true" +
            "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26next%3Dhttps%253A%252F%252Fwww.youtube.com%252F"

    private const val POLL_MS = 1_500L
    private const val SETTLE_MS = 3_000L
    private const val CALL_TIMEOUT_MS = 5_000L
    private const val STARTUP_MS = 30_000L
    private const val GIVE_UP_MS = 30L * 60 * 1000
}
