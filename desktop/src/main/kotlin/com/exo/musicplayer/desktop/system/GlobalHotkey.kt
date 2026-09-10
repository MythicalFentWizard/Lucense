package com.exo.musicplayer.desktop.system

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A single system-wide hotkey, live even while another window has focus.
 *
 * Registered through `RegisterHotKey`, which asks Windows to forward one
 * specific key and nothing else. The alternative — a `WH_KEYBOARD_LL` hook —
 * would see every keystroke typed anywhere on the machine, which is both more
 * than this feature needs and the exact shape antivirus software treats as a
 * keylogger. The narrower call costs a little (Windows reports the press but
 * never the release, so ducking toggles rather than being held) and is worth it.
 *
 * `RegisterHotKey` with a null window posts `WM_HOTKEY` to the *calling thread's*
 * message queue, so registration and the message loop must live on the same
 * thread. That thread is owned here and nothing else runs on it.
 */
class GlobalHotkey {

    private companion object {
        /** Arbitrary; only needs to be unique within this process. */
        const val HOTKEY_ID = 0x5250
        const val WM_HOTKEY = 0x0312
        const val PEEK_REMOVE = 1
    }

    private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    @Volatile private var pendingKey: Int = 0
    @Volatile private var pendingModifiers: Int = 0

    /** Set when registration failed, e.g. another app already owns the key. */
    @Volatile var lastError: String? = null
        private set

    @Volatile var isRegistered: Boolean = false
        private set

    /**
     * Binds [virtualKey], replacing any previous binding.
     *
     * @param virtualKey a Windows virtual-key code.
     * @param onPressed invoked on the hotkey thread, so it must not block.
     */
    fun bind(virtualKey: Int, modifiers: Int = 0, onPressed: () -> Unit) {
        unbind()
        if (virtualKey == 0) return

        pendingKey = virtualKey
        pendingModifiers = modifiers
        running.set(true)
        lastError = null

        worker = thread(name = "resonate-hotkey", isDaemon = true) {
            val user32 = User32.INSTANCE
            val registered = user32.RegisterHotKey(
                null, HOTKEY_ID, pendingModifiers, pendingKey
            )
            if (!registered) {
                lastError = "Windows refused that key — another program is probably using it."
                isRegistered = false
                running.set(false)
                return@thread
            }
            isRegistered = true

            val message = WinUser.MSG()
            try {
                while (running.get()) {
                    // Polled rather than blocking on GetMessage, so unbind() can
                    // stop this thread without having to post a wake-up message
                    // to it from outside.
                    val hasMessage = user32.PeekMessage(
                        message, null, 0, 0, PEEK_REMOVE
                    )
                    if (hasMessage) {
                        if (message.message == WM_HOTKEY) {
                            runCatching { onPressed() }
                        }
                    } else {
                        Thread.sleep(15)
                    }
                }
            } catch (_: InterruptedException) {
                // Falls through to unregistering below.
            } finally {
                runCatching { user32.UnregisterHotKey(null, HOTKEY_ID) }
                isRegistered = false
            }
        }
    }

    fun unbind() {
        running.set(false)
        worker?.let { runCatching { it.join(400) } }
        worker = null
        isRegistered = false
    }
}

/**
 * Keys offered for the duck binding.
 *
 * Restricted to function keys and the numeric keypad on purpose: a global hotkey
 * swallows that key everywhere, so binding a letter would break typing across
 * the whole machine. These are the keys a game is least likely to want too.
 */
enum class DuckKey(val label: String, val virtualKey: Int) {
    F6("F6", 0x75),
    F7("F7", 0x76),
    F8("F8", 0x77),
    F9("F9", 0x78),
    F10("F10", 0x79),
    F11("F11", 0x7A),
    F12("F12", 0x7B),
    NUMPAD0("Num 0", 0x60),
    NUMPAD1("Num 1", 0x61),
    NUMPAD_MULTIPLY("Num *", 0x6A),
    NUMPAD_DIVIDE("Num /", 0x6F),
    SCROLL_LOCK("Scroll Lock", 0x91),
    PAUSE("Pause", 0x13);

    companion object {
        fun fromName(name: String?): DuckKey = entries.firstOrNull { it.name == name } ?: F9
    }
}
