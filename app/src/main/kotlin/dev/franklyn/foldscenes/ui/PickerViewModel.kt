package dev.franklyn.foldscenes.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.franklyn.foldscenes.data.ConsentRepository
import dev.franklyn.foldscenes.data.SceneRepository
import dev.franklyn.foldscenes.scene.Scenes
import dev.franklyn.foldscenes.wallpaper.SceneBitmap
import dev.franklyn.foldscenes.wallpaper.WallpaperApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Scene display names, keyed by scene id. */
val SCENE_NAMES: Map<String, String> = mapOf(
    "sunrise" to "Sunrise",
    "eclipse" to "Eclipse",
    "mountains" to "Mountains",
    "spiderverse" to "Spider-Verse",
    "rickmorty" to "Rick and Morty",
    "ronaldo" to "Ronaldo",
)

/**
 * Holds the picker's state across rotation and folding: the chosen scene, cached thumbnails, and the
 * apply buttons' busy flag and result text. Applying runs on [viewModelScope], so it survives the activity.
 */
class PickerViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = SceneRepository(app)
    private val consent = ConsentRepository(app)

    /** Accessibility disclosure choice: null until the user taps Accept or Decline. */
    val disclosureChoice: StateFlow<Boolean?> = consent.accessibilityDisclosure
        .stateIn(viewModelScope, SharingStarted.Eagerly, consent.accessibilityDisclosureBlocking())

    fun recordDisclosure(accepted: Boolean) {
        viewModelScope.launch { consent.setAccessibilityDisclosure(accepted) }
    }

    // Initial value from a one-off blocking read of the tiny DataStore, so the radio never flashes the wrong row.
    val chosenId: StateFlow<String> = repository.sceneId
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.currentSceneIdBlocking())

    private val _thumbnails = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<String, Bitmap>> = _thumbnails.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _result = MutableStateFlow("")
    val result: StateFlow<String> = _result.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val h = (THUMB_WIDTH * 1272f / 1080f).roundToInt()
            for (scene in Scenes.all) {
                // SceneBitmap hops the draw itself to the main thread.
                val bmp = runCatching { SceneBitmap.render(scene, THUMB_WIDTH, h, 0.5f) }.getOrNull() ?: continue
                _thumbnails.update { it + (scene.id to bmp) }
            }
        }
    }

    /** Pins [id] as the scene. Does not apply any wallpaper. */
    fun select(id: String) {
        viewModelScope.launch { repository.setSceneId(id) }
    }

    fun setInner() = apply(inner = true, cover = false)

    fun setCover() = apply(inner = false, cover = true)

    /** Inner first; the cover editor opens only when the inner wallpaper succeeded. */
    fun setBoth() = apply(inner = true, cover = true)

    private fun apply(inner: Boolean, cover: Boolean) {
        if (!_busy.compareAndSet(expect = false, update = true)) return
        val scene = Scenes.byId(chosenId.value)
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                if (inner) {
                    _result.value = "Applying inner wallpaper..."
                    val r = withContext(Dispatchers.Default) { WallpaperApplier.applyInner(ctx, scene) }
                    val failure = r.exceptionOrNull()
                    if (failure != null) {
                        _result.value = "Inner wallpaper failed: ${failure.message ?: failure.javaClass.simpleName}"
                        return@launch
                    }
                    _result.value = "Inner wallpaper set."
                }
                if (cover) {
                    _result.value = if (inner) "Inner wallpaper set. Saving cover frame..." else "Saving cover frame..."
                    val r = withContext(Dispatchers.Default) { WallpaperApplier.applyCover(ctx, scene) }
                    val prefix = if (inner) "Inner wallpaper set. " else ""
                    _result.value = r.fold(
                        onSuccess = { "${prefix}Cover frame saved: $it. Tap Save in Motorola's editor." },
                        onFailure = { "${prefix}Cover wallpaper: ${it.message ?: it.javaClass.simpleName}" },
                    )
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        const val THUMB_WIDTH = 200
    }
}
