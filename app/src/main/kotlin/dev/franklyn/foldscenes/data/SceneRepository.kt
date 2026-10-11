package dev.franklyn.foldscenes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.franklyn.foldscenes.scene.Scenes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.appStore: DataStore<Preferences> by preferencesDataStore(SceneRepository.APP_STORE_NAME)

/** The pinned scene id, stored in a tiny DataStore. Every read is passed through [resolveSceneId]. */
class SceneRepository internal constructor(
    private val store: DataStore<Preferences>,
    private val validIds: () -> List<String>,
) {
    constructor(context: Context) : this(
        context.applicationContext.appStore,
        { Scenes.all.map { it.id } },
    )

    val sceneId: Flow<String> = store.data.map { resolveSceneId(it[KEY], validIds()) }

    suspend fun setSceneId(id: String) {
        store.edit { it[KEY] = id }
    }

    /** One-shot read for callers on the main thread that cannot suspend (overlay creation). */
    fun currentSceneIdBlocking(): String = runBlocking { sceneId.first() }

    companion object {
        internal val KEY = stringPreferencesKey("scene_id")
        internal const val APP_STORE_NAME = "scene_choice"

    }
}
