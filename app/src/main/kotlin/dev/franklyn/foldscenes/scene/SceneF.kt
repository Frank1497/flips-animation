package dev.franklyn.foldscenes.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import dev.franklyn.foldscenes.R
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Rick and Morty: a pixel portal background drawn at 1/[CS] scale (concentric swirl palette with a
 * growing green portal; port of `bg_rick()`), with the real show art on top at full resolution.
 * Closed (p=0) Rick's left half and Morty's right half form one merged face; as p grows the halves
 * slide apart (Rick up, Morty down), tilt, and each grows into a full face. Main-thread draw only.
 *
 * The art is private-use only (copyrighted); see scripts/export_assets.py.
 */
class SceneF : Scene {
    override val id: String = "rickmorty"

    private class Buf(val gw: Int, val gh: Int) {
        val px = IntArray(gw * gh)
        val bmp: Bitmap = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        // s-independent phase terms of the swirl, so the per-frame loop is two table sines per cell
        val bgA = FloatArray(gw * gh)     // 3 * angle
        val bgD = FloatArray(gw * gh)     // 0.38 * distance
        val swA = FloatArray(gw * gh)     // 2 * angle + 0.75 * distance
        val swD = FloatArray(gw * gh)     // 0.3 * distance
        val starX: IntArray
        val starY: IntArray
        val src = Rect(0, 0, gw, gh)
        val dst = Rect(0, 0, gw * CS, gh * CS)

        init {
            val cx = gw / 2f - 0.5f
            val cy = gh * 0.5f
            for (y in 0 until gh) for (x in 0 until gw) {
                val dx = x - cx
                val dy = y - cy
                val d = hypot(dx, dy)
                val a = atan2(dy, dx)
                val i = y * gw + x
                bgA[i] = a * 3f; bgD[i] = d * 0.38f; swA[i] = a * 2f + d * 0.75f; swD[i] = d * 0.3f
            }
            val rng = Random(3)
            val n = max(6, gw * gh / 70)
            starX = IntArray(n) { rng.nextInt(gw) }
            starY = IntArray(n) { rng.nextInt(gh) }
        }
    }

    /** A face plus its blurred drop shadow, both precomputed once (never per frame). */
    private class Face(val face: Bitmap, val shadow: Bitmap)

    private val cache = GridCache({ gw, gh -> Buf(gw, gh) }, { it.bmp.recycle() })
    private val up = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val facePaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true; isDither = true }
    // not `by lazy`: assets may be missing on the first frame; a null result must be retried next frame
    // @Volatile: the pre-warm writes these from a background thread, the main-thread draw reads them.
    // A face is immutable once published; a rare duplicate build (draw and pre-warm racing) is harmless.
    @Volatile private var rickFaceCache: Face? = null
    @Volatile private var mortyFaceCache: Face? = null
    private fun rickFace(): Face? = rickFaceCache ?: makeFace(R.drawable.rick).also { rickFaceCache = it }
    private fun mortyFace(): Face? = mortyFaceCache ?: makeFace(R.drawable.morty).also { mortyFaceCache = it }

    /**
     * Builds both faces (decode + shadow blur, the expensive part) so the first draw does not. Safe to call
     * from any thread: it touches no Canvas, Paint or scene state, only [SceneAssets] (synchronised) and
     * the volatile caches. Drawing stays main-thread only.
     */
    fun prewarm() {
        rickFace()
        mortyFace()
    }

    private val dstFace = RectF()
    private val dstShadow = RectF()

    override fun draw(canvas: Canvas, width: Int, height: Int, p: Float, timeSeconds: Float) {
        if (width <= 0 || height <= 0) return
        val pp = if (p.isNaN()) 0f else p.coerceIn(0f, 1f)
        val gw = (width + CS - 1) / CS
        val gh = (height + CS - 1) / CS
        val b = cache.get(gw, gh)
        portal(b, pp)
        b.bmp.setPixels(b.px, 0, gw, 0, 0, gw, gh)
        canvas.drawBitmap(b.bmp, b.src, b.dst, up)

        val rick = rickFace() ?: return
        val morty = mortyFace() ?: return
        faces(canvas, width, height, pp, rick, morty)
    }

    private fun portal(b: Buf, s: Float) {
        val gw = b.gw
        val gh = b.gh
        val cx = gw / 2f - 0.5f
        val cy = gh * 0.5f
        val shift = (s * 6f).toInt()
        val rp = (0.06f + 0.36f * smoothstep(0f, 0.75f, s)) * min(gw.toFloat(), gh * 0.8f)
        val invA = 1f / (rp * 0.85f + 1e-6f)
        val invB = 1f / (rp + 1e-6f)
        val sA = s * 14f
        val sB = s * 6f
        val sC = s * 22f
        val sD = s * 8f
        val sin = SIN
        val bg = PAL_BG
        val port = PAL_PORT
        val px = b.px
        for (y in 0 until gh) {
            val row = y * gw
            val dy = (y - cy) * invB
            val dy2 = dy * dy
            // portal columns on this row: ex^2 < 1 - dy2; none when the row misses the ellipse
            val half = if (dy2 < 1f) sqrt(1f - dy2) / invA else -1f
            val x0 = if (half < 0f) gw else max(0, (cx - half).toInt() - 1)
            val x1 = if (half < 0f) gw else min(gw, (cx + half).toInt() + 2)
            for (x in 0 until gw) {
                val i = row + x
                if (x in x0 until x1) {
                    val ex = (x - cx) * invA
                    val e2 = ex * ex + dy2
                    if (e2 < 0.7396f) {          // 0.86^2
                        val inner = sin[((b.swD[i] + sD) * LUT_K).toInt() and (LUT_N - 1)]
                        val sw = sin[((b.swA[i] - sC + 2f * inner) * LUT_K).toInt() and (LUT_N - 1)]
                        px[i] = port[((sw + 1f) * 1.9995f).toInt().coerceIn(0, 3)]
                        continue
                    } else if (e2 < 1f) {
                        px[i] = RIM
                        continue
                    }
                }
                val inner = sin[((b.bgA[i] + sB) * LUT_K).toInt() and (LUT_N - 1)]
                val v = sin[((b.bgD[i] - sA + 3f * inner) * LUT_K).toInt() and (LUT_N - 1)]
                px[i] = bg[(((v + 1f) * 3f).toInt().coerceIn(0, 5) + shift) % 6]
            }
        }
        for (k in b.starX.indices) {
            if (kotlin.math.sin(s * 20f + b.starX[k]) > 0.2f) {
                val i = b.starY[k] * gw + b.starX[k]
                val ex = (b.starX[k] - cx) * invA
                val ey = (b.starY[k] - cy) * invB
                val e = sqrt(ex * ex + ey * ey)
                if (e >= 0.86f) b.px[i] = STAR
            }
        }
    }

    private fun faces(c: Canvas, w: Int, h: Int, s: Float, rick: Face, morty: Face) {
        val ang = s * 180f
        val slide = smoothstep(0f, 100f, ang)
        val grow = smoothstep(85f, 150f, ang)
        val bw = rick.face.width.toFloat()
        val bh = rick.face.height.toFloat()
        val frac = if (h > w * 1.8f) INNER_FRAC else COVER_FRAC
        val k = w * frac / bw
        val fw = bw * k
        val fh = bh * k
        val pad = PAD * k
        val spread = max(0f, min(fh * 0.5f, h / 2f - fh / 2f - 4f))
        val x0 = ((w - fw) / 2f).roundToInt()
        val y0 = h / 2f - fh / 2f
        val ya = (y0 - slide * spread).roundToInt()
        val yb = (y0 + slide * spread).roundToInt()
        val tilt = slide * 6f
        val cx = bw / 2f
        // integer cut columns: identical at grow=0, so the merged face has no seam
        val cutA = (x0 + (cx + (bw - cx) * grow) * k).roundToInt()
        val cutB = (x0 + (cx - cx * grow) * k).roundToInt()
        val left = x0.toFloat()
        val right = x0 + fw
        val off = SHADOW_DX * k

        // shadows first, then faces: a half's blurred cut edge never darkens the other half
        shadowLayer(c, rick, left, ya, fw, fh, pad, tilt, ya + fh * 0.9f, -1f, left - pad, cutA + off)
        shadowLayer(c, morty, left, yb, fw, fh, pad, tilt, yb + fh * 0.1f, 1f, cutB.toFloat(), right + pad + off)
        faceLayer(c, rick, left, ya, fw, fh, tilt, ya + fh * 0.9f, -1f, left, cutA.toFloat())
        faceLayer(c, morty, left, yb, fw, fh, tilt, yb + fh * 0.1f, 1f, cutB.toFloat(), right)
    }

    private fun faceLayer(
        c: Canvas, f: Face, x: Float, y: Int, fw: Float, fh: Float, tilt: Float,
        pivotY: Float, dir: Float, clipL: Float, clipR: Float,
    ) {
        val n = c.save()
        c.rotate(dir * tilt, x + fw / 2f, pivotY)
        c.clipRect(clipL, y - 4f, clipR, y + fh + 4f)
        dstFace.set(x, y.toFloat(), x + fw, y + fh)
        c.drawBitmap(f.face, null, dstFace, facePaint)
        c.restoreToCount(n)
    }

    private fun shadowLayer(
        c: Canvas, f: Face, x: Float, y: Int, fw: Float, fh: Float, pad: Float, tilt: Float,
        pivotY: Float, dir: Float, clipL: Float, clipR: Float,
    ) {
        val n = c.save()
        c.rotate(dir * tilt, x + fw / 2f, pivotY)
        c.clipRect(clipL, y - pad, clipR, y + fh + pad)
        dstShadow.set(x - pad, y - pad, x + fw + pad, y + fh + pad)
        c.drawBitmap(f.shadow, null, dstShadow, facePaint)
        c.restoreToCount(n)
    }

    private fun makeFace(resId: Int): Face? {
        val face = SceneAssets.bitmap(resId) ?: return null
        val w = face.width
        val h = face.height
        val sw = w + 2 * PAD
        val sh = h + 2 * PAD
        val src = IntArray(w * h)
        face.getPixels(src, 0, w, 0, 0, w, h)
        val alpha = FloatArray(sw * sh)
        for (y in 0 until h) for (x in 0 until w) {
            alpha[(y + PAD) * sw + x + PAD] = (src[y * w + x] ushr 24) / 255f
        }
        boxBlur3(alpha, sw, sh, SHADOW_BLUR)
        // offset by (SHADOW_DX, SHADOW_DY) while writing out black with SHADOW_OPACITY
        val out = IntArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            val sx = x - SHADOW_DX.toInt()
            val sy = y - SHADOW_DY.toInt()
            if (sx in 0 until sw && sy in 0 until sh) {
                val a = (alpha[sy * sw + sx] * SHADOW_OPACITY).roundToInt().coerceIn(0, 255)
                out[y * sw + x] = a shl 24
            }
        }
        val shadow = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        shadow.setPixels(out, 0, sw, 0, 0, sw, sh)
        return Face(face, shadow)
    }

    /** Three box passes of width ~sqrt(12 sigma^2 / 3 + 1) approximate a gaussian of [sigma]. */
    private fun boxBlur3(a: FloatArray, w: Int, h: Int, sigma: Float) {
        val boxW = sqrt(12f * sigma * sigma / 3f + 1f).toInt().coerceAtLeast(1)
        val r = boxW / 2
        val tmp = FloatArray(max(w, h))
        repeat(3) {
            for (y in 0 until h) blurLine(a, y * w, 1, w, r, tmp)
            for (x in 0 until w) blurLine(a, x, w, h, r, tmp)
        }
    }

    private fun blurLine(a: FloatArray, start: Int, step: Int, n: Int, r: Int, tmp: FloatArray) {
        var sum = 0f
        for (i in 0..min(r, n - 1)) sum += a[start + i * step]
        val inv = 1f / (2 * r + 1)
        for (i in 0 until n) {
            tmp[i] = sum * inv
            val add = i + r + 1
            val sub = i - r
            if (add < n) sum += a[start + add * step]
            if (sub >= 0) sum -= a[start + sub * step]
        }
        for (i in 0 until n) a[start + i * step] = tmp[i]
    }

    companion object {
        const val CS = 7
        /** Face canvas width as a fraction of the panel width (cover / inner). */
        private const val COVER_FRAC = 0.675f
        private const val INNER_FRAC = 0.70f
        private const val PAD = 60                 // shadow padding, in face-bitmap pixels
        private const val SHADOW_BLUR = 15f
        private const val SHADOW_DX = 12f
        private const val SHADOW_DY = 20f
        private const val SHADOW_OPACITY = 120f

        private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        private val PAL_BG = intArrayOf(
            rgb(14, 8, 40), rgb(52, 20, 108), rgb(24, 86, 142), rgb(18, 160, 150), rgb(214, 56, 168), rgb(250, 200, 60),
        )
        private val PAL_PORT = intArrayOf(rgb(8, 60, 16), rgb(36, 160, 44), rgb(140, 250, 96), rgb(225, 255, 190))
        private val RIM = rgb(200, 255, 130)
        private val STAR = rgb(255, 255, 255)

        internal const val LUT_N = 4096
        internal val SIN = FloatArray(LUT_N) { kotlin.math.sin(it * 2.0 * PI / LUT_N).toFloat() }
        internal const val LUT_K = (LUT_N / (2.0 * PI)).toFloat()

    }
}
