package com.cointerpreter.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cointerpreter.app.R
import com.cointerpreter.app.ui.session.SessionViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: SessionViewModel, onBack: () -> Unit, onNavigatePrivacy: () -> Unit) {
    val settings by viewModel.settingsRepository.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val s = settings
            if (s != null) {
                ListItem(
                    headlineContent = { Text("Professional Mode by default") },
                    trailingContent = {
                        Switch(
                            checked = s.professionalModeDefault,
                            onCheckedChange = { checked ->
                                scope.launch { viewModel.settingsRepository.update { it.copy(professionalModeDefault = checked) } }
                            },
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("Keep screen awake during session") },
                    trailingContent = {
                        Switch(
                            checked = s.keepScreenAwake,
                            onCheckedChange = { checked ->
                                scope.launch { viewModel.settingsRepository.update { it.copy(keepScreenAwake = checked) } }
                            },
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("Auto-save transcripts") },
                    trailingContent = {
                        Switch(
                            checked = s.autoSaveTranscripts,
                            onCheckedChange = { checked ->
                                scope.launch { viewModel.settingsRepository.update { it.copy(autoSaveTranscripts = checked) } }
                            },
                        )
                    },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.privacy_title)) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = { TextButton(onClick = onNavigatePrivacy) { Text("View") } },
            )
        }
    }
}
