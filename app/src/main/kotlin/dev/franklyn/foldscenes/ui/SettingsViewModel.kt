package dev.franklyn.foldscenes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.franklyn.foldscenes.data.AppEntry
import dev.franklyn.foldscenes.data.CameraRow
import dev.franklyn.foldscenes.data.ExclusionRepository
import dev.franklyn.foldscenes.data.InstalledApps
import dev.franklyn.foldscenes.data.ThemeMode
import dev.franklyn.foldscenes.data.buildAppRows
import dev.franklyn.foldscenes.data.excludedCount
import dev.franklyn.foldscenes.data.exclusionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings state: theme override, exclusion set, and the live app list (null until loaded). */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ExclusionRepository(app)

    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.themeModeBlocking())

    val excluded: StateFlow<Set<String>> = repository.excluded
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.excludedBlocking())

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    private val _camera = MutableStateFlow<CameraRow?>(null)
    val camera: StateFlow<CameraRow?> = _camera.asStateFlow()

    /** "2 apps excluded" / "All apps allowed", counting only apps still installed once the list is known. */
    val summary: StateFlow<String> = combine(excluded, _apps) { ex, list ->
        exclusionSummary(excludedCount(ex, list?.mapTo(HashSet()) { it.packageName }))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, exclusionSummary(excluded.value.size))

    init {
        refreshApps()
    }

    /** Reloads the app list; called again on resume so apps installed meanwhile show up. */
    fun refreshApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val cam = InstalledApps.camera(ctx)
            _camera.value = cam
            _apps.value = buildAppRows(runCatching { InstalledApps.launcherApps(ctx) }.getOrDefault(emptyList()), cam?.packageName)
        }
    }

    fun setAllowed(packageName: String, allowed: Boolean) {
        viewModelScope.launch { repository.setAllowed(packageName, allowed) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }
}
