package dev.franklyn.foldscenes.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.franklyn.foldscenes.scene.Scenes

private const val ADB_HELP =
    "adb shell settings put secure enabled_accessibility_services " +
        "dev.franklyn.foldscenes/dev.franklyn.foldscenes.overlay.FoldOverlayService"

/** The one-scene picker: service status, scene list, preview, and the three apply buttons. */
@Composable
fun PickerScreen(viewModel: PickerViewModel, serviceEnabled: Boolean) {
    val context = LocalContext.current
    val chosen by viewModel.chosenId.collectAsStateWithLifecycle()
    val thumbs by viewModel.thumbnails.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Razr Fold Scenes", style = MaterialTheme.typography.titleLarge)

            Text(
                "Fold animation: ${if (serviceEnabled) "on" else "off"}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text("Open accessibility settings") }
            Text(ADB_HELP, style = MaterialTheme.typography.bodySmall)

            Text("Scene", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            for (id in SCENE_NAMES.keys) {
                val thumb = thumbs[id]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = chosen == id, role = Role.RadioButton, onClick = { viewModel.select(id) })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = chosen == id, onClick = null)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .width(72.dp)
                            .aspectRatio(1080f / 1272f),
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        } else {
                            // placeholder until rendered
                            Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                    Text(SCENE_NAMES.getValue(id), style = MaterialTheme.typography.bodyLarge)
                }
            }

            Text("Preview", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            PreviewScreen(
                scene = Scenes.byId(chosen),
                modifier = Modifier.fillMaxWidth().height(440.dp),
            )

            Text(
                "Setting the inner wallpaper replaces your current one. It is not backed up.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = viewModel::setInner, modifier = Modifier.fillMaxWidth()) {
                    Text("Set inner wallpaper")
                }
                Button(enabled = !busy, onClick = viewModel::setCover, modifier = Modifier.fillMaxWidth()) {
                    Text("Set cover wallpaper")
                }
                Button(enabled = !busy, onClick = viewModel::setBoth, modifier = Modifier.fillMaxWidth()) {
                    Text("Set both")
                }
            }
            Text(
                "The cover wallpaper opens Motorola's editor. Tap Save there.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (result.isNotEmpty()) Text(result, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
