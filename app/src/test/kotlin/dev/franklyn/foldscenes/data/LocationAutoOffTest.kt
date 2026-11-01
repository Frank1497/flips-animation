package dev.franklyn.foldscenes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAutoOffTest {
    @Test
    fun since_starts_when_location_turns_on_and_clears_when_off() {
        assertEquals(1000L, trackLocationOnSince(null, locationOn = true, nowMs = 1000L))
        assertNull(trackLocationOnSince(500L, locationOn = false, nowMs = 1000L))
    }

    @Test
    fun since_does_not_reset_while_location_stays_on() {
        assertEquals(500L, trackLocationOnSince(500L, locationOn = true, nowMs = 9000L))
    }

    @Test
    fun turning_off_then_on_again_restarts_the_timer() {
        val offAgain = trackLocationOnSince(500L, locationOn = false, nowMs = 1000L)
        assertEquals(2000L, trackLocationOnSince(offAgain, locationOn = true, nowMs = 2000L))
    }

    @Test
    fun fires_only_once_interval_has_fully_elapsed() {
        val sinceMs = 0L
        val intervalMinutes = 30
        assertFalse(shouldAutoOffLocation(sinceMs, nowMs = 29 * 60_000L, intervalMinutes, locationOn = true))
        assertTrue(shouldAutoOffLocation(sinceMs, nowMs = 30 * 60_000L, intervalMinutes, locationOn = true))
        assertTrue(shouldAutoOffLocation(sinceMs, nowMs = 45 * 60_000L, intervalMinutes, locationOn = true))
    }

    @Test
    fun never_fires_when_location_already_off_or_never_seen_on() {
        assertFalse(shouldAutoOffLocation(sinceMs = null, nowMs = 100_000L, intervalMinutes = 30, locationOn = true))
        assertFalse(shouldAutoOffLocation(sinceMs = 0L, nowMs = 100_000L, intervalMinutes = 30, locationOn = false))
    }

    @Test
    fun interval_clamps_to_the_nearest_preset() {
        assertEquals(15, clampIntervalMinutes(10))
        assertEquals(30, clampIntervalMinutes(30))
        assertEquals(60, clampIntervalMinutes(50))
        assertEquals(240, clampIntervalMinutes(999))
    }
}
