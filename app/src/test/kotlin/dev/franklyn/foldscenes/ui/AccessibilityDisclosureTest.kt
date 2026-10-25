package dev.franklyn.foldscenes.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityDisclosureTest {
    @Test fun not_yet_decided_requires_showing_disclosure() {
        assertTrue(shouldShowAccessibilityDisclosure(storedChoice = null))
    }
    @Test fun accepted_does_not_show_again() {
        assertFalse(shouldShowAccessibilityDisclosure(storedChoice = true))
    }
    @Test fun declined_does_not_show_again_automatically() {
        assertFalse(shouldShowAccessibilityDisclosure(storedChoice = false))
    }
}
