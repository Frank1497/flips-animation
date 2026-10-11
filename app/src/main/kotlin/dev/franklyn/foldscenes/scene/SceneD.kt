package dev.franklyn.foldscenes.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import dev.franklyn.foldscenes.R
import java.util.Random
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Spider-Verse pixel scene: a Bayer-dithered navy to crimson gradient, a web of spokes and rings that
 * spins outward from the crease as p grows, a night-city skyline with lit windows, and the Peter and
 * Miles masks side by side on the crease. Drawn at 1/[CS] scale into a small offscreen bitmap and
 * scaled up with nearest-neighbour filtering. Port of `bg_spider()` from the concept prototype.
 * Main-thread draw only; buffers are allocated once per grid size.
 */
class SceneD : Scene {
    override val id: String = "spiderverse"

    private class Buf(val gw: Int, val gh: Int) {
        val px = IntArray(gw * gh)
        val bmp: Bitmap = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cityH = CITY_ROWS.coerceAtMost(gh)
        val city: Bitmap = buildCity(gw, gh, cityH)
        val src = Rect(0, 0, gw, gh)
        val dst = Rect(0, 0, gw * CS, gh * CS)

        /** Seeded sky stars (grid indices into [px], upper 55% of the grid) with a tint each. */
        val starIdx: IntArray
        val starCol: IntArray

        init {
            val rng = Random(11)
            val n = max(8, gw * gh / 90)
            starIdx = IntArray(n) { rng.nextInt(gw) + rng.nextInt((gh * 0.55f).toInt().coerceAtLeast(1)) * gw }
            starCol = IntArray(n) { STAR_TINTS[rng.nextInt(STAR_TINTS.size)] }
        }
    }

    private val cache = GridCache({ gw, gh -> Buf(gw, gh) }, { it.bmp.recycle(); it.city.recycle() })
    private val up = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val nearest = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val spokePaint = linePaint(SPOKE)
    private val ringPaint = linePaint(RING)
    private val pal = IntArray(PAL_N)
    private val lines = FloatArray(4 * 64)
    private val spriteDst = Rect()

    override fun draw(canvas: Canvas, width: Int, height: Int, p: Float, timeSeconds: Float) {
        if (width <= 0 || height <= 0) return
        val pp = if (p.isNaN()) 0f else p.coerceIn(0f, 1f)
        val gw = (width + CS - 1) / CS
        val gh = (height + CS - 1) / CS
        val b = cache.get(gw, gh)

        gradient(b, pp)
        if (pp < 0.7f) for (k in b.starIdx.indices) b.px[b.starIdx[k]] = b.starCol[k]  // fade out into the sunset
        b.bmp.setPixels(b.px, 0, gw, 0, 0, gw, gh)
        web(b, pp)
        b.canvas.drawBitmap(b.city, 0f, (gh - b.cityH).toFloat(), nearest)
        masks(b)

        canvas.drawBitmap(b.bmp, b.src, b.dst, up)
    }

    private fun gradient(b: Buf, p: Float) {
        val heat = smoothstep(0f, 1f, p)
        val c0 = mix(12, 6, 40, 30, 18, 96, heat)
        val c1 = mix(64, 16, 108, 150, 30, 124, heat)
        val c2 = mix(150, 24, 96, 238, 60, 98, heat)
        val c3 = mix(226, 66, 80, 255, 156, 96, heat)
        // the prototype's four stops, with a midpoint between each pair (more bands, same look)
        pal[0] = c0; pal[1] = lerp(c0, c1, 0.5f); pal[2] = c1; pal[3] = lerp(c1, c2, 0.5f)
        pal[4] = c2; pal[5] = lerp(c2, c3, 0.5f); pal[6] = c3
        val gw = b.gw
        val gh = b.gh
        for (y in 0 until gh) {
            val v = (y.toFloat() / max(1, gh - 1)).toDouble().pow(1.15).toFloat()
            val pos = v * (PAL_N - 1)
            val i = pos.toInt().coerceIn(0, PAL_N - 2)
            val frac = pos - i
            val lo = pal[i]
            val hi = pal[i + 1]
            val row = y * gw
            val bay = (y and 3) * 4
            for (x in 0 until gw) {
                b.px[row + x] = if (frac > BAYER[bay + (x and 3)]) hi else lo
            }
        }
    }

    private fun web(b: Buf, p: Float) {
        val c = b.canvas
        val cx = (b.gw / 2).toFloat()
        val cy = (b.gh / 2).toFloat()
        val pr = smoothstep(0f, 0.9f, p)
        val reach = max(b.gw, b.gh) * 0.9f * pr
        if (reach < 1f) return
        val spin = p * 0.5f   // the whole web twists a little as it spreads
        for (k in 0 until 12) {
            val r = Math.toRadians(k * 30.0).toFloat() + spin
            c.drawLine(cx, cy, cx + reach * cos(r), cy + reach * sin(r), spokePaint)
        }
        val sc = b.gw / 56f
        var ring = 4f * sc
        while (ring < 60f * sc && ring <= reach) {
            var n = 0
            var prevX = 0f
            var prevY = 0f
            var first = true
            var a = 0
            while (a <= 360) {
                val r = Math.toRadians(a.toDouble()).toFloat() + spin
                val r2 = Math.toRadians((a + 15).toDouble()).toFloat() + spin
                val x1 = cx + ring * cos(r)
                val y1 = cy + ring * sin(r)
                val x2 = cx + ring * 0.9f * cos(r2)
                val y2 = cy + ring * 0.9f * sin(r2)
                if (!first) n = seg(n, prevX, prevY, x1, y1)
                n = seg(n, x1, y1, x2, y2)
                prevX = x2
                prevY = y2
                first = false
                a += 30
            }
            c.drawLines(lines, 0, n, ringPaint)
            ring += 5f * sc
        }
    }

    private fun seg(n: Int, x0: Float, y0: Float, x1: Float, y1: Float): Int {
        lines[n] = x0; lines[n + 1] = y0; lines[n + 2] = x1; lines[n + 3] = y1
        return n + 4
    }

    private fun masks(b: Buf) {
        val spider = SceneAssets.bitmap(R.drawable.spider) ?: return
        val miles = SceneAssets.bitmap(R.drawable.miles) ?: return
        // largest whole-number art-pixel size that keeps both masks inside 90% of the grid width
        val s = ((b.gw * 0.9f - GAP) / (2 * spider.width)).toInt().coerceAtLeast(1)
        val w = spider.width * s
        val h = spider.height * s
        val left = (b.gw - (2 * w + GAP)) / 2
        val top = (b.gh - h) / 2
        spriteDst.set(left, top, left + w, top + h)
        b.canvas.drawBitmap(spider, null, spriteDst, nearest)
        spriteDst.set(left + w + GAP, top, left + 2 * w + GAP, top + h)
        b.canvas.drawBitmap(miles, null, spriteDst, nearest)
    }

    companion object {
        const val CS = 7
        private const val PAL_N = 7
        private const val GAP = 4
        private const val CITY_ROWS = 96
        private val STAR_TINTS = intArrayOf(rgb(255, 255, 255), rgb(190, 210, 255), rgb(255, 196, 220))
        private val SPOKE = rgb(200, 176, 214)
        private val RING = rgb(150, 112, 172)
        private val BAYER = floatArrayOf(
            0f, 8f, 2f, 10f, 12f, 4f, 14f, 6f, 3f, 11f, 1f, 9f, 15f, 7f, 13f, 5f,
        ).also { for (i in it.indices) it[i] /= 16f }

        private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        private fun mix(r0: Int, g0: Int, b0: Int, r1: Int, g1: Int, b1: Int, t: Float) = rgb(
            (r0 + (r1 - r0) * t).roundToInt(), (g0 + (g1 - g0) * t).roundToInt(), (b0 + (b1 - b0) * t).roundToInt(),
        )

        private fun lerp(a: Int, b: Int, t: Float): Int {
            fun ch(sh: Int) = (((a shr sh) and 255) + (((b shr sh) and 255) - ((a shr sh) and 255)) * t).roundToInt()
            return rgb(ch(16), ch(8), ch(0))
        }

        private fun linePaint(color: Int) = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 0f          // hairline: one art pixel wide, no antialiasing
            isAntiAlias = false
        }

        /**
         * Skyline strip of the bottom [rows] grid rows, seeded so every frame at a given size is the
         * same. Building heights grow with the grid so the skyline keeps the prototype's proportions.
         */
        private fun buildCity(gw: Int, gh: Int, rows: Int): Bitmap {
            val px = IntArray(gw * rows)
            val rng = Random(4)
            val hs = (gh / 120).coerceAtLeast(1)
            val wall = rgb(10, 8, 22)
            val lit = rgb(255, 214, 120)
            var x = 0
            while (x < gw) {
                val bw = 4 + rng.nextInt(5)
                val bh = ((5 + rng.nextInt(10)) * hs).coerceAtMost(rows)
                for (yy in rows - bh until rows) for (xx in x until minOf(x + bw, gw)) px[yy * gw + xx] = wall
                var wy = rows - bh + 2
                while (wy < rows - 1) {
                    var wx = x + 1
                    while (wx < x + bw - 1) {
                        if (rng.nextFloat() < 0.35f && wx < gw) px[wy * gw + wx] = lit
                        wx += 2
                    }
                    wy += 3
                }
                x += bw
            }
            val bmp = Bitmap.createBitmap(gw, rows, Bitmap.Config.ARGB_8888)
            bmp.setPixels(px, 0, gw, 0, 0, gw, rows)
            return bmp
        }
    }
}
