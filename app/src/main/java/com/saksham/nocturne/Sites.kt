package com.saksham.nocturne

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RawSite(val name: String, val url: String, val logo: String)

data class Site(val name: String, val url: String, val logoName: String, val logo: Bitmap?)

object Images {
    /** Decodes an image, shrinking it if it is far bigger than needed. Returns null if missing or unreadable. */
    fun decode(f: File, max: Int): Bitmap? {
        if (!f.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > max * 2 || bounds.outHeight / sample > max * 2) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    fun wallpaper(ctx: Context): Bitmap? {
        val dir = Sites.themeDir(ctx)
        for (name in listOf("wallpaper.png", "wallpaper.jpg", "wallpaper.jpeg", "wallpaper.webp")) {
            val bmp = decode(File(dir, name), 1920)
            if (bmp != null) return bmp
        }
        return null
    }
}

object Sites {
    /** .../Android/data/com.saksham.nocturne/files/theme : wallpaper, sites.json, logos and adblock_hosts.txt live here. */
    fun themeDir(ctx: Context): File {
        val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val dir = File(base, "theme")
        File(dir, "logos").mkdirs()
        return dir
    }

    fun logosDir(ctx: Context): File = File(themeDir(ctx), "logos")

    /** Changes when sites.json, a logo or the wallpaper changes, so the home screen refreshes. */
    fun stamp(ctx: Context): String {
        val dir = themeDir(ctx)
        val parts = mutableListOf<Long>()
        parts.add(File(dir, "sites.json").lastModified())
        for (n in listOf("wallpaper.png", "wallpaper.jpg", "wallpaper.jpeg", "wallpaper.webp")) {
            parts.add(File(dir, n).lastModified())
        }
        logosDir(ctx).listFiles()?.forEach { parts.add(it.lastModified()) }
        return parts.joinToString(",")
    }

    fun readRaw(ctx: Context): MutableList<RawSite> {
        val override = File(themeDir(ctx), "sites.json")
        val text = try {
            if (override.exists()) override.readText()
            else ctx.assets.open("sites.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "[]"
        }
        val out = ArrayList<RawSite>()
        try {
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name")
                val url = o.optString("url")
                if (name.isBlank() || url.isBlank()) continue
                out.add(RawSite(name, url, o.optString("logo", "")))
            }
        } catch (e: Exception) {
            // A broken sites.json just shows no sites instead of crashing the home screen.
        }
        return out
    }

    private fun writeRaw(ctx: Context, list: List<RawSite>) {
        val arr = JSONArray()
        for (r in list) {
            arr.put(JSONObject().put("name", r.name).put("url", r.url).put("logo", r.logo))
        }
        File(themeDir(ctx), "sites.json").writeText(arr.toString(2))
    }

    fun load(ctx: Context): List<Site> {
        val logos = logosDir(ctx)
        return readRaw(ctx).map { r ->
            val bmp = if (r.logo.isNotBlank()) Images.decode(File(logos, r.logo), 512) else null
            Site(r.name, r.url, r.logo, bmp)
        }
    }

    /** Turns what someone typed into a usable web address, or "" if it can't be one. */
    fun normalizeUrl(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return ""
        if (!s.contains("://")) s = "https://$s"
        val uri = Uri.parse(s)
        val host = uri.host ?: return ""
        if (uri.scheme != "http" && uri.scheme != "https") return ""
        if (!host.contains('.') && host != "localhost") return ""
        return s
    }

    /** Returns false if that address is already a tile. */
    fun add(ctx: Context, name: String, url: String, logo: String = ""): Boolean {
        val list = readRaw(ctx)
        if (list.any { it.url == url }) return false
        list.add(RawSite(name, url, logo))
        writeRaw(ctx, list)
        return true
    }

    fun remove(ctx: Context, url: String) {
        val list = readRaw(ctx)
        list.removeAll { it.url == url }
        writeRaw(ctx, list)
    }

    fun setLogo(ctx: Context, url: String, logo: String) {
        val list = readRaw(ctx)
        val i = list.indexOfFirst { it.url == url }
        if (i < 0) return
        list[i] = list[i].copy(logo = logo)
        writeRaw(ctx, list)
    }
}
