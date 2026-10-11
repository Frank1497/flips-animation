package dev.franklyn.foldscenes.fold

private const val IDLE_START_S = 0.7f
private const val IDLE_END_S = 1.0f
private const val EDGE_DEG = 12f

private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Overlay alpha 0..1: smoothstep near the closed/flat end, faded out once the angle has been idle. */
fun overlayAlpha(panel: Panel, angle: Float, idleSeconds: Float): Float {
    val a = sanitizeAngle(angle)
    val idle = if (idleSeconds.isNaN()) 0f else maxOf(idleSeconds, 0f)
    val base = when (panel) {
        Panel.COVER -> smoothstep(0f, EDGE_DEG, a)
        Panel.INNER -> smoothstep(0f, EDGE_DEG, 180f - a)
    }
    val idleFactor = ((IDLE_END_S - idle) / (IDLE_END_S - IDLE_START_S)).coerceIn(0f, 1f)
    return base * idleFactor
}
