package com.cointerpreter.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cointerpreter.app.data.settings.AppTheme
import com.cointerpreter.app.ui.about.AboutScreen
import com.cointerpreter.app.ui.glossary.GlossaryScreen
import com.cointerpreter.app.ui.main.MainScreen
import com.cointerpreter.app.ui.privacy.PrivacyScreen
import com.cointerpreter.app.ui.session.SessionScreen
import com.cointerpreter.app.ui.session.SessionViewModel
import com.cointerpreter.app.ui.settings.SettingsScreen
import com.cointerpreter.app.ui.theme.CoInterpreterTheme
import com.cointerpreter.app.ui.theme.ThemePreference

object Routes {
    const val MAIN = "main"
    const val SESSION = "session"
    const val GLOSSARY = "glossary"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
}

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: SessionViewModel

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm: SessionViewModel = viewModel(factory = SessionViewModelFactory(applicationContext))
            viewModel = vm
            val settings by vm.settingsRepository.settings.collectAsStateSafely()

            val themePref = when (settings?.theme) {
                AppTheme.LIGHT -> ThemePreference.LIGHT
                AppTheme.DARK -> ThemePreference.DARK
                else -> ThemePreference.SYSTEM
            }

            CoInterpreterTheme(themePreference = themePref) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = Routes.MAIN) {
                    composable(Routes.MAIN) {
                        MainScreen(
                            viewModel = vm,
                            onStartRequested = { ensureMicPermissionThenStart() },
                            onNavigateSession = { navController.navigate(Routes.SESSION) },
                            onNavigateGlossary = { navController.navigate(Routes.GLOSSARY) },
                            onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                            onNavigateAbout = { navController.navigate(Routes.ABOUT) },
                        )
                    }
                    composable(Routes.SESSION) {
                        SessionScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onNavigateGlossary = { navController.navigate(Routes.GLOSSARY) },
                        )
                    }
                    composable(Routes.GLOSSARY) {
                        GlossaryScreen(viewModel = vm, onBack = { navController.popBackStack() })
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onNavigatePrivacy = { navController.navigate(Routes.PRIVACY) },
                        )
                    }
                    composable(Routes.ABOUT) {
                        AboutScreen(onBack = { navController.popBackStack() }, onNavigatePrivacy = { navController.navigate(Routes.PRIVACY) })
                    }
                    composable(Routes.PRIVACY) {
                        PrivacyScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    private fun ensureMicPermissionThenStart() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.start()
        if (granted) {
            viewModel.onPermissionResult(true)
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/** Null-safe first-state collection helper so the theme doesn't flash before settings load. */
@androidx.compose.runtime.Composable
private fun kotlinx.coroutines.flow.Flow<com.cointerpreter.app.data.settings.AppSettings>.collectAsStateSafely() =
    this.collectAsStateWithInitial(null)

@androidx.compose.runtime.Composable
private fun <T> kotlinx.coroutines.flow.Flow<T>.collectAsStateWithInitial(initial: T?) =
    androidx.compose.runtime.produceState(initialValue = initial) {
        collect { value = it }
    }

class SessionViewModelFactory(private val context: android.content.Context) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return SessionViewModel(context) as T
    }
}
