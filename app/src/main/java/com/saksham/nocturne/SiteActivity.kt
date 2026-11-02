package com.saksham.nocturne

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject

/**
 * Opens a website full screen like an app, driven by a cursor.
 *
 * Arrow keys move the cursor (hold to speed up, push against the top or bottom edge to scroll).
 * OK clicks. Hold OK (or press Menu) for the site menu. Back goes to the previous page, then leaves.
 *
 * Clicks are done through the page's DOM (see JsKit), which skips ad overlays and forces links to open in place.
 * Where the DOM can't be reached (frames, canvases, video, dropdowns) a real touch tap is sent instead.
 * Underneath, the ad blocker (AdBlock) stops ad and popunder requests from loading at all.
 */
class SiteActivity : Activity() {
    private lateinit var frame: FrameLayout
    private lateinit var web: WebView
    private lateinit var cursor: CursorView
    private lateinit var menu: LinearLayout
    private lateinit var cursorTile: SkewTile
    private lateinit var clickTile: SkewTile
    private lateinit var adTile: SkewTile
    private lateinit var videoTile: SkewTile
    private lateinit var zoomInTile: SkewTile
    private lateinit var zoomOutTile: SkewTile

    // video player bar
    private lateinit var playerBar: LinearLayout
    private lateinit var playerInfo: TextView
    private lateinit var playBtn: TextView
    private lateinit var speedBtn: TextView
    private lateinit var sizeBtn: TextView
    private lateinit var subBtn: TextView
    private lateinit var audioBtn: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    private var siteUrl = ""
    private var cx = 0f
    private var cy = 0f
    private var menuOpen = false
    private var cursorMode = true
    private var tapMode = false
    private var adBlock = true
    private var longFired = false

    private var playerOpen = false
    private var videoFull = false
    private var vPlaying = false
    private var vTime = 0.0
    private var vDur = 0.0
    private var vRate = 1.0
    private var vSubs: List<Pair<Int, String>> = emptyList()
    private var vAudio: List<Pair<Int, String>> = emptyList()
    private var lastVideoSeen = 0L
    private var lastNativeTapAt = 0L
    private var zoom = 100

    private val zoomSteps = intArrayOf(60, 70, 80, 90, 100, 110, 120, 130, 150, 175, 200)
    private val speeds = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0)
    private val hideBar = Runnable { if (playerOpen) closePlayer() }

    private val longPress = Runnable {
        longFired = true
        openMenu()
    }
    private val hoverRun = Runnable { hover() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url")
        if (url == null) {
            finish()
            return
        }
        siteUrl = url
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cursorMode = Prefs.cursorMode(this)
        tapMode = Prefs.tapClick(this)
        adBlock = Prefs.adBlock(this)
        zoom = Prefs.webZoom(this)
        AdBlock.ensureLoaded(this)

        frame = FrameLayout(this)
        frame.setBackgroundColor(Color.BLACK)

        web = WebView(this)
        setupWeb()
        frame.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))

        cursor = CursorView(this)
        frame.addView(cursor, FrameLayout.LayoutParams(MATCH, MATCH))

        buildMenu()
        buildPlayer()
        setContentView(frame)

        frame.post {
            cx = frame.width / 2f
            cy = frame.height / 2f
            cursor.moveTo(cx, cy)
            applyCursorVisibility()
        }

        web.loadUrl(url)
        web.requestFocus()
    }

    // ---------------------------------------------------------------- web view

    private fun setupWeb() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = false
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(true)
        s.userAgentString = s.userAgentString + " NocturneTV"
        web.isFocusable = true
        web.isFocusableInTouchMode = true

        // Lets the video agent (VideoKit) talk to the app. Android exposes it to the frames inside the page too.
        web.addJavascriptInterface(Bridge(), "NocturneBridge")
        // Runs the agent in every frame, including the iframes video players live in.
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(web, VideoKit.AGENT, setOf("*"))
            }
        } catch (e: Exception) {
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (adBlock && AdBlock.isBlocked(request.url.host)) return AdBlock.empty()
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val scheme = u.scheme ?: return true
                // Stops pages from launching other apps (intent://, market://) or running javascript: links.
                if (scheme != "http" && scheme != "https") return true
                if (adBlock && AdBlock.isBlocked(u.host)) return true
                return false
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                lastVideoSeen = 0L
                if (videoFull) {
                    videoFull = false
                    applyCursorVisibility()
                }
                view.evaluateJavascript(VideoKit.AGENT, null)
                applyZoom()
                if (adBlock) view.evaluateJavascript(JsKit.hideAds(), null)
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                super.onPageCommitVisible(view, url)
                applyZoom()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(VideoKit.AGENT, null)
                applyZoom()
                if (adBlock) view.evaluateJavascript(JsKit.hideAds(), null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            // Popups and popunders are never allowed. Links you click open in place instead.
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean = false

            override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                customView = view
                customCallback = callback
                frame.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                web.visibility = View.GONE
                cursor.visibility = View.GONE
                // Keep our own controls on top of the site's full screen player.
                playerBar.bringToFront()
                menu.bringToFront()
            }

            override fun onHideCustomView() {
                customView?.let { frame.removeView(it) }
                customView = null
                customCallback?.onCustomViewHidden()
                customCallback = null
                web.visibility = View.VISIBLE
                applyCursorVisibility()
            }
        }
    }

    // ---------------------------------------------------------------- keys and cursor

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val code = e.keyCode
        val down = e.action == KeyEvent.ACTION_DOWN
        val isCenter = code == KeyEvent.KEYCODE_DPAD_CENTER ||
            code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_NUMPAD_ENTER

        if (playerOpen) resetHide()

        // Media keys on the remote control a detected video from anywhere.
        if (hasVideoNow() && !menuOpen) {
            when (code) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    if (down && e.repeatCount == 0) {
                        val want = when (code) {
                            KeyEvent.KEYCODE_MEDIA_PLAY -> true
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> false
                            else -> !vPlaying
                        }
                        setPlaying(want)
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    if (down) seekBy(10.0)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    if (down) seekBy(-10.0)
                    return true
                }
            }
        }

        // A site's own full screen player: OK or Menu brings up our controls, Left/Right seek.
        if (customView != null) {
            if (code == KeyEvent.KEYCODE_MENU) {
                if (e.action == KeyEvent.ACTION_UP) {
                    if (playerOpen) closePlayer() else openPlayer()
                }
                return true
            }
            if (!playerOpen && hasVideoNow()) {
                when {
                    isCenter -> {
                        if (e.action == KeyEvent.ACTION_UP) openPlayer()
                        return true
                    }
                    code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (down) seekBy(-10.0)
                        return true
                    }
                    code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (down) seekBy(10.0)
                        return true
                    }
                    code == KeyEvent.KEYCODE_DPAD_UP -> {
                        if (down) openPlayer()
                        return true
                    }
                }
            }
            return super.dispatchKeyEvent(e)
        }

        if (code == KeyEvent.KEYCODE_MENU) {
            if (e.action == KeyEvent.ACTION_UP) {
                when {
                    playerOpen -> closePlayer()
                    menuOpen -> closeMenu()
                    else -> openMenu()
                }
            }
            return true
        }

        if (menuOpen || playerOpen) return super.dispatchKeyEvent(e)

        // Video stretched over the whole page: the cursor is hidden, OK opens the controls.
        if (videoFull && hasVideoNow()) {
            when {
                isCenter -> {
                    if (e.action == KeyEvent.ACTION_UP) openPlayer()
                    return true
                }
                code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (down) seekBy(-10.0)
                    return true
                }
                code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (down) seekBy(10.0)
                    return true
                }
                code == KeyEvent.KEYCODE_DPAD_UP -> {
                    if (down) openPlayer()
                    return true
                }
                code == KeyEvent.KEYCODE_DPAD_DOWN -> return true
            }
        }

        if (isCenter) {
            if (e.action == KeyEvent.ACTION_DOWN) {
                if (e.repeatCount == 0) {
                    longFired = false
                    handler.postDelayed(longPress, 650)
                }
                return if (cursorMode) true else super.dispatchKeyEvent(e)
            }
            if (e.action == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(longPress)
                if (longFired) {
                    longFired = false
                    return true
                }
                if (cursorMode) {
                    clickAtCursor()
                    return true
                }
            }
            return super.dispatchKeyEvent(e)
        }

        if (!cursorMode) return super.dispatchKeyEvent(e)

        when (code) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (e.action == KeyEvent.ACTION_DOWN) moveCursor(code, e.repeatCount)
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (e.action == KeyEvent.ACTION_DOWN) scrollPage(web.height * 0.8f)
                return true
            }
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (e.action == KeyEvent.ACTION_DOWN) scrollPage(-web.height * 0.8f)
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    private fun moveCursor(code: Int, repeat: Int) {
        val step = (frame.width / 90f) * (1f + minOf(repeat, 24) * 0.4f)
        var dx = 0f
        var dy = 0f
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> dy = -step
            KeyEvent.KEYCODE_DPAD_DOWN -> dy = step
            KeyEvent.KEYCODE_DPAD_LEFT -> dx = -step
            KeyEvent.KEYCODE_DPAD_RIGHT -> dx = step
        }
        val margin = 6f
        val maxX = frame.width - margin
        val maxY = frame.height - margin
        var ny = cy + dy
        // Pushing against the top or bottom edge scrolls the page instead of moving further.
        if (ny < margin && dy < 0f) {
            scrollPage(dy * 2.5f)
            ny = margin
        }
        if (ny > maxY && dy > 0f) {
            scrollPage(dy * 2.5f)
            ny = maxY
        }
        cx = (cx + dx).coerceIn(margin, maxX)
        cy = ny.coerceIn(margin, maxY)
        cursor.moveTo(cx, cy)
        handler.removeCallbacks(hoverRun)
        handler.postDelayed(hoverRun, 120)
    }

    private fun hover() {
        if (web.width == 0 || web.height == 0) return
        web.evaluateJavascript(JsKit.move(cx.toInt(), cy.toInt(), web.width, web.height)) { r ->
            cursor.active = r?.trim() == "1"
        }
    }

    private fun scrollPage(dy: Float) {
        if (web.width == 0 || web.height == 0) return
        web.evaluateJavascript(JsKit.scroll(cx.toInt(), cy.toInt(), web.width, web.height, dy.toInt()), null)
    }

    private fun clickAtCursor() {
        cursor.ripple()
        if (tapMode || web.width == 0 || web.height == 0) {
            nativeTap()
            return
        }
        web.evaluateJavascript(JsKit.click(cx.toInt(), cy.toInt(), web.width, web.height)) { r ->
            when (r?.trim()?.trim('"')) {
                "text" -> {
                    web.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT)
                }
                "native" -> {
                    nativeTap()
                    lastNativeTapAt = SystemClock.uptimeMillis()
                    // The site's own controls often don't appear for a remote, so show ours.
                    if (hasVideoNow() && !menuOpen) openPlayer()
                }
                else -> {}
            }
        }
    }

    /** A real touch tap at the cursor, for things the DOM can't reach. */
    private fun nativeTap() {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cx, cy, 0)
        val up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, cx, cy, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        web.dispatchTouchEvent(down)
        web.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun applyCursorVisibility() {
        cursor.visibility =
            if (cursorMode && !menuOpen && !playerOpen && !videoFull && customView == null) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- menu

    private fun buildMenu() {
        menu = LinearLayout(this)
        menu.orientation = LinearLayout.VERTICAL
        menu.visibility = View.GONE
        menu.setPadding(ui(22f), ui(18f), ui(14f), ui(10f))
        val bg = GradientDrawable()
        bg.setColor(Theme.alpha(Color.parseColor("#050B1F"), 235))
        bg.setStroke(ui(2f), Theme.accent)
        menu.background = bg

        cursorTile = menuTile("CURSOR", "Move a pointer with the arrow keys")
        cursorTile.setOnClickListener {
            cursorMode = !cursorMode
            Prefs.setCursorMode(this, cursorMode)
            refreshMenu()
        }

        clickTile = menuTile("CLICK METHOD", "DOM skips ad overlays. TAP is a real touch.")
        clickTile.setOnClickListener {
            tapMode = !tapMode
            Prefs.setTapClick(this, tapMode)
            refreshMenu()
        }

        adTile = menuTile("AD BLOCK", "Blocks ads, popups and trackers")
        adTile.setOnClickListener {
            adBlock = !adBlock
            Prefs.setAdBlock(this, adBlock)
            refreshMenu()
            web.reload()
        }

        val pinTile = menuTile("PIN TO HOME", "Make this page a tile on your home screen")
        pinTile.setOnClickListener {
            pinCurrentPage()
            closeMenu()
        }

        val reloadTile = menuTile("RELOAD", "Load this page again")
        reloadTile.setOnClickListener {
            web.reload()
            closeMenu()
        }

        val exitTile = menuTile("CLOSE SITE", "Back to the home screen")
        exitTile.setOnClickListener { finish() }

        videoTile = menuTile("VIDEO PLAYER", "Speed, size, server, dub and language")
        videoTile.setOnClickListener {
            closeMenu()
            openPlayer()
        }

        zoomInTile = menuTile("ZOOM IN", "Make the page bigger")
        zoomInTile.setOnClickListener {
            zoom = zoomSteps.firstOrNull { it > zoom } ?: zoom
            Prefs.setWebZoom(this, zoom)
            applyZoom()
            refreshMenu()
        }

        zoomOutTile = menuTile("ZOOM OUT", "Make the page smaller")
        zoomOutTile.setOnClickListener {
            zoom = zoomSteps.lastOrNull { it < zoom } ?: zoom
            Prefs.setWebZoom(this, zoom)
            applyZoom()
            refreshMenu()
        }

        for (t in listOf(videoTile, cursorTile, clickTile, adTile, zoomInTile, zoomOutTile, pinTile, reloadTile, exitTile)) menu.addView(t)

        frame.addView(
            menu,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER),
        )
        refreshMenu()
    }

    private fun menuTile(label: String, sub: String): SkewTile =
        SkewTile(this, label, sub, wUnits = 460f, hUnits = 54f)

    private fun refreshMenu() {
        cursorTile.status = if (cursorMode) "ON" else "OFF"
        clickTile.status = if (tapMode) "TAP" else "DOM"
        adTile.status = if (adBlock) "ON" else "OFF"
        zoomInTile.status = "$zoom%"
        zoomOutTile.status = "$zoom%"
        videoTile.status = when {
            !hasVideoNow() -> "NONE"
            vPlaying -> "PLAYING"
            else -> "FOUND"
        }
    }

    private fun openMenu() {
        if (playerOpen) closePlayer()
        menuOpen = true
        refreshMenu()
        // The page behind the menu must not steal focus from the menu tiles.
        web.isFocusable = false
        web.isFocusableInTouchMode = false
        menu.visibility = View.VISIBLE
        applyCursorVisibility()
        videoTile.requestFocus()
    }

    private fun closeMenu() {
        menuOpen = false
        menu.visibility = View.GONE
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        applyCursorVisibility()
        web.requestFocus()
    }

    private fun pinCurrentPage() {
        val url = web.url ?: siteUrl
        val host = (Uri.parse(url).host ?: url).removePrefix("www.")
        val title = (web.title ?: "").trim().ifEmpty { host }
        if (Sites.add(this, title, url)) {
            Favicon.fetch(this, url)
            Toast.makeText(this, "Pinned: $title", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Already on your home screen", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- video agent bridge

    private inner class Bridge {
        @JavascriptInterface
        fun report(json: String) {
            handler.post { onVideoReport(json) }
        }
    }

    private fun hasVideoNow(): Boolean =
        lastVideoSeen != 0L && SystemClock.uptimeMillis() - lastVideoSeen < 4000

    private fun tracks(a: JSONArray?): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        if (a == null) return out
        for (i in 0 until a.length()) {
            val x = a.optJSONObject(i) ?: continue
            out.add(Pair(x.optInt("i", i), x.optString("l", "Track ${i + 1}")))
        }
        return out
    }

    private fun onVideoReport(json: String) {
        try {
            val o = JSONObject(json)
            val wasThere = hasVideoNow()
            lastVideoSeen = SystemClock.uptimeMillis()
            vPlaying = o.optBoolean("p", false)
            vTime = o.optDouble("t", 0.0)
            vDur = o.optDouble("d", 0.0)
            vRate = o.optDouble("r", 1.0)
            vSubs = tracks(o.optJSONArray("s"))
            vAudio = tracks(o.optJSONArray("a"))
            val f = o.optBoolean("f", false)
            if (f != videoFull) {
                videoFull = f
                applyCursorVisibility()
            }
            if (!wasThere) {
                if (SystemClock.uptimeMillis() - lastNativeTapAt < 6000 && !menuOpen) {
                    openPlayer()
                } else {
                    Toast.makeText(this, "Video found. Press MENU for player controls.", Toast.LENGTH_LONG).show()
                }
            }
            refreshPlayer()
            if (menuOpen) refreshMenu()
        } catch (e: Exception) {
        }
    }

    /** Sends a command to the video in the page or in any frame inside it. */
    private fun vcmd(c: String, a: String = "null") {
        web.evaluateJavascript("window.__ncv&&window.__ncv.cmd('$c',$a)", null)
    }

    private fun setPlaying(play: Boolean) {
        vcmd(if (play) "play" else "pause")
        vPlaying = play
        refreshPlayer()
    }

    private fun seekBy(sec: Double) {
        vcmd("seek", sec.toString())
    }

    private fun setFull(on: Boolean) {
        vcmd("full", on.toString())
        videoFull = on
        applyCursorVisibility()
        refreshPlayer()
    }

    private fun applyZoom() {
        val dm = resources.displayMetrics
        val dpWidth = (dm.widthPixels / dm.density).toInt()
        web.evaluateJavascript("window.__ncv&&window.__ncv.zoom($zoom,$dpWidth)", null)
    }

    // ---------------------------------------------------------------- video player bar

    private fun paintBtn(b: TextView, focused: Boolean) {
        val d = GradientDrawable()
        d.setColor(if (focused) Theme.focusB else Theme.alpha(Color.parseColor("#12306F"), 235))
        d.setStroke(ui(1.5f), if (focused) Theme.focusA else Theme.edge)
        d.cornerRadius = u(6f)
        b.background = d
        b.setPadding(ui(16f), ui(9f), ui(16f), ui(9f))
        b.setTextColor(if (focused) Theme.ink else Theme.text)
    }

    private fun barButton(label: String, action: () -> Unit): TextView {
        val b = tv(label, 17f, Theme.text)
        b.gravity = Gravity.CENTER
        b.isFocusable = true
        b.isClickable = true
        paintBtn(b, false)
        b.setOnFocusChangeListener { v, hasFocus -> paintBtn(v as TextView, hasFocus) }
        b.setOnClickListener { action() }
        return b
    }

    private fun buildPlayer() {
        playerBar = LinearLayout(this)
        playerBar.orientation = LinearLayout.VERTICAL
        playerBar.visibility = View.GONE
        playerBar.setPadding(ui(24f), ui(14f), ui(24f), ui(18f))
        val bg = GradientDrawable()
        bg.setColor(Theme.alpha(Color.parseColor("#050B1F"), 240))
        bg.setStroke(ui(2f), Theme.accent)
        playerBar.background = bg

        playerInfo = tv("", 18f, Theme.text, Theme.body)
        playerBar.addView(playerInfo, LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = ui(10f) })

        playBtn = barButton("PAUSE") { setPlaying(!vPlaying) }
        val backBtn = barButton("-10s") { seekBy(-10.0) }
        val fwdBtn = barButton("+10s") { seekBy(10.0) }
        speedBtn = barButton("SPEED x1") { cycleSpeed() }
        sizeBtn = barButton("FULL SCREEN") { setFull(!videoFull) }
        val serverBtn = barButton("SERVER / DUB / LANG") { showOptions() }
        subBtn = barButton("SUBTITLES") { pickTrack("sub", vSubs, true) }
        audioBtn = barButton("AUDIO") { pickTrack("audio", vAudio, false) }
        val closeBtn = barButton("CLOSE") { closePlayer() }

        playerBar.addView(buttonRow(listOf(playBtn, backBtn, fwdBtn, speedBtn, sizeBtn)))
        playerBar.addView(
            buttonRow(listOf(serverBtn, subBtn, audioBtn, closeBtn)),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = ui(8f) },
        )

        frame.addView(playerBar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        refreshPlayer()
    }

    private fun buttonRow(items: List<TextView>): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for (b in items) row.addView(b, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = ui(10f) })
        return row
    }

    private fun speedText(r: Double): String =
        if (r == r.toInt().toDouble()) r.toInt().toString() else r.toString()

    private fun fmt(sec: Double): String {
        val t = sec.toInt()
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }

    private fun refreshPlayer() {
        if (!::playerBar.isInitialized) return
        val now = hasVideoNow()
        playerInfo.text = if (!now) {
            "No video detected on this page yet"
        } else {
            val total = if (vDur > 0.0) " / " + fmt(vDur) else "  (live)"
            (if (vPlaying) "PLAYING   " else "PAUSED   ") + fmt(vTime) + total + "   speed x" + speedText(vRate)
        }
        playBtn.text = if (vPlaying) "PAUSE" else "PLAY"
        speedBtn.text = "SPEED x" + speedText(vRate)
        sizeBtn.text = if (videoFull) "HALF SCREEN" else "FULL SCREEN"
        subBtn.visibility = if (vSubs.isNotEmpty()) View.VISIBLE else View.GONE
        audioBtn.visibility = if (vAudio.size > 1) View.VISIBLE else View.GONE
    }

    private fun cycleSpeed() {
        val i = speeds.indexOfFirst { it > vRate + 0.001 }
        val next = if (i < 0) speeds[0] else speeds[i]
        vcmd("speed", next.toString())
        vRate = next
        refreshPlayer()
    }

    private fun pickTrack(cmd: String, list: List<Pair<Int, String>>, withOff: Boolean) {
        if (list.isEmpty()) return
        val labels = ArrayList<String>()
        val ids = ArrayList<Int>()
        if (withOff) {
            labels.add("Off")
            ids.add(-1)
        }
        for (p in list) {
            labels.add(p.second)
            ids.add(p.first)
        }
        AlertDialog.Builder(this)
            .setTitle(if (cmd == "sub") "Subtitles" else "Audio")
            .setItems(labels.toTypedArray()) { _, which -> vcmd(cmd, ids[which].toString()) }
            .show()
    }

    /** Lists the page's own Sub / Dub / Server / language buttons and presses the one you choose. */
    private fun showOptions() {
        web.evaluateJavascript("window.__ncv&&window.__ncv.options()") { r ->
            val labels = ArrayList<String>()
            try {
                val arr = JSONArray(r ?: "[]")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val mark = if (o.optBoolean("a")) "\u25CF  " else "     "
                    labels.add(mark + o.optString("t"))
                }
            } catch (e: Exception) {
            }
            if (labels.isEmpty()) {
                Toast.makeText(this, "No server, dub or language buttons found on this page", Toast.LENGTH_LONG).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Server / Dub / Language")
                    .setItems(labels.toTypedArray()) { _, which ->
                        web.evaluateJavascript("window.__ncv&&window.__ncv.pick($which)", null)
                        closePlayer()
                    }
                    .show()
            }
        }
    }

    private fun openPlayer() {
        if (menuOpen) closeMenu()
        playerOpen = true
        web.isFocusable = false
        web.isFocusableInTouchMode = false
        refreshPlayer()
        playerBar.visibility = View.VISIBLE
        playerBar.bringToFront()
        applyCursorVisibility()
        playBtn.requestFocus()
        resetHide()
    }

    private fun closePlayer() {
        playerOpen = false
        handler.removeCallbacks(hideBar)
        playerBar.visibility = View.GONE
        if (!menuOpen) {
            web.isFocusable = true
            web.isFocusableInTouchMode = true
        }
        applyCursorVisibility()
        if (customView == null) web.requestFocus()
    }

    private fun resetHide() {
        handler.removeCallbacks(hideBar)
        handler.postDelayed(hideBar, 8000)
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onBackPressed() {
        when {
            playerOpen -> closePlayer()
            customView != null -> web.webChromeClient?.onHideCustomView()
            videoFull -> {
                // One step back: leave the stretched video first, before leaving the page.
                setFull(false)
            }
            menuOpen -> closeMenu()
            web.canGoBack() -> web.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
