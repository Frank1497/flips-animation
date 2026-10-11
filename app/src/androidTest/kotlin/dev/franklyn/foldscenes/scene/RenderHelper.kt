package dev.franklyn.foldscenes.scene

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader

/**
 * Renders through the hardware pipeline (RenderNode + HardwareRenderer + ImageReader).
 * RuntimeShader (AGSL) draws nothing on a software Canvas(Bitmap), so tests must use this.
 */
object RenderHelper {
    fun render(width: Int, height: Int, draw: (android.graphics.Canvas) -> Unit): Bitmap {
        val reader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
        )
        val node = RenderNode("scene")
        node.setPosition(0, 0, width, height)
        val rec = node.beginRecording(width, height)
        try {
            draw(rec)
        } finally {
            node.endRecording()
        }
        val renderer = HardwareRenderer()
        try {
            renderer.setContentRoot(node)
            renderer.setSurface(reader.surface)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = reader.acquireNextImage() ?: error("no image rendered")
            try {
                val buf = image.hardwareBuffer ?: error("no hardware buffer")
                val hw = Bitmap.wrapHardwareBuffer(buf, null) ?: error("wrapHardwareBuffer failed")
                val out = hw.copy(Bitmap.Config.ARGB_8888, false) ?: error("copy failed")
                hw.recycle()
                buf.close()
                return out
            } finally {
                image.close()
            }
        } finally {
            renderer.destroy()
            node.discardDisplayList()
            reader.close()
        }
    }

    fun renderScene(scene: Scene, width: Int, height: Int, p: Float, t: Float = 1f): Bitmap =
        render(width, height) { scene.draw(it, width, height, p, t) }
}
