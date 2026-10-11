package dev.franklyn.foldscenes.scene

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader

/**
 * Eclipse ring centred on the crease (0.5 of height). AGSL port of `eclipse()` from the concept
 * prototype: the ring widens and shifts hue as p grows. RuntimeShader only renders on
 * hardware-accelerated canvases; draw on the main thread only (one shared shader).
 */
class SceneB : Scene {
    override val id: String = "eclipse"

    // Created once, on first draw (keeps JVM unit tests that touch Scenes.all off the GPU classes).
    private val shader: RuntimeShader by lazy { RuntimeShader(AGSL) }
    private val paint: Paint by lazy { Paint() }

    override fun draw(canvas: Canvas, width: Int, height: Int, p: Float, timeSeconds: Float) {
        if (width <= 0 || height <= 0) return
        val pp = if (p.isNaN()) 0f else p.coerceIn(0f, 1f)
        shader.setFloatUniform("iResolution", width.toFloat(), height.toFloat())
        shader.setFloatUniform("p", pp)
        shader.setFloatUniform("t", timeSeconds)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    companion object {
        const val AGSL = """
uniform float2 iResolution;
uniform float p;
uniform float t;

float sm(float a, float b, float x) { return smoothstep(a, b, x); }

float3 hsv(float h, float s, float v) {
    float3 k = clamp(abs(fract(h + float3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    return v * mix(float3(1.0), k, s);
}

float hash21(float2 q) {
    q = fract(q * float2(123.34, 456.21));
    q += dot(q, q + 45.32);
    return fract(q.x * q.y);
}

half4 main(float2 fc) {
    float w = iResolution.x;
    float h = iResolution.y;
    float2 c = float2(w * 0.5, h * 0.5);
    float2 dv = fc - c;
    float d = length(dv);
    float ang = 0.0;
    if (d > 0.001) ang = atan(dv.y, dv.x);

    float R = (0.07 + 0.88 * p) * w * 0.5;
    float thick = max(2.0, w * 0.02) * (1.0 + p);
    // hue only uses sin(ang) and cos(2 ang): periodic, so no seam at +-pi
    float hue = fract(0.58 + 0.30 * p + 0.12 * sin(ang) + 0.06 * cos(2.0 * ang));
    // integer ray count keeps the modulation seamless; slow idle drift with t
    float rays = 0.65 + 0.35 * cos(10.0 * ang + p * 18.0 + t * 0.25);

    float dr = (d - R) / thick;
    float ringI = exp(-dr * dr) * rays;
    float gr = (d - R) / (w * mix(0.14, 0.11, p));
    float inner = mix(0.45, 1.0, sm(R - thick * 2.0, R + thick * 2.0, d));
    float glow = exp(-gr * gr) * 0.5 * inner;
    float3 hc = hsv(hue, 0.65, 1.0);

    // near-black base with a faint violet bloom around the crease, so the panel is never flat
    float2 bd = float2(dv.x / (w * 0.9), dv.y / (h * 0.45));
    float3 base = float3(4.0, 5.0, 12.0) / 255.0 + float3(10.0, 6.0, 26.0) / 255.0 * exp(-dot(bd, bd) * 2.2);
    // ring eases down a little at p=1 so a clock and icons drawn over the rim stay readable
    float ease = 1.0 - 0.22 * sm(0.6, 1.0, p);
    float3 col = base + (glow * 0.55 + ringI) * hc * ease;

    // soft interior wash that fades in toward the ring and never reaches the outer panel
    float inMask = 1.0 - sm(R - thick * 2.0, R, d);
    float3 fillc = hsv(fract(hue + 0.1), 0.7, 1.0);
    float fillAmt = (0.05 + 0.25 * sm(0.5, 1.0, p)) * clamp(1.0 - d / max(R, 1.0), 0.0, 1.0);
    col += fillc * fillAmt * inMask;

    // dark core ("moon")
    float coreR = R * 0.55 * (1.0 - 0.6 * sm(0.5, 1.0, p));
    float core = clamp((coreR - d) / 1.5, 0.0, 1.0);
    col *= 1.0 - core * 0.9;

    // faint dust so the dark outer area keeps some texture (hidden under the ring)
    float cell = max(w / 22.0, 8.0);
    float2 id = floor(fc / cell);
    float2 f = fc / cell - id;
    if (hash21(id + 17.0) < 0.22) {
        float2 pos = 0.2 + 0.6 * float2(hash21(id + 2.3), hash21(id + 9.1));
        float rad = max(1.2, w / 600.0) / cell;
        float sd = length(f - pos);
        float tw = 0.5 + 0.5 * sin(t * 1.5 + hash21(id + 4.4) * 6.28);
        col += float3(0.55, 0.6, 0.85) * clamp((rad - sd) / (rad * 0.5), 0.0, 1.0) * (0.25 + 0.2 * tw) * (1.0 - 0.6 * p);
    }

    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
    }
}
