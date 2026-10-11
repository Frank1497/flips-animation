package dev.franklyn.foldscenes.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import dev.franklyn.foldscenes.BuildConfig
import dev.franklyn.foldscenes.data.SceneRepository
import dev.franklyn.foldscenes.fold.HingeSource
import dev.franklyn.foldscenes.fold.overlayAlpha
import dev.franklyn.foldscenes.fold.panelFor
import dev.franklyn.foldscenes.fold.progress
import dev.franklyn.foldscenes.scene.Scenes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Draws the fold scene over every lit panel (display 0 inner, display 1 cover) while the phone folds.
 *
 * Threading: everything here runs on the MAIN thread (display listener, Choreographer frames).
 * Scenes share one RuntimeShader each, which is not safe for concurrent use, so a scene must never be drawn off the main thread.
 */
class FoldOverlayService : AccessibilityService() {

    private class Overlay(
        val displayId: Int,
        val view: SceneView,
        val windowManager: WindowManager,
        var lastLogMs: Long = 0L,
    )

    private val overlays = mutableMapOf<Int, Overlay>()
    private var displayManager: DisplayManager? = null
    private var hinge: HingeSource? = null
    private var framePosted = false
    private var pollPosted = false
    private var pausedAtEventNanos = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var repository: SceneRepository? = null
    private var scope: CoroutineScope? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = sync()
        override fun onDisplayRemoved(displayId: Int) = sync()
        override fun onDisplayChanged(displayId: Int) = sync()
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            framePosted = false
            if (overlays.isEmpty() || hinge == null) return
            val busy = onFrame()
            if (overlays.isEmpty()) return
            if (busy) postFrame() else pauseFrames()
        }
    }

    // While paused (every overlay invisible and the hinge quiet) no Choreographer callback is pending.
    // This cheap poll notices a new hinge event within POLL_MS and restarts the frame loop.
    private val resumePoll = object : Runnable {
        override fun run() {
            pollPosted = false
            val h = hinge
            if (h == null || overlays.isEmpty()) return // torn down: do not reschedule
            if (h.lastEventNanos.value != pausedAtEventNanos) postFrame() else schedulePoll()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        teardown() // a re-connect on the same instance must not leak the previous run
        val h = HingeSource(this)
        hinge = h
        h.start()
        val repo = SceneRepository(this)
        repository = repo
        // A scene picked in the app updates the live overlays. Main.immediate: views are main-thread only.
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = sc
        sc.launch {
            repo.sceneId.collect { id ->
                val scene = Scenes.byId(id)
                for (o in overlays.values) if (o.view.scene !== scene) o.view.scene = scene
            }
        }
        val dm = getSystemService(DisplayManager::class.java)
        displayManager = dm
        dm.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        sync()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        scope?.cancel()
        scope = null
        repository = null
        displayManager?.unregisterDisplayListener(displayListener)
        displayManager = null
        overlays.keys.toList().forEach(::removeOverlay)
        stopFrames()
        hinge?.stop()
        hinge = null
    }

    private fun sync() {
        val dm = displayManager ?: return
        val wanted = dm.displays
            .filter { it.isValid && it.state != Display.STATE_OFF && panelFor(it.displayId) != null }
        val wantedIds = wanted.map { it.displayId }.toSet()
        overlays.keys.filter { it !in wantedIds }.forEach(::removeOverlay)
        wanted.filter { it.displayId !in overlays }.forEach(::addOverlay)
    }

    private fun addOverlay(display: Display) {
        val id = display.displayId
        val wctx: Context = try {
            createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        } catch (e: Exception) {
            Log.e(TAG, "display $id: displayContext route failed: ${e.javaClass.name}: ${e.message}")
            try {
                createWindowContext(display, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
            } catch (e2: Exception) {
                Log.e(TAG, "display $id: fallback createWindowContext failed: ${e2.javaClass.name}: ${e2.message}", e2)
                return
            }
        }
        val view = SceneView(wctx).apply {
            // Blocking read of a tiny DataStore on the main thread; it happens only when an overlay is
            // created (a few times per fold), so the cost is acceptable and the first frame is the right scene.
            scene = Scenes.byId(repository?.currentSceneIdBlocking() ?: Scenes.all.first().id)
            progress = 0f
            alpha01 = 0f
        }
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
        } catch (e: Exception) {
            Log.e(TAG, "overlay FAILED display=$id: ${e.javaClass.name}: ${e.message}", e)
            return
        }
        overlays[id] = Overlay(id, view, wm)
        val m = wctx.resources.displayMetrics
        Log.i(TAG, "overlay ADDED display=$id size=${m.widthPixels}x${m.heightPixels}")
        postFrame()
    }

    private fun removeOverlay(displayId: Int) {
        val overlay = overlays.remove(displayId) ?: return
        try {
            overlay.windowManager.removeView(overlay.view)
            Log.i(TAG, "overlay REMOVED display=$displayId")
        } catch (e: Exception) {
            Log.e(TAG, "overlay remove failed display=$displayId: ${e.javaClass.name}: ${e.message}", e)
        }
        if (overlays.isEmpty()) stopFrames()
    }

    private fun pauseFrames() {
        pausedAtEventNanos = hinge?.lastEventNanos?.value ?: 0L
        schedulePoll()
    }

    private fun schedulePoll() {
        if (pollPosted) return
        pollPosted = true
        mainHandler.postDelayed(resumePoll, POLL_MS)
    }

    private fun postFrame() {
        if (hinge == null) return // after teardown nothing may be scheduled
        mainHandler.removeCallbacks(resumePoll)
        pollPosted = false
        if (framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopFrames() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        framePosted = false
        mainHandler.removeCallbacks(resumePoll)
        pollPosted = false
    }

    /** Updates every overlay for this frame. Returns true while frames must keep coming (something visible or the hinge recently moved). */
    private fun onFrame(): Boolean {
        val h = hinge ?: return false
        val angle = h.angle.value
        val lastEvent = h.lastEventNanos.value
        val idleSeconds = if (lastEvent == 0L) 0f else (SystemClock.elapsedRealtimeNanos() - lastEvent) / 1e9f
        val nowMs = SystemClock.uptimeMillis()
        var anyVisible = false
        for (o in overlays.values) {
            val panel = panelFor(o.displayId) ?: continue
            val p = progress(angle)
            val alpha = overlayAlpha(panel, angle, idleSeconds)
            if (abs(o.view.progress - p) > EPS) o.view.progress = p
            if (abs(o.view.alpha01 - alpha) > EPS) o.view.alpha01 = alpha
            if (alpha > 0f) {
                anyVisible = true
                o.view.invalidate() // the scene animates with time (twinkle), so redraw every frame while visible
                if (BuildConfig.DEBUG && nowMs - o.lastLogMs >= LOG_INTERVAL_MS) {
                    o.lastLogMs = nowMs
                    Log.i(TAG, "frame display=${o.displayId} angle=$angle p=$p alpha=$alpha")
                }
            }
        }
        // No event yet (lastEvent == 0) counts as quiet: the first event resumes the loop via the poll.
        val hingeQuiet = lastEvent == 0L || idleSeconds >= PAUSE_AFTER_S
        return anyVisible || !hingeQuiet
    }

    private companion object {
        const val TAG = "FoldScenes"
        const val EPS = 0.001f
        const val LOG_INTERVAL_MS = 100L
        const val POLL_MS = 250L
        const val PAUSE_AFTER_S = 1.5f
    }
}
