package com.cointerpreter.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cointerpreter.app.R
import com.cointerpreter.app.model.InterpreterMode
import com.cointerpreter.app.model.Language
import com.cointerpreter.app.ui.session.SessionViewModel

@Composable
fun MainScreen(
    viewModel: SessionViewModel,
    onStartRequested: () -> Unit,
    onNavigateSession: () -> Unit,
    onNavigateGlossary: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateAbout: () -> Unit,
) {
    val source by viewModel.sourceLanguage.collectAsState()
    val target by viewModel.targetLanguage.collectAsState()
    val mode by viewModel.mode.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.settings_title))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.glossary_title)) }, onClick = { menuExpanded = false; onNavigateGlossary() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = { menuExpanded = false; onNavigateSettings() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.about_title)) }, onClick = { menuExpanded = false; onNavigateAbout() })
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                LanguagePickerRow(
                    source = source,
                    target = target,
                    onSourceChange = { viewModel.swapLanguages().let { } },
                    onSwap = { viewModel.swapLanguages() },
                )

                Spacer(Modifier.height(28.dp))

                Text(text = "Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(stringResource(R.string.mode_conversation), mode == InterpreterMode.CONVERSATION) { viewModel.setMode(InterpreterMode.CONVERSATION) }
                    ModeChip(stringResource(R.string.mode_one_way), mode == InterpreterMode.ONE_WAY) { viewModel.setMode(InterpreterMode.ONE_WAY) }
                    ModeChip(stringResource(R.string.mode_professional), mode == InterpreterMode.PROFESSIONAL) { viewModel.setMode(InterpreterMode.PROFESSIONAL) }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        onStartRequested()
                        onNavigateSession()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) {
                    Text(stringResource(R.string.start_interpreting), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun LanguagePickerRow(
    source: Language,
    target: Language,
    onSourceChange: (Language) -> Unit,
    onSwap: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LanguageDropdown(selected = source, modifier = androidx.compose.ui.Modifier.weight(1f))
        IconButton(onClick = onSwap) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.swap_languages))
        }
        LanguageDropdown(selected = target, modifier = androidx.compose.ui.Modifier.weight(1f))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(selected: Language, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        TextField(
            value = selected.nativeName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(),
            label = { Text(if (selected.isRtl) "اللغة" else "Language") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Language.FIRST_CLASS.forEach { lang ->
                DropdownMenuItem(text = { Text("${lang.nativeName} (${lang.englishName})") }, onClick = { expanded = false })
            }
        }
    }
}
