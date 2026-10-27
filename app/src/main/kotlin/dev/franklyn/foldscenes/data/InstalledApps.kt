package dev.franklyn.foldscenes.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

/** Live app list and default camera from PackageManager: nothing is hard-coded to one OEM. */
object InstalledApps {
    fun launcherApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .filter { it.activityInfo.packageName != context.packageName }
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
    }

    /** The default camera, or null when none resolves (the Apps screen then hides the locked row). */
    fun camera(context: Context): CameraRow? = runCatching {
        val pm = context.packageManager
        val info = pm.resolveActivity(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        ) ?: return null
        val pkg = info.activityInfo?.packageName
        cameraRowFor(pkg, pkg?.let { appLabel(pm, it) } ?: info.loadLabel(pm).toString())
    }.getOrNull()

    private fun appLabel(pm: PackageManager, pkg: String): String? = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
    }.getOrNull()
}
