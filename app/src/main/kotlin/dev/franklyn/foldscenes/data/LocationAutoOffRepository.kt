package dev.franklyn.foldscenes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.locationAutoOffStore: DataStore<Preferences> by
    preferencesDataStore(LocationAutoOffRepository.STORE_NAME)

/** Persists the location auto-off toggle and chosen interval. Mirrors [ExclusionRepository]'s shape. */
class LocationAutoOffRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.locationAutoOffStore)

    val settings: Flow<LocationAutoOffSettings> = store.data.map {
        LocationAutoOffSettings(
            enabled = it[ENABLED_KEY] ?: LocationAutoOffSettings.DEFAULT.enabled,
            intervalMinutes = it[INTERVAL_KEY] ?: LocationAutoOffSettings.DEFAULT.intervalMinutes,
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED_KEY] = enabled }
    }

    suspend fun setIntervalMinutes(minutes: Int) {
        store.edit { it[INTERVAL_KEY] = clampIntervalMinutes(minutes) }
    }

    /** One-shot read so the service's first tick never races the DataStore. */
    fun settingsBlocking(): LocationAutoOffSettings = runBlocking { settings.first() }

    companion object {
        internal val ENABLED_KEY = booleanPreferencesKey("location_autooff_enabled")
        internal val INTERVAL_KEY = intPreferencesKey("location_autooff_interval_minutes")
        internal const val STORE_NAME = "location_autooff"
    }
}
