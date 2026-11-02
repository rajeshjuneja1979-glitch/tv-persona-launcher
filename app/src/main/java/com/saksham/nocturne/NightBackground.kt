package com.saksham.nocturne

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * The night sky. The still layers (gradient, moon, skyline) are drawn once into a bitmap.
 * Each frame only redraws twinkling stars, a few lit windows and a slow light sweep, so it stays light on a TV chip.
 * If theme/wallpaper.png (or .jpg/.webp) exists, it replaces the moon and skyline.
 */
class NightBackground(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sweepPaint = Paint()
    private val motion = Prefs.motionEnabled(ctx)
    private val wallpaper: Bitmap? = Images.wallpaper(ctx)

    private val starX = FloatArray(40)
    private val starY = FloatArray(40)
    private val starR = FloatArray(40)
    private val starPhase = FloatArray(40)
    private var windows = FloatArray(0)

    private var base: Bitmap? = null
    private var t = 0f
    private var animator: ValueAnimator? = null

    init {
        val rnd = Random(7)
        for (i in starX.indices) {
            starX[i] = rnd.nextFloat()
            starY[i] = rnd.nextFloat() * 0.55f
            starR[i] = 0.5f + rnd.nextFloat() * 1.6f
            starPhase[i] = rnd.nextFloat()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        base = buildBase(w, h)
        sweepPaint.shader = LinearGradient(
            0f, 0f, w * 0.25f, 0f,
            intArrayOf(Theme.alpha(Theme.accent, 0), Theme.alpha(Theme.accent, 38), Theme.alpha(Theme.accent, 0)),
            null, Shader.TileMode.CLAMP,
        )
    }

    private fun buildBase(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val wf = w.toFloat()
        val hf = h.toFloat()

        val wp = wallpaper
        if (wp != null) {
            val scale = maxOf(wf / wp.width, hf / wp.height)
            val dw = wp.width * scale
            val dh = wp.height * scale
            c.drawBitmap(wp, null, RectF((wf - dw) / 2f, (hf - dh) / 2f, (wf + dw) / 2f, (hf + dh) / 2f), p)
            p.shader = LinearGradient(
                0f, 0f, 0f, hf,
                intArrayOf(Theme.alpha(Theme.bgTop, 150), Theme.alpha(Theme.bgTop, 90), Theme.alpha(Theme.bgTop, 200)),
                null, Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, wf, hf, p)
            p.shader = null
        } else {
            p.shader = LinearGradient(
                0f, 0f, 0f, hf,
                intArrayOf(Theme.bgTop, Theme.bgMid, Theme.bgBot),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, wf, hf, p)
            p.shader = null

            val cx = wf * 0.78f
            val cy = hf * 0.22f
            val r = hf * 0.17f
            p.shader = RadialGradient(
                cx, cy, r * 2.4f,
                Theme.alpha(Theme.accent, 90), Theme.alpha(Theme.accent, 0), Shader.TileMode.CLAMP,
            )
            c.drawCircle(cx, cy, r * 2.4f, p)
            p.shader = RadialGradient(
                cx - r * 0.25f, cy - r * 0.3f, r * 1.2f,
                intArrayOf(Color.parseColor("#F4FBFF"), Color.parseColor("#CFE9FF"), Color.parseColor("#8FBDF0")),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawCircle(cx, cy, r, p)
            p.shader = null

            p.style = Paint.Style.STROKE
            p.strokeWidth = hf * 0.002f
            p.color = Theme.alpha(Theme.accent, 130)
            p.pathEffect = DashPathEffect(floatArrayOf(hf * 0.003f, hf * 0.014f), 0f)
            c.drawCircle(cx, cy, r * 1.35f, p)
            p.pathEffect = null
            p.style = Paint.Style.FILL

            val rnd = Random(11)
            val lit = ArrayList<Float>()
            p.color = Color.parseColor("#030818")
            var x = 0f
            while (x < wf) {
                val bw = wf * (0.04f + rnd.nextFloat() * 0.05f)
                val bh = hf * (0.05f + rnd.nextFloat() * 0.17f)
                c.drawRect(x, hf - bh, x + bw, hf, p)
                if (rnd.nextFloat() < 0.5f) {
                    lit.add(x + bw * 0.3f)
                    lit.add(hf - bh + hf * 0.02f + rnd.nextFloat() * bh * 0.5f)
                }
                x += bw
            }
            windows = lit.toFloatArray()
        }

        p.shader = RadialGradient(
            wf / 2f, hf * 0.4f, wf * 0.75f,
            intArrayOf(Theme.alpha(Color.parseColor("#020614"), 0), Theme.alpha(Color.parseColor("#020614"), 0), Theme.alpha(Color.parseColor("#020614"), 165)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, wf, hf, p)
        return bmp
    }

    override fun onDraw(canvas: Canvas) {
        val b = base ?: return
        canvas.drawBitmap(b, 0f, 0f, null)
        val w = width.toFloat()
        val h = height.toFloat()

        if (wallpaper == null) {
            paint.style = Paint.Style.FILL
            for (i in starX.indices) {
                val a = 60 + (195 * (0.5f + 0.5f * sin(2.0 * PI * (t * 3f + starPhase[i])).toFloat())).toInt()
                paint.color = Theme.alpha(Color.parseColor("#DFF1FF"), a)
                canvas.drawCircle(starX[i] * w, starY[i] * h, starR[i] * (w / 1280f), paint)
            }
            var i = 0
            while (i + 1 < windows.size) {
                val a = 40 + (200 * (0.5f + 0.5f * sin(2.0 * PI * (t * 2f + i * 0.13f)).toFloat())).toInt()
                paint.color = Theme.alpha(Theme.accent, a)
                val s = w / 1280f * 4f
                canvas.drawRect(windows[i], windows[i + 1], windows[i] + s, windows[i + 1] + s, paint)
                i += 2
            }
        }

        if (motion) {
            val x = -w * 0.3f + t * w * 1.35f
            canvas.save()
            canvas.translate(x, 0f)
            canvas.skew(-0.3f, 0f)
            canvas.drawRect(0f, 0f, w * 0.25f, h, sweepPaint)
            canvas.restore()
        }
    }

    private fun start() {
        if (!motion || animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 12000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) start() else stop()
    }
}
