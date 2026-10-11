package dev.franklyn.foldscenes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.franklyn.foldscenes.fold.Panel
import dev.franklyn.foldscenes.fold.overlayAlpha
import dev.franklyn.foldscenes.fold.progress
import dev.franklyn.foldscenes.overlay.SceneView

private fun aspectOf(panel: Panel): Float = when (panel) {
    Panel.COVER -> 1080f / 1272f
    Panel.INNER -> 1224f / 2992f
}

/** Slider-driven preview of the scene on a chosen panel's aspect ratio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(modifier: Modifier = Modifier) {
    var angle by remember { mutableFloatStateOf(90f) }
    var panel by remember { mutableStateOf(Panel.INNER) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = panel == Panel.COVER, onClick = { panel = Panel.COVER }, label = { Text("Cover") })
            FilterChip(selected = panel == Panel.INNER, onClick = { panel = Panel.INNER }, label = { Text("Inner") })
        }
        Slider(value = angle, onValueChange = { angle = it }, valueRange = 0f..180f)
        val p = progress(angle)
        Text("angle=$angle p=$p alpha=${overlayAlpha(panel, angle, 0f)}")
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            // Fits within the available width and height at the panel's aspect ratio, centred.
            AndroidView(
                modifier = Modifier.aspectRatio(aspectOf(panel), matchHeightConstraintsFirst = false),
                factory = { SceneView(it) },
                update = {
                    it.progress = p
                    // The preview always shows the scene at full opacity so it can be seen at any angle;
                    // the real overlay fades with overlayAlpha (shown in the readout above).
                    it.alpha01 = 1f
                },
            )
        }
    }
}
