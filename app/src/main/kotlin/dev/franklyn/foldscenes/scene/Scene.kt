package dev.franklyn.foldscenes.scene

import android.graphics.Canvas

/** A fold-driven scene. [p] is fold progress 0..1; [timeSeconds] drives idle animation. */
interface Scene {
    val id: String
    fun draw(canvas: Canvas, width: Int, height: Int, p: Float, timeSeconds: Float)

    /**
     * Blocks (never call on the main thread) until the scene's background assets are decoded, or
     * [timeoutMs] passes. Scenes with nothing to load are always ready. Offscreen renders (wallpaper,
     * thumbnails) call this first so they do not capture a half-loaded scene.
     */
    fun awaitAssets(timeoutMs: Long): Boolean = true
}
