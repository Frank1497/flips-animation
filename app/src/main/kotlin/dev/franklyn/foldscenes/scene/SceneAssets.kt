package dev.franklyn.foldscenes.scene

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.SparseArray

/**
 * Lazily decoded bitmap resources for scenes D and F. Scenes draw with no Context, so
 * [FoldScenesApp][dev.franklyn.foldscenes.FoldScenesApp] hands over the application resources once.
 * Main-thread use only (like the scenes themselves).
 */
object SceneAssets {
    private var resources: Resources? = null
    private val cache = SparseArray<Bitmap>()

    fun init(context: Context) {
        resources = context.applicationContext.resources
    }

    /** Decoded, unscaled ARGB_8888 bitmap for [resId], or null when assets are not initialised. */
    fun bitmap(resId: Int): Bitmap? {
        cache.get(resId)?.let { return it }
        val res = resources ?: return null
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeResource(res, resId, opts) ?: return null
        cache.put(resId, bmp)
        return bmp
    }
}
