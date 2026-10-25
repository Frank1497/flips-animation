package dev.franklyn.foldscenes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.consentStore: DataStore<Preferences> by preferencesDataStore(ConsentRepository.STORE_NAME)

/** The user's Accessibility API disclosure choice: null = not decided yet, true = accepted, false = declined. */
class ConsentRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.consentStore)

    val accessibilityDisclosure: Flow<Boolean?> = store.data.map { it[ACCESSIBILITY_KEY] }

    suspend fun setAccessibilityDisclosure(accepted: Boolean) {
        store.edit { it[ACCESSIBILITY_KEY] = accepted }
    }

    /** One-shot read so the first frame never flashes the wrong screen. */
    fun accessibilityDisclosureBlocking(): Boolean? = runBlocking { accessibilityDisclosure.first() }

    companion object {
        internal val ACCESSIBILITY_KEY = booleanPreferencesKey("accessibility_disclosure_accepted")
        internal const val STORE_NAME = "consent"
    }
}
