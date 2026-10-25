package dev.franklyn.foldscenes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.franklyn.foldscenes.ui.AccessibilityDisclosureScreen
import dev.franklyn.foldscenes.ui.PickerScreen
import dev.franklyn.foldscenes.ui.PickerViewModel
import dev.franklyn.foldscenes.ui.ServiceStatus
import dev.franklyn.foldscenes.ui.shouldShowAccessibilityDisclosure

class PickerActivity : ComponentActivity() {
    private val viewModel: PickerViewModel by viewModels()
    private var serviceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val choice by viewModel.disclosureChoice.collectAsStateWithLifecycle()
                if (shouldShowAccessibilityDisclosure(choice)) {
                    AccessibilityDisclosureScreen(
                        onAccept = { viewModel.recordDisclosure(true) },
                        onDecline = { viewModel.recordDisclosure(false) },
                    )
                } else {
                    PickerScreen(viewModel, serviceEnabled, disclosureAccepted = choice == true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user toggles the service in system settings, then comes back here.
        serviceEnabled = ServiceStatus.isEnabled(this)
    }
}
