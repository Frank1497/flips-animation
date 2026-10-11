package dev.franklyn.foldscenes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.franklyn.foldscenes.scene.Scenes
import dev.franklyn.foldscenes.ui.PreviewScreen
import dev.franklyn.foldscenes.wallpaper.WallpaperApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                var result by remember { mutableStateOf("") }
                val scene = Scenes.all.first()
                Scaffold { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        Text("Razr Fold Scenes", modifier = Modifier.padding(16.dp))
                        // Weighted so the preview shrinks and the buttons stay visible below it.
                        Column(modifier = Modifier.weight(1f)) { PreviewScreen() }
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    result = "Applying inner wallpaper..."
                                    val r = withContext(Dispatchers.Default) { WallpaperApplier.applyInner(applicationContext, scene) }
                                    result = r.fold(
                                        onSuccess = { "Inner wallpaper set." },
                                        onFailure = { "Inner wallpaper failed: ${it.message}" },
                                    )
                                }
                            }) { Text("Set inner wallpaper") }
                            Button(onClick = {
                                scope.launch {
                                    result = "Saving cover frame..."
                                    val r = withContext(Dispatchers.Default) { WallpaperApplier.applyCover(applicationContext, scene) }
                                    result = r.fold(
                                        onSuccess = { "Cover frame saved: $it" },
                                        onFailure = { "Cover wallpaper: ${it.message}" },
                                    )
                                }
                            }) { Text("Set cover wallpaper") }
                            Text("Opens Motorola's editor. Tap Save there.", style = MaterialTheme.typography.bodySmall)
                            if (result.isNotEmpty()) Text(result)
                        }
                    }
                }
            }
        }
    }
}
