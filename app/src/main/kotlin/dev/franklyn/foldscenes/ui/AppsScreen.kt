package dev.franklyn.foldscenes.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.franklyn.foldscenes.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PRESSED_SCALE = 0.97f

@Composable
fun AppsScreen(viewModel: SettingsViewModel, reducedMotion: Boolean, onBack: () -> Unit) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val camera by viewModel.camera.collectAsStateWithLifecycle()
    val excluded by viewModel.excluded.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        Column(Modifier.padding(innerPadding).padding(horizontal = 16.dp)) {
            ScreenHeader("Apps", onBack)
            Text(
                "Choose which apps never show the fold animation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                camera?.let { cam ->
                    item(key = "camera:${cam.packageName}") {
                        AppRow(packageName = cam.packageName, label = cam.label, onClick = {}) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("\uD83D\uDD12", modifier = Modifier.semantics { contentDescription = "Locked" })
                                Text(
                                    "Always off (camera)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                items(list, key = AppEntry::packageName) { app ->
                    val allowed = app.packageName !in excluded
                    AppRow(
                        packageName = app.packageName,
                        label = app.label,
                        onClick = { viewModel.setAllowed(app.packageName, !allowed) },
                    ) {
                        FoldSwitch(
                            checked = allowed,
                            onCheckedChange = { viewModel.setAllowed(app.packageName, it) },
                            reducedMotion = reducedMotion,
                            contentDescription = "Fold animation in ${app.label}",
                        )
                    }
                }
            }
        }
    }
}

/** Icon + label + trailing control. Pressing anywhere on the row scales it to 0.97 (also the locked camera row). */
@Composable
private fun AppRow(packageName: String, label: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) PRESSED_SCALE else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = springStiffness(SPRING_RESPONSE_S)),
        label = "rowScale",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        AppIcon(packageName)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        trailing()
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) { loadIcon(context, packageName) }
    }
    Box(Modifier.size(40.dp)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

private fun loadIcon(context: Context, packageName: String): ImageBitmap? = runCatching {
    val d = context.packageManager.getApplicationIcon(packageName)
    val px = (40 * context.resources.displayMetrics.density).toInt()
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    d.setBounds(0, 0, px, px)
    d.draw(Canvas(bmp))
    bmp.asImageBitmap()
}.getOrNull()
