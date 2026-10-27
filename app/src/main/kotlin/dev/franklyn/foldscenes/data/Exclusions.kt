package dev.franklyn.foldscenes.data

/** A launcher-visible app: package name plus its user-facing label. */
data class AppEntry(val packageName: String, val label: String)

/** The pinned, locked camera row. Absent entirely when no camera app resolves. */
data class CameraRow(val packageName: String, val label: String)

/** [excluded] with [packageName] allowed (removed) or excluded (added). Blank names are ignored. */
fun withAppAllowed(excluded: Set<String>, packageName: String, allowed: Boolean): Set<String> = when {
    packageName.isBlank() -> excluded
    allowed -> excluded - packageName
    else -> excluded + packageName
}

/** Excluded apps that are still installed; before the app list loads, every stored entry counts. */
fun excludedCount(excluded: Set<String>, installed: Set<String>?): Int =
    if (installed == null) excluded.size else excluded.count { it in installed }

fun exclusionSummary(count: Int): String = when (count) {
    0 -> "All apps allowed"
    1 -> "1 app excluded"
    else -> "$count apps excluded"
}

/**
 * The default camera's package from `resolveActivity(ACTION_IMAGE_CAPTURE)`, or null when nothing usable
 * resolved: no camera at all, or several cameras and no default, in which case the system's chooser
 * (package "android") comes back instead of a camera.
 */
fun cameraPackageOrNull(resolvedPackage: String?): String? =
    resolvedPackage?.takeIf { it.isNotBlank() && it != CHOOSER_PACKAGE }

/** The camera row to show, or null to hide it (never a row with a broken label or package). */
fun cameraRowFor(resolvedPackage: String?, label: String?): CameraRow? =
    cameraPackageOrNull(resolvedPackage)?.let { CameraRow(it, label?.takeIf { l -> l.isNotBlank() } ?: "Camera") }

/** The togglable rows: distinct packages, camera removed (it has its own locked row), sorted by label. */
fun buildAppRows(apps: List<AppEntry>, cameraPackage: String?): List<AppEntry> =
    apps.filter { it.packageName.isNotBlank() && it.packageName != cameraPackage }
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, AppEntry::label).thenBy { it.packageName })

private const val CHOOSER_PACKAGE = "android"

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

fun parseThemeMode(stored: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
