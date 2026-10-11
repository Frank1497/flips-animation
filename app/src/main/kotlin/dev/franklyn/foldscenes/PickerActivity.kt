package dev.franklyn.foldscenes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.franklyn.foldscenes.ui.PickerScreen
import dev.franklyn.foldscenes.ui.PickerViewModel
import dev.franklyn.foldscenes.ui.ServiceStatus

class PickerActivity : ComponentActivity() {
    private val viewModel: PickerViewModel by viewModels()
    private var serviceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PickerScreen(viewModel, serviceEnabled)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user toggles the service in system settings, then comes back here.
        serviceEnabled = ServiceStatus.isEnabled(this)
    }
}
