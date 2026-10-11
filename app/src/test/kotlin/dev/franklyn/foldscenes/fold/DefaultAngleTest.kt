package dev.franklyn.foldscenes.fold

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAngleTest {
    @Test
    fun inner_display_on_means_flat() = assertEquals(180f, defaultAngle(true), 0f)

    @Test
    fun inner_display_off_means_closed() = assertEquals(0f, defaultAngle(false), 0f)
}
