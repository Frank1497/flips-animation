package dev.franklyn.foldscenes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.franklyn.foldscenes.ui.PreviewScreen

class PickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        Text("Razr Fold Scenes", modifier = Modifier.padding(16.dp))
                        PreviewScreen()
                    }
                }
            }
        }
    }
}
