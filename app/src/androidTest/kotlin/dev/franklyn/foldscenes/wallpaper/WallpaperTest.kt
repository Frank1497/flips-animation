package dev.franklyn.foldscenes.wallpaper

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.franklyn.foldscenes.scene.Scenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private fun coverRows(resolver: ContentResolver, name: String): List<Uri> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val rows = ArrayList<Uri>()
        resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(name),
            null,
        )?.use { c ->
            while (c.moveToNext()) rows += android.content.ContentUris.withAppendedId(collection, c.getLong(0))
        }
        return rows
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

    @Test
    fun cover_editor_intent_resolves() {
        val intent = Intent(WallpaperApplier.COVER_EDITOR_ACTION).apply { type = "image/png" }
        assertNotNull(context.packageManager.resolveActivity(intent, 0))
    }

    @Test
    fun applyCover_inserts_mediastore_row_and_overwrites() {
        val name = WallpaperApplier.coverFileName(scene.id)
        assertEquals("razrfold_sunrise_cover.png", name)
        val resolver = context.contentResolver
        try {
            WallpaperApplier.saveCoverFrame(context, scene)
            val uri = WallpaperApplier.saveCoverFrame(context, scene)
            val rows = coverRows(resolver, name)
            assertEquals("rows named $name: $rows", 1, rows.size)
            assertEquals(uri, rows.single())
            val decoded = resolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }
            assertEquals(1080, decoded.width)
            assertEquals(1272, decoded.height)
        } finally {
            coverRows(resolver, name).forEach { resolver.delete(it, null, null) }
        }
    }
}
