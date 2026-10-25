package dev.franklyn.foldscenes.wallpaper

/**
 * Motorola's cover display has two WallpaperManager slots, keyed by the hidden FLAG_CLI (4) and
 * FLAG_CLI_LOCK (16). Lock is written first: writing home while the lock slot is empty can migrate the
 * old home image into the lock slot.
 */
enum class CoverSlot(val which: Int) {
    LOCK(which = 16),
    HOME(which = 4),
    ;

    companion object {
        val SEQUENCE = listOf(LOCK, HOME)
    }
}

/** The cover slots' ids and writes. Real: WallpaperManager; tests: a fake. */
interface CoverSlots {
    fun id(which: Int): Int
    fun write(which: Int)
}

enum class CoverSlotState(val text: String) {
    IDLE("not set"),
    SET("set"),
    UNCHANGED("unchanged"),
    FAILED("failed"),
}

data class CoverOutcome(val lock: CoverSlotState, val home: CoverSlotState, val error: Throwable? = null) {
    val ok get() = lock == CoverSlotState.SET && home == CoverSlotState.SET
}

/** A slot is set only if its wallpaper id changed; a write that returns without moving the id proves nothing. */
fun slotOutcome(before: Int?, after: Int?): CoverSlotState = when {
    before == null || after == null -> CoverSlotState.FAILED
    after != before -> CoverSlotState.SET
    else -> CoverSlotState.UNCHANGED
}

/**
 * Writes lock then home, judging each by its id delta. Stops at the first exception: on a phone without
 * cover slots WallpaperManager throws IllegalArgumentException("Must specify a valid wallpaper category").
 */
fun writeCover(slots: CoverSlots): CoverOutcome {
    val states = mutableMapOf<CoverSlot, CoverSlotState>()
    var error: Throwable? = null
    for (slot in CoverSlot.SEQUENCE) {
        states[slot] = runCatching {
            val before = slots.id(slot.which)
            slots.write(slot.which)
            slotOutcome(before, slots.id(slot.which))
        }.getOrElse {
            error = it
            CoverSlotState.FAILED
        }
        if (error != null) break
    }
    return CoverOutcome(
        lock = states[CoverSlot.LOCK] ?: CoverSlotState.IDLE,
        home = states[CoverSlot.HOME] ?: CoverSlotState.IDLE,
        error = error,
    )
}

fun coverStatusText(lock: CoverSlotState, home: CoverSlotState): String = when {
    lock == CoverSlotState.IDLE && home == CoverSlotState.IDLE -> ""
    lock == CoverSlotState.SET && home == CoverSlotState.SET -> "Cover: lock + home both set"
    else -> "Cover: lock ${lock.text}, home ${home.text}"
}

fun coverResultText(outcome: CoverOutcome): String {
    val e = outcome.error
    return when {
        outcome.ok -> "Cover wallpaper set on lock and home screens."
        e is IllegalArgumentException -> "This phone has no cover wallpaper slots."
        e != null -> "Cover wallpaper failed: ${e.message ?: e.javaClass.simpleName}"
        else -> "Cover wallpaper not confirmed: the wallpaper id did not change."
    }
}
