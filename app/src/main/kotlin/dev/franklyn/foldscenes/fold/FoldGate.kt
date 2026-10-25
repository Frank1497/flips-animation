package dev.franklyn.foldscenes.fold

import kotlin.math.abs

/**
 * Hinge jitter measured at rest on the Razr 60 Ultra (2026-10-26, 5 x 30 s windows, phone untouched, OPENED
 * at 180°): 0 events, so 0° of jitter. The sensor is on-change and reports whole degrees; the smallest real
 * step seen while folding was 1°. The threshold is that jitter plus a 1° margin, so a single 1° flicker
 * never counts as a fold.
 */
const val REST_JITTER_DEG = 0f
const val MOVE_THRESHOLD_DEG = REST_JITTER_DEG + 1f

/** Same idle time at which [overlayAlpha] reaches 0: once faded out, the hinge is at rest again. */
private const val SETTLE_S = 1.0f

/**
 * Arms the fold animation only after the hinge moves more than [MOVE_THRESHOLD_DEG] from its last resting
 * angle, and disarms once the hinge has been idle for [SETTLE_S], taking the current angle as the new rest.
 */
class FoldGate {
    var restAngle = Float.NaN
        private set
    var moving = false
        private set

    /** True if [angle] would arm the gate now. Read-only: use it to decide whether to wake the frame loop. */
    fun wouldMove(angle: Float): Boolean =
        !restAngle.isNaN() && abs(sanitizeAngle(angle) - restAngle) > MOVE_THRESHOLD_DEG

    /** Updates the gate for one frame and returns whether a fold is in progress. */
    fun update(angle: Float, idleSeconds: Float, hasEvent: Boolean = true): Boolean {
        if (!hasEvent) return false
        val a = sanitizeAngle(angle)
        when {
            // The first event is the sensor's initial on-change report, not motion.
            restAngle.isNaN() -> restAngle = a
            moving && idleSeconds >= SETTLE_S -> {
                moving = false
                restAngle = a
            }
            !moving && wouldMove(a) -> moving = true
        }
        return moving
    }
}

/** [overlayAlpha], forced to 0 unless the gate says a real fold is in progress. */
fun foldAlpha(panel: Panel, angle: Float, idleSeconds: Float, moving: Boolean): Float =
    if (moving) overlayAlpha(panel, angle, idleSeconds) else 0f
