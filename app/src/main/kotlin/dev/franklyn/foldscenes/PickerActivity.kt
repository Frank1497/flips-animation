package dev.franklyn.foldscenes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.franklyn.foldscenes.ui.AccessibilityDisclosureScreen
import dev.franklyn.foldscenes.ui.AppsScreen
import dev.franklyn.foldscenes.ui.PickerScreen
import dev.franklyn.foldscenes.ui.PickerViewModel
import dev.franklyn.foldscenes.ui.SPRING_RESPONSE_S
import dev.franklyn.foldscenes.ui.Screen
import dev.franklyn.foldscenes.ui.ServiceStatus
import dev.franklyn.foldscenes.ui.SettingsScreen
import dev.franklyn.foldscenes.ui.SettingsViewModel
import dev.franklyn.foldscenes.ui.isPush
import dev.franklyn.foldscenes.ui.isReducedMotion
import dev.franklyn.foldscenes.ui.shouldShowAccessibilityDisclosure
import dev.franklyn.foldscenes.ui.springStiffness

class PickerActivity : ComponentActivity() {
    private val viewModel: PickerViewModel by viewModels()
    private val settings: SettingsViewModel by viewModels()
    private var serviceEnabled by mutableStateOf(false)
    private var reducedMotion by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            val dark = mode.isDark(isSystemInDarkTheme())
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val choice by viewModel.disclosureChoice.collectAsStateWithLifecycle()
                if (shouldShowAccessibilityDisclosure(choice)) {
                    AccessibilityDisclosureScreen(
                        onAccept = { viewModel.recordDisclosure(true) },
                        onDecline = { viewModel.recordDisclosure(false) },
                    )
                } else {
                    var screen by rememberSaveable { mutableStateOf(Screen.PICKER) }
                    BackHandler(enabled = screen != Screen.PICKER) { screen = screen.parent ?: Screen.PICKER }
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = { navTransition(initialState, targetState, reducedMotion) },
                        label = "nav",
                    ) { s ->
                        when (s) {
                            Screen.PICKER -> PickerScreen(
                                viewModel,
                                serviceEnabled,
                                disclosureAccepted = choice == true,
                                onOpenSettings = { screen = Screen.SETTINGS },
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                settings,
                                onBack = { screen = Screen.PICKER },
                                onOpenApps = { screen = Screen.APPS },
                            )
                            Screen.APPS -> AppsScreen(settings, reducedMotion, onBack = { screen = Screen.SETTINGS })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user toggles the service and animation settings in system settings, then comes back here.
        serviceEnabled = ServiceStatus.isEnabled(this)
        reducedMotion = isReducedMotion(this)
        settings.refreshApps()
    }
}

/** Push from the trailing edge going deeper, reversed going back; a plain cut with reduced motion. */
private fun navTransition(from: Screen, to: Screen, reducedMotion: Boolean): ContentTransform {
    if (reducedMotion) return EnterTransition.None togetherWith ExitTransition.None
    val spec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = springStiffness(SPRING_RESPONSE_S),
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )
    val dir = if (isPush(from, to)) 1 else -1
    return slideInHorizontally(spec) { w -> dir * w } togetherWith slideOutHorizontally(spec) { w -> -dir * w / 3 }
}
