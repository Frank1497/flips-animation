package dev.franklyn.foldscenes.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelSwapTest {
    @Test
    fun cover_on_to_off_emits_cover_off() =
        assertEquals(SwapEvent(SwapType.COVER_OFF, 56f), swapFor(1, wasOn = true, isOn = false, angle = 56f))

    @Test
    fun inner_off_to_on_emits_inner_on() =
        assertEquals(SwapEvent(SwapType.INNER_ON, 109f), swapFor(0, wasOn = false, isOn = true, angle = 109f))

    @Test
    fun inner_on_to_off_emits_nothing() = assertNull(swapFor(0, wasOn = true, isOn = false, angle = 100f))

    @Test
    fun cover_off_to_on_emits_nothing() = assertNull(swapFor(1, wasOn = false, isOn = true, angle = 10f))

    @Test
    fun unchanged_emits_nothing() {
        for (id in 0..1) for (on in listOf(true, false)) assertNull(swapFor(id, on, on, 90f))
    }
}
