package com.saksham.nocturne

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator

/**
 * The slanted focusable tile used everywhere. On focus it scales up, fills light and pulses a glow.
 * A tile can show an app banner (the app's own logo art), an icon plus label, a site logo, or a wide label row with a status.
 */
class SkewTile(
    ctx: Context,
    val label: String,
    private val sub: String = "",
    private val icon: Drawable? = null,
    private val banner: Drawable? = null,
    private val logo: Bitmap? = null,
    wUnits: Float = 176f,
    hUnits: Float = 99f,
    private val tintIcon: Boolean = false,
) : View(ctx) {

    var status: String? = null
        set(value) {
            field = value
            contentDescription = if (value == null) label else "$label, $value"
            invalidate()
        }

    var onFocusLabel: ((String, String) -> Unit)? = null

    private val hc = Prefs.highContrast(ctx)
    private val motion = Prefs.motionEnabled(ctx)
    private val wide = wUnits >= 400f
    private val skew = ctx.u(14f)

    private val path = Path()
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Theme.display
        textSize = ctx.u(if (wide) 22f else 20f) * Prefs.textScale(ctx)
    }
    private val subP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Theme.body
        textSize = ctx.u(13f) * Prefs.textScale(ctx)
    }
    private val bigP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Theme.display }
    private val iconTmp = Rect()
    private var focusShader: Shader? = null

    private var focusAmt = 0f
    private var pulse = 0f
    private var focusAnim: ValueAnimator? = null
    private var pulseAnim: ValueAnimator? = null

    init {
        isFocusable = true
        isClickable = true
        contentDescription = label
        setDefaultFocusHighlightEnabled(false)
        layoutParams = ViewGroup.MarginLayoutParams(ctx.ui(wUnits), ctx.ui(hUnits)).apply {
            rightMargin = ctx.ui(12f)
            bottomMargin = ctx.ui(8f)
        }
        if (tintIcon) icon?.mutate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = context.u(3f)
        path.reset()
        path.moveTo(skew + pad, pad)
        path.lineTo(w - pad, pad)
        path.lineTo(w - skew - pad, h - pad)
        path.lineTo(pad, h - pad)
        path.close()
        focusShader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Theme.focusA, Theme.focusB, Shader.TileMode.CLAMP)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animateTo(if (gainFocus) 1f else 0f)
        if (gainFocus) onFocusLabel?.invoke(label, sub)
        if (gainFocus && motion) startPulse() else stopPulse()
    }

    private fun animateTo(target: Float) {
        focusAnim?.cancel()
        if (!motion) {
            setAmount(target)
            return
        }
        focusAnim = ValueAnimator.ofFloat(focusAmt, target).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { setAmount(it.animatedValue as Float) }
            start()
        }
    }

    private fun setAmount(a: Float) {
        focusAmt = a
        val s = 1f + 0.08f * a
        scaleX = s
        scaleY = s
        translationZ = a * context.u(10f)
        invalidate()
    }

    private fun startPulse() {
        if (pulseAnim != null) return
        pulseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopPulse() {
        pulseAnim?.cancel()
        pulseAnim = null
        pulse = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        focusAnim?.cancel()
        stopPulse()
        super.onDetachedFromWindow()
    }

    private fun mix(a: Int, b: Int): Int = evaluator.evaluate(focusAmt, a, b) as Int

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val u = context.u(1f)

        if (focusAmt > 0f) {
            for (i in 3 downTo 1) {
                strokeP.color = Theme.alpha(Theme.accent, ((40 + 50 * pulse) * focusAmt / i).toInt())
                strokeP.strokeWidth = u * 2f * i * 2.2f
                c.drawPath(path, strokeP)
            }
        }

        fillP.shader = null
        fillP.color = if (hc) Theme.panelHc else Theme.panel
        c.drawPath(path, fillP)
        if (focusAmt > 0f) {
            fillP.shader = focusShader
            fillP.alpha = (255 * focusAmt).toInt()
            c.drawPath(path, fillP)
            fillP.shader = null
            fillP.alpha = 255
        }

        c.save()
        c.clipPath(path)
        drawContent(c, w, h, u)
        c.restore()

        strokeP.color = if (hc) Color.WHITE else mix(Theme.edge, Color.WHITE)
        strokeP.strokeWidth = u * 1.6f
        c.drawPath(path, strokeP)
    }

    private fun drawContent(c: Canvas, w: Float, h: Float, u: Float) {
        if (banner != null) {
            banner.setBounds(0, 0, width, height)
            banner.draw(c)
            if (focusAmt < 1f) {
                fillP.color = Theme.alpha(Color.parseColor("#050B1F"), (70 * (1f - focusAmt)).toInt())
                c.drawRect(0f, 0f, w, h, fillP)
            }
            return
        }

        val labelColor = mix(Theme.text, Theme.ink)
        val subColor = mix(Theme.textDim, Theme.ink)

        if (wide) {
            val x = skew + u * 18f
            textP.color = labelColor
            textP.textAlign = Paint.Align.LEFT
            val y = if (sub.isEmpty()) h * 0.62f else h * 0.5f
            c.drawText(label, x, y, textP)
            if (sub.isNotEmpty()) {
                subP.color = subColor
                c.drawText(sub, x, h * 0.8f, subP)
            }
            status?.let { s ->
                textP.textAlign = Paint.Align.RIGHT
                c.drawText(s, w - u * 24f, h * 0.62f, textP)
                textP.textAlign = Paint.Align.LEFT
            }
            return
        }

        val padX = skew + u * 8f
        val box = h * 0.42f
        val top = u * 10f
        if (icon != null) {
            if (tintIcon) icon.setTint(labelColor)
            iconTmp.set(padX.toInt(), top.toInt(), (padX + box).toInt(), (top + box).toInt())
            icon.setBounds(iconTmp.left, iconTmp.top, iconTmp.right, iconTmp.bottom)
            icon.draw(c)
        } else if (logo != null) {
            val dst = RectF(padX, top, padX + box, top + box)
            val s = minOf(box / logo.width, box / logo.height)
            val dw = logo.width * s
            val dh = logo.height * s
            val l = dst.left + (box - dw) / 2f
            val t = dst.top + (box - dh) / 2f
            c.drawBitmap(logo, null, RectF(l, t, l + dw, t + dh), null)
        } else {
            bigP.textSize = box
            bigP.color = mix(Theme.accent, Theme.ink)
            val letter = label.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            c.drawText(letter, padX, top + box * 0.85f, bigP)
        }

        textP.color = labelColor
        textP.textAlign = Paint.Align.LEFT
        val avail = w - padX - skew - u * 8f
        val shown = TextUtils.ellipsize(label.uppercase(), textP, avail, TextUtils.TruncateAt.END).toString()
        c.drawText(shown, padX, h - u * 12f, textP)
    }

    companion object {
        private val evaluator = ArgbEvaluator()
    }
}
