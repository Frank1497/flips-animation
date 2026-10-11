package dev.franklyn.foldscenes.fold

import dev.franklyn.foldscenes.fold.Panel.COVER
import dev.franklyn.foldscenes.fold.Panel.INNER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressMapperTest {
    @Test
    fun cover_closed_is_zero() {
        assertEquals(0f, progress(COVER, 0f), 0f)
    }

    @Test
    fun cover_at_swap_is_half() {
        assertEquals(0.5f, progress(COVER, 56f), 0f)
    }

    @Test
    fun cover_beyond_swap_clamps_at_half() {
        assertEquals(0.5f, progress(COVER, 90f), 0f)
    }

    @Test
    fun inner_at_swap_is_half() {
        assertEquals(0.5f, progress(INNER, 109f), 0f)
    }

    @Test
    fun inner_flat_is_one() {
        assertEquals(1f, progress(INNER, 180f), 0f)
    }

    @Test
    fun inner_below_swap_clamps_at_half() {
        assertEquals(0.5f, progress(INNER, 60f), 0f)
    }

    @Test
    fun swap_is_continuous() {
        val cal = Calibration(50f, 120f)
        assertEquals(progress(COVER, 50f, cal), progress(INNER, 120f, cal), 0f)
    }

    @Test
    fun monotonic_per_panel() {
        for (panel in Panel.values()) {
            var previous = progress(panel, 0f)
            for (deg in 1..180) {
                val p = progress(panel, deg.toFloat())
                assertTrue("$panel decreased at $deg: $previous -> $p", p >= previous - 1e-6f)
                previous = p
            }
        }
    }

    @Test
    fun bad_angles_clamp() {
        for (panel in Panel.values()) {
            for (angle in listOf(Float.NaN, -10f, 400f)) {
                val p = progress(panel, angle)
                assertTrue("$panel $angle -> $p", p in 0f..1f)
            }
        }
    }

    @Test
    fun degenerate_calibration_returns_finite() {
        val bad = listOf(
            Calibration(0f, 109f),
            Calibration(56f, 180f),
            Calibration(Float.NaN, 109f),
            Calibration(56f, 50f),
        )
        for (cal in bad) {
            for (panel in Panel.values()) {
                for (angle in listOf(0f, 56f, 109f, 180f)) {
                    val p = progress(panel, angle, cal)
                    assertTrue("$cal $panel $angle -> $p", p.isFinite() && p in 0f..1f)
                    assertEquals("$cal $panel $angle", progress(panel, angle, Calibration()), p, 0f)
                }
            }
        }
    }

    @Test
    fun valid_calibration_is_used() {
        assertEquals(0.5f, progress(COVER, 50f, Calibration(50f, 120f)), 0f)
    }
}
