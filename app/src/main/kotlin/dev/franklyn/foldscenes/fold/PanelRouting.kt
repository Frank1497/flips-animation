package dev.franklyn.foldscenes.fold

/** Which panel a display id shows: 0 is the inner display, 1 the cover display, anything else is not ours. */
fun panelFor(displayId: Int): Panel? = when (displayId) {
    0 -> Panel.INNER
    1 -> Panel.COVER
    else -> null
}

/** True when a swap's hinge angle is physically plausible for its type (NaN is not). */
fun isPlausibleSwap(event: SwapEvent): Boolean = when (event.type) {
    SwapType.COVER_OFF -> event.angle in COVER_OFF_RANGE
    SwapType.INNER_ON -> event.angle in INNER_ON_RANGE
}
