package dev.franklyn.foldscenes.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.franklyn.foldscenes.data.ConsentRepository
import dev.franklyn.foldscenes.data.SceneRepository
import dev.franklyn.foldscenes.scene.Scenes
import dev.franklyn.foldscenes.wallpaper.CoverSlotState
import dev.franklyn.foldscenes.wallpaper.SceneBitmap
import dev.franklyn.foldscenes.wallpaper.WallpaperApplier
import dev.franklyn.foldscenes.wallpaper.coverResultText
import dev.franklyn.foldscenes.wallpaper.coverStatusText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _inner = MutableStateFlow("")
    private val _lock = MutableStateFlow(CoverSlotState.IDLE)
    private val _home = MutableStateFlow(CoverSlotState.IDLE)

    /** Real per-slot state, e.g. "Cover: lock + home both set | Inner: applied". */
    val status: StateFlow<String> = combine(_lock, _home, _inner) { lock, home, inner ->
        listOf(coverStatusText(lock, home), inner).filter { it.isNotEmpty() }.joinToString(" | ")
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

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

    /** Inner first; the cover is written only when the inner wallpaper succeeded. */
    fun setBoth() = apply(inner = true, cover = true)

    private fun apply(inner: Boolean, cover: Boolean) {
        if (!_busy.compareAndSet(expect = false, update = true)) return
        val scene = Scenes.byId(chosenId.value)
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                if (inner) {
                    _result.value = "Applying inner wallpaper..."
                    val failure = withContext(Dispatchers.Default) { WallpaperApplier.applyInner(ctx, scene) }
                        .exceptionOrNull()
                    if (failure != null) {
                        _inner.value = "Inner: failed"
                        _result.value = "Inner wallpaper failed: ${failure.message ?: failure.javaClass.simpleName}"
                        return@launch
                    }
                    _inner.value = "Inner: applied"
                    _result.value = "Inner wallpaper set."
                }
                if (cover) {
                    _result.value = "Applying cover wallpaper..."
                    val outcome = withContext(Dispatchers.Default) { WallpaperApplier.applyCover(ctx, scene) }
                    Log.i(TAG, "cover scene=${scene.id} $outcome")
                    _lock.value = outcome.lock
                    _home.value = outcome.home
                    _result.value = coverResultText(outcome)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        const val THUMB_WIDTH = 200
        const val TAG = "FoldScenesCover"
    }
}
