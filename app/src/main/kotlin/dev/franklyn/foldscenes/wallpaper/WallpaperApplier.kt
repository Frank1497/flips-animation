package dev.franklyn.foldscenes.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import dev.franklyn.foldscenes.scene.Scene

/** Applies a scene as a static wallpaper on the inner display or the cover, both via WallpaperManager. */
object WallpaperApplier {
    const val INNER_WIDTH = 1224
    const val INNER_HEIGHT = 2992
    const val COVER_WIDTH = 1080
    const val COVER_HEIGHT = 1272

    /** Renders the scene flat (p = 1) at inner-display size and sets it as the system wallpaper. */
    fun applyInner(context: Context, scene: Scene): Result<Unit> = runCatching {
        val bitmap = SceneBitmap.render(scene, INNER_WIDTH, INNER_HEIGHT, 1f)
        try {
            WallpaperManager.getInstance(context)
                .setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            Unit
        } finally {
            bitmap.recycle()
        }
    }

    /** Renders the closed (p = 0) cover frame and writes it straight into both cover slots. */
    fun applyCover(context: Context, scene: Scene): CoverOutcome {
        val bitmap = runCatching { SceneBitmap.render(scene, COVER_WIDTH, COVER_HEIGHT, 0f) }
            .getOrElse { return CoverOutcome(CoverSlotState.FAILED, CoverSlotState.IDLE, it) }
        return try {
            writeCover(WallpaperManagerCoverSlots(WallpaperManager.getInstance(context), bitmap))
        } finally {
            bitmap.recycle()
        }
    }
}

private class WallpaperManagerCoverSlots(private val wm: WallpaperManager, private val bitmap: Bitmap) : CoverSlots {
    override fun id(which: Int): Int = wm.getWallpaperId(which)

    override fun write(which: Int) {
        wm.setBitmap(bitmap, null, false, which)
    }
}
