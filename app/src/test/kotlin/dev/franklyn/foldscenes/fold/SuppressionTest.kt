package dev.franklyn.foldscenes.fold

import org.junit.Assert.assertEquals
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
    fun shouldSuppressOverlay_covers_camera_excluded_app_and_default_camera() {
        assertTrue("any open camera", shouldSuppressOverlay(true, emptyList(), emptySet(), null))
        assertTrue("excluded app", shouldSuppressOverlay(false, listOf("com.netflix"), excluded, cam))
        assertTrue("default camera app", shouldSuppressOverlay(false, listOf(cam), emptySet(), cam))
        assertFalse(shouldSuppressOverlay(false, listOf("com.other"), excluded, cam))
        assertFalse("nothing seen yet", shouldSuppressOverlay(false, emptyList(), excluded, cam))
    }

    // Regression for the 15d device bug: the old SuppressionLatch froze "allowed" at fold start and
    // returned false here, so an excluded app or the camera reaching the foreground while the hinge
    // gate was armed still played the scene.
    @Test
    fun fold_that_started_allowed_suppresses_as_soon_as_an_excluded_app_is_in_front() {
        val s = FoldSuppression()
        assertFalse(s.update(moving = true, suppressedNow = false))
        assertTrue(s.update(moving = true, suppressedNow = true))
    }

    @Test
    fun suppression_holds_for_the_rest_of_the_fold_once_triggered() {
        val s = FoldSuppression()
        assertTrue(s.update(moving = true, suppressedNow = true))
        // Folding shut brings the cover launcher to the foreground mid-fold; the fold must stay suppressed.
        assertTrue(s.update(moving = true, suppressedNow = false))
        assertFalse("settled: live value again", s.update(moving = false, suppressedNow = false))
    }

    @Test
    fun nothing_is_held_between_folds() {
        val s = FoldSuppression()
        s.update(moving = true, suppressedNow = true)
        s.update(moving = false, suppressedNow = false)
        assertFalse(s.update(moving = true, suppressedNow = false))
    }

    @Test
    fun live_value_always_wins_at_rest() {
        val s = FoldSuppression()
        assertTrue(s.update(moving = false, suppressedNow = true))
        assertFalse(s.update(moving = false, suppressedNow = false))
    }

    @Test
    fun finished_fold_reports_why_it_was_suppressed() {
        val s = FoldSuppression()
        s.update(moving = true, suppressedNow = false) { "played (foreground: a.b)" }
        s.update(moving = true, suppressedNow = false) { "not recorded: decision unchanged" }
        s.update(moving = true, suppressedNow = true) { "suppressed: com.netflix" }
        s.update(moving = true, suppressedNow = false) { "not recorded: still held" }
        assertEquals("fold still running", null, s.takeFinishedFold())
        s.update(moving = false, suppressedNow = false)
        assertEquals("suppressed: com.netflix", s.takeFinishedFold())
        assertEquals("reported once", null, s.takeFinishedFold())
    }

    @Test
    fun finished_fold_reports_an_allowed_fold() {
        val s = FoldSuppression()
        s.update(moving = true, suppressedNow = false) { "played (foreground: a.b)" }
        s.update(moving = false, suppressedNow = false)
        assertEquals("played (foreground: a.b)", s.takeFinishedFold())
    }

    @Test
    fun fold_decision_text_names_the_reason() {
        assertEquals("suppressed: camera in use", foldDecisionText(true, listOf("x"), excluded, cam))
        assertEquals("suppressed: com.netflix", foldDecisionText(false, listOf("a.b", "com.netflix"), excluded, cam))
        assertEquals("suppressed: $cam", foldDecisionText(false, listOf(cam), emptySet(), cam))
        assertEquals("played (foreground: a.b)", foldDecisionText(false, listOf("a.b"), excluded, cam))
        assertEquals("played (foreground: none seen)", foldDecisionText(false, emptyList(), excluded, cam))
    }

    @Test
    fun diagnostics_lines_show_every_input_of_the_decision() {
        val lines = diagnosticsLines(
            SuppressionSnapshot(
                connected = true,
                foreground = mapOf(1 to "com.cover", 0 to "com.netflix"),
                camerasInUse = setOf("0"),
                cameraPackage = cam,
                excludedCount = 2,
                lastFold = "14:02:11 suppressed: com.netflix",
            ),
        )
        assertEquals(
            listOf(
                "Service: running",
                "Foreground: display 0 com.netflix, display 1 com.cover",
                "Camera in use: yes (0)",
                "Default camera: $cam",
                "Excluded apps: 2",
                "Last fold: 14:02:11 suppressed: com.netflix",
            ),
            lines,
        )
        val empty = diagnosticsLines(SuppressionSnapshot())
        assertEquals("Service: not running", empty[0])
        assertEquals("Foreground: none seen yet", empty[1])
        assertEquals("Camera in use: no", empty[2])
        assertEquals("Default camera: none resolved", empty[3])
        assertEquals("Last fold: none yet", empty[5])
    }
}
