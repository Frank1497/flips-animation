package dev.franklyn.foldscenes.fold

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The swap a display on/off transition represents: display 1 (cover) going off is [SwapType.COVER_OFF],
 * display 0 (inner) turning on from off is [SwapType.INNER_ON]. Anything else is not a swap.
 */
fun swapFor(displayId: Int, wasOn: Boolean, isOn: Boolean, angle: Float): SwapEvent? = when {
    displayId == 1 && wasOn && !isOn -> SwapEvent(SwapType.COVER_OFF, angle)
    displayId == 0 && !wasOn && isOn -> SwapEvent(SwapType.INNER_ON, angle)
    else -> null
}

/** Tracks which displays are lit and emits a [SwapEvent] (with the current hinge angle) on each panel swap. */
class PanelTracker(context: Context, private val hinge: HingeSource) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    private val _swaps = MutableSharedFlow<SwapEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val swaps: SharedFlow<SwapEvent> = _swaps.asSharedFlow()

    private val lastOn = mutableMapOf<Int, Boolean>() // touched on the main thread only
    private var started = false

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            lastOn[displayId] = isLit(displayId)
        }

        override fun onDisplayRemoved(displayId: Int) {
            lastOn.remove(displayId)
        }

        override fun onDisplayChanged(displayId: Int) {
            val wasOn = lastOn[displayId] ?: false
            val isOn = isLit(displayId)
            lastOn[displayId] = isOn
            val angle = hinge.angle.value
            val swap = swapFor(displayId, wasOn, isOn, angle) ?: return
            Log.i(TAG, "swap ${swap.type} angle=$angle")
            _swaps.tryEmit(swap)
        }
    }

    fun litDisplayIds(): Set<Int> =
        displayManager.displays.filter { it.state != Display.STATE_OFF && it.isValid }.map { it.displayId }.toSet()

    fun start() {
        if (started) return
        started = true
        lastOn.clear()
        litDisplayIds().forEach { lastOn[it] = true }
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        if (!started) return
        started = false
        displayManager.unregisterDisplayListener(listener)
    }

    private fun isLit(displayId: Int): Boolean =
        displayManager.getDisplay(displayId)?.let { it.isValid && it.state != Display.STATE_OFF } ?: false

    private companion object {
        const val TAG = "FoldScenes"
    }
}
