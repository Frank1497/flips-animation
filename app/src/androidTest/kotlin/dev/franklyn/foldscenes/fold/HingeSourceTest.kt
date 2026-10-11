package dev.franklyn.foldscenes.fold

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HingeSourceTest {
    private lateinit var context: Context
    private lateinit var hinge: HingeSource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        hinge = HingeSource(context)
    }

    @After
    fun tearDown() = hinge.stop()

    @Test
    fun start_then_stop_unregisters_listener() {
        assertFalse(hinge.isRegistered)
        hinge.start()
        assertTrue(hinge.isRegistered)
        hinge.stop()
        assertFalse(hinge.isRegistered)

        hinge.start()
        hinge.start()
        hinge.stop()
        assertFalse(hinge.isRegistered)

        hinge.stop() // stop without start must not throw
        assertFalse(hinge.isRegistered)
    }

    @Test
    fun angle_is_clamped() {
        hinge.start()
        Thread.sleep(300)
        assertTrue(hinge.angle.value in 0f..180f)
    }

    @Test
    fun initial_angle_matches_inner_display_state() {
        val dm = context.getSystemService(DisplayManager::class.java)
        val innerOn = dm.getDisplay(0)?.state == Display.STATE_ON
        assertEquals(defaultAngle(innerOn), hinge.angle.value, 0f)
    }
}
