package com.saksham.nocturne

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object Theme {
    val bgTop = Color.parseColor("#040A1D")
    val bgMid = Color.parseColor("#081847")
    val bgBot = Color.parseColor("#0B2A6E")
    val accent = Color.parseColor("#5CC8FF")
    val text = Color.parseColor("#E8F3FF")
    val textDim = Color.parseColor("#9FC3FF")
    val ink = Color.parseColor("#04122B")
    val panel = Color.parseColor("#9E0A1848")
    val panelHc = Color.parseColor("#0A1C55")
    val edge = Color.parseColor("#478FBEFF")
    val focusA = Color.parseColor("#E9FBFF")
    val focusB = Color.parseColor("#9FE4FF")

    val display: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    val body: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun alpha(c: Int, a: Int): Int = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
}

/** Layout units: the design was drawn on a 1280 unit wide canvas, so one unit scales with the screen width. */
fun Context.u(v: Float): Float = v * resources.displayMetrics.widthPixels / 1280f

fun Context.ui(v: Float): Int = u(v).toInt()

fun Context.tv(
    text: String,
    sizeU: Float,
    color: Int = Theme.text,
    face: Typeface = Theme.display,
    spacing: Float = 0f,
): TextView {
    val scale = Prefs.textScale(this)
    val view = TextView(this)
    view.text = text
    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, u(sizeU) * scale)
    view.setTextColor(color)
    view.typeface = face
    view.letterSpacing = spacing
    view.includeFontPadding = false
    return view
}
