package dev.franklyn.foldscenes.fold

enum class SwapType { COVER_OFF, INNER_ON }

data class SwapEvent(val type: SwapType, val angle: Float)

private const val WINDOW = 10

/**
 * Recomputes calibration from chronological swap events: per type, the median of the last
 * [WINDOW] accepted (in-range, non-NaN) angles. A type without accepted events keeps its
 * current value. If the result is not valid, [current] is returned unchanged.
 */
fun updated(current: Calibration, events: List<SwapEvent>): Calibration {
    val cover = median(events, SwapType.COVER_OFF, COVER_OFF_RANGE) ?: current.coverOffAngle
    val inner = median(events, SwapType.INNER_ON, INNER_ON_RANGE) ?: current.innerOnAngle
    val result = Calibration(coverOffAngle = cover, innerOnAngle = inner)
    return if (result.isValid()) result else current
}

private fun median(events: List<SwapEvent>, type: SwapType, range: ClosedFloatingPointRange<Float>): Float? {
    val last = events.filter { it.type == type && it.angle in range }.takeLast(WINDOW).map { it.angle }.sorted()
    if (last.isEmpty()) return null
    val mid = last.size / 2
    return if (last.size % 2 == 1) last[mid] else (last[mid - 1] + last[mid]) / 2f
}
