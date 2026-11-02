package com.saksham.nocturne

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.widget.LinearLayout

/** The small slanted accent bar used as a logo mark and as an underline. */
class AccentBar(ctx: Context, private val wUnits: Float, private val hUnits: Float) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.accent }
    private val path = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val s = minOf(w * 0.4f, context.u(6f))
        path.reset()
        path.moveTo(s, 0f)
        path.lineTo(w.toFloat(), 0f)
        path.lineTo(w - s, h.toFloat())
        path.lineTo(0f, h.toFloat())
        path.close()
    }

    override fun onDraw(c: Canvas) {
        c.drawPath(path, p)
    }
}

object UiKit {
    /** Big title, accent underline and a dim subtitle. */
    fun header(ctx: Context, title: String, subtitle: String): LinearLayout {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.addView(ctx.tv(title, 64f, Theme.text))
        col.addView(
            AccentBar(ctx, 180f, 6f),
            LinearLayout.LayoutParams(ctx.ui(180f), ctx.ui(6f)).apply {
                topMargin = ctx.ui(10f)
                bottomMargin = ctx.ui(10f)
            },
        )
        col.addView(ctx.tv(subtitle, 18f, Theme.textDim, Theme.body))
        return col
    }
}
