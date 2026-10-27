package dev.franklyn.foldscenes.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundTrackerTest {
    private val activities = setOf(
        "com.netflix" to "com.netflix.HomeActivity",
        "com.motorola.camera5" to "com.motorola.camera.Camera",
        "com.motorola.launcher.secondarydisplay" to "com.motorola.cli.CliLauncher",
    )
    private var lookups = 0
    private fun tracker() = ForegroundTracker(ownPackage = "dev.franklyn.foldscenes") { pkg, cls ->
        lookups++
        (pkg to cls) in activities
    }

    @Test
    fun an_activity_window_sets_the_display_foreground() {
        val t = tracker()
        assertTrue(t.onWindowStateChanged(0, "com.netflix", "com.netflix.HomeActivity"))
        assertEquals(mapOf(0 to "com.netflix"), t.foreground)
    }

    // Regression for the 15d device bug: last-event-wins let the keyguard (inner display locks as the
    // phone closes), the keyboard, or a SystemUI panel overwrite the excluded app on display 0.
    @Test
    fun non_activity_windows_never_overwrite_the_app() {
        val t = tracker()
        t.onWindowStateChanged(0, "com.netflix", "com.netflix.HomeActivity")
        assertFalse(t.onWindowStateChanged(0, "com.android.systemui", "android.widget.FrameLayout"))
        assertFalse(t.onWindowStateChanged(0, "com.google.android.inputmethod.latin", "android.inputmethodservice.SoftInputWindow"))
        assertFalse(t.onWindowStateChanged(0, "com.netflix", "android.app.Dialog"))
        assertEquals(mapOf(0 to "com.netflix"), t.foreground)
        assertTrue(shouldSuppressOverlay(false, t.foreground.values, setOf("com.netflix"), null))
    }

    @Test
    fun own_package_blank_and_null_events_are_ignored() {
        val t = tracker()
        assertFalse(t.onWindowStateChanged(0, "dev.franklyn.foldscenes", "dev.franklyn.foldscenes.PickerActivity"))
        assertFalse(t.onWindowStateChanged(0, "", "x"))
        assertFalse(t.onWindowStateChanged(0, null, "x"))
        assertFalse(t.onWindowStateChanged(0, "com.netflix", null))
        assertTrue(t.foreground.isEmpty())
    }

    @Test
    fun displays_are_tracked_independently() {
        val t = tracker()
        t.onWindowStateChanged(0, "com.netflix", "com.netflix.HomeActivity")
        t.onWindowStateChanged(1, "com.motorola.launcher.secondarydisplay", "com.motorola.cli.CliLauncher")
        assertEquals(mapOf(0 to "com.netflix", 1 to "com.motorola.launcher.secondarydisplay"), t.foreground)
    }

    @Test
    fun same_app_again_reports_no_change_and_lookups_are_cached() {
        val t = tracker()
        assertTrue(t.onWindowStateChanged(0, "com.netflix", "com.netflix.HomeActivity"))
        assertFalse(t.onWindowStateChanged(0, "com.netflix", "com.netflix.HomeActivity"))
        t.onWindowStateChanged(0, "com.android.systemui", "android.widget.FrameLayout")
        t.onWindowStateChanged(0, "com.android.systemui", "android.widget.FrameLayout")
        assertEquals(2, lookups)
    }

    @Test
    fun clear_forgets_everything() {
        val t = tracker()
        t.onWindowStateChanged(0, "com.netflix", "com.netflix.HomeActivity")
        t.clear()
        assertTrue(t.foreground.isEmpty())
    }
}
