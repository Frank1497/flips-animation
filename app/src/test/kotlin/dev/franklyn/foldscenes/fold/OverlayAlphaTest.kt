package dev.franklyn.foldscenes.fold

import dev.franklyn.foldscenes.fold.Panel.COVER
import dev.franklyn.foldscenes.fold.Panel.INNER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayAlphaTest {
    @Test
    fun cover_closed_is_zero() {
        assertEquals(0f, overlayAlpha(COVER, 0f, 0f), 0f)
    }

    @Test
    fun cover_mid_fold_is_one() {
        assertEquals(1f, overlayAlpha(COVER, 40f, 0f), 0f)
    }

    @Test
    fun inner_flat_is_zero() {
        assertEquals(0f, overlayAlpha(INNER, 180f, 0f), 0f)
    }

    @Test
    fun inner_mid_fold_is_one() {
        assertEquals(1f, overlayAlpha(INNER, 120f, 0f), 0f)
    }

    @Test
    fun smoothstep_midpoint() {
        assertEquals(0.5f, overlayAlpha(COVER, 6f, 0f), 1e-4f)
    }

    @Test
    fun idle_below_threshold_keeps_alpha() {
        assertEquals(1f, overlayAlpha(COVER, 40f, 0.7f), 0f)
    }

    @Test
    fun idle_fades_linearly() {
        assertEquals(0.5f, overlayAlpha(COVER, 40f, 0.85f), 1e-4f)
    }

    @Test
    fun idle_past_end_is_zero() {
        assertEquals(0f, overlayAlpha(COVER, 40f, 1.0f), 0f)
        assertEquals(0f, overlayAlpha(COVER, 40f, 5f), 0f)
    }

    @Test
    fun bad_inputs_clamp() {
        val values = listOf(
            overlayAlpha(COVER, Float.NaN, 0f),
            overlayAlpha(INNER, Float.NaN, 0f),
            overlayAlpha(COVER, 40f, -1f),
            overlayAlpha(INNER, 120f, -1f),
            overlayAlpha(COVER, 400f, 0f),
            overlayAlpha(INNER, 400f, 0f),
        )
        for (v in values) assertTrue("alpha out of range: $v", v in 0f..1f)
    }
}
