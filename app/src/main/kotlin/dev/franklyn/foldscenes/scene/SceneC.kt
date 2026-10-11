package dev.franklyn.foldscenes.scene

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader

/**
 * Five layered mountain ridges that sit low at p=0 and rise into a full landscape at p=1, under a
 * night-violet to warm-pink sky with a sun that lifts. AGSL port of `mountains()` from the concept
 * prototype; ridges are evaluated per pixel (no paths). Main-thread draw only (one shared shader).
 */
class SceneC : Scene {
    override val id: String = "mountains"

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

// One ridge layer. low = ridge height (fraction of h) at p=0, base = at p=1.
float3 layer(float3 col, float2 fc, float3 lc, float low, float base, float amp, float freq,
             float ph, float e, float haze, float3 hazeCol) {
    float w = iResolution.x;
    float h = iResolution.y;
    float xn = fc.x / w;
    float a = amp * (0.7 + 0.3 * e);
    float shape = sin(xn * freq * 6.0 + ph) * 0.6 + sin(xn * freq * 13.0 + ph * 2.0) * 0.25
                + sin(xn * freq * 31.0 + ph * 3.0) * 0.06;
    float ridgeY = (mix(low, base, e) + a * shape) * h;
    float cover = sm(-1.2, 1.2, fc.y - ridgeY);
    float below = max(fc.y - ridgeY, 0.0) / h;
    float3 c = mix(lc, hazeCol, haze * exp(-below * 12.0));
    // slightly darker toward the bottom of each layer, so overlapping layers separate
    c *= 1.0 - 0.18 * clamp(below * 3.0, 0.0, 1.0);
    return mix(col, c, cover);
}

half4 main(float2 fc) {
    float w = iResolution.x;
    float h = iResolution.y;
    float y = clamp(fc.y / h, 0.0, 1.0);
    float e = sm(0.0, 1.0, p);

    float3 top = float3(30.0, 20.0, 70.0) / 255.0;
    float3 bot = float3(255.0, 170.0, 140.0) / 255.0;
    float3 col = mix(top, bot, pow(y, 1.6));

    // sun: right of centre so it stays off the top-left clock; sinks behind the far ridge at p=0
    float breathe = 1.0 + 0.015 * sin(t * 0.8);
    float r = w * 0.10 * breathe;
    float2 sp = float2(w * 0.76, h * (0.60 - 0.34 * e));
    float ds = length(fc - sp);
    float halo = exp(-pow(ds / (r * 2.3), 2.0));
    col += halo * float3(120.0, 70.0, 60.0) / 255.0 * (0.55 + 0.25 * e);
    float sun = clamp((r - ds) / 1.5, 0.0, 1.0);
    col = mix(col, float3(255.0, 236.0, 190.0) / 255.0, sun);

    float3 hz = float3(255.0, 170.0, 140.0) / 255.0;
    col = layer(col, fc, float3(200.0, 190.0, 235.0) / 255.0, 0.62, 0.40, 0.05, 1.3, 0.2, e, 0.30, hz);
    col = layer(col, fc, float3(150.0, 140.0, 210.0) / 255.0, 0.70, 0.50, 0.06, 1.7, 1.1, e, 0.25, hz);
    col = layer(col, fc, float3(105.0, 95.0, 180.0) / 255.0, 0.78, 0.60, 0.07, 2.1, 2.3, e, 0.18, hz);
    col = layer(col, fc, float3(68.0, 60.0, 140.0) / 255.0, 0.86, 0.71, 0.08, 2.6, 3.4, e, 0.10, hz);
    col = layer(col, fc, float3(34.0, 30.0, 90.0) / 255.0, 0.94, 0.83, 0.09, 3.1, 4.4, e, 0.0, hz);

    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
    }
}
