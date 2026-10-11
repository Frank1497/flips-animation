package dev.franklyn.foldscenes.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import dev.franklyn.foldscenes.scene.Scene
import java.util.concurrent.CountDownLatch

/**
 * Renders a [Scene] frame to a bitmap through the hardware pipeline (RenderNode + HardwareRenderer +
 * ImageReader). RuntimeShader (AGSL) draws nothing on a software Canvas(Bitmap), so this is the only
 * route. The scene's draw call always runs on the MAIN thread (scenes share one RuntimeShader); when
 * called from another thread the recording hops to the main looper and blocks until done.
 */
object SceneBitmap {
    fun render(scene: Scene, width: Int, height: Int, p: Float, timeSeconds: Float = 0f): Bitmap {
        val reader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
        )
        val node = RenderNode("scene")
        node.setPosition(0, 0, width, height)
        val renderer = HardwareRenderer()
        try {
            recordOnMain(node, width, height) { scene.draw(it, width, height, p, timeSeconds) }
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

    private fun recordOnMain(node: RenderNode, width: Int, height: Int, draw: (Canvas) -> Unit) {
        val record = {
            val canvas = node.beginRecording(width, height)
            try {
                draw(canvas)
            } finally {
                node.endRecording()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            record()
            return
        }
        var failure: Throwable? = null
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                record()
            } catch (t: Throwable) {
                failure = t
            } finally {
                done.countDown()
            }
        }
        done.await()
        failure?.let { throw it }
    }
}
