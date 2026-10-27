package dev.franklyn.foldscenes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(ExclusionRepository.STORE_NAME)

/** App settings: packages the fold animation never plays over, and the theme override. */
class ExclusionRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsStore)

    val excluded: Flow<Set<String>> = store.data.map { it[EXCLUDED_KEY].orEmpty() }

    val themeMode: Flow<ThemeMode> = store.data.map { parseThemeMode(it[THEME_KEY]) }

    suspend fun setAllowed(packageName: String, allowed: Boolean) {
        store.edit { it[EXCLUDED_KEY] = withAppAllowed(it[EXCLUDED_KEY].orEmpty(), packageName, allowed) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_KEY] = mode.name }
    }

    /** One-shot reads so the first frame never flashes the wrong theme or list. */
    fun excludedBlocking(): Set<String> = runBlocking { excluded.first() }

    fun themeModeBlocking(): ThemeMode = runBlocking { themeMode.first() }

    companion object {
        internal val EXCLUDED_KEY = stringSetPreferencesKey("excluded_packages")
        internal val THEME_KEY = stringPreferencesKey("theme_override")
        internal const val STORE_NAME = "settings"
    }
}
