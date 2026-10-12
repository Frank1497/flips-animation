package dev.franklyn.foldscenes.scene

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import dev.franklyn.foldscenes.R
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
        // Scene G decodes its frames on a background thread; wait so the matrix renders the real scene.
        Scenes.all.forEach { assertTrue("${it.id} assets not ready", it.awaitAssets(20_000)) }
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
        assertEquals(listOf("sunrise", "eclipse", "mountains", "spiderverse", "rickmorty", "ronaldo"), Scenes.all.map { it.id })
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

    /** Mean colour of a [n]x[n] block centred on ([cx], [cy]) as an (r, g, b) triple. */
    private fun blockMean(b: Bitmap, cx: Int, cy: Int, n: Int = 15): IntArray {
        var r = 0L; var g = 0L; var bl = 0L
        val x0 = cx - n / 2; val y0 = cy - n / 2
        for (y in y0 until y0 + n) for (x in x0 until x0 + n) {
            val c = b.getPixel(x, y)
            r += Color.red(c); g += Color.green(c); bl += Color.blue(c)
        }
        val k = n * n
        return intArrayOf((r / k).toInt(), (g / k).toInt(), (bl / k).toInt())
    }

    private fun isGreySkin(c: IntArray) =
        c.all { it > 150 } && (c.max() - c.min()) < 40

    private fun isPeachSkin(c: IntArray) =
        c[0] > 200 && c[1] in 150..215 && c[2] in 110..190 && c[0] > c[2] + 40

    private fun dist(a: IntArray, b: IntArray) =
        Math.sqrt(((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])).toDouble())

    @Test
    fun assets_are_loaded_in_app_process() {
        val ids = mapOf(
            "rick" to R.drawable.rick, "morty" to R.drawable.morty,
            "spider" to R.drawable.spider, "miles" to R.drawable.miles,
        )
        for ((name, id) in ids) {
            var bmp: Bitmap? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync { bmp = SceneAssets.bitmap(id) }
            assertTrue("SceneAssets.bitmap($name) is null: FoldScenesApp.init has not run", bmp != null)
        }
    }

    @Test
    fun rick_and_morty_halves_merge_at_rest() {
        val f = Scenes.byId("rickmorty")
        assertEquals("rickmorty", f.id)
        for ((w, h) in sizes) {
            val d = meanAbsDiff(SceneBitmap.render(f, w, h, 0f, 1f), SceneBitmap.render(f, w, h, 1f, 1f))
            assertTrue("rickmorty ${w}x$h p0 vs p1 diff=$d", d > 20)
        }
        val w = 1080; val h = 1272
        val bmp = SceneBitmap.render(f, w, h, 0f, 1f)
        val rick = blockMean(bmp, (w * 0.5f - w * 0.12f).toInt(), h / 2)
        val morty = blockMean(bmp, (w * 0.5f + w * 0.12f).toInt(), h / 2)
        assertTrue("Rick side not grey skin: ${rick.toList()}", isGreySkin(rick))
        assertTrue("Morty side not peach skin: ${morty.toList()}", isPeachSkin(morty))
        val dd = dist(rick, morty)
        assertTrue("halves too alike: dist=$dd rick=${rick.toList()} morty=${morty.toList()}", dd > 30)
    }

    @Test
    fun face_scenes_draw_faces_not_just_background() {
        val f = Scenes.byId("rickmorty")
        val w = 1224; val h = 2992
        val bmp = SceneBitmap.render(f, w, h, 1f, 1f)
        val face = blockMean(bmp, w / 2, (h * 0.35f).toInt())
        assertTrue("no Rick face at inner centre-top: ${face.toList()}", isGreySkin(face))
    }

    @Test
    fun assets_decode() {
        val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val ids = mapOf(
            "rick" to R.drawable.rick, "morty" to R.drawable.morty,
            "spider" to R.drawable.spider, "miles" to R.drawable.miles,
        )
        for ((name, id) in ids) {
            val opts = BitmapFactory.Options().apply { inScaled = false }
            val b = BitmapFactory.decodeResource(res, id, opts)
            assertTrue("$name decodes", b != null && b.width > 0 && b.height > 0)
            val px = IntArray(b.width * b.height)
            b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            val opaque = px.count { Color.alpha(it) > 0 }
            // the 32x34 sprites only hold ~650 opaque pixels by construction; the faces hold >70k
            val min = if (name == "rick" || name == "morty") 1000 else 300
            assertTrue("$name opaque=$opaque", opaque >= min)
        }
    }

    @Test
    fun ronaldo_figure_is_drawn() {
        val g = Scenes.byId("ronaldo")
        assertEquals("ronaldo", g.id)
        assertTrue("ronaldo assets not ready", g.awaitAssets(20_000))
        val w = 1080; val h = 1272
        val bmp = SceneBitmap.render(g, w, h, 0.5f, 1f)
        val c = blockMean(bmp, w / 2, (h * 0.46f).toInt())
        assertTrue("no blue kit at the figure centroid: ${c.toList()}", c[2] > c[0] + 25)
    }

    @Test
    fun ronaldo_assets_decode() {
        val am = InstrumentationRegistry.getInstrumentation().targetContext.assets
        for (i in 0 until 37) {
            val name = "ronaldo/f_%02d.png".format(i)
            val b = am.open(name).use { BitmapFactory.decodeStream(it) }
            assertTrue("$name decodes", b != null && b.width > 0 && b.height > 0)
            assertTrue("$name width ${b.width}", b.width in 300..700)
            val px = IntArray(b.width * b.height)
            b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            val opaque = px.count { Color.alpha(it) > 0 }
            assertTrue("$name opaque=$opaque", opaque >= 5000)
            b.recycle()
        }
    }
}
