package com.saksham.nocturne

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

/** Launcher-only preferences. Nothing here touches system settings, and nothing touches sound. */
object Prefs {
    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences("nocturne", Context.MODE_PRIVATE)

    fun reduceMotion(c: Context): Boolean = sp(c).getBoolean("reduce_motion", false)
    fun setReduceMotion(c: Context, v: Boolean) = sp(c).edit().putBoolean("reduce_motion", v).apply()

    fun highContrast(c: Context): Boolean = sp(c).getBoolean("high_contrast", false)
    fun setHighContrast(c: Context, v: Boolean) = sp(c).edit().putBoolean("high_contrast", v).apply()

    /** Site browser options. */
    fun cursorMode(c: Context): Boolean = sp(c).getBoolean("cursor", true)
    fun setCursorMode(c: Context, v: Boolean) = sp(c).edit().putBoolean("cursor", v).apply()

    /** false = clicks go through the page's DOM, true = real touch taps. */
    fun tapClick(c: Context): Boolean = sp(c).getBoolean("tap_click", false)
    fun setTapClick(c: Context, v: Boolean) = sp(c).edit().putBoolean("tap_click", v).apply()

    fun adBlock(c: Context): Boolean = sp(c).getBoolean("ad_block", true)
    fun setAdBlock(c: Context, v: Boolean) = sp(c).edit().putBoolean("ad_block", v).apply()

    /** Page zoom in the site browser, in percent. */
    fun webZoom(c: Context): Int = sp(c).getInt("web_zoom", 100)
    fun setWebZoom(c: Context, v: Int) = sp(c).edit().putInt("web_zoom", v).apply()

    /** 0 = 100%, 1 = 115%, 2 = 130% */
    fun textLevel(c: Context): Int = sp(c).getInt("text_level", 0)
    fun setTextLevel(c: Context, v: Int) = sp(c).edit().putInt("text_level", v).apply()

    fun textScale(c: Context): Float = when (textLevel(c)) {
        1 -> 1.15f
        2 -> 1.3f
        else -> 1f
    }

    /** Animations run only if neither this launcher nor Android's own animation scale has them off. */
    fun motionEnabled(c: Context): Boolean {
        if (reduceMotion(c)) return false
        val scale = try {
            Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: Exception) {
            1f
        }
        return scale > 0f
    }

    fun stamp(c: Context): String =
        "${reduceMotion(c)}|${highContrast(c)}|${textLevel(c)}|${motionEnabled(c)}"
}
