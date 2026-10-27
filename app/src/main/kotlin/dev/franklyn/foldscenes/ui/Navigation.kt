package dev.franklyn.foldscenes.ui

import android.content.Context
import android.provider.Settings
import kotlin.math.PI

/** The app's three screens. [parent] is where back goes. */
enum class Screen(val depth: Int) {
    PICKER(0), SETTINGS(1), APPS(2);

    val parent: Screen? get() = entries.firstOrNull { it.depth == depth - 1 }
}

/** Going deeper slides in from the trailing edge; going back reverses it. */
fun isPush(from: Screen, to: Screen): Boolean = to.depth > from.depth

/** Stiffness of a unit-mass spring with the given response (period, seconds): (2π / response)². */
fun springStiffness(responseSeconds: Float): Float = ((2 * PI / responseSeconds) * (2 * PI / responseSeconds)).toFloat()

/** Android's "Remove animations" sets the animator duration scale to 0. */
fun isReducedMotion(animatorScale: Float): Boolean = animatorScale == 0f

fun isReducedMotion(context: Context): Boolean = isReducedMotion(
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
)

/** On release, a drag commits to the side the thumb is nearer; a plain tap flips the switch. */
fun switchCommit(wasChecked: Boolean, dragged: Boolean, position: Float): Boolean =
    if (dragged) position >= 0.5f else !wasChecked

/** Critically damped (no bounce), response 0.3 s: the apple-design "move" spring. */
const val SPRING_RESPONSE_S = 0.3f
