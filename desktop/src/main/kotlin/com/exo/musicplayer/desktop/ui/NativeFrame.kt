package com.exo.musicplayer.desktop.ui

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import java.util.concurrent.ConcurrentHashMap

/**
 * Lucense's own title strip and edges, with Windows doing the moving.
 *
 * Moving and resizing the window by hand from mouse events lags a step behind
 * the mouse, and gets none of what Windows gives its own windows. So the
 * window takes the styles of an ordinary resizable window - a caption, a
 * sizing frame, minimise and maximise - while the space Windows would draw
 * them in is handed to the window itself (WM_NCCALCSIZE), so none of them
 * shows. Asked what is under the pointer (WM_NCHITTEST), the window answers
 * "caption" over Lucense's title strip, "maximise button" over its maximise
 * button, and "left edge", "bottom right corner" and so on along its edges.
 * From there it is Windows' own window: dragged smoothly, snapped to halves
 * and quarters with Windows' outline and snap assist after, Win+arrows,
 * Windows 11's snap layouts over the maximise button, double-click to
 * maximise, the minimise and maximise animations, and the shadow.
 *
 * Skia draws into a child window covering the whole frame, and Windows asks
 * that child first, so the child is taught to pass the strip and the edges on
 * to the frame (HTTRANSPARENT). If any of this can't be set up (not Windows,
 * or the calls fail), nothing changes and the hand-made dragging stays.
 */
object NativeFrame {

    private interface WndProc : StdCallLibrary.StdCallCallback {
        fun callback(hWnd: Pointer, msg: Int, wParam: Pointer?, lParam: Pointer?): Pointer?
    }

    private interface EnumProc : StdCallLibrary.StdCallCallback {
        fun callback(hWnd: Pointer, data: Pointer?): Boolean
    }

    @Structure.FieldOrder("cbSize", "dwFlags", "hwndTrack", "dwHoverTime")
    class TrackMouseEvent : Structure() {
        @JvmField var cbSize: Int = 0
        @JvmField var dwFlags: Int = 0
        @JvmField var hwndTrack: Pointer? = null
        @JvmField var dwHoverTime: Int = 0
    }

    @Suppress("FunctionName")
    private interface User32 : StdCallLibrary {
        fun SetWindowLongPtrW(hWnd: Pointer, index: Int, newLong: Callback): Pointer?
        fun CallWindowProcW(previous: Pointer, hWnd: Pointer, msg: Int, wParam: Pointer?, lParam: Pointer?): Pointer?
        fun GetWindowLongW(hWnd: Pointer, index: Int): Int
        fun SetWindowLongW(hWnd: Pointer, index: Int, newLong: Int): Int
        fun SetWindowPos(hWnd: Pointer, after: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
        fun GetWindowRect(hWnd: Pointer, rect: WinDef.RECT): Boolean
        fun ShowWindowAsync(hWnd: Pointer, command: Int): Boolean
        fun IsZoomed(hWnd: Pointer): Boolean
        fun GetDpiForWindow(hWnd: Pointer): Int
        fun SendMessageW(hWnd: Pointer, msg: Int, wParam: Pointer?, lParam: Pointer?): Pointer?
        fun PostMessageW(hWnd: Pointer, msg: Int, wParam: Pointer?, lParam: Pointer?): Boolean
        fun EnumChildWindows(parent: Pointer, proc: EnumProc, data: Pointer?): Boolean
        fun TrackMouseEvent(track: TrackMouseEvent): Boolean
        fun ReleaseCapture(): Boolean
        fun ScreenToClient(hWnd: Pointer, point: WinDef.POINT): Boolean
        fun GetAsyncKeyState(key: Int): Short
        fun GetSystemMetrics(index: Int): Int
    }

    private val user32: User32? by lazy { runCatching { Native.load("user32", User32::class.java) }.getOrNull() }

    /**
     * Every window procedure handed to Windows, by window, with the one it
     * replaced. Held here so the collector never takes a procedure Windows
     * still calls.
     */
    private val installed = ConcurrentHashMap<Long, Pair<WndProc, Pointer>>()

    /** The windows already handed over, so a second [install] changes nothing. */
    private val frames = ConcurrentHashMap<Long, WindowFrame>()

    /**
     * Hands [window]'s moving, sizing and snapping to Windows, with [frame]
     * saying where its title strip and maximise button are. True when it took.
     */
    fun install(window: Window, frame: WindowFrame): Boolean {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return false
        val api = user32 ?: return false
        return runCatching {
            val hwnd = Native.getComponentPointer(window) ?: return false
            val key = Pointer.nativeValue(hwnd)
            if (frames.containsKey(key)) return true
            val rect = WinDef.RECT()
            val point = WinDef.POINT()
            // Pressed on the maximise button and not yet let go; only touched on Windows' thread.
            var pressingMaximize = false

            lateinit var previous: Pointer
            val proc = object : WndProc {
                override fun callback(hWnd: Pointer, msg: Int, wParam: Pointer?, lParam: Pointer?): Pointer? {
                    when (msg) {
                        // The whole window is the window's own to draw: no frame shows.
                        // Maximised, Windows makes it larger than the screen by the
                        // frame it would have had; that much stays frame, unseen.
                        WM_NCCALCSIZE -> if (wParam.value() != 0L) {
                            if (lParam != null && api.IsZoomed(hWnd)) keepToWorkArea(lParam)
                            return ZERO
                        }
                        WM_NCHITTEST -> return code(hitTest(api, hWnd, rect, point, lParam, frame))
                        // Nothing of a frame to repaint; this keeps Windows from flashing one.
                        WM_NCACTIVATE -> return api.CallWindowProcW(previous, hWnd, msg, wParam, MINUS_ONE)
                        // Minimised says nothing about whether it comes back maximised.
                        WM_SIZE -> if (wParam.value() != SIZE_MINIMIZED) frame.nativeSized(maximized = wParam.value() == SIZE_MAXIMIZED)
                        WM_PARENTNOTIFY -> if ((wParam.value() and 0xFFFF) == WM_CREATE.toLong() && lParam != null) {
                            adopt(api, lParam, hWnd, frame)
                        }
                        // The maximise button belongs to Windows here, for its snap layouts,
                        // so its hover and its click come as these. The cursor is asked
                        // for whenever the pointer moves or the window moves under it.
                        // Not tracked from here: asked before the pointer has moved in,
                        // Windows reports it gone at once, and asks again, round and round.
                        WM_SETCURSOR -> frame.hoverMaximize((lParam.value() and 0xFFFF) == HTMAXBUTTON.toLong())
                        WM_NCMOUSEMOVE -> {
                            val over = wParam.value() == HTMAXBUTTON.toLong()
                            if (over) track(api, hWnd)
                            frame.hoverMaximize(over)
                            if (!over) {
                                pressingMaximize = false
                                frame.pressMaximize(false)
                            }
                        }
                        WM_NCMOUSELEAVE, WM_MOUSEMOVE -> {
                            frame.hoverMaximize(false)
                            pressingMaximize = false
                            frame.pressMaximize(false)
                        }
                        WM_NCLBUTTONDOWN, WM_NCLBUTTONDBLCLK -> if (wParam.value() == HTMAXBUTTON.toLong()) {
                            pressingMaximize = true
                            frame.pressMaximize(true)
                            return ZERO
                        }
                        WM_NCLBUTTONUP -> if (wParam.value() == HTMAXBUTTON.toLong()) {
                            if (pressingMaximize) toggleMaximize(api, hWnd)
                            pressingMaximize = false
                            frame.pressMaximize(false)
                            return ZERO
                        }
                        WM_LUCENSE_MOVE -> {
                            // A press reached the strip as an ordinary click: Skia's
                            // window is new and wasn't taught yet. Teach it, and move.
                            adoptChildren(api, hWnd, frame)
                            // Only while the button is still held: after a quick click
                            // the move would carry on with the pointer until the next.
                            val primary = if (api.GetSystemMetrics(SM_SWAPBUTTON) != 0) VK_RBUTTON else VK_LBUTTON
                            if (api.GetAsyncKeyState(primary).toInt() and 0x8000 == 0) return ZERO
                            api.ReleaseCapture()
                            return api.CallWindowProcW(previous, hWnd, WM_SYSCOMMAND, Pointer.createConstant(SC_DRAGMOVE), ZERO)
                        }
                    }
                    return api.CallWindowProcW(previous, hWnd, msg, wParam, lParam)
                }
            }

            // An ordinary resizable window's styles: what Windows snaps, maximises and animates.
            val style = api.GetWindowLongW(hwnd, GWL_STYLE)
            val wanted = (style and WS_POPUP.inv()) or WS_CAPTION or WS_THICKFRAME or WS_SYSMENU or WS_MINIMIZEBOX or WS_MAXIMIZEBOX
            previous = api.SetWindowLongPtrW(hwnd, GWLP_WNDPROC, proc) ?: return false
            installed[key] = proc to previous
            frames[key] = frame
            adoptChildren(api, hwnd, frame)
            api.SetWindowLongW(hwnd, GWL_STYLE, wanted)
            api.SetWindowPos(hwnd, null, 0, 0, 0, 0, SWP_FRAMECHANGED or SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE)
            frame.attachNative(
                minimize = { api.ShowWindowAsync(hwnd, SW_MINIMIZE) },
                toggleMaximize = { toggleMaximize(api, hwnd) },
                maximized = api.IsZoomed(hwnd)
            )
            true
        }.getOrDefault(false)
    }

    /** Starts Windows' own move from a press that came to the title strip as a click. */
    fun startMove(window: Window) {
        val api = user32 ?: return
        val hwnd = Native.getComponentPointer(window) ?: return
        api.PostMessageW(hwnd, WM_LUCENSE_MOVE, null, null)
    }

    private fun toggleMaximize(api: User32, hWnd: Pointer) {
        api.ShowWindowAsync(hWnd, if (api.IsZoomed(hWnd)) SW_RESTORE else SW_MAXIMIZE)
    }

    /** Teaches every window inside the frame - Skia's, mostly - to leave the strip and the edges to the frame. */
    private fun adoptChildren(api: User32, top: Pointer, frame: WindowFrame) {
        val each = object : EnumProc {
            override fun callback(hWnd: Pointer, data: Pointer?): Boolean {
                adopt(api, hWnd, top, frame)
                return true
            }
        }
        api.EnumChildWindows(top, each, null)
    }

    private fun adopt(api: User32, child: Pointer, top: Pointer, frame: WindowFrame) {
        val key = Pointer.nativeValue(child)
        if (installed.containsKey(key)) return
        val rect = WinDef.RECT()
        val point = WinDef.POINT()
        lateinit var previous: Pointer
        val proc = object : WndProc {
            override fun callback(hWnd: Pointer, msg: Int, wParam: Pointer?, lParam: Pointer?): Pointer? {
                if (msg == WM_NCHITTEST && hitTest(api, top, rect, point, lParam, frame) != HTCLIENT) return MINUS_ONE // HTTRANSPARENT
                if (msg == WM_MOUSEMOVE) frame.hoverMaximize(false)
                return api.CallWindowProcW(previous, hWnd, msg, wParam, lParam)
            }
        }
        previous = api.SetWindowLongPtrW(child, GWLP_WNDPROC, proc) ?: return
        installed[key] = proc to previous
    }

    /** Asks for WM_NCMOUSELEAVE, so the maximise button's hover ends when the pointer leaves the window over it. */
    private fun track(api: User32, hWnd: Pointer) {
        val track = TrackMouseEvent()
        track.cbSize = track.size()
        track.dwFlags = TME_LEAVE or TME_NONCLIENT
        track.hwndTrack = hWnd
        api.TrackMouseEvent(track)
    }

    /**
     * What is under the pointer: an edge or a corner to size from, the
     * maximise button, the strip to drag by, or the window's own.
     */
    private fun hitTest(api: User32, top: Pointer, rect: WinDef.RECT, point: WinDef.POINT, lParam: Pointer?, frame: WindowFrame): Int {
        val packed = lParam.value()
        val sx = (packed and 0xFFFF).toInt().toShort().toInt()
        val sy = ((packed shr 16) and 0xFFFF).toInt().toShort().toInt()
        if (!api.IsZoomed(top)) {
            if (!api.GetWindowRect(top, rect)) return HTCLIENT
            val x = sx - rect.left
            val y = sy - rect.top
            val width = rect.right - rect.left
            val height = rect.bottom - rect.top
            val dpi = api.GetDpiForWindow(top).takeIf { it > 0 } ?: 96
            val side = (6 * dpi + 48) / 96
            val corner = (16 * dpi + 48) / 96
            val left = x < side
            val right = x >= width - side
            val upper = y < side
            val lower = y >= height - side
            when {
                upper && x < corner || left && y < corner -> return HTTOPLEFT
                upper && x >= width - corner || right && y < corner -> return HTTOPRIGHT
                lower && x < corner || left && y >= height - corner -> return HTBOTTOMLEFT
                lower && x >= width - corner || right && y >= height - corner -> return HTBOTTOMRIGHT
                left -> return HTLEFT
                right -> return HTRIGHT
                upper -> return HTTOP
                lower -> return HTBOTTOM
            }
        }
        // The strip and the button are where the layout put them, from the corner of what the window draws.
        point.x = sx
        point.y = sy
        if (!api.ScreenToClient(top, point)) return HTCLIENT
        if (frame.maximizeButton?.contains(point.x, point.y) == true) return HTMAXBUTTON
        if (frame.dragArea?.contains(point.x, point.y) == true) return HTCAPTION
        return HTCLIENT
    }

    /**
     * Maximised, the window is larger than the screen by the frame Windows
     * gives a window with a sizing frame, and keeps off screen. What the
     * window draws is kept to the screen's working area; the rest is frame.
     */
    private fun keepToWorkArea(params: Pointer) {
        val platform = com.sun.jna.platform.win32.User32.INSTANCE
        val proposed = WinDef.RECT().apply {
            left = params.getInt(0)
            top = params.getInt(4)
            right = params.getInt(8)
            bottom = params.getInt(12)
        }
        val monitor = platform.MonitorFromRect(proposed, WinUser.MONITOR_DEFAULTTONEAREST) ?: return
        val about = WinUser.MONITORINFO()
        if (!platform.GetMonitorInfo(monitor, about).booleanValue()) return
        val work = about.rcWork
        params.setInt(0, maxOf(proposed.left, work.left))
        params.setInt(4, maxOf(proposed.top, work.top))
        params.setInt(8, minOf(proposed.right, work.right))
        params.setInt(12, minOf(proposed.bottom, work.bottom))
    }

    /**
     * Asks the window what is at a point of the screen, as Windows does,
     * through Skia's window first if [throughChild]; for checking without
     * moving the mouse.
     */
    fun hitTestAt(window: Window, screenX: Int, screenY: Int, throughChild: Pointer? = null): Int {
        val api = user32 ?: return -2
        val hwnd = throughChild ?: Native.getComponentPointer(window) ?: return -2
        val packed = ((screenY.toLong() and 0xFFFF) shl 16) or (screenX.toLong() and 0xFFFF)
        return api.SendMessageW(hwnd, WM_NCHITTEST, null, Pointer.createConstant(packed)).value().toInt()
    }

    /** The windows inside [window], for the checks. */
    fun childrenOf(window: Window): List<Pointer> {
        val api = user32 ?: return emptyList()
        val hwnd = Native.getComponentPointer(window) ?: return emptyList()
        val found = ArrayList<Pointer>()
        api.EnumChildWindows(hwnd, object : EnumProc {
            override fun callback(hWnd: Pointer, data: Pointer?): Boolean {
                found += hWnd
                return true
            }
        }, null)
        return found
    }

    private fun Pointer?.value(): Long = if (this == null) 0L else Pointer.nativeValue(this)

    private val ZERO: Pointer? = null
    private val MINUS_ONE: Pointer = Pointer.createConstant(-1L)
    private val CODES: Array<Pointer?> = Array(21) { if (it == 0) null else Pointer.createConstant(it.toLong()) }
    private fun code(hit: Int): Pointer? = CODES.getOrNull(hit) ?: Pointer.createConstant(hit.toLong())

    private const val GWL_STYLE = -16
    private const val GWLP_WNDPROC = -4

    private const val WS_POPUP = 0x80000000.toInt()
    private const val WS_CAPTION = 0x00C00000
    private const val WS_THICKFRAME = 0x00040000
    private const val WS_SYSMENU = 0x00080000
    private const val WS_MINIMIZEBOX = 0x00020000
    private const val WS_MAXIMIZEBOX = 0x00010000

    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020

    private const val WM_CREATE = 0x0001
    private const val WM_SIZE = 0x0005
    private const val WM_SETCURSOR = 0x0020
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084
    private const val WM_NCACTIVATE = 0x0086
    private const val WM_NCMOUSEMOVE = 0x00A0
    private const val WM_NCLBUTTONDOWN = 0x00A1
    private const val WM_NCLBUTTONUP = 0x00A2
    private const val WM_NCLBUTTONDBLCLK = 0x00A3
    private const val WM_SYSCOMMAND = 0x0112
    private const val WM_MOUSEMOVE = 0x0200
    private const val WM_PARENTNOTIFY = 0x0210
    private const val WM_NCMOUSELEAVE = 0x02A2
    private const val WM_LUCENSE_MOVE = 0x8000 + 0x4C // WM_APP + 'L'

    private const val SM_SWAPBUTTON = 23
    private const val VK_LBUTTON = 0x01
    private const val VK_RBUTTON = 0x02

    private const val SIZE_MINIMIZED = 1L
    private const val SIZE_MAXIMIZED = 2L
    private const val SC_DRAGMOVE = 0xF012L

    private const val TME_LEAVE = 0x00000002
    private const val TME_NONCLIENT = 0x00000010

    private const val SW_MAXIMIZE = 3
    private const val SW_MINIMIZE = 6
    private const val SW_RESTORE = 9

    const val HTCLIENT = 1
    const val HTCAPTION = 2
    const val HTMAXBUTTON = 9
    const val HTLEFT = 10
    const val HTRIGHT = 11
    const val HTTOP = 12
    const val HTTOPLEFT = 13
    const val HTTOPRIGHT = 14
    const val HTBOTTOM = 15
    const val HTBOTTOMLEFT = 16
    const val HTBOTTOMRIGHT = 17
    const val HTTRANSPARENT = -1
}
