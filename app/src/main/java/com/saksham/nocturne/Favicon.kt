package com.saksham.nocturne

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a site's icon in the background and saves it as that site's logo.
 * It asks Google's public favicon service for a clean PNG, which tells Google which domain you added.
 * If that fails, the tile just shows the site's first letter. You can always drop your own logo into theme/logos.
 */
object Favicon {
    fun fetch(ctx: Context, siteUrl: String) {
        val app = ctx.applicationContext
        Thread {
            try {
                val host = Uri.parse(siteUrl).host ?: return@Thread
                val fileName = host.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png"
                val out = File(Sites.logosDir(app), fileName)
                val src = "https://www.google.com/s2/favicons?sz=128&domain=" + Uri.encode(host)
                val conn = URL(src).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.inputStream.use { input -> out.outputStream().use { o -> input.copyTo(o) } }
                if (Images.decode(out, 256) != null) Sites.setLogo(app, siteUrl, fileName) else out.delete()
            } catch (e: Exception) {
                // No logo is fine; the tile falls back to a letter.
            }
        }.start()
    }
}
