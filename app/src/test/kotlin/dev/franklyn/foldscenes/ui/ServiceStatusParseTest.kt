package dev.franklyn.foldscenes.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceStatusParseTest {
    private val pkg = "dev.franklyn.foldscenes"
    private val cls = "dev.franklyn.foldscenes.overlay.FoldOverlayService"

    @Test
    fun listed_alone() {
        assertTrue(isServiceListed("$pkg/$cls", pkg, cls))
    }

    @Test
    fun listed_in_colon_separated_list() {
        assertTrue(isServiceListed("com.a/com.a.X:$pkg/$cls:com.b/.Y", pkg, cls))
    }

    @Test
    fun listed_in_short_form() {
        assertTrue(isServiceListed("$pkg/.overlay.FoldOverlayService", pkg, cls))
        assertTrue(isServiceListed("com.a/.X:$pkg/.overlay.FoldOverlayService", pkg, cls))
    }

    @Test
    fun absent() {
        assertFalse(isServiceListed("com.a/com.a.X:com.b/.Y", pkg, cls))
    }

    @Test
    fun null_and_empty() {
        assertFalse(isServiceListed(null, pkg, cls))
        assertFalse(isServiceListed("", pkg, cls))
        assertFalse(isServiceListed("  ", pkg, cls))
    }

    @Test
    fun substring_lookalikes_are_false() {
        assertFalse(isServiceListed("other.pkg/$cls", pkg, cls))
        assertFalse(isServiceListed("other.pkg/.overlay.FoldOverlayService", pkg, cls))
        assertFalse(isServiceListed("x$pkg/$cls", pkg, cls))
        assertFalse(isServiceListed("$pkg/${cls}2", pkg, cls))
        assertFalse(isServiceListed("$pkg/.overlay.FoldOverlayServiceX", pkg, cls))
    }
}
