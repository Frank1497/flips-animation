package dev.franklyn.foldscenes.fold

/** Which panel a display id shows: 0 is the inner display, 1 the cover display, anything else is not ours. */
fun panelFor(displayId: Int): Panel? = when (displayId) {
    0 -> Panel.INNER
    1 -> Panel.COVER
    else -> null
}
