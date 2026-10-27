package dev.franklyn.foldscenes.fold

/** True when the foreground app must never show the fold scene: user-excluded, or the default camera. */
fun appSuppressed(foreground: String?, excluded: Set<String>, cameraPackage: String?): Boolean =
    foreground != null && (foreground in excluded || foreground == cameraPackage)

/** True when the last-known foreground app of any display must not show the scene. */
fun anyAppSuppressed(foregrounds: Collection<String>, excluded: Set<String>, cameraPackage: String?): Boolean =
    foregrounds.any { appSuppressed(it, excluded, cameraPackage) }

/** An open camera (any process) suppresses live; the per-app decision is the latched one. */
fun overlaySuppressed(latchedApp: Boolean, cameraInUse: Boolean): Boolean = latchedApp || cameraInUse

/**
 * Freezes the per-app decision for the length of one fold. Folding shut hands the foreground to the cover
 * launcher mid-fold; without the latch an excluded app's fold would suddenly start showing halfway through.
 */
class SuppressionLatch {
    private var latched: Boolean? = null

    fun update(moving: Boolean, appSuppressedNow: Boolean): Boolean {
        if (!moving) {
            latched = null
            return appSuppressedNow
        }
        return latched ?: appSuppressedNow.also { latched = it }
    }
}
