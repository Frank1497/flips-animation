package dev.franklyn.foldscenes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
    @Test
    fun back_walks_apps_to_settings_to_picker() {
        assertEquals(Screen.SETTINGS, Screen.APPS.parent)
        assertEquals(Screen.PICKER, Screen.SETTINGS.parent)
        assertNull(Screen.PICKER.parent)
    }

    @Test
    fun deeper_screen_is_a_push() {
        assertTrue(isPush(from = Screen.PICKER, to = Screen.SETTINGS))
        assertTrue(isPush(from = Screen.SETTINGS, to = Screen.APPS))
        assertFalse(isPush(from = Screen.APPS, to = Screen.SETTINGS))
    }

    @Test
    fun spring_response_0_3s_maps_to_stiffness() {
        assertEquals(438.65f, springStiffness(0.3f), 0.05f)
    }

    @Test
    fun animator_scale_zero_means_reduced_motion() {
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(1f))
        assertFalse(isReducedMotion(0.5f))
    }

    @Test
    fun drag_release_decides_by_thumb_position() {
        assertTrue(switchCommit(wasChecked = false, dragged = true, position = 0.6f))
        assertFalse(switchCommit(wasChecked = false, dragged = true, position = 0.4f))
        assertTrue("tap flips", switchCommit(wasChecked = false, dragged = false, position = 0f))
        assertFalse("tap flips", switchCommit(wasChecked = true, dragged = false, position = 1f))
    }
}
