package dev.franklyn.foldscenes.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelRoutingTest {
    @Test
    fun panel_for_display_ids() {
        assertEquals(Panel.INNER, panelFor(0))
        assertEquals(Panel.COVER, panelFor(1))
        assertNull(panelFor(2))
        assertNull(panelFor(-1))
    }
}
