package dev.franklyn.foldscenes.scene

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader

/**
 * Sunrise over a sea, horizon on the crease (0.5 of height). AGSL port of `sunrise()` from the
 * concept prototype. RuntimeShader only renders on hardware-accelerated canvases.
 */
class SceneA : Scene {
    override val id: String = "sunrise"

    // Created once, on first draw (keeps JVM unit tests that touch Scenes.all off the GPU classes).
    private val shader: RuntimeShader by lazy { RuntimeShader(AGSL) }
    private val paint: Paint by lazy { Paint() }

    override fun draw(canvas: Canvas, width: Int, height: Int, p: Float, timeSeconds: Float) {
        if (width <= 0 || height <= 0) return
        val pp = if (p.isNaN()) 0f else p.coerceIn(0f, 1f)
        shader.setFloatUniform("iResolution", width.toFloat(), height.toFloat())
        shader.setFloatUniform("p", pp)
        shader.setFloatUniform("t", timeSeconds)
        shader.setFloatUniform("horizon", HORIZON)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    companion object {
        const val HORIZON = 0.5f

        const val AGSL = """
uniform float2 iResolution;
uniform float p;
uniform float t;
uniform float horizon;

float sm(float a, float b, float x) { return smoothstep(a, b, x); }

float hash21(float2 q) {
    q = fract(q * float2(123.34, 456.21));
    q += dot(q, q + 45.32);
    return fract(q.x * q.y);
}

// Four-key gradient at 0, 0.45, 0.7, 1.0 (SKY keys from the prototype).
float3 keyed(float s, float3 c0, float3 c1, float3 c2, float3 c3) {
    if (s <= 0.45) return mix(c0, c1, s / 0.45);
    if (s <= 0.7) return mix(c1, c2, (s - 0.45) / 0.25);
    return mix(c2, c3, clamp((s - 0.7) / 0.3, 0.0, 1.0));
}

half4 main(float2 fc) {
    float w = iResolution.x;
    float h = iResolution.y;
    float hy = h * horizon;
    float cx = w * 0.5;

    float3 top = keyed(p,
        float3(6.0, 8.0, 26.0) / 255.0, float3(18.0, 16.0, 58.0) / 255.0,
        float3(70.0, 66.0, 150.0) / 255.0, float3(60.0, 140.0, 225.0) / 255.0);
    float3 hor = keyed(p,
        float3(18.0, 22.0, 58.0) / 255.0, float3(130.0, 62.0, 110.0) / 255.0,
        float3(255.0, 150.0, 90.0) / 255.0, float3(255.0, 220.0, 160.0) / 255.0);

    float3 col;
    if (fc.y < hy) {
        float ty = clamp(fc.y / hy, 0.0, 1.0);
        col = top + (hor - top) * pow(ty, 1.4);

        // horizon glow
        float g = sm(0.1, 0.6, p) * 0.8;
        float2 gd = float2((fc.x - cx) / (w * 0.9), (fc.y - hy) / (h * 0.35));
        col += exp(-dot(gd, gd) * 3.0) * g * (150.0 / 255.0) * float3(1.0, 0.55, 0.3);

        // stars: one candidate per cell, fade out by p ~ 0.55, twinkle with t
        float fade = 1.0 - sm(0.0, 0.55, p);
        if (fade > 0.0) {
            float cell = w / 14.0;
            float2 id = floor(fc / cell);
            float2 f = fc / cell - id;
            float exists = hash21(id + 11.0);
            if (exists < 0.4) {
                float2 pos = 0.15 + 0.7 * float2(hash21(id + 3.7), hash21(id + 8.1));
                float sp = hash21(id + 5.3);
                float rad = max(1.3, w / 500.0) / cell;
                float d = length(f - pos);
                float a = fade * (0.55 + 0.45 * sin(p * 12.0 + t * 2.0 + sp * 6.0));
                a *= clamp((rad - d) / (rad * 0.5), 0.0, 1.0);
                col = mix(col, float3(1.0), a);
            }
        }

        // sun and halo
        float r = w * 0.17;
        float u = sm(0.45, 1.0, p);
        float sy = hy + r * 1.3 - u * hy * 0.78;
        float ds = length(fc - float2(cx, sy));
        float sun = clamp((r - ds) / 1.5, 0.0, 1.0);
        float halo = exp(-pow(ds / (r * 2.2), 2.0)) * 0.55;
        col = mix(col, float3(255.0, 238.0, 190.0) / 255.0, sun);
        col += halo * float3(255.0, 170.0, 90.0) / 255.0 * 0.5;
    } else {
        float3 sc = hor * 0.35;
        float depth = clamp((fc.y - hy) / max(h - hy, 1.0), 0.0, 1.0);
        col = sc + (top * 0.25 - sc) * depth;
        float sx = (fc.x - cx) / (w * 0.07 * (0.5 + depth));
        float streak = exp(-sx * sx) * (1.0 - depth) * sm(0.45, 0.9, p);
        col += streak * float3(255.0, 200.0, 140.0) / 255.0 * 0.7;
        float k = 400.0 / w;
        float ripple = 0.5 + 0.5 * sin(fc.y * 0.9 * k + p * 25.0 + t * 1.5 + fc.x * 0.05 * k);
        col *= 0.9 + 0.1 * ripple;
    }
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
    }
}
