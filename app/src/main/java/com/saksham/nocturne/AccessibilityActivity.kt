package com.saksham.nocturne

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Accessibility shortcuts. The first two rows open Android's own screens (screen reader, color correction, captions).
 * The other rows are launcher-only options. There is deliberately nothing here that changes sound.
 */
class AccessibilityActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.addView(NightBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(ui(64f), ui(34f), ui(64f), 0)
        column.clipChildren = false
        column.clipToPadding = false
        column.addView(UiKit.header(this, "ACCESSIBILITY", "Options for this launcher and shortcuts to the system ones."))

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.clipChildren = false
        list.clipToPadding = false
        list.setPadding(ui(12f), ui(20f), 0, 0)

        val rows = ArrayList<SkewTile>()

        rows.add(
            SkewTile(this, "SYSTEM ACCESSIBILITY", "Screen reader, color correction and more", wUnits = 720f, hUnits = 58f).also { t ->
                t.setOnClickListener { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            },
        )
        rows.add(
            SkewTile(this, "CAPTIONS", "Subtitle style and size", wUnits = 720f, hUnits = 58f).also { t ->
                t.setOnClickListener { open(Intent(Settings.ACTION_CAPTIONING_SETTINGS)) }
            },
        )
        rows.add(
            SkewTile(this, "HIGH CONTRAST", "Brighter borders and flat panels in this launcher", wUnits = 720f, hUnits = 58f).also { t ->
                t.status = onOff(Prefs.highContrast(this))
                t.setOnClickListener {
                    Prefs.setHighContrast(this, !Prefs.highContrast(this))
                    recreate()
                }
            },
        )
        rows.add(
            SkewTile(this, "REDUCE MOTION", "Turns off animations in this launcher", wUnits = 720f, hUnits = 58f).also { t ->
                t.status = onOff(Prefs.reduceMotion(this))
                t.setOnClickListener {
                    Prefs.setReduceMotion(this, !Prefs.reduceMotion(this))
                    recreate()
                }
            },
        )
        rows.add(
            SkewTile(this, "TEXT SIZE", "Larger text in this launcher", wUnits = 720f, hUnits = 58f).also { t ->
                t.status = listOf("100%", "115%", "130%")[Prefs.textLevel(this)]
                t.setOnClickListener {
                    Prefs.setTextLevel(this, (Prefs.textLevel(this) + 1) % 3)
                    recreate()
                }
            },
        )

        rows.forEach { list.addView(it) }
        column.addView(list)

        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        rows.firstOrNull()?.let { f -> f.post { f.requestFocus() } }
    }

    private fun onOff(v: Boolean): String = if (v) "ON" else "OFF"

    private fun open(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show()
        }
    }
}
