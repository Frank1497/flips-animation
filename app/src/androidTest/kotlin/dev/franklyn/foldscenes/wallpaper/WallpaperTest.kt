package dev.franklyn.foldscenes.wallpaper

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import dev.franklyn.foldscenes.scene.Scenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WallpaperTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scene = Scenes.all.first()

    private fun sampled(b: Bitmap, step: Int): List<Int> {
        val out = ArrayList<Int>()
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) { out += b.getPixel(x, y); x += step }
            y += step
        }
        return out
    }

    @Test
    fun scene_bitmap_has_requested_size_and_content() {
        for ((w, h) in listOf(1224 to 2992, 1080 to 1272)) {
            val bmp = SceneBitmap.render(scene, w, h, 0.5f)
            assertEquals(w, bmp.width)
            assertEquals(h, bmp.height)
            val distinct = sampled(bmp, 24).toSet().size
            assertTrue("${w}x$h distinct=$distinct", distinct > 20)
            bmp.recycle()
        }
    }

    @Test
    fun rest_frames_differ() {
        val a = SceneBitmap.render(scene, 1080, 1272, 0f)
        val b = SceneBitmap.render(scene, 1080, 1272, 1f)
        val pa = sampled(a, 8)
        val pb = sampled(b, 8)
        var sum = 0L
        for (i in pa.indices) {
            sum += abs(Color.red(pa[i]) - Color.red(pb[i])) +
                abs(Color.green(pa[i]) - Color.green(pb[i])) +
                abs(Color.blue(pa[i]) - Color.blue(pb[i]))
        }
        val diff = sum.toDouble() / (pa.size * 3)
        assertTrue("p0 vs p1 diff=$diff", diff > 8)
    }

    /** Writes the real cover slots (Razr only): both ids must move, proving setBitmap(which=16, then 4) landed. */
    @Test
    fun applyCover_writes_both_cover_slots_on_device() {
        val wm = WallpaperManager.getInstance(context)
        val lockBefore = wm.getWallpaperId(CoverSlot.LOCK.which)
        val homeBefore = wm.getWallpaperId(CoverSlot.HOME.which)

        val outcome = WallpaperApplier.applyCover(context, scene)

        assertNull("error: ${outcome.error}", outcome.error)
        assertEquals(CoverSlotState.SET, outcome.lock)
        assertEquals(CoverSlotState.SET, outcome.home)
        assertNotEquals(lockBefore, wm.getWallpaperId(CoverSlot.LOCK.which))
        assertNotEquals(homeBefore, wm.getWallpaperId(CoverSlot.HOME.which))
        assertNotEquals(
            "lock and home must be separate slots",
            wm.getWallpaperId(CoverSlot.LOCK.which),
            wm.getWallpaperId(CoverSlot.HOME.which),
        )
    }
}
