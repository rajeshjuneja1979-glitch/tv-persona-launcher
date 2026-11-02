package com.saksham.nocturne

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Network-level ad blocking for the site browser. Any request to a listed domain (or one of its subdomains)
 * gets an empty answer, so ads, trackers and popunder scripts never load, whatever the page's DOM looks like.
 * The list is assets/adblock_hosts.txt plus an optional theme/adblock_hosts.txt you can add your own lines to.
 * Both accept one domain per line, or hosts-file lines like "0.0.0.0 ads.example.com". "#" starts a comment.
 */
object AdBlock {
    @Volatile
    private var hosts: Set<String> = emptySet()

    @Volatile
    private var loaded = false

    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        val set = HashSet<String>()

        fun read(text: String) {
            for (raw in text.lineSequence()) {
                val line = raw.substringBefore('#').trim().lowercase()
                if (line.isEmpty()) continue
                val domain = line.split(Regex("\\s+")).last()
                if (domain.contains('.')) set.add(domain)
            }
        }

        try {
            ctx.assets.open("adblock_hosts.txt").bufferedReader().use { read(it.readText()) }
        } catch (e: Exception) {
            // Missing built-in list: only the user's own list applies.
        }
        try {
            val extra = File(Sites.themeDir(ctx), "adblock_hosts.txt")
            if (extra.exists()) read(extra.readText())
        } catch (e: Exception) {
            // Ignore an unreadable extra list.
        }
        hosts = set
        loaded = true
    }

    fun isBlocked(host: String?): Boolean {
        if (host == null) return false
        val set = hosts
        var h = host.lowercase()
        while (h.contains('.')) {
            if (set.contains(h)) return true
            h = h.substringAfter('.')
        }
        return false
    }

    fun empty(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "utf-8",
        200,
        "OK",
        mapOf("Access-Control-Allow-Origin" to "*"),
        ByteArrayInputStream(ByteArray(0)),
    )
}
