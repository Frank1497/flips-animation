package dev.franklyn.foldscenes.wallpaper

import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder

/** Spike: red on display 0, blue on any other display, to see where the system runs us. */
class TestWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = TestEngine()

    private inner class TestEngine : Engine() {

        private fun displayId(): Int {
            val fromEngine = if (Build.VERSION.SDK_INT >= 34) displayContext?.display?.displayId else null
            return fromEngine ?: this@TestWallpaperService.display?.displayId ?: -1
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            Log.i(TAG, "onCreate displayId=${displayId()}")
        }

        override fun onDestroy() {
            Log.i(TAG, "onDestroy displayId=${displayId()}")
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            Log.i(TAG, "onSurfaceChanged displayId=${displayId()} size=${width}x$height")
            draw(holder)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            val frame = holder.surfaceFrame
            Log.i(TAG, "onSurfaceRedrawNeeded displayId=${displayId()} size=${frame.width()}x${frame.height()}")
            draw(holder)
        }

        private fun draw(holder: SurfaceHolder) {
            val id = displayId()
            val canvas = holder.lockCanvas() ?: return
            try {
                canvas.drawColor(if (id == 0) Color.RED else Color.BLUE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = canvas.width / 3f
                    textAlign = Paint.Align.CENTER
                }
                val y = canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText(id.toString(), canvas.width / 2f, y, paint)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    private companion object {
        const val TAG = "FoldTest"
    }
}
