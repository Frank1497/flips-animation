package dev.franklyn.foldscenes.overlay

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import dev.franklyn.foldscenes.scene.Scene
import dev.franklyn.foldscenes.scene.Scenes
import kotlin.math.roundToInt

/** Draws [scene] at [progress] with layer alpha [alpha01]. Never touchable or focusable. */
class SceneView(context: Context) : View(context) {
    var scene: Scene = Scenes.all.first()
        set(value) { field = value; invalidate() }

    var progress: Float = 0f
        set(value) { field = clamp01(value); invalidate() }

    var alpha01: Float = 1f
        set(value) { field = clamp01(value); invalidate() }

    init {
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        isLongClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onDraw(canvas: Canvas) {
        if (alpha01 <= 0f || width <= 0 || height <= 0) return
        val seconds = (SystemClock.uptimeMillis() % TIME_WRAP_MS) / 1000f
        // A layer is only needed to fade the scene; at full opacity draw it directly.
        val save = if (alpha01 >= 1f) {
            -1
        } else {
            canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (alpha01 * 255f).roundToInt())
        }
        try {
            scene.draw(canvas, width, height, progress, seconds)
        } catch (t: Throwable) {
            // A shader failure must not take the service process down.
            Log.e(TAG, "scene ${scene.id} draw failed: ${t.javaClass.name}: ${t.message}", t)
        } finally {
            if (save >= 0) canvas.restoreToCount(save)
        }
    }

    private fun clamp01(v: Float): Float = if (v.isNaN()) 0f else v.coerceIn(0f, 1f)

    private companion object {
        const val TIME_WRAP_MS = 600_000L
        const val TAG = "FoldScenes"
    }
}
