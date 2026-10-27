package dev.franklyn.foldscenes.fold

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuppressionTest {
    private val excluded = setOf("com.netflix")
    private val cam = "com.motorola.camera5"

    @Test
    fun excluded_foreground_app_is_suppressed() {
        assertTrue(appSuppressed("com.netflix", excluded, cam))
        assertFalse(appSuppressed("com.other", excluded, cam))
        assertFalse("unknown foreground is not suppressed", appSuppressed(null, excluded, cam))
    }

    @Test
    fun default_camera_in_foreground_is_suppressed_even_without_an_open_camera() {
        assertTrue(appSuppressed(cam, emptySet(), cam))
        assertFalse(appSuppressed(cam, emptySet(), cameraPackage = null))
    }

    @Test
    fun any_lit_display_showing_an_excluded_app_suppresses() {
        // Inner still shows Netflix while the cover shows its launcher: unfolding returns to Netflix.
        assertTrue(anyAppSuppressed(listOf("com.motorola.launcher.secondarydisplay", "com.netflix"), excluded, cam))
        assertFalse(anyAppSuppressed(listOf("com.motorola.launcher.secondarydisplay"), excluded, cam))
        assertFalse(anyAppSuppressed(emptyList(), excluded, cam))
    }

    @Test
    fun latch_holds_the_decision_taken_when_the_fold_started() {
        val latch = SuppressionLatch()
        assertTrue(latch.update(moving = true, appSuppressedNow = true))
        // Folding shut brings the cover launcher to the foreground mid-fold; the fold must stay suppressed.
        assertTrue(latch.update(moving = true, appSuppressedNow = false))
        assertFalse("settled: live value again", latch.update(moving = false, appSuppressedNow = false))
    }

    @Test
    fun latch_does_not_start_suppressing_mid_fold() {
        val latch = SuppressionLatch()
        assertFalse(latch.update(moving = true, appSuppressedNow = false))
        assertFalse(latch.update(moving = true, appSuppressedNow = true))
        assertTrue(latch.update(moving = false, appSuppressedNow = true))
    }

    @Test
    fun an_open_camera_always_suppresses_live() {
        val latch = SuppressionLatch()
        assertFalse(overlaySuppressed(latch.update(true, false), cameraInUse = false))
        assertTrue(overlaySuppressed(latch.update(true, false), cameraInUse = true))
    }
}
