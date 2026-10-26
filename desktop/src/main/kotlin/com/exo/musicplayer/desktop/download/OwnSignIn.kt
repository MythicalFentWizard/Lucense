package com.exo.musicplayer.desktop.download

import com.exo.musicplayer.desktop.system.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
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
import java.sql.DriverManager
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * YouTube signed in to once, in the default browser, with a profile of
 * Lucense's own.
 *
 * A browser's own sign-in won't do for long: YouTube cycles a session's
 * cookies while it is used, and copies taken from a browser in daily use stop
 * working soon after. A session nothing else touches isn't cycled. So this
 * opens the default browser - started apart from the one in daily use, with a
 * profile of its own - at YouTube's sign-in. Once the account is signed in its
 * cookies are read, the window is closed and the profile deleted. What is left
 * is Lucense's copy, used only by yt-dlp.
 *
 * Chrome-family browsers (Opera, Opera GX, Chrome, Edge, Brave, Vivaldi)
 * encrypt their cookies on disk so no other program can read them; they are
 * read over the browser's local debugging connection instead. Google's
 * sign-in refuses a browser that has that connection open ("This browser or
 * app may not be secure"), so the window signed in with has none: it is the
 * browser as started by hand. Once it is closed, the same profile is opened
 * again unseen, on a blank page with nothing of Google's loaded, and read.
 * Firefox keeps its cookies readable in its profile, and is watched as it goes.
 */
object OwnSignIn {

    sealed interface Outcome {
        /** Signed in and kept. */
        data object Kept : Outcome
        /** Stop was pressed. */
        data object Stopped : Outcome
        /** The window was closed, or Done pressed, without YouTube signed in to. */
        data object NotSignedIn : Outcome
        data class Failed(val why: String) : Outcome
    }

    enum class Family { CHROMIUM, FIREFOX }

    /** A browser to sign in with: its program and how its cookies are read. */
    data class Browser(val name: String, val exe: File, val family: Family)

    /**
     * The default browser, as Windows opens a web link; Edge, or another
     * Chrome-family browser, when the default is one this can't use.
     */
    fun browser(): Browser? =
        if (Platform.isWindows) defaultBrowser() ?: fallback() else linuxDefaultBrowser() ?: linuxFallback()

    private fun defaultBrowser(): Browser? = runCatching {
        val progId = Advapi32Util.registryGetStringValue(
            WinReg.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\https\\UserChoice",
            "ProgId"
        )
        val command = Advapi32Util.registryGetStringValue(WinReg.HKEY_CLASSES_ROOT, "$progId\\shell\\open\\command", "")
        // The program is the first part: quoted, or up to the first space.
        val exe = File(
            if (command.startsWith("\"")) command.drop(1).substringBefore('"') else command.substringBefore(' ')
        ).takeIf { it.isFile } ?: return null
        describe(exe)
    }.getOrNull()

    private fun fallback(): Browser? {
        val programs = listOfNotNull(System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles"), System.getenv("LOCALAPPDATA"))
        val candidates = listOf(
            "Microsoft/Edge/Application/msedge.exe",
            "Google/Chrome/Application/chrome.exe",
            "BraveSoftware/Brave-Browser/Application/brave.exe",
            "Vivaldi/Application/vivaldi.exe"
        )
        return candidates.firstNotNullOfOrNull { path -> programs.map { File(it, path) }.firstOrNull { it.isFile } }?.let(::describe)
    }

    /**
     * Linux's default browser: the menu entry xdg-settings names, and the
     * program its Exec line starts. Null where there is none this can use - a
     * Flatpak, whose browser can't be given a profile from outside it.
     */
    private fun linuxDefaultBrowser(): Browser? = runCatching {
        val tool = Platform.onPath("xdg-settings") ?: return null
        val process = ProcessBuilder(tool.path, "get", "default-web-browser").redirectErrorStream(true).start()
        val entry = process.inputStream.bufferedReader().readText().trim()
        process.waitFor(5, TimeUnit.SECONDS)
        if (!entry.endsWith(".desktop")) return null
        val home = System.getProperty("user.home")
        val dataDirs = listOfNotNull(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share") +
            (System.getenv("XDG_DATA_DIRS")?.takeIf { it.isNotBlank() } ?: "/usr/local/share:/usr/share").split(':') +
            listOf("/var/lib/snapd/desktop", "/var/lib/flatpak/exports/share", "$home/.local/share/flatpak/exports/share")
        val desktopFile = dataDirs.map { File(it, "applications/$entry") }.firstOrNull { it.isFile } ?: return null
        val exec = desktopFile.readLines().asSequence()
            .dropWhile { it.trim() != "[Desktop Entry]" }
            .firstOrNull { it.startsWith("Exec=") }
            ?.removePrefix("Exec=") ?: return null
        // The program: the first word that isn't env or a VARIABLE=value for it.
        val program = exec.split(' ').map { it.trim('"') }
            .firstOrNull { it.isNotBlank() && it != "env" && !it.contains('=') } ?: return null
        val exe = if (program.startsWith("/")) File(program) else Platform.onPath(program) ?: return null
        describe(exe)
    }.getOrNull()

    /** A Chrome-family browser or Firefox that is installed, when there is no default to use. */
    private fun linuxFallback(): Browser? = listOf(
        "google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "microsoft-edge",
        "microsoft-edge-stable", "brave-browser", "brave", "vivaldi", "vivaldi-stable", "opera", "firefox"
    ).firstNotNullOfOrNull { name -> Platform.onPath(name)?.let(::describe) }

    /**
     * Where [browser] keeps the sign-in window's profile. A snap browser can't
     * reach hidden folders like Lucense's own, so it gets one in its own.
     */
    private fun profileFor(browser: Browser): File {
        val path = browser.exe.path
        if (Platform.isLinux && path.startsWith("/snap/")) {
            val snap = path.removePrefix("/snap/bin/").substringBefore('/').substringBefore('.')
            return File(System.getProperty("user.home"), "snap/$snap/common/lucense-sign-in")
        }
        return YouTubeSignIn.windowProfile
    }

    /** What [exe] is, by its name and folder; null for a browser this can't read. */
    private fun describe(exe: File): Browser? {
        if (!Platform.isWindows) return describeOnLinux(exe)
        val name = exe.nameWithoutExtension.lowercase()
        val where = exe.parentFile?.path.orEmpty().lowercase()
        return when {
            name == "firefox" -> Browser("Firefox", exe, Family.FIREFOX)
            name == "msedge" -> Browser("Microsoft Edge", exe, Family.CHROMIUM)
            name == "chrome" -> Browser("Chrome", exe, Family.CHROMIUM)
            name == "brave" -> Browser("Brave", exe, Family.CHROMIUM)
            name == "vivaldi" -> Browser("Vivaldi", exe, Family.CHROMIUM)
            name == "opera" || name == "launcher" -> Browser(
                if ("opera gx" in where) "Opera GX" else "Opera",
                // Opera's launcher hands a link to an Opera already open, ignoring the
                // profile it was given; the browser itself, started directly, keeps apart.
                File(exe.parentFile, "opera.exe").takeIf { it.isFile } ?: exe,
                Family.CHROMIUM
            )
            else -> null
        }
    }

    private fun describeOnLinux(exe: File): Browser? {
        if (!exe.isFile) return null
        val name = exe.name.lowercase()
        return when {
            name.startsWith("firefox") -> Browser("Firefox", exe, Family.FIREFOX)
            name.startsWith("microsoft-edge") || name == "msedge" -> Browser("Microsoft Edge", exe, Family.CHROMIUM)
            name.startsWith("google-chrome") || name == "chrome" -> Browser("Chrome", exe, Family.CHROMIUM)
            name.startsWith("chromium") -> Browser("Chromium", exe, Family.CHROMIUM)
            name.startsWith("brave") -> Browser("Brave", exe, Family.CHROMIUM)
            name.startsWith("vivaldi") -> Browser("Vivaldi", exe, Family.CHROMIUM)
            name.startsWith("opera") -> Browser("Opera", exe, Family.CHROMIUM)
            else -> null
        }
    }

    @Volatile private var stopRequested = false
    @Volatile private var doneRequested = false

    /** Ends a sign-in under way: the window closes and nothing is kept. */
    fun stop() {
        stopRequested = true
    }

    /** Done signing in: the window is closed for the sign-in in it to be read, as if closed by hand. */
    fun done() {
        doneRequested = true
    }

    /**
     * Opens the window at YouTube's sign-in and waits for the account to be
     * signed in, then keeps its cookies as [YouTubeSignIn]'s. [proxy] is the
     * route Lucense goes by, so the sign-in comes from the same address as the
     * downloads; [onStatus] hears what is happening.
     */
    suspend fun signIn(proxy: String?, onStatus: (String) -> Unit): Outcome = withContext(Dispatchers.IO) {
        stopRequested = false
        doneRequested = false
        val browser = browser() ?: return@withContext Outcome.Failed(
            "No browser was found to sign in with. Use a cookies.txt file instead."
        )
        val profile = profileFor(browser)
        if (profile.exists()) endProcessesUsing(profile)
        profile.deleteRecursively()
        profile.mkdirs()
        try {
            when (browser.family) {
                Family.CHROMIUM -> chromiumSignIn(browser, profile, proxy, onStatus)
                Family.FIREFOX -> firefoxSignIn(browser, profile, proxy, onStatus)
            }
        } finally {
            // The profile held the session too; only Lucense's copy is left.
            repeat(20) {
                if (!profile.exists() || profile.deleteRecursively()) return@repeat
                delay(500)
            }
        }
    }

    /**
     * A Chrome-family browser: signed in to as an ordinary window, then read
     * unseen once that is closed - by hand, or by Done.
     */
    private suspend fun chromiumSignIn(browser: Browser, profile: File, proxy: String?, onStatus: (String) -> Unit): Outcome {
        val window = runCatching { PlainWindow.open(browser.exe, profile, SIGN_IN_URL, proxy) }.getOrElse {
            endProcessesUsing(profile)
            return Outcome.Failed("${browser.name} didn't open: ${it.message}")
        }
        try {
            onStatus(
                "${browser.name} opened in a window of its own, apart from your usual one. Sign in to YouTube " +
                    "there, then close that window, or press Done here."
            )
            val deadline = System.currentTimeMillis() + GIVE_UP_MS
            while (window.open()) {
                if (stopRequested) return Outcome.Stopped
                if (doneRequested) {
                    onStatus("Closing ${browser.name}…")
                    window.closeGently()
                    break
                }
                if (System.currentTimeMillis() > deadline) {
                    return Outcome.Failed("Nobody signed in for half an hour, so the window was closed.")
                }
                delay(POLL_MS)
            }
        } finally {
            // Whatever is left of it, now: the profile is about to be read, or deleted.
            endProcessesUsing(profile)
        }

        onStatus("Reading the sign-in…")
        // The same profile, unseen, on a blank page: nothing of Google's is loaded to see it.
        val reader = runCatching { ChromiumWindow.open(browser.exe, profile, "about:blank", proxy, hidden = true) }.getOrElse {
            return Outcome.Failed("${browser.name} didn't open again to hand over the sign-in: ${it.message}")
        }
        try {
            val cookies = reader.cookies() ?: return Outcome.Failed("${browser.name} didn't hand over the sign-in. Try again.")
            if (!signedIn(cookies)) return Outcome.NotSignedIn
            return if (YouTubeSignIn.keepLines(cookies, YouTubeSignIn.Source.OWN)) {
                Outcome.Kept
            } else {
                Outcome.Failed("The sign-in finished without YouTube's cookies. Try again.")
            }
        } finally {
            reader.close()
        }
    }

    /** Firefox: its cookies are readable as it goes, so the window closes by itself once signed in. */
    private suspend fun firefoxSignIn(browser: Browser, profile: File, proxy: String?, onStatus: (String) -> Unit): Outcome {
        val window = runCatching { FirefoxWindow.open(browser.exe, profile, SIGN_IN_URL, proxy) }.getOrElse {
            return Outcome.Failed("${browser.name} didn't open: ${it.message}")
        }
        try {
            onStatus(
                "${browser.name} opened in a window of its own, apart from your usual one. Sign in to YouTube " +
                    "there; it closes by itself once you have."
            )
            return waitForSignIn(window, onStatus)
        } finally {
            window.close()
        }
    }

    private suspend fun waitForSignIn(window: Window, onStatus: (String) -> Unit): Outcome {
        val deadline = System.currentTimeMillis() + GIVE_UP_MS
        var gone = 0
        while (true) {
            if (stopRequested) return Outcome.Stopped
            if (System.currentTimeMillis() > deadline) {
                return Outcome.Failed("Nobody signed in for half an hour, so the window was closed.")
            }
            val cookies = window.cookies()
            if (doneRequested && (cookies == null || !signedIn(cookies))) return Outcome.NotSignedIn
            if (cookies != null && signedIn(cookies)) {
                onStatus("Signed in. Keeping the sign-in…")
                // YouTube sets its last cookies a moment after the account's.
                delay(SETTLE_MS)
                val final = window.cookies() ?: cookies
                return if (YouTubeSignIn.keepLines(final, YouTubeSignIn.Source.OWN)) {
                    Outcome.Kept
                } else {
                    Outcome.Failed("The sign-in finished without YouTube's cookies. Try again.")
                }
            }
            // Closed by hand, though a browser may linger in the background.
            gone = if (window.open()) 0 else gone + 1
            if (gone >= 2) return Outcome.NotSignedIn
            delay(POLL_MS)
        }
    }

    /** Signed in to YouTube, not only to Google: YouTube's own copy of the account cookie is there. */
    private fun signedIn(lines: List<String>): Boolean = lines.any { line ->
        val fields = line.removePrefix("#HttpOnly_").split('\t')
        fields.size >= 7 && fields[0].endsWith("youtube.com") && fields[5] in setOf("SAPISID", "__Secure-3PAPISID")
    }

    /** A browser window being signed in with, whichever kind. */
    interface Window {
        /** The browser's cookies as cookies.txt lines, or null when it didn't say. */
        suspend fun cookies(): List<String>?

        /** Whether the window is still there to sign in with. */
        suspend fun open(): Boolean

        suspend fun close()
    }

    /** A cookies.txt line, the format yt-dlp reads. */
    private fun line(domain: String, path: String, secure: Boolean, httpOnly: Boolean, expires: Long, name: String, value: String): String =
        listOf(
            (if (httpOnly) "#HttpOnly_" else "") + domain,
            if (domain.startsWith(".")) "TRUE" else "FALSE",
            path.ifEmpty { "/" },
            if (secure) "TRUE" else "FALSE",
            expires.coerceAtLeast(0).toString(),
            name,
            value
        ).joinToString("\t")

    /** The browser's cookies, from the debugging connection's answer, as cookies.txt lines. */
    fun netscape(cookies: JSONArray): List<String> = (0 until cookies.length()).mapNotNull { index ->
        val cookie = cookies.optJSONObject(index) ?: return@mapNotNull null
        val domain = cookie.optString("domain").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val expires = cookie.optDouble("expires", -1.0)
        line(
            domain,
            cookie.optString("path", "/"),
            cookie.optBoolean("secure"),
            cookie.optBoolean("httpOnly"),
            if (cookie.optBoolean("session") || expires <= 0) 0 else expires.toLong(),
            cookie.optString("name"),
            cookie.optString("value")
        )
    }

    /**
     * A Chrome-family browser started with a profile of its own and a
     * debugging port, and the connection to it. The port is the browser's own
     * pick, read from the file it writes into the profile. Some browsers start
     * through a launcher that hands over and exits - Opera does - so the
     * browser is followed by its connection, not by the program started.
     */
    class ChromiumWindow private constructor(
        private val profile: File,
        private val socket: WebSocket,
        private val replies: ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>,
        private val listener: Replies
    ) : Window {
        private val ids = AtomicInteger()

        override suspend fun cookies(): List<String>? =
            call("Storage.getCookies")?.optJSONObject("result")?.optJSONArray("cookies")?.let(::netscape)

        override suspend fun open(): Boolean {
            if (listener.closed) return false
            val infos = call("Target.getTargets")?.optJSONObject("result")?.optJSONArray("targetInfos") ?: return false
            return (0 until infos.length()).any { infos.optJSONObject(it)?.optString("type") == "page" }
        }

        /** How many tabs are open; for checking. */
        suspend fun pages(): Int = call("Target.getTargets")?.optJSONObject("result")?.optJSONArray("targetInfos")
            ?.let { infos -> (0 until infos.length()).count { infos.optJSONObject(it)?.optString("type") == "page" } } ?: 0

        private suspend fun call(method: String, params: JSONObject = JSONObject()): JSONObject? {
            if (listener.closed) return null
            val id = ids.incrementAndGet()
            val reply = CompletableDeferred<JSONObject>()
            replies[id] = reply
            return runCatching {
                socket.sendText(JSONObject().put("id", id).put("method", method).put("params", params).toString(), true).await()
                withTimeoutOrNull(CALL_TIMEOUT_MS) { reply.await() }
            }.getOrNull().also { replies.remove(id) }
        }

        /** Closes the browser: asked first, then its processes ended if it is still there. */
        override suspend fun close() {
            val pid = call("SystemInfo.getProcessInfo")?.optJSONObject("result")?.optJSONArray("processInfo")
                ?.let { infos -> (0 until infos.length()).mapNotNull { infos.optJSONObject(it) }.firstOrNull { it.optString("type") == "browser" } }
                ?.optLong("id")?.takeIf { it > 0 }
            runCatching { withTimeoutOrNull(3_000) { call("Browser.close") } }
            runCatching { socket.abort() }
            withContext(Dispatchers.IO) {
                val browser = pid?.let { ProcessHandle.of(it).orElse(null) }
                if (browser != null) {
                    runCatching { browser.onExit().get(10, TimeUnit.SECONDS) }
                    if (browser.isAlive) {
                        browser.descendants().forEach { it.destroyForcibly() }
                        browser.destroyForcibly()
                    }
                }
                // Anything still holding the profile: helpers, or a browser its launcher left.
                endProcessesUsing(profile)
            }
        }

        companion object {
            /** Starts [exe] on [url] with [profile]; [hidden] only for checking this works without a window. */
            suspend fun open(exe: File, profile: File, url: String, proxy: String?, hidden: Boolean): ChromiumWindow {
                val command = buildList {
                    add(exe.absolutePath)
                    add("--user-data-dir=${profile.absolutePath}")
                    add("--remote-debugging-port=0")
                    add("--no-first-run")
                    add("--no-default-browser-check")
                    add("--disable-sync")
                    if (Platform.isLinux) add("--password-store=basic")
                    if (hidden) {
                        add("--headless=new")
                        // Off the screen, for a browser that starts a window all the same.
                        add("--window-position=-4000,-4000")
                    }
                    // Chrome takes socks5:// where yt-dlp says socks5h://; the name is resolved through it either way.
                    proxy?.let { add("--proxy-server=" + it.replace("socks5h://", "socks5://").replace("socks4a://", "socks4://")) }
                    add("--new-window")
                    add(url)
                }
                ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                try {
                    // The browser writes the port it chose into the profile once it listens.
                    val portFile = File(profile, "DevToolsActivePort")
                    val port = withTimeoutOrNull(STARTUP_MS) {
                        var found: Int? = null
                        while (found == null) {
                            found = portFile.takeIf { it.isFile }?.readLines()?.firstOrNull()?.trim()?.toIntOrNull()
                            if (found == null) delay(200)
                        }
                        found
                    } ?: error("it didn't start in time")
                    // Straight to this PC: Lucense's proxy is for the internet, not for localhost.
                    val version = withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        URL("http://127.0.0.1:$port/json/version").openConnection(Proxy.NO_PROXY).getInputStream().use { it.readBytes().decodeToString() }
                    }
                    val address = JSONObject(version).getString("webSocketDebuggerUrl")
                    val replies = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
                    val listener = Replies(replies)
                    val socket = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build()
                        .newWebSocketBuilder()
                        .buildAsync(URI(address), listener)
                        .await()
                    return ChromiumWindow(profile, socket, replies, listener)
                } catch (failure: Throwable) {
                    withContext(Dispatchers.IO) { endProcessesUsing(profile) }
                    throw failure
                }
            }
        }
    }

    /**
     * A Chrome-family browser as started by hand, with a profile of its own and
     * nothing else: no debugging connection, which Google's sign-in refuses.
     * It is followed by the lock it holds on its profile while it runs (the
     * program started may be a launcher that hands over and exits).
     */
    class PlainWindow private constructor(private val profile: File) {

        /** On Windows the browser holds a lockfile in its profile while it runs; on Linux its processes say. */
        fun open(): Boolean = if (Platform.isWindows) File(profile, "lockfile").exists() else processesUsing(profile).isNotEmpty()

        /**
         * Asks the browser to close, as its own close button would, so it saves
         * its cookies on the way out; ended outright afterwards if it hasn't.
         */
        suspend fun closeGently() {
            withContext(Dispatchers.IO) {
                if (!Platform.isWindows) {
                    // SIGTERM, which Chrome-family browsers and Firefox take as quit: they save, then go.
                    processesUsing(profile).filter { it.second.none { arg -> arg.startsWith("--type=") } }
                        .forEach { (pid, _) -> ProcessHandle.of(pid).ifPresent { it.destroy() } }
                    return@withContext
                }
                runCatching {
                    val path = profile.absolutePath.replace("'", "''")
                    ProcessBuilder(
                        "powershell", "-NoProfile", "-NonInteractive", "-Command",
                        // The browser itself: its helpers name their --type. Not this search, whose command line names the profile too.
                        "Get-CimInstance Win32_Process | Where-Object { \$_.ProcessId -ne \$PID -and \$_.CommandLine -and " +
                            "\$_.CommandLine.Contains('$path') -and -not \$_.CommandLine.Contains('--type=') } | " +
                            "ForEach-Object { taskkill /PID \$_.ProcessId | Out-Null }"
                    ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor(20, TimeUnit.SECONDS)
                }
            }
            withTimeoutOrNull(CLOSE_MS) { while (open()) delay(250) }
        }

        companion object {
            /** [offscreen] only for checking this works without a window in the way. */
            suspend fun open(exe: File, profile: File, url: String, proxy: String?, offscreen: Boolean = false): PlainWindow {
                val command = buildList {
                    add(exe.absolutePath)
                    add("--user-data-dir=${profile.absolutePath}")
                    add("--no-first-run")
                    add("--no-default-browser-check")
                    add("--disable-sync")
                    // A profile thrown away afterwards needs no keyring, and asking to unlock one would only get in the way.
                    if (Platform.isLinux) add("--password-store=basic")
                    if (offscreen) add("--window-position=-4000,-4000")
                    proxy?.let { add("--proxy-server=" + it.replace("socks5h://", "socks5://").replace("socks4a://", "socks4://")) }
                    add("--new-window")
                    add(url)
                }
                ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                val window = PlainWindow(profile)
                withTimeoutOrNull(STARTUP_MS) { while (!window.open()) delay(200) } ?: error("it didn't start in time")
                return window
            }
        }
    }

    /**
     * Firefox with a profile of its own: started apart from any Firefox
     * already open, told to wait for its browser so the program started is
     * the browser's life, with first-run pages off and Lucense's proxy set.
     * Its cookies are read from the profile's own database.
     */
    class FirefoxWindow private constructor(private val process: Process, private val profile: File) : Window {

        override suspend fun cookies(): List<String>? = withContext(Dispatchers.IO) {
            val database = File(profile, "cookies.sqlite").takeIf { it.isFile } ?: return@withContext null
            // Copied with its write-ahead log, so the read sees what Firefox has written and doesn't lock it.
            val copy = File(profile.parentFile, "firefox-cookies-${System.nanoTime()}")
            copy.mkdirs()
            try {
                database.copyTo(File(copy, "cookies.sqlite"), overwrite = true)
                File(profile, "cookies.sqlite-wal").takeIf { it.isFile }?.copyTo(File(copy, "cookies.sqlite-wal"), overwrite = true)
                DriverManager.getConnection("jdbc:sqlite:" + File(copy, "cookies.sqlite").absolutePath).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT host, path, isSecure, isHttpOnly, expiry, name, value FROM moz_cookies").use { rows ->
                            buildList {
                                while (rows.next()) {
                                    var expiry = rows.getLong(5)
                                    // Newer Firefox counts it in milliseconds.
                                    if (expiry > 100_000_000_000L) expiry /= 1000
                                    add(line(rows.getString(1), rows.getString(2), rows.getInt(3) != 0, rows.getInt(4) != 0, expiry, rows.getString(6), rows.getString(7)))
                                }
                            }
                        }
                    }
                }
            } catch (failure: Exception) {
                null
            } finally {
                copy.deleteRecursively()
            }
        }

        /**
         * Firefox's launcher may hand over and exit even when asked to wait, so
         * the browser is followed by the lock it holds on its profile instead.
         */
        override suspend fun open(): Boolean = process.isAlive || withContext(Dispatchers.IO) {
            if (!Platform.isWindows) return@withContext processesUsing(profile).isNotEmpty()
            val lock = File(profile, "parent.lock")
            lock.isFile && runCatching { java.io.RandomAccessFile(lock, "rw").close() }.isFailure
        }

        override suspend fun close() {
            withContext(Dispatchers.IO) {
                process.descendants().forEach { it.destroy() }
                process.destroy()
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.descendants().forEach { it.destroyForcibly() }
                    process.destroyForcibly()
                }
                endProcessesUsing(profile)
            }
        }

        companion object {
            /** [hidden] only for checking this works without a window. */
            fun open(exe: File, profile: File, url: String, proxy: String?, hidden: Boolean = false): FirefoxWindow {
                File(profile, "user.js").writeText(firefoxPrefs(proxy))
                val command = buildList {
                    addAll(listOf(exe.absolutePath, "-profile", profile.absolutePath, "-no-remote", "-new-instance", "-wait-for-browser"))
                    if (hidden) add("-headless")
                    add(url)
                }
                val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                return FirefoxWindow(process, profile)
            }

            private fun firefoxPrefs(proxy: String?): String = buildString {
                fun pref(name: String, value: Any) =
                    appendLine("user_pref(\"$name\", ${if (value is String) "\"$value\"" else value});")
                pref("browser.shell.checkDefaultBrowser", false)
                pref("browser.aboutwelcome.enabled", false)
                pref("startup.homepage_welcome_url", "")
                pref("startup.homepage_welcome_url.additional", "")
                pref("browser.startup.homepage_override.mstone", "ignore")
                pref("datareporting.policy.dataSubmissionPolicyBypassNotification", true)
                pref("toolkit.telemetry.reportingpolicy.firstRun", false)
                pref("browser.sessionstore.resume_from_crash", false)
                val route = proxy?.let { runCatching { URI(it) }.getOrNull() }
                if (route?.host != null && route.port > 0) {
                    pref("network.proxy.type", 1)
                    if (route.scheme.startsWith("socks")) {
                        pref("network.proxy.socks", route.host)
                        pref("network.proxy.socks_port", route.port)
                        pref("network.proxy.socks_version", if (route.scheme.startsWith("socks4")) 4 else 5)
                        pref("network.proxy.socks_remote_dns", true)
                    } else {
                        pref("network.proxy.http", route.host)
                        pref("network.proxy.http_port", route.port)
                        pref("network.proxy.ssl", route.host)
                        pref("network.proxy.ssl_port", route.port)
                    }
                }
            }
        }
    }

    /**
     * The processes started with [profile] in their command line, and their
     * arguments, read from /proc. Linux only; this one isn't among them.
     */
    private fun processesUsing(profile: File): List<Pair<Long, List<String>>> {
        val path = profile.absolutePath
        val self = ProcessHandle.current().pid()
        return File("/proc").listFiles { file -> file.name.all(Char::isDigit) }.orEmpty().mapNotNull { folder ->
            val pid = folder.name.toLongOrNull() ?: return@mapNotNull null
            if (pid == self) return@mapNotNull null
            val args = runCatching { File(folder, "cmdline").readBytes() }.getOrNull()
                ?.toString(Charsets.UTF_8)?.split('\u0000')?.filter { it.isNotEmpty() } ?: return@mapNotNull null
            if (args.none { path in it }) return@mapNotNull null
            pid to args
        }
    }

    /** Ends whatever is still running with [profile] - a browser left behind by its launcher. */
    private fun endProcessesUsing(profile: File) {
        if (!Platform.isWindows) {
            processesUsing(profile).forEach { (pid, _) -> ProcessHandle.of(pid).ifPresent { it.destroyForcibly() } }
            return
        }
        runCatching {
            val path = profile.absolutePath.replace("'", "''")
            ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                // Not this search itself, whose own command line names the profile too.
                "Get-CimInstance Win32_Process | Where-Object { \$_.ProcessId -ne \$PID -and \$_.CommandLine -and \$_.CommandLine.Contains('$path') } | " +
                    "ForEach-Object { Stop-Process -Id \$_.ProcessId -Force -ErrorAction SilentlyContinue }"
            ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor(20, TimeUnit.SECONDS)
        }
    }

    /** Collects the browser's answers, a message at a time, and hands each to whoever asked. */
    class Replies(private val waiting: ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>) : WebSocket.Listener {
        private val partial = StringBuilder()

        @Volatile var closed = false
            private set

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

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String?): CompletionStage<*>? {
            closed = true
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable?) {
            closed = true
        }
    }

    /** YouTube's sign-in, landing back on YouTube. */
    const val SIGN_IN_URL =
        "https://accounts.google.com/ServiceLogin?service=youtube&uilel=3&passive=true" +
            "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26next%3Dhttps%253A%252F%252Fwww.youtube.com%252F"

    private const val POLL_MS = 1_500L
    private const val SETTLE_MS = 3_000L
    private const val CALL_TIMEOUT_MS = 5_000L
    private const val STARTUP_MS = 30_000L
    private const val CLOSE_MS = 15_000L
    private const val GIVE_UP_MS = 30L * 60 * 1000
}
