package com.saksham.nocturne

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Every launchable app, sorted by name. Nothing is hidden because it is not flagged for TV. */
class AllAppsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.addView(NightBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(ui(64f), ui(34f), ui(64f), 0)
        column.clipChildren = false
        column.clipToPadding = false
        column.addView(UiKit.header(this, "ALL APPS", "Everything installed. Nothing hidden."))

        val grid = GridLayout(this)
        grid.columnCount = 6
        grid.clipChildren = false
        grid.clipToPadding = false
        grid.setPadding(ui(12f), ui(18f), ui(12f), ui(40f))

        val apps = Apps.load(this).sortedBy { it.label.lowercase() }
        var first: SkewTile? = null
        for (e in apps) {
            val tile = SkewTile(this, e.label, icon = e.icon, wUnits = 170f, hUnits = 96f)
            tile.setOnClickListener { launch(e.intent) }
            grid.addView(tile)
            if (first == null) first = tile
        }

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.clipChildren = false
        scroll.clipToPadding = false
        scroll.addView(grid)
        column.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        first?.let { f -> f.post { f.requestFocus() } }
    }

    private fun launch(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open that on this TV", Toast.LENGTH_SHORT).show()
        }
    }
}
