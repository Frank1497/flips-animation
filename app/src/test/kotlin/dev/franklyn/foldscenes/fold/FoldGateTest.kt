package dev.franklyn.foldscenes.fold

import dev.franklyn.foldscenes.fold.Panel.COVER
import dev.franklyn.foldscenes.fold.Panel.INNER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldGateTest {
    private fun seeded(rest: Float) = FoldGate().apply { update(rest, 0f, hasEvent = true) }

    @Test
    fun threshold_is_measured_jitter_plus_one_degree() {
        assertEquals(0f, REST_JITTER_DEG, 0f)
        assertEquals(1f, MOVE_THRESHOLD_DEG, 0f)
    }

    @Test
    fun no_sensor_event_yet_never_arms() {
        val g = FoldGate()
        assertFalse(g.update(180f, 0f, hasEvent = false))
        assertFalse(g.update(0f, 0f, hasEvent = false))
    }

    @Test
    fun first_event_seeds_the_rest_angle_without_arming() {
        val g = FoldGate()
        g.update(180f, 0f, hasEvent = false)
        assertFalse("initial on-change report is not motion", g.update(176f, 0f, hasEvent = true))
        assertEquals(176f, g.restAngle, 0f)
    }

    @Test
    fun one_degree_flicker_at_rest_never_arms() {
        val g = seeded(180f)
        for (a in listOf(179f, 180f, 179f, 180f)) assertFalse("$a", g.update(a, 0f))
    }

    @Test
    fun moving_past_threshold_arms() {
        val g = seeded(180f)
        assertTrue(g.update(178f, 0f))
        assertTrue("stays armed while moving", g.update(150f, 0.1f))
    }

    @Test
    fun slow_creep_is_measured_from_rest_not_from_last_sample() {
        val g = seeded(180f)
        assertFalse(g.update(179f, 0f))
        assertTrue(g.update(178f, 0f))
    }

    @Test
    fun settling_disarms_and_rerests_at_the_new_angle() {
        val g = seeded(180f)
        assertTrue(g.update(100f, 0f))
        assertFalse(g.update(100f, 1.0f))
        assertEquals(100f, g.restAngle, 0f)
        assertFalse(g.update(101f, 0f))
        assertTrue(g.update(98f, 0f))
    }

    @Test
    fun wouldMove_matches_update_without_changing_state() {
        val g = seeded(180f)
        assertFalse(g.wouldMove(179f))
        assertTrue(g.wouldMove(178f))
        assertFalse(g.moving)
        assertFalse("unseeded gate never claims motion", FoldGate().wouldMove(10f))
    }

    @Test
    fun replay_of_a_real_recorded_fold_arms_and_shows_the_inner_overlay() {
        // Hinge events captured from the device on 2026-10-26 02:24 (seconds since first event, degrees).
        val trace = listOf(
            0.0 to 141f, 0.0 to 136f, 0.04 to 134f, 0.04 to 130f, 0.079 to 129f, 0.079 to 127f,
            0.119 to 125f, 0.119 to 124f, 0.158 to 122f, 0.158 to 121f, 0.475 to 105f, 0.475 to 103f,
            0.515 to 100f, 0.554 to 98f, 0.594 to 97f, 1.114 to 95f, 1.153 to 94f, 1.193 to 92f,
        )
        val g = FoldGate()
        var visibleFrames = 0
        var frames = 0
        var t = 0.0
        while (t <= trace.last().first + 0.05) {
            val (lastT, angle) = trace.last { it.first <= t }
            val moving = g.update(angle, (t - lastT).toFloat())
            if (t > 0.0) {
                frames++
                if (foldAlpha(INNER, angle, (t - lastT).toFloat(), moving) > 0.5f) visibleFrames++
            }
            t += 0.016
        }
        assertTrue("visible $visibleFrames of $frames frames", visibleFrames > frames * 0.9)
    }

    @Test
    fun jitter_at_a_half_open_rest_keeps_alpha_zero() {
        // Ungated, a resting half-open phone (100°) would be fully visible on any event.
        assertEquals(1f, overlayAlpha(INNER, 100f, 0f), 0f)
        assertEquals(0f, foldAlpha(INNER, 100f, 0f, moving = false), 0f)
        assertEquals(0f, foldAlpha(COVER, 100f, 0f, moving = false), 0f)
        assertEquals(1f, foldAlpha(COVER, 100f, 0f, moving = true), 0f)
    }
}
