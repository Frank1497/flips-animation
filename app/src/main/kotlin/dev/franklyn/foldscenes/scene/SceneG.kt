package dev.franklyn.foldscenes.scene

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Ronaldo's bicycle kick on purple Matrix rain. The fold scrubs a 37-frame cut-out of the kick
 * (pose index p*36, neighbouring frames cross-faded) over falling katakana that fall as the phone
 * opens and drift slowly while idle. Port of `matrix()` / `render()` from the approved mockup.
 *
 * Rain: one AGSL pass. Each pixel finds its glyph cell, derives that column's head position from a
 * per-column hash (no loops), and samples a glyph atlas (system katakana, rendered once with the
 * system font) for the sharp glyph and a pre-blurred copy for the bloom.
 *
 * Figure: frames are decoded once on a background thread the first time the scene draws (or when
 * [awaitAssets] is called) and kept as GPU-resident bitmaps; until then only the rain is drawn.
 * Frames are cropped cut-outs, drawn at one uniform scale so the figure never pulses.
 * Main-thread draw only (one shared shader, paints and matrices).
 *
 * The art is private-use only (copyrighted broadcast footage); see scripts/export_ronaldo.py.
 */
class SceneG : Scene {
    override val id: String = "ronaldo"

    // Rain
    private val shader: RuntimeShader by lazy { RuntimeShader(AGSL) }
    private val bgPaint: Paint by lazy { Paint() }
    private var atlasShader: BitmapShader? = null

    // Figure
    private val figPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val upperPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val glowPaint = Paint().apply {
        isFilterBitmap = true
        color = Color.argb(166, 200, 110, 255) // ~65% of the violet tint
    }
    private val ghostPaints = arrayOf(ghostPaint(0.12f), ghostPaint(0.22f))
    private val m = Matrix()

    /** One cut-out: bitmap (GPU-resident when possible), alpha centroid in source px, and its blurred glow. */
    private class Frame(
        val bmp: Bitmap, val cx: Float, val cy: Float,
        val glow: Bitmap, val glowDx: Int, val glowDy: Int, val glowScaleX: Float, val glowScaleY: Float,
    )

    @Volatile private var frames: Array<Frame>? = null
    private val started = AtomicBoolean(false)
    private val ready = CountDownLatch(1)

    /** Decoded footprint in bytes (sum of w*h*4); 0 until loaded. */
    @Volatile var decodedBytes: Long = 0L
        private set

    /** Wall-clock ms the background decode took; 0 until loaded. */
    @Volatile var decodeMs: Long = 0L
        private set

    private fun ghostPaint(alpha: Float) = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
        colorFilter = PorterDuffColorFilter(Color.argb((alpha * 255f).roundToInt(), 190, 120, 255), PorterDuff.Mode.SRC_IN)
    }

    /** Starts the background decode once (idempotent, any thread). */
    fun prewarm() {
        if (!started.compareAndSet(false, true)) return
        Thread({
            val t0 = System.nanoTime()
            try {
                val am = SceneAssets.assetManager()
                if (am == null) {
                    started.set(false) // assets not handed over yet: allow a retry on a later draw
                } else {
                    val list = arrayOfNulls<Frame>(FRAMES)
                    val opts = BitmapFactory.Options().apply {
                        inScaled = false
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    var bytes = 0L
                    for (i in 0 until FRAMES) {
                        val src = am.open("ronaldo/f_%02d.png".format(i)).use { BitmapFactory.decodeStream(it, null, opts) }
                            ?: error("frame $i failed to decode")
                        bytes += src.width.toLong() * src.height * 4
                        list[i] = makeFrame(src)
                    }
                    @Suppress("UNCHECKED_CAST")
                    frames = list as Array<Frame>
                    decodedBytes = bytes
                    decodeMs = (System.nanoTime() - t0) / 1_000_000
                    ready.countDown()
                }
            } catch (t: Throwable) {
                Log.e("FoldScenes", "scene ronaldo frame decode failed (rain only): ${t.javaClass.name}: ${t.message}", t)
                ready.countDown() // unblock waiters; frames stays null so draw stays rain only
            }
        }, "sceneg-decode").start()
    }

    /** Waits until the frames are decoded (starting the decode if needed). Never call on the main thread. */
    override fun awaitAssets(timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (frames == null && System.nanoTime() < deadline) {
            prewarm()
            if (ready.await(50, TimeUnit.MILLISECONDS)) break
        }
        return frames != null
    }

    // Software work only (centroid, alpha blur, GPU upload): safe on the loader thread.
    private fun makeFrame(src: Bitmap): Frame {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var sx = 0.0; var sy = 0.0; var n = 0L
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((px[row + x] ushr 24) > 128) { sx += x; sy += y; n++ }
            }
        }
        val cx = if (n > 0) (sx / n).toFloat() else w / 2f
        val cy = if (n > 0) (sy / n).toFloat() else h / 2f
        // Glow: the frame's alpha at 1/4 size, blurred. extractAlpha with a blur paint grows the bitmap and reports the offset.
        val sw = maxOf(1, w / GLOW_DOWN)
        val sh = maxOf(1, h / GLOW_DOWN)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val off = IntArray(2)
        val blur = Paint().apply { maskFilter = BlurMaskFilter(GLOW_BLUR, BlurMaskFilter.Blur.NORMAL) }
        val glow = small.extractAlpha(blur, off)
        if (small !== src) small.recycle()
        val hw = src.copy(Bitmap.Config.HARDWARE, false)
        val bmp = if (hw != null) { src.recycle(); hw } else src
        return Frame(bmp, cx, cy, glow, off[0], off[1], w.toFloat() / sw, h.toFloat() / sh)
    }

    private fun buildAtlas(): Bitmap {
        val bmp = Bitmap.createBitmap(ATLAS_W * 2, ATLAS_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val tf = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val sharp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = tf; textSize = ACELL * 0.95f }
        val blur = Paint(sharp).apply { maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL) }
        val ch = CharArray(1)
        val r = Rect()
        for (gi in 0 until NG) {
            ch[0] = if (gi < 86) (0x30A1 + gi).toChar() else ('0' + (gi - 86))
            sharp.getTextBounds(ch, 0, 1, r)
            val cellX = (gi % ATLAS_COLS) * ACELL
            val cellY = (gi / ATLAS_COLS) * ACELL
            val tx = cellX + (ACELL - r.width()) / 2f - r.left
            val ty = cellY + (ACELL - r.height()) / 2f - r.top
            for (pass in 0..1) {
                val dx = pass * ATLAS_W
                c.save()
                c.clipRect(cellX + dx, cellY, cellX + dx + ACELL, cellY + ACELL)
                c.drawText(ch, 0, 1, tx + dx, ty, if (pass == 0) sharp else blur)
                c.restore()
            }
        }
        return bmp
    }

    private fun placeFrame(left: Float, top: Float, k: Float) {
        m.setScale(k, k)
        m.postTranslate(left, top)
    }

    override fun draw(canvas: Canvas, width: Int, height: Int, p: Float, timeSeconds: Float) {
        if (width <= 0 || height <= 0) return
        val pp = if (p.isNaN()) 0f else p.coerceIn(0f, 1f)
        val tt = if (timeSeconds.isNaN()) 0f else timeSeconds
        if (!started.get()) prewarm()

        // --- rain ---
        val atlasShader = atlasShader ?: BitmapShader(buildAtlas(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            .also { it.filterMode = BitmapShader.FILTER_MODE_LINEAR; this.atlasShader = it }
        shader.setInputShader("atlas", atlasShader)
        shader.setFloatUniform("iResolution", width.toFloat(), height.toFloat())
        shader.setFloatUniform("p", pp)
        shader.setFloatUniform("t", tt)
        shader.setFloatUniform("cs", CELL)
        bgPaint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // --- figure ---
        val fr = frames ?: return
        if (!canvas.isHardwareAccelerated) return // hardware bitmaps cannot be drawn on a software canvas
        val inner = height > width * 1.6f
        val w = width.toFloat()
        val h = height.toFloat()
        val k = (if (inner) 0.93f else 0.82f) * w / REF_W
        val cx = w * 0.5f
        val cy = h * (if (inner) 0.50f else 0.46f)

        if (pp > 0.25f) {
            for (g in 0..1) {
                val dq = if (g == 0) 0.10f else 0.05f
                val gi = (((pp - dq).coerceAtLeast(0f)) * (FRAMES - 1)).roundToInt().coerceIn(0, FRAMES - 1)
                val f = fr[gi]
                placeFrame(cx - f.cx * k - dq * GHOST_SHIFT * REF_W * k, cy - f.cy * k, k)
                canvas.drawBitmap(f.bmp, m, ghostPaints[g])
            }
        }

        val fi = pp * (FRAMES - 1)
        val lo = floor(fi).toInt().coerceIn(0, FRAMES - 1)
        val hi = (lo + 1).coerceAtMost(FRAMES - 1)
        val frac = (fi - lo).coerceIn(0f, 1f)
        val a = fr[lo]
        val left = cx - a.cx * k
        val top = cy - a.cy * k
        // glow behind the (lower) pose
        m.setScale(a.glowScaleX * k, a.glowScaleY * k)
        m.postTranslate(left + a.glowDx * a.glowScaleX * k, top + a.glowDy * a.glowScaleY * k)
        canvas.drawBitmap(a.glow, m, glowPaint)
        placeFrame(left, top, k)
        canvas.drawBitmap(a.bmp, m, figPaint)
        if (frac > 0.003f && hi != lo) {
            val b = fr[hi]
            placeFrame(cx - b.cx * k, cy - b.cy * k, k)
            upperPaint.alpha = (frac * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawBitmap(b.bmp, m, upperPaint)
        }
    }

    companion object {
        const val FRAMES = 37
        private const val REF_W = 660f
        private const val CELL = 11f
        private const val GLOW_DOWN = 4
        private const val GLOW_BLUR = 10f
        private const val GHOST_SHIFT = 0.5f // fraction of figure width per unit p: 0.05 per 0.10
        private const val ACELL = 22
        private const val ATLAS_COLS = 12
        private const val NG = 96
        private const val ATLAS_W = ATLAS_COLS * ACELL // 264
        private const val ATLAS_H = (NG / ATLAS_COLS) * ACELL // 176

        const val AGSL = """
uniform shader atlas;
uniform float2 iResolution;
uniform float p;
uniform float t;
uniform float cs;

float h11(float x) { return fract(sin(x * 127.1 + 311.7) * 43758.5453); }
float h21(float2 v) { return fract(sin(dot(v, float2(127.1, 311.7))) * 43758.5453); }

half4 main(float2 fc) {
    float w = max(iResolution.x, 1.0);
    float h = max(iResolution.y, 1.0);
    float xn = clamp(fc.x / w, 0.0, 1.0);
    float yn = clamp(fc.y / h, 0.0, 1.0);

    // deep violet gradient, brighter near the crease
    float gy = (yn - 0.52) / 0.30;
    float glow = exp(-gy * gy);
    float3 col = float3(6.0, 0.0, 14.0) + glow * float3(22.0, 4.0, 44.0);

    // glyph cell of this pixel and its column's rain state (all hashed, no loops)
    float csz = max(cs, 1.0);
    float2 cell = floor(fc / csz);
    float2 loc = fc / csz - cell;
    float rows = floor(h / csz) + 1.0;
    float seed = floor(w / 8.0) * 0.37;
    float cidx = cell.x + seed;
    float spd = 0.7 + 1.0 * h11(cidx + 1.0);
    float ph = h11(cidx * 3.1 + 7.0) * (rows + 30.0);
    float len = 10.0 + floor(h11(cidx * 5.7 + 13.0) * 20.0);
    float cyc = rows + len + 8.0;
    float travel = p * (rows + 40.0) * 1.25 + t * 2.0;
    float head = mod(ph + travel * spd, cyc);
    float dist = head - cell.y;

    if (dist >= 0.0 && dist < len) {
        float a = pow(max(1.0 - dist / len, 0.0), 1.6);
        float isHead = dist < 1.0 ? 1.0 : 0.0;
        float stp = dist < 2.0 ? floor(p * 26.0 + cidx + t * 6.0) : 0.0;
        float gi = floor(h21(float2(cidx * 7.13 + stp * 3.7, cell.y * 1.31 + stp)) * 96.0);
        gi = clamp(gi, 0.0, 95.0);
        float2 ap = float2(mod(gi, 12.0) * 22.0 + loc.x * 22.0, floor(gi / 12.0) * 22.0 + loc.y * 22.0);
        float sharp = atlas.eval(ap).a;
        float bloom = atlas.eval(ap + float2(264.0, 0.0)).a;
        float3 tc = mix(float3(178.0, 74.0, 255.0) * (0.35 + 0.65 * a), float3(235.0, 205.0, 255.0), isHead);
        float fade = mix(0.30 + 0.70 * a, 1.0, isHead);
        float3 lay = tc * (sharp * fade) * 0.85 + tc * (bloom * fade) * 0.9 * 0.55;
        // keep the clock zone (top-left) and the icon rows (bottom) legible
        float clockZone = (1.0 - smoothstep(0.30, 0.40, xn)) * (1.0 - smoothstep(0.22, 0.30, yn));
        float iconZone = smoothstep(0.72, 0.78, yn);
        float dim = 1.0 - 0.45 * max(clockZone, iconZone);
        col += lay * dim;
    }

    float vx = (xn - 0.5) * (xn - 0.5) * 1.2 + (yn - 0.5) * (yn - 0.5) * 0.8;
    col *= 1.0 - clamp(vx, 0.0, 1.0) * 0.55;
    col += (h21(fc) - 0.5);   // 1/255 dither against banding in the dark gradient
    return half4(half3(clamp(col / 255.0, 0.0, 1.0)), 1.0);
}
"""
    }
}
