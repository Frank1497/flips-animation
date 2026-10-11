package dev.franklyn.foldscenes.scene

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import dev.franklyn.foldscenes.overlay.SceneView
import dev.franklyn.foldscenes.wallpaper.SceneBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SceneRenderTest {
    private val sizes = listOf(1224 to 2992, 1080 to 1272)
    private val ps = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

    private fun sampled(b: Bitmap, step: Int = 24): List<Int> {
        val out = ArrayList<Int>()
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) { out += b.getPixel(x, y); x += step }
            y += step
        }
        return out
    }

    private fun meanAbsDiff(a: Bitmap, b: Bitmap): Double {
        val pa = sampled(a, 8); val pb = sampled(b, 8)
        var sum = 0L
        for (i in pa.indices) {
            sum += abs(Color.red(pa[i]) - Color.red(pb[i])) +
                abs(Color.green(pa[i]) - Color.green(pb[i])) +
                abs(Color.blue(pa[i]) - Color.blue(pb[i]))
        }
        return sum.toDouble() / (pa.size * 3)
    }

    @Test
    fun every_scene_renders_varied_and_progress_changes_it() {
        assertTrue(Scenes.all.isNotEmpty())
        for (scene in Scenes.all) for ((w, h) in sizes) {
            val frames = ps.associateWith { p -> SceneBitmap.render(scene, w, h, p, 1f) }
            for ((p, bmp) in frames) {
                val distinct = sampled(bmp).toSet().size
                assertTrue("${scene.id} ${w}x$h p=$p distinct=$distinct", distinct > 20)
            }
            val d = meanAbsDiff(frames.getValue(0f), frames.getValue(1f))
            assertTrue("${scene.id} ${w}x$h p0 vs p1 diff=$d", d > 8)
        }
    }

    @Test
    fun registry_lists_all_scenes_in_order() {
        assertEquals(listOf("sunrise", "eclipse", "mountains"), Scenes.all.map { it.id })
    }

    @Test
    fun unknown_id_falls_back_to_scene_a() {
        assertEquals(Scenes.all.first().id, Scenes.byId("no-such-scene").id)
    }

    @Test
    fun view_clamps_inputs() {
        var progressNaN = -1f; var hi = -1f; var lo = -1f; var pHi = -1f; var pLo = -1f
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val v = SceneView(InstrumentationRegistry.getInstrumentation().targetContext)
            v.progress = Float.NaN; progressNaN = v.progress
            v.alpha01 = 7f; hi = v.alpha01
            v.alpha01 = -3f; lo = v.alpha01
            v.progress = 2f; pHi = v.progress
            v.progress = -1f; pLo = v.progress
        }
        assertEquals(0f, progressNaN, 0f)
        assertEquals(1f, hi, 0f)
        assertEquals(0f, lo, 0f)
        assertEquals(1f, pHi, 0f)
        assertEquals(0f, pLo, 0f)
    }
}
