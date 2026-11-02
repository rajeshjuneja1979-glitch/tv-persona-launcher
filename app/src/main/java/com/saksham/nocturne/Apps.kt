package com.saksham.nocturne

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppEntry(
    val pkg: String,
    val label: String,
    val intent: Intent,
    val icon: Drawable,
    val banner: Drawable?,
    val tv: Boolean,
)

object Apps {
    /** Cheap fingerprint of what is installed, so the home screen only rebuilds when something changed. */
    fun signature(ctx: Context): String {
        val pm = ctx.packageManager
        val pkgs = sortedSetOf<String>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) pkgs.add(ri.activityInfo.packageName)
        }
        return pkgs.joinToString(",")
    }

    /**
     * Every app that can be launched, TV apps first, then phone-style apps.
     * Nothing is filtered out: apps without a TV entry point still show up in All Apps.
     */
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val out = LinkedHashMap<String, AppEntry>()

        fun scan(category: String, tv: Boolean) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || out.containsKey(pkg)) continue
                val launch = (if (tv) pm.getLeanbackLaunchIntentForPackage(pkg) else null)
                    ?: pm.getLaunchIntentForPackage(pkg)
                    ?: continue
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                out[pkg] = AppEntry(
                    pkg = pkg,
                    label = ri.loadLabel(pm).toString(),
                    intent = launch,
                    icon = ri.loadIcon(pm),
                    banner = ri.activityInfo.loadBanner(pm),
                    tv = tv,
                )
            }
        }

        scan(Intent.CATEGORY_LEANBACK_LAUNCHER, true)
        scan(Intent.CATEGORY_LAUNCHER, false)
        return out.values.toList()
    }
}
