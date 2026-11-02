package com.saksham.nocturne

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** The pointer drawn over the page. It turns accent blue over something clickable and ripples on click. */
class CursorView(ctx: Context) : View(ctx) {
    var active = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var px = 0f
    private var py = 0f
    private var ripple = 0f
    private var anim: ValueAnimator? = null
    private val motion = Prefs.motionEnabled(ctx)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arrow = Path()

    init {
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun moveTo(x: Float, y: Float) {
        px = x
        py = y
        invalidate()
    }

    fun ripple() {
        if (!motion) return
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            addUpdateListener {
                ripple = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val u = context.u(1f)

        if (ripple > 0f && ripple < 1f) {
            line.color = Theme.alpha(Theme.accent, ((1f - ripple) * 200).toInt())
            line.strokeWidth = u * 2.5f
            c.drawCircle(px, py, u * (8f + 28f * ripple), line)
        }

        arrow.reset()
        arrow.moveTo(0f, 0f)
        arrow.lineTo(0f, u * 24f)
        arrow.lineTo(u * 6f, u * 18.5f)
        arrow.lineTo(u * 10.5f, u * 28f)
        arrow.lineTo(u * 14.5f, u * 26f)
        arrow.lineTo(u * 10f, u * 17f)
        arrow.lineTo(u * 17f, u * 17f)
        arrow.close()

        c.save()
        c.translate(px, py)
        if (active) {
            fill.color = Theme.alpha(Theme.accent, 70)
            c.drawCircle(u * 4f, u * 4f, u * 16f, fill)
        }
        fill.color = if (active) Theme.accent else Color.WHITE
        c.drawPath(arrow, fill)
        line.color = Theme.ink
        line.strokeWidth = u * 1.6f
        c.drawPath(arrow, line)
        c.restore()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }
}
