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
import androidx.compose.material3.Switch
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
import dev.franklyn.foldscenes.data.LOCATION_INTERVAL_PRESETS_MINUTES
import dev.franklyn.foldscenes.data.LocationAutoOffSettings
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
    val locationAutoOff by viewModel.locationAutoOff.collectAsStateWithLifecycle()
    val hasLocationPermission by viewModel.hasLocationWritePermission.collectAsStateWithLifecycle()

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
            LocationAutoOffCard(
                settings = locationAutoOff,
                hasPermission = hasLocationPermission,
                onEnabledChange = viewModel::setLocationAutoOffEnabled,
                onIntervalChange = viewModel::setLocationAutoOffIntervalMinutes,
            )
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

/**
 * Point 4: turns location off automatically once it has been on continuously for [settings.intervalMinutes].
 * The permission row tells the truth about what will actually happen — a direct OS write when
 * WRITE_SECURE_SETTINGS is granted (adb only), a reminder notification otherwise.
 */
@Composable
private fun LocationAutoOffCard(
    settings: LocationAutoOffSettings,
    hasPermission: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
) {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-off location", style = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 0.sp))
                    if (settings.enabled) {
                        Text(
                            if (hasPermission) {
                                "Turns location off directly if it stays on too long."
                            } else {
                                "No permission to turn location off — sends a reminder instead. " +
                                    "Grant it: adb shell pm grant dev.franklyn.foldscenes android.permission.WRITE_SECURE_SETTINGS"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = settings.enabled, onCheckedChange = onEnabledChange)
            }
            if (settings.enabled) {
                Text("Check after", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    LOCATION_INTERVAL_PRESETS_MINUTES.forEachIndexed { i, minutes ->
                        SegmentedButton(
                            selected = settings.intervalMinutes == minutes,
                            onClick = { onIntervalChange(minutes) },
                            shape = SegmentedButtonDefaults.itemShape(i, LOCATION_INTERVAL_PRESETS_MINUTES.size),
                            icon = {},
                        ) { Text(formatInterval(minutes), maxLines = 1) }
                    }
                }
            }
        }
    }
}

private fun formatInterval(minutes: Int): String = if (minutes < 60) "${minutes}m" else "${minutes / 60}h"
