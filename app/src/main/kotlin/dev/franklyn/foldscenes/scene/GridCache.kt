package dev.franklyn.foldscenes.scene

/**
 * Tiny per-size cache for pixel-art scenes (three slots, round-robin eviction), so the two panels
 * (cover and inner) alternating every frame never reallocate their offscreen buffers.
 * Avoids boxed keys on the draw path. Main-thread use only.
 */
internal class GridCache<T : Any>(
    private val create: (gw: Int, gh: Int) -> T,
    private val release: (T) -> Unit = {},
) {
    private val keyW = IntArray(SLOTS)
    private val keyH = IntArray(SLOTS)
    private val items = arrayOfNulls<Any>(SLOTS)
    private var next = 0

    @Suppress("UNCHECKED_CAST")
    fun get(gw: Int, gh: Int): T {
        for (i in 0 until SLOTS) {
            if (items[i] != null && keyW[i] == gw && keyH[i] == gh) return items[i] as T
        }
        val i = next
        next = (next + 1) % SLOTS
        (items[i] as T?)?.let(release)
        val t = create(gw, gh)
        items[i] = t
        keyW[i] = gw
        keyH[i] = gh
        return t
    }

    private companion object {
        const val SLOTS = 3
    }
}

internal fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
