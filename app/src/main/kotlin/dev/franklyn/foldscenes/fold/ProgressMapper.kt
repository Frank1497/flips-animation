package dev.franklyn.foldscenes.fold

enum class Panel { COVER, INNER }

/** Clamps to 0..180; NaN is treated as 0. */
internal fun sanitizeAngle(angle: Float): Float =
    if (angle.isNaN()) 0f else angle.coerceIn(0f, 180f)

/** Scene progress 0..1, linear in the hinge angle: 0 closed, 0.5 at 90 degrees, 1 flat. */
fun progress(angle: Float): Float = sanitizeAngle(angle) / 180f
