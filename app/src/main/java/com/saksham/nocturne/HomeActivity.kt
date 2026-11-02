package com.saksham.nocturne

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

class HomeActivity : Activity() {
    private lateinit var titleView: TextView
    private lateinit var subView: TextView
    private var signature = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (currentSignature() != signature) render()
    }

    /** A launcher never closes on Back. */
    override fun onBackPressed() {}

    private fun currentSignature(): String =
        Apps.signature(this) + "#" + Sites.stamp(this) + "#" + Prefs.stamp(this)

    private fun render() {
        signature = currentSignature()
        val apps = Apps.load(this)
        val sites = Sites.load(this)

        val root = FrameLayout(this)
        root.addView(NightBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(ui(64f), ui(30f), ui(64f), ui(10f))
        column.clipChildren = false
        column.clipToPadding = false
        column.addView(topBar())

        titleView = tv("NOCTURNE", 64f, Theme.text)
        subView = tv("", 18f, Theme.textDim, Theme.body)
        column.addView(titleView, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = ui(24f) })
        column.addView(
            AccentBar(this, 180f, 6f),
            LinearLayout.LayoutParams(ui(180f), ui(6f)).apply {
                topMargin = ui(10f)
                bottomMargin = ui(10f)
            },
        )
        column.addView(subView)

        val appTiles = apps.filter { it.tv }.map { e ->
            SkewTile(this, e.label, "Open ${e.label}", icon = e.icon, banner = e.banner).also { t ->
                t.setOnClickListener { safeStart(e.intent) }
            }
        }

        val siteTiles = ArrayList<SkewTile>()
        for (s in sites) {
            val host = Uri.parse(s.url).host ?: s.url
            val tile = SkewTile(this, s.name, "$host. Hold OK to remove.", logo = s.logo)
            tile.setOnClickListener {
                safeStart(Intent(this, SiteActivity::class.java).putExtra("url", s.url))
            }
            tile.setOnLongClickListener {
                confirmRemove(s)
                true
            }
            siteTiles.add(tile)
        }
        siteTiles.add(
            SkewTile(this, "ADD A SITE", "Turn any website into an app", icon = getDrawable(R.drawable.ic_globe), tintIcon = true).also { t ->
                t.setOnClickListener { safeStart(Intent(this, AddSiteActivity::class.java)) }
            },
        )

        val systemTiles = listOf(
            SkewTile(this, "ALL APPS", "Everything installed, nothing hidden", icon = getDrawable(R.drawable.ic_apps), tintIcon = true).also { t ->
                t.setOnClickListener { safeStart(Intent(this, AllAppsActivity::class.java)) }
            },
            SkewTile(this, "ACCESSIBILITY", "Captions, contrast, motion and text size", icon = getDrawable(R.drawable.ic_access), tintIcon = true).also { t ->
                t.setOnClickListener { safeStart(Intent(this, AccessibilityActivity::class.java)) }
            },
            SkewTile(this, "ORIGINAL HOME", "Switch to the TV's stock home screen", icon = getDrawable(R.drawable.ic_home), tintIcon = true).also { t ->
                t.setOnClickListener { openStockHome() }
            },
            SkewTile(this, "SETTINGS", "System settings", icon = getDrawable(R.drawable.ic_settings), tintIcon = true).also { t ->
                t.setOnClickListener { safeStart(Intent(Settings.ACTION_SETTINGS)) }
            },
        )

        val all = appTiles + siteTiles + systemTiles
        all.forEach { t ->
            t.onFocusLabel = { l, s ->
                titleView.text = l.uppercase()
                subView.text = s
            }
        }

        val rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        rows.clipChildren = false
        rows.clipToPadding = false
        if (appTiles.isNotEmpty()) rows.addView(row("APPS", appTiles))
        rows.addView(row("SITES", siteTiles))
        rows.addView(row("SYSTEM", systemTiles))

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.clipChildren = false
        scroll.clipToPadding = false
        scroll.addView(rows)
        column.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = ui(14f) })

        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        all.firstOrNull()?.let { first -> first.post { first.requestFocus() } }
    }

    private fun topBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(AccentBar(this, 12f, 34f), LinearLayout.LayoutParams(ui(12f), ui(34f)))
        bar.addView(
            tv("NOCTURNE", 26f, Color.WHITE, spacing = 0.14f),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = ui(14f) },
        )
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val clockCol = LinearLayout(this)
        clockCol.orientation = LinearLayout.VERTICAL
        clockCol.gravity = Gravity.END

        val time = TextClock(this)
        time.format12Hour = "h:mm a"
        time.format24Hour = "h:mm a"
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(30f))
        time.setTextColor(Color.WHITE)
        time.typeface = Theme.display
        clockCol.addView(time)

        val date = TextClock(this)
        date.format12Hour = "EEE dd MMM"
        date.format24Hour = "EEE dd MMM"
        date.isAllCaps = true
        date.letterSpacing = 0.2f
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(13f))
        date.setTextColor(Theme.textDim)
        date.typeface = Theme.body
        clockCol.addView(date)

        bar.addView(clockCol)
        return bar
    }

    private fun row(label: String, tiles: List<SkewTile>): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.clipChildren = false
        col.clipToPadding = false
        col.addView(tv(label, 13f, Theme.textDim, Theme.body, 0.3f).apply { setPadding(ui(4f), 0, 0, 0) })

        val strip = LinearLayout(this)
        strip.orientation = LinearLayout.HORIZONTAL
        strip.clipChildren = false
        strip.clipToPadding = false
        strip.setPadding(ui(12f), ui(14f), ui(12f), ui(6f))
        tiles.forEach { strip.addView(it) }

        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.clipChildren = false
        hs.clipToPadding = false
        hs.addView(strip)
        col.addView(hs)
        return col
    }

    private fun confirmRemove(s: Site) {
        AlertDialog.Builder(this)
            .setTitle("Remove ${s.name}?")
            .setMessage("This only removes the tile. The website itself is not affected.")
            .setPositiveButton("Remove") { _, _ ->
                Sites.remove(this, s.url)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Opens the TV's own home screen (not Nocturne) so you can switch between the two. */
    private fun openStockHome() {
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val others = packageManager.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
        val pick = others.firstOrNull { (it.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 }
            ?: others.firstOrNull()
        if (pick == null) {
            Toast.makeText(this, "No other home screen found on this TV", Toast.LENGTH_SHORT).show()
            return
        }
        safeStart(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setClassName(pick.activityInfo.packageName, pick.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun safeStart(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open that on this TV", Toast.LENGTH_SHORT).show()
        }
    }
}
