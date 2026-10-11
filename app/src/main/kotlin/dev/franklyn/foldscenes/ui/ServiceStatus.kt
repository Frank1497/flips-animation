package dev.franklyn.foldscenes.ui

import android.content.Context
import android.provider.Settings
import dev.franklyn.foldscenes.overlay.FoldOverlayService

/** Whether the fold-animation accessibility service is enabled. */
object ServiceStatus {
    fun isEnabled(context: Context): Boolean {
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        return isServiceListed(setting, context.packageName, FoldOverlayService::class.java.name)
    }
}

/**
 * True when [setting] (the `:`-separated `enabled_accessibility_services` value) lists [cls] of [pkg],
 * in either the full form `pkg/pkg.overlay.X` or the short form `pkg/.overlay.X`. Entries must match
 * exactly, so a look-alike package or class is not a hit.
 */
fun isServiceListed(setting: String?, pkg: String, cls: String): Boolean {
    if (setting.isNullOrBlank()) return false
    return setting.split(':').any { entry ->
        val parts = entry.trim().split('/')
        if (parts.size != 2 || parts[0] != pkg) return@any false
        val c = parts[1]
        val full = if (c.startsWith(".")) pkg + c else c
        full == cls
    }
}
