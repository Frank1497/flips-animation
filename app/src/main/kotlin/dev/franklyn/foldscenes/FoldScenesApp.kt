package dev.franklyn.foldscenes

import android.app.Application
import dev.franklyn.foldscenes.scene.SceneAssets

/** Gives the scenes (which draw without a Context) access to bitmap resources. */
class FoldScenesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SceneAssets.init(this)
    }
}
