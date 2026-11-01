package dev.franklyn.foldscenes.data

/** User-chosen auto-off behavior. [intervalMinutes] is how long location may stay on before it is forced off. */
data class LocationAutoOffSettings(val enabled: Boolean, val intervalMinutes: Int) {
    companion object {
        val DEFAULT = LocationAutoOffSettings(enabled = false, intervalMinutes = 30)
    }
}

/** Presets shown in Settings; keeps the stored value always one of these. */
val LOCATION_INTERVAL_PRESETS_MINUTES = listOf(15, 30, 60, 120, 240)

fun clampIntervalMinutes(minutes: Int): Int =
    LOCATION_INTERVAL_PRESETS_MINUTES.minByOrNull { kotlin.math.abs(it - minutes) } ?: LOCATION_INTERVAL_PRESETS_MINUTES[1]

/**
 * Tracks the timestamp location was last seen turning on, so an in-between OFF resets the timer.
 * [previousSinceMs] is the last tracked "has been on since" value (null = currently off / never seen on).
 * Pure function: no clock, no Android, fully unit-testable.
 */
fun trackLocationOnSince(previousSinceMs: Long?, locationOn: Boolean, nowMs: Long): Long? = when {
    !locationOn -> null
    previousSinceMs != null -> previousSinceMs
    else -> nowMs
}

/**
 * True exactly when location has been continuously ON for at least [intervalMinutes] since [sinceMs].
 * [locationOn] is re-checked here too so a stale [sinceMs] from a race never fires after the fact.
 */
fun shouldAutoOffLocation(sinceMs: Long?, nowMs: Long, intervalMinutes: Int, locationOn: Boolean): Boolean =
    locationOn && sinceMs != null && (nowMs - sinceMs) >= intervalMinutes * 60_000L
