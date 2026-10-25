package dev.franklyn.foldscenes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Show the disclosure only when the user has not yet made an explicit choice. */
fun shouldShowAccessibilityDisclosure(storedChoice: Boolean?): Boolean = storedChoice == null

const val ACCESSIBILITY_DISCLOSURE_TEXT =
    "This app uses Android's Accessibility API to draw its fold animation as a full-screen overlay " +
        "on both the cover and inner displays, including when the phone is locked. It does not read, " +
        "record, or transmit anything you type or view."

/**
 * Google Play's required prominent disclosure for Accessibility API use. Its own screen, never combined
 * with another permission's disclosure, and only an explicit tap records a choice.
 */
@Composable
fun AccessibilityDisclosureScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Accessibility API use", style = MaterialTheme.typography.headlineSmall)
            Text(ACCESSIBILITY_DISCLOSURE_TEXT, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text("Accept and continue") }
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) { Text("Decline") }
        }
    }
}
