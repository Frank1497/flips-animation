package dev.franklyn.foldscenes.probe

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Throwaway spike: does a TYPE_ACCESSIBILITY_OVERLAY window show on the cover display (id 1)?
 * Draws a translucent colour + "overlay on display N WxH" on every lit display.
 */
class ProbeAccessibilityService : AccessibilityService() {

    private class Overlay(val view: View, val windowManager: WindowManager)

    private val overlays = mutableMapOf<Int, Overlay>()
    private lateinit var displayManager: DisplayManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = sync()
        override fun onDisplayRemoved(displayId: Int) = sync()
        override fun onDisplayChanged(displayId: Int) = sync()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager.registerDisplayListener(displayListener, null)
        sync()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (::displayManager.isInitialized) {
            displayManager.unregisterDisplayListener(displayListener)
        }
        overlays.keys.toList().forEach(::removeOverlay)
        super.onDestroy()
    }

    private fun sync() {
        val displays = displayManager.displays
        Log.i(TAG, "sync: " + displays.joinToString { "${it.displayId}:${stateName(it.state)}" })
        val lit = displays.filter { it.state != Display.STATE_OFF && it.isValid }
        val litIds = lit.map { it.displayId }.toSet()

        overlays.keys.filter { it !in litIds }.forEach(::removeOverlay)
        lit.filter { it.displayId !in overlays }.forEach(::addOverlay)
    }

    private fun addOverlay(display: Display) {
        val id = display.displayId
        val route: String
        val wctx: Context
        try {
            wctx = createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
            route = "createDisplayContext.createWindowContext"
        } catch (e: Exception) {
            Log.e(TAG, "display $id: displayContext route failed: $e")
            val fallback = try {
                createWindowContext(display, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
            } catch (e2: Exception) {
                Log.e(TAG, "display $id: fallback createWindowContext(display,...) failed: $e2", e2)
                return
            }
            attach(display, fallback, "createWindowContext(display,...) fallback")
            return
        }
        attach(display, wctx, route)
    }

    private fun attach(display: Display, wctx: Context, route: String) {
        val id = display.displayId
        val size = "${wctx.resources.displayMetrics.widthPixels}x${wctx.resources.displayMetrics.heightPixels}"
        val view = ProbeView(wctx, id, size)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        val wm = wctx.getSystemService(WindowManager::class.java)
        try {
            wm.addView(view, lp)
            overlays[id] = Overlay(view, wm)
            Log.i(TAG, "overlay ADDED display=$id size=$size flags=${display.flags} route=$route")
        } catch (e: Exception) {
            Log.e(TAG, "overlay FAILED display=$id route=$route: ${e.javaClass.name}: ${e.message}", e)
        }
    }

    private fun removeOverlay(displayId: Int) {
        val overlay = overlays.remove(displayId) ?: return
        try {
            overlay.windowManager.removeView(overlay.view)
            Log.i(TAG, "overlay REMOVED display=$displayId")
        } catch (e: Exception) {
            Log.e(TAG, "overlay remove failed display=$displayId: ${e.javaClass.name}: ${e.message}", e)
        }
    }

    private fun stateName(state: Int) = when (state) {
        Display.STATE_OFF -> "OFF"
        Display.STATE_ON -> "ON"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_VR -> "VR"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        else -> "UNKNOWN($state)"
    }

    private class ProbeView(context: Context, private val displayId: Int, private val size: String) : View(context) {
        private val label = "overlay on display $displayId $size"
        private val fill = Paint().apply {
            color = if (displayId == 1) Color.argb(170, 255, 0, 255) else Color.argb(170, 0, 200, 0)
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 64f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            canvas.drawText(label, width / 2f, height / 2f, text)
        }
    }

    private companion object {
        const val TAG = "FoldProbe"
    }
}
