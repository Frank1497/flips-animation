package dev.franklyn.foldscenes.fold

import kotlinx.coroutines.flow.MutableStateFlow

/** True when the foreground app must never show the fold scene: user-excluded, or the default camera. */
fun appSuppressed(foreground: String?, excluded: Set<String>, cameraPackage: String?): Boolean =
    foreground != null && (foreground in excluded || foreground == cameraPackage)

/** True when the last-known foreground app of any display must not show the scene. */
fun anyAppSuppressed(foregrounds: Collection<String>, excluded: Set<String>, cameraPackage: String?): Boolean =
    foregrounds.any { appSuppressed(it, excluded, cameraPackage) }

/**
 * The single overlay-suppression rule, evaluated every frame from live state: any open camera (any process),
 * or any display's last foreground app being excluded or the default camera app.
 */
fun shouldSuppressOverlay(
    cameraActive: Boolean,
    foregrounds: Collection<String>,
    excluded: Set<String>,
    cameraPackage: String?,
): Boolean = cameraActive || anyAppSuppressed(foregrounds, excluded, cameraPackage)

/**
 * Sticky for one fold: once [shouldSuppressOverlay] is true during a fold, it stays true until the hinge
 * settles, so the cover launcher taking the foreground mid-fold cannot un-suppress it. It can only turn
 * suppression ON mid-fold, never off. (The previous latch froze the fold-start value, including "allowed",
 * so a camera or excluded app reaching the foreground after the gate armed was ignored for the whole fold.)
 */
class FoldSuppression {
    private var held = false
    private var reason: String? = null
    private var finished: String? = null

    /**
     * [describe] explains the current decision; it is recorded at fold start and again the moment the fold
     * first becomes suppressed, so the finished fold reports why it was (or was not) suppressed.
     */
    fun update(moving: Boolean, suppressedNow: Boolean, describe: () -> String = { "" }): Boolean {
        if (!moving) {
            if (reason != null) finished = reason
            reason = null
            held = false
            return suppressedNow
        }
        if (reason == null || (suppressedNow && !held)) reason = describe()
        held = held || suppressedNow
        return held
    }

    /** The last finished fold's summary, once; null when no fold has ended since the previous call. */
    fun takeFinishedFold(): String? = finished.also { finished = null }
}

/** One log line per fold, naming the reason, so a device report says why the scene did or did not play. */
fun foldDecisionText(
    cameraActive: Boolean,
    foregrounds: Collection<String>,
    excluded: Set<String>,
    cameraPackage: String?,
): String {
    if (cameraActive) return "suppressed: camera in use"
    val hit = foregrounds.firstOrNull { appSuppressed(it, excluded, cameraPackage) }
    return when {
        hit != null -> "suppressed: $hit"
        foregrounds.isEmpty() -> "played (foreground: none seen)"
        else -> "played (foreground: ${foregrounds.joinToString()})"
    }
}

/** What the running service currently believes, for the in-app diagnostics block. */
data class SuppressionSnapshot(
    val connected: Boolean = false,
    val foreground: Map<Int, String> = emptyMap(),
    val camerasInUse: Set<String> = emptySet(),
    val cameraPackage: String? = null,
    val excludedCount: Int = 0,
    val lastFold: String? = null,
)

/** Written by FoldOverlayService (main thread), read by the Settings screen; same process. */
object SuppressionDiagnostics {
    val state = MutableStateFlow(SuppressionSnapshot())
}

fun diagnosticsLines(s: SuppressionSnapshot): List<String> = listOf(
    "Service: " + if (s.connected) "running" else "not running",
    "Foreground: " + if (s.foreground.isEmpty()) {
        "none seen yet"
    } else {
        s.foreground.toSortedMap().entries.joinToString { (d, p) -> "display $d $p" }
    },
    "Camera in use: " + if (s.camerasInUse.isEmpty()) "no" else "yes (${s.camerasInUse.sorted().joinToString()})",
    "Default camera: " + (s.cameraPackage ?: "none resolved"),
    "Excluded apps: ${s.excludedCount}",
    "Last fold: " + (s.lastFold ?: "none yet"),
)
