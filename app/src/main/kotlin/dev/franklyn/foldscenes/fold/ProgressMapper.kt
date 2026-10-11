package dev.franklyn.foldscenes.fold

enum class Panel { COVER, INNER }

data class Calibration(
    val coverOffAngle: Float = 56f,
    val innerOnAngle: Float = 109f,
)

internal val COVER_OFF_RANGE = 20f..90f
internal val INNER_ON_RANGE = 80f..150f

/** True when both swap angles are in range (NaN fails) and the inner swap comes after the cover swap. */
fun Calibration.isValid(): Boolean =
    coverOffAngle in COVER_OFF_RANGE && innerOnAngle in INNER_ON_RANGE && innerOnAngle > coverOffAngle

/** Clamps to 0..180; NaN is treated as 0. */
internal fun sanitizeAngle(angle: Float): Float =
    if (angle.isNaN()) 0f else angle.coerceIn(0f, 180f)

/**
 * Scene progress 0..1 across the whole fold. Cover covers 0..0.5 up to its swap angle,
 * inner covers 0.5..1 from its swap angle to flat.
 */
fun progress(panel: Panel, angle: Float, cal: Calibration = Calibration()): Float {
    val a = sanitizeAngle(angle)
    val c = if (cal.isValid()) cal else Calibration()
    return when (panel) {
        Panel.COVER -> 0.5f * minOf(a, c.coverOffAngle) / c.coverOffAngle
        Panel.INNER -> (0.5f + 0.5f * (a - c.innerOnAngle) / (180f - c.innerOnAngle)).coerceIn(0.5f, 1f)
    }
}
