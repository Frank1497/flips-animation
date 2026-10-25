package dev.franklyn.foldscenes.wallpaper

import dev.franklyn.foldscenes.wallpaper.CoverSlotState.FAILED
import dev.franklyn.foldscenes.wallpaper.CoverSlotState.IDLE
import dev.franklyn.foldscenes.wallpaper.CoverSlotState.SET
import dev.franklyn.foldscenes.wallpaper.CoverSlotState.UNCHANGED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverFlowTest {
    /** Mimics WallpaperManagerService: every successful write gives the slot a fresh id. */
    private class FakeSlots(
        private val throwOn: Set<Int> = emptySet(),
        private val idMoves: Boolean = true,
    ) : CoverSlots {
        val writes = ArrayList<Int>()
        private val ids = mutableMapOf(4 to 68, 16 to 68)
        private var next = 100

        override fun id(which: Int): Int {
            if (which in throwOn) throw IllegalArgumentException("Must specify a valid wallpaper category")
            return ids.getValue(which)
        }

        override fun write(which: Int) {
            if (which in throwOn) throw IllegalArgumentException("Must specify a valid wallpaper category")
            writes += which
            if (idMoves) ids[which] = next++
        }
    }

    @Test
    fun slots_map_to_motorola_cli_flags_lock_first() {
        assertEquals(listOf(CoverSlot.LOCK, CoverSlot.HOME), CoverSlot.SEQUENCE)
        assertEquals(16, CoverSlot.LOCK.which)
        assertEquals(4, CoverSlot.HOME.which)
    }

    @Test
    fun writes_lock_16_then_home_4_and_both_count_as_set() {
        val slots = FakeSlots()
        val outcome = writeCover(slots)
        assertEquals(listOf(16, 4), slots.writes)
        assertEquals(SET, outcome.lock)
        assertEquals(SET, outcome.home)
        assertTrue(outcome.ok)
        assertNull(outcome.error)
    }

    @Test
    fun write_that_does_not_move_the_id_is_not_trusted() {
        val outcome = writeCover(FakeSlots(idMoves = false))
        assertEquals(UNCHANGED, outcome.lock)
        assertEquals(UNCHANGED, outcome.home)
        assertFalse(outcome.ok)
    }

    @Test
    fun device_without_cover_slots_fails_cleanly_and_stops_after_lock() {
        val slots = FakeSlots(throwOn = setOf(4, 16))
        val outcome = writeCover(slots)
        assertEquals(emptyList<Int>(), slots.writes)
        assertEquals(FAILED, outcome.lock)
        assertEquals(IDLE, outcome.home)
        assertTrue(outcome.error is IllegalArgumentException)
        assertEquals("This phone has no cover wallpaper slots.", coverResultText(outcome))
    }

    @Test
    fun slot_counts_as_set_only_when_wallpaper_id_changed() {
        assertEquals(SET, slotOutcome(before = 46, after = 67))
        assertEquals(UNCHANGED, slotOutcome(before = 46, after = 46))
        assertEquals(FAILED, slotOutcome(before = 46, after = null))
        assertEquals(FAILED, slotOutcome(before = null, after = 67))
    }

    @Test
    fun status_and_result_text() {
        assertEquals("", coverStatusText(IDLE, IDLE))
        assertEquals("Cover: lock + home both set", coverStatusText(SET, SET))
        assertEquals("Cover: lock set, home unchanged", coverStatusText(SET, UNCHANGED))
        assertEquals("Cover: lock failed, home not set", coverStatusText(FAILED, IDLE))
        assertEquals("Cover wallpaper set on lock and home screens.", coverResultText(CoverOutcome(SET, SET)))
        assertEquals(
            "Cover wallpaper not confirmed: the wallpaper id did not change.",
            coverResultText(CoverOutcome(SET, UNCHANGED)),
        )
        assertEquals(
            "Cover wallpaper failed: boom",
            coverResultText(CoverOutcome(FAILED, IDLE, IllegalStateException("boom"))),
        )
    }
}
