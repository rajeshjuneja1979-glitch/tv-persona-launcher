package com.saksham.nocturne

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast

/** Type any web address and it becomes a tile on the home screen. */
class AddSiteActivity : Activity() {
    private lateinit var nameBox: EditText
    private lateinit var urlBox: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.addView(NightBackground(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(ui(64f), ui(34f), ui(64f), 0)
        column.addView(UiKit.header(this, "ADD A SITE", "Type any address. It becomes a tile on your home screen."))

        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(ui(12f), ui(28f), 0, 0)

        urlBox = field("Address, for example example.com", true)
        nameBox = field("Name (optional)", false)
        urlBox.imeOptions = EditorInfo.IME_ACTION_NEXT
        nameBox.imeOptions = EditorInfo.IME_ACTION_DONE
        nameBox.setOnEditorActionListener { _, _, _ ->
            save()
            true
        }
        form.addView(urlBox)
        form.addView(nameBox)

        val saveTile = SkewTile(this, "SAVE AS APP", "Adds a tile to your home screen", wUnits = 420f, hUnits = 58f)
        saveTile.setOnClickListener { save() }
        form.addView(saveTile, LinearLayout.LayoutParams(ui(420f), ui(58f)).apply { topMargin = ui(10f) })

        column.addView(form)
        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        urlBox.requestFocus()
    }

    private fun field(hint: String, isUrl: Boolean): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(Theme.textDim)
        e.setTextColor(Color.WHITE)
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(22f) * Prefs.textScale(this))
        e.typeface = Theme.body
        e.inputType = InputType.TYPE_CLASS_TEXT or
            (if (isUrl) InputType.TYPE_TEXT_VARIATION_URI else InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val bg = GradientDrawable()
        bg.setColor(if (Prefs.highContrast(this)) Theme.panelHc else Theme.panel)
        bg.setStroke(ui(2f), Theme.accent)
        e.background = bg
        e.setPadding(ui(18f), ui(12f), ui(18f), ui(12f))
        e.layoutParams = LinearLayout.LayoutParams(ui(720f), WRAP).apply { bottomMargin = ui(14f) }
        return e
    }

    private fun save() {
        val url = Sites.normalizeUrl(urlBox.text.toString())
        if (url.isEmpty()) {
            Toast.makeText(this, "That doesn't look like a web address", Toast.LENGTH_LONG).show()
            return
        }
        val typed = nameBox.text.toString().trim()
        val host = (Uri.parse(url).host ?: url).removePrefix("www.")
        val name = if (typed.isEmpty()) host else typed
        if (Sites.add(this, name, url)) {
            Favicon.fetch(this, url)
            Toast.makeText(this, "Added $name", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "That site is already on your home screen", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
