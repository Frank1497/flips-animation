package dev.franklyn.foldscenes.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val TRACK_W = 52.dp
private val TRACK_H = 32.dp
private val THUMB = 24.dp
private val PAD = 4.dp

/**
 * A switch that answers on touch-down: the thumb starts toward the other side the instant a finger lands,
 * follows a horizontal drag 1:1, and commits on release ([switchCommit]). Motion is one critically damped
 * spring (response [SPRING_RESPONSE_S]) driven from the thumb's live position, so a second tap mid-flight
 * reverses from wherever the thumb is. With reduced motion it cross-fades between the two end states.
 */
@Composable
fun FoldSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    reducedMotion: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val spec = remember { spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = springStiffness(SPRING_RESPONSE_S)) }
    val position = remember { Animatable(if (checked) 1f else 0f) }
    var interacting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestChecked by rememberUpdatedState(checked)
    val latestOnChange by rememberUpdatedState(onCheckedChange)
    val travelPx = with(LocalDensity.current) { (TRACK_W - THUMB - PAD * 2).toPx() }

    LaunchedEffect(checked, reducedMotion) {
        if (interacting) return@LaunchedEffect
        val target = if (checked) 1f else 0f
        if (reducedMotion) position.snapTo(target) else position.animateTo(target, spec)
    }

    val gesture = Modifier.pointerInput(reducedMotion) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            interacting = true
            val start = latestChecked
            if (!reducedMotion) scope.launch { position.animateTo(if (start) 0f else 1f, spec) }
            var dragged = false
            var released = false
            var totalDx = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) break // a parent (the list's scroll) took the gesture: cancel
                if (!change.pressed) {
                    released = true
                    change.consume() // keep the enclosing row's click from toggling a second time
                    break
                }
                val dx = change.positionChange().x
                totalDx += dx
                if (!dragged && !reducedMotion && abs(totalDx) > viewConfiguration.touchSlop) dragged = true
                if (dragged) {
                    change.consume()
                    scope.launch { position.snapTo((position.value + dx / travelPx).coerceIn(0f, 1f)) }
                }
            }
            interacting = false
            val result = if (released) switchCommit(start, dragged, position.value) else start
            val target = if (result) 1f else 0f
            scope.launch { if (reducedMotion) position.snapTo(target) else position.animateTo(target, spec) }
            if (result != start) latestOnChange(result)
        }
    }

    Box(
        modifier = modifier
            .size(TRACK_W, TRACK_H)
            .semantics {
                role = Role.Switch
                this.contentDescription = contentDescription
                stateDescription = if (checked) "On" else "Off"
                onClick { latestOnChange(!latestChecked); true }
            }
            .then(gesture),
    ) {
        if (reducedMotion) {
            Crossfade(targetState = checked, label = "switch") { on -> SwitchFace(if (on) 1f else 0f, travelPx) }
        } else {
            SwitchFace(position.value, travelPx)
        }
    }
}

@Composable
private fun SwitchFace(p: Float, travelPx: Float) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(TRACK_W, TRACK_H)
            .background(lerp(colors.surfaceVariant, colors.primary, p), RoundedCornerShape(50)),
    ) {
        Box(
            Modifier
                .padding(PAD)
                .offset { IntOffset((p * travelPx).roundToInt(), 0) }
                .size(THUMB)
                .shadow(2.dp, CircleShape)
                .background(lerp(colors.outline, colors.onPrimary, p), CircleShape),
        )
    }
}
