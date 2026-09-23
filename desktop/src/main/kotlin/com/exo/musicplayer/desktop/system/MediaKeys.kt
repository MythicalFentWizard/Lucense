package com.exo.musicplayer.desktop.system

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The keyboard's media keys, live whatever has focus.
 *
 * The same RegisterHotKey route as [GlobalHotkey] - Windows forwards only the
 * keys asked for, where a low-level hook would see every keystroke typed on the
 * machine - but four of them sharing one message loop, because registration and
 * that loop have to live on the same thread.
 *
 * Windows gives these keys to whoever asks first, so while Lucense holds them
 * another player will not see them. That is why this can be turned off.
 */
class MediaKeys {

    private companion object {
        /** Arbitrary, and only has to be unique within this process. */
        const val FIRST_ID = 0x5260
        const val WM_HOTKEY = 0x0312
        const val PEEK_REMOVE = 1
        const val VK_MEDIA_NEXT = 0xB0
        const val VK_MEDIA_PREVIOUS = 0xB1
        const val VK_MEDIA_STOP = 0xB2
        const val VK_MEDIA_PLAY_PAUSE = 0xB3
    }

    private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    /** How many of the four keys Windows actually handed over. */
    @Volatile var registered: Int = 0
        private set

    /** Each callback runs on the media-key thread, so none of them may block. */
    fun bind(
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        onStop: () -> Unit
    ) {
        unbind()
        running.set(true)
        worker = thread(name = "lucense-media-keys", isDaemon = true) {
            val user32 = User32.INSTANCE
            val keys = intArrayOf(VK_MEDIA_PLAY_PAUSE, VK_MEDIA_NEXT, VK_MEDIA_PREVIOUS, VK_MEDIA_STOP)
            val actions = listOf(onPlayPause, onNext, onPrevious, onStop)
            val taken = mutableListOf<Int>()
            keys.forEachIndexed { index, key ->
                if (user32.RegisterHotKey(null, FIRST_ID + index, 0, key)) taken.add(index)
            }
            registered = taken.size
            if (taken.isEmpty()) {
                running.set(false)
                return@thread
            }
            val message = WinUser.MSG()
            try {
                while (running.get()) {
                    // Polled rather than blocking on GetMessage, so unbind() can
                    // stop this thread without posting a wake-up message to it.
                    if (user32.PeekMessage(message, null, 0, 0, PEEK_REMOVE)) {
                        if (message.message == WM_HOTKEY) {
                            val which = message.wParam.toInt() - FIRST_ID
                            actions.getOrNull(which)?.let { action -> runCatching { action() } }
                        }
                    } else {
                        Thread.sleep(15)
                    }
                }
            } catch (_: InterruptedException) {
                // Falls through to unregistering below.
            } finally {
                taken.forEach { index -> runCatching { user32.UnregisterHotKey(null, FIRST_ID + index) } }
                registered = 0
            }
        }
    }

    fun unbind() {
        running.set(false)
        worker?.let { runCatching { it.join(400) } }
        worker = null
    }
}
