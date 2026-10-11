package dev.franklyn.foldscenes.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SceneChoiceTest {
    // Plain ids: Scenes.all builds Paints, which are unmocked on the JVM.
    private val ids5 = listOf("sunrise", "eclipse", "mountains", "spiderverse", "rickmorty")

    @Test
    fun known_id_is_kept() {
        assertEquals("eclipse", resolveSceneId("eclipse", ids5))
    }

    @Test
    fun null_falls_back_to_first() {
        assertEquals("sunrise", resolveSceneId(null, ids5))
    }

    @Test
    fun unknown_falls_back_to_first() {
        assertEquals("sunrise", resolveSceneId("no-such-scene", ids5))
    }

    @Test
    fun blank_falls_back_to_first() {
        assertEquals("sunrise", resolveSceneId("", ids5))
        assertEquals("sunrise", resolveSceneId("   ", ids5))
    }

    @Test
    fun empty_valid_ids_returns_empty_string_without_crashing() {
        assertEquals("", resolveSceneId("eclipse", emptyList()))
        assertEquals("", resolveSceneId(null, emptyList()))
    }
}
