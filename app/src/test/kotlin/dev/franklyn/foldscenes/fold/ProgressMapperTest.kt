package dev.franklyn.foldscenes.fold

import dev.franklyn.foldscenes.wallpaper.WallpaperApplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressMapperTest {
    @Test
    fun zero_is_zero() {
        assertEquals(0f, progress(0f), 0f)
    }

    @Test
    fun flat_is_one() {
        assertEquals(1f, progress(180f), 0f)
    }

    @Test
    fun ninety_is_half() {
        assertEquals(0.5f, progress(90f), 1e-6f)
    }

    @Test
    fun linear() {
        for (deg in 0..180) {
            assertEquals("at $deg", deg / 180f, progress(deg.toFloat()), 1e-6f)
        }
    }

    @Test
    fun monotonic() {
        var previous = progress(0f)
        for (deg in 1..180) {
            val p = progress(deg.toFloat())
            assertTrue("decreased at $deg: $previous -> $p", p >= previous - 1e-6f)
            previous = p
        }
    }

    @Test
    fun bad_angles_clamp() {
        for (angle in listOf(Float.NaN, -10f, 400f)) {
            val p = progress(angle)
            assertTrue("$angle -> $p", p in 0f..1f)
        }
    }

    @Test
    fun coverFileName_is_stable_and_safe() {
        assertEquals("razrfold_sunrise_cover.png", WallpaperApplier.coverFileName("sunrise"))
        val unsafe = WallpaperApplier.coverFileName("../we ird/id:1")
        assertTrue(unsafe, Regex("razrfold_[A-Za-z0-9_]+_cover\\.png").matches(unsafe))
    }
}
