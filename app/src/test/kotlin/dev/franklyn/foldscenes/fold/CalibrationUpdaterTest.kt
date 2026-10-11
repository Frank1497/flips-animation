package dev.franklyn.foldscenes.fold

import dev.franklyn.foldscenes.fold.SwapType.COVER_OFF
import dev.franklyn.foldscenes.fold.SwapType.INNER_ON
import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationUpdaterTest {
    private val base = Calibration()

    @Test
    fun median_of_last_ten() {
        val events = List(2) { SwapEvent(COVER_OFF, 30f) } + List(10) { SwapEvent(COVER_OFF, 60f) }
        val result = updated(base, events)
        assertEquals(60f, result.coverOffAngle, 0f)
        assertEquals(base.innerOnAngle, result.innerOnAngle, 0f)
    }

    @Test
    fun even_count_uses_mean_of_middle_two() {
        val events = listOf(SwapEvent(INNER_ON, 100f), SwapEvent(INNER_ON, 110f))
        assertEquals(105f, updated(base, events).innerOnAngle, 0f)
    }

    @Test
    fun rejects_out_of_range() {
        val events = listOf(
            SwapEvent(COVER_OFF, 5f),
            SwapEvent(COVER_OFF, 95f),
            SwapEvent(INNER_ON, 70f),
            SwapEvent(INNER_ON, 160f),
            SwapEvent(COVER_OFF, Float.NaN),
        )
        assertEquals(base, updated(base, events))
    }

    @Test
    fun rejected_events_do_not_consume_the_window() {
        val events = List(10) { SwapEvent(COVER_OFF, 60f) } + List(5) { SwapEvent(COVER_OFF, 5f) }
        assertEquals(60f, updated(base, events).coverOffAngle, 0f)
    }

    @Test
    fun keeps_order() {
        val events = listOf(SwapEvent(COVER_OFF, 90f), SwapEvent(INNER_ON, 80f))
        assertEquals(base, updated(base, events))
    }

    @Test
    fun empty_returns_current() {
        assertEquals(base, updated(base, emptyList()))
    }
}
