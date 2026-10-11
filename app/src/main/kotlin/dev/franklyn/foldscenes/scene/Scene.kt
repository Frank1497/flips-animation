package dev.franklyn.foldscenes.scene

import android.graphics.Canvas

/** A fold-driven scene. [p] is fold progress 0..1; [timeSeconds] drives idle animation. */
interface Scene {
    val id: String
    fun draw(canvas: Canvas, width: Int, height: Int, p: Float, timeSeconds: Float)
}
