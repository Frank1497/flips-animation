package dev.franklyn.foldscenes

import android.app.Application
import dev.franklyn.foldscenes.scene.SceneAssets
import dev.franklyn.foldscenes.scene.SceneF
import dev.franklyn.foldscenes.scene.Scenes

/** Gives the scenes (which draw without a Context) access to bitmap resources. */
class FoldScenesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SceneAssets.init(this)
        // Scene F's face decode + shadow blur is slow; do it off the main thread so the first fold is smooth.
        // Only builds bitmaps; nothing is drawn here.
        Thread({
            runCatching { Scenes.all.filterIsInstance<SceneF>().forEach { it.prewarm() } }
        }, "scenef-prewarm").start()
    }
}
