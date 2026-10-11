package dev.franklyn.foldscenes.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

/** Uses its own DataStore file (never the app's real one) and deletes it afterwards. */
class SceneRepositoryTest {
    private val ids = listOf("sunrise", "eclipse", "mountains", "spiderverse", "rickmorty")
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "scene_repo_test_${System.nanoTime()}.preferences_pb")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    private lateinit var repo: SceneRepository

    @Before
    fun setUp() {
        repo = SceneRepository(store) { ids }
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
        File(file.path + ".tmp").delete()
    }

    @Test
    fun default_is_first_scene() = runBlocking {
        assertEquals("sunrise", repo.sceneId.first())
        assertEquals("sunrise", repo.currentSceneIdBlocking())
    }

    @Test
    fun set_then_read_roundtrip() = runBlocking {
        repo.setSceneId("mountains")
        assertEquals("mountains", repo.sceneId.first())
        assertEquals("mountains", repo.currentSceneIdBlocking())
    }

    @Test
    fun invalid_stored_value_resolves_to_first() = runBlocking {
        store.edit { it[SceneRepository.KEY] = "deleted-scene" }
        assertEquals("sunrise", repo.sceneId.first())
        assertEquals("sunrise", repo.currentSceneIdBlocking())
    }
}
