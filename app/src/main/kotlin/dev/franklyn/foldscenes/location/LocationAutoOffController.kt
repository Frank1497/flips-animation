package dev.franklyn.foldscenes.location

import android.content.Context
import dev.franklyn.foldscenes.data.LocationAutoOffSettings
import dev.franklyn.foldscenes.data.shouldAutoOffLocation
import dev.franklyn.foldscenes.data.trackLocationOnSince

/**
 * Owns the "has location been on too long" timer. [tick] is cheap (one system read) and meant to be
 * called from a periodic Handler loop in [dev.franklyn.foldscenes.overlay.FoldOverlayService] — this
 * class holds no Android lifecycle state itself, so it is safe to recreate per service connection.
 * Settings are pushed in via [updateSettings] from a collected [dev.franklyn.foldscenes.data.LocationAutoOffRepository]
 * flow, the same pattern [dev.franklyn.foldscenes.overlay.FoldOverlayService] already uses for exclusions.
 */
class LocationAutoOffController(
    private val locationOn: () -> Boolean = { false },
    private val actionFor: (Context) -> LocationAutoOffAction = ::locationAutoOffActionFor,
) {
    private var settings: LocationAutoOffSettings = LocationAutoOffSettings.DEFAULT
    private var onSinceMs: Long? = null

    fun updateSettings(newSettings: LocationAutoOffSettings) {
        settings = newSettings
        if (!newSettings.enabled) onSinceMs = null
    }

    /** Call once per check interval with [context] (for the action) and the current wall-clock [nowMs]. */
    fun tick(context: Context, nowMs: Long) {
        if (!settings.enabled) return
        val on = locationOn()
        onSinceMs = trackLocationOnSince(onSinceMs, on, nowMs)
        if (shouldAutoOffLocation(onSinceMs, nowMs, settings.intervalMinutes, on)) {
            actionFor(context).act(context)
            onSinceMs = null
        }
    }
}
