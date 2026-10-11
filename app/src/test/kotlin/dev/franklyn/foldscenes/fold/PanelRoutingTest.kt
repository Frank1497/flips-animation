package dev.franklyn.foldscenes.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelRoutingTest {
    @Test
    fun panel_for_display_ids() {
        assertEquals(Panel.INNER, panelFor(0))
        assertEquals(Panel.COVER, panelFor(1))
        assertNull(panelFor(2))
        assertNull(panelFor(-1))
    }

    @Test
    fun cover_off_plausible_only_in_range() {
        assertTrue(isPlausibleSwap(SwapEvent(SwapType.COVER_OFF, 56f)))
        assertTrue(isPlausibleSwap(SwapEvent(SwapType.COVER_OFF, 30f)))
        assertFalse(isPlausibleSwap(SwapEvent(SwapType.COVER_OFF, 0f)))
        assertFalse(isPlausibleSwap(SwapEvent(SwapType.COVER_OFF, 5f)))
        assertFalse(isPlausibleSwap(SwapEvent(SwapType.COVER_OFF, 95f)))
    }

    @Test
    fun inner_on_plausible_only_in_range() {
        assertTrue(isPlausibleSwap(SwapEvent(SwapType.INNER_ON, 109f)))
        assertFalse(isPlausibleSwap(SwapEvent(SwapType.INNER_ON, 180f)))
        assertFalse(isPlausibleSwap(SwapEvent(SwapType.INNER_ON, 70f)))
    }

    @Test
    fun nan_is_never_plausible() {
        assertFalse(isPlausibleSwap(SwapEvent(SwapType.COVER_OFF, Float.NaN)))
        assertFalse(isPlausibleSwap(SwapEvent(SwapType.INNER_ON, Float.NaN)))
    }
}
