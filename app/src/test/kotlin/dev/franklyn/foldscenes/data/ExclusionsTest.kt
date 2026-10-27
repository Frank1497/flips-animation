package dev.franklyn.foldscenes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExclusionsTest {
    @Test
    fun turning_an_app_off_adds_it_and_on_removes_it() {
        val off = withAppAllowed(emptySet(), "com.netflix", allowed = false)
        assertEquals(setOf("com.netflix"), off)
        assertEquals(emptySet<String>(), withAppAllowed(off, "com.netflix", allowed = true))
    }

    @Test
    fun toggling_is_idempotent_and_leaves_other_apps_alone() {
        val start = setOf("a.bank")
        assertEquals(setOf("a.bank", "com.netflix"), withAppAllowed(withAppAllowed(start, "com.netflix", false), "com.netflix", false))
        assertEquals(start, withAppAllowed(start, "not.there", allowed = true))
    }

    @Test
    fun blank_package_is_ignored() {
        assertEquals(setOf("a.bank"), withAppAllowed(setOf("a.bank"), "  ", allowed = false))
    }

    @Test
    fun count_ignores_uninstalled_packages_once_the_list_is_known() {
        val excluded = setOf("com.netflix", "gone.app")
        assertEquals(2, excludedCount(excluded, installed = null))
        assertEquals(1, excludedCount(excluded, installed = setOf("com.netflix", "com.other")))
    }

    @Test
    fun summary_text() {
        assertEquals("All apps allowed", exclusionSummary(0))
        assertEquals("1 app excluded", exclusionSummary(1))
        assertEquals("2 apps excluded", exclusionSummary(2))
    }

    @Test
    fun app_rows_drop_camera_blank_and_duplicates_and_sort_by_label() {
        val rows = buildAppRows(
            listOf(
                AppEntry("com.zeta", "zeta"),
                AppEntry("com.motorola.camera5", "Camera"),
                AppEntry("com.alpha", "Alpha"),
                AppEntry("com.alpha", "Alpha"),
                AppEntry("", "Ghost"),
                AppEntry("com.beta2", "beta"),
                AppEntry("com.beta1", "Beta"),
            ),
            cameraPackage = "com.motorola.camera5",
        )
        assertEquals(listOf("com.alpha", "com.beta1", "com.beta2", "com.zeta"), rows.map { it.packageName })
    }

    @Test
    fun app_rows_keep_everything_when_there_is_no_camera() {
        val rows = buildAppRows(listOf(AppEntry("com.b", "B"), AppEntry("com.a", "A")), cameraPackage = null)
        assertEquals(listOf("com.a", "com.b"), rows.map { it.packageName })
    }

    @Test
    fun camera_package_null_blank_or_chooser_means_no_row() {
        assertNull(cameraPackageOrNull(null))
        assertNull(cameraPackageOrNull(""))
        assertNull("the 'android' ResolverActivity is a chooser, not a camera", cameraPackageOrNull("android"))
        assertEquals("com.motorola.camera5", cameraPackageOrNull("com.motorola.camera5"))
    }

    @Test
    fun camera_row_is_hidden_when_unresolved_and_labelled_when_resolved() {
        assertNull(cameraRowFor(null, "Camera"))
        assertNull(cameraRowFor("android", "Open with"))
        assertEquals(CameraRow("com.motorola.camera5", "Camera"), cameraRowFor("com.motorola.camera5", "Camera"))
        assertEquals("blank label falls back", "Camera", cameraRowFor("com.x.cam", " ")?.label)
    }

    @Test
    fun theme_mode_parses_with_system_fallback() {
        assertEquals(ThemeMode.SYSTEM, parseThemeMode(null))
        assertEquals(ThemeMode.SYSTEM, parseThemeMode("purple"))
        assertEquals(ThemeMode.DARK, parseThemeMode("DARK"))
        assertEquals(ThemeMode.LIGHT, parseThemeMode("LIGHT"))
    }

    @Test
    fun theme_mode_resolves_against_system() {
        assertEquals(true, ThemeMode.SYSTEM.isDark(systemDark = true))
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemDark = false))
        assertEquals(true, ThemeMode.DARK.isDark(systemDark = false))
        assertEquals(false, ThemeMode.LIGHT.isDark(systemDark = true))
    }
}
