package dev.franklyn.foldscenes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.franklyn.foldscenes.data.ThemeMode
import dev.franklyn.foldscenes.fold.SuppressionDiagnostics
import dev.franklyn.foldscenes.fold.SuppressionSnapshot
import dev.franklyn.foldscenes.fold.diagnosticsLines

private val THEME_LABELS = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")

/** Back button + title, shared by Settings and Apps. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
            Text("\u2039", style = MaterialTheme.typography.headlineMedium)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpenApps: () -> Unit) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val diagnostics by SuppressionDiagnostics.state.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenHeader("Settings", onBack)
            SettingsCard {
                // Label above a full-width control: the label never shares a row, so it cannot wrap mid-word.
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 0.sp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        THEME_LABELS.forEachIndexed { i, (value, label) ->
                            SegmentedButton(
                                selected = mode == value,
                                onClick = { viewModel.setThemeMode(value) },
                                shape = SegmentedButtonDefaults.itemShape(i, THEME_LABELS.size),
                                icon = {},
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                }
            }
            SettingsCard(onClick = onOpenApps) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Apps", style = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 0.sp))
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("\u203A", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DiagnosticsCard(diagnostics)
            Spacer(Modifier.size(8.dp))
        }
    }
}

/** What the fold service currently sees, so a failed exclusion can be diagnosed on the phone without adb. */
@Composable
private fun DiagnosticsCard(snapshot: SuppressionSnapshot) {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Diagnostics", style = MaterialTheme.typography.labelLarge)
            diagnosticsLines(snapshot).forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val base = Modifier.fillMaxWidth()
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base,
    ) { content() }
}
