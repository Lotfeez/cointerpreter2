package com.cointerpreter.app.ui.glossary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cointerpreter.app.R
import com.cointerpreter.app.ui.session.SessionViewModel
import kotlinx.coroutines.launch

@Composable
fun GlossaryScreen(viewModel: SessionViewModel, onBack: () -> Unit) {
    val entries by viewModel.glossaryRepository.entries.collectAsState()
    val scope = rememberCoroutineScope()
    var sourceTerm by remember { mutableStateOf("") }
    var preferredTranslation by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.glossary_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    TextButton(onClick = { scope.launch { viewModel.glossaryRepository.clear() } }) {
                        Text("Clear all")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sourceTerm,
                    onValueChange = { sourceTerm = it },
                    label = { Text("Term") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = preferredTranslation,
                    onValueChange = { preferredTranslation = it },
                    label = { Text("Preferred rendering") },
                    modifier = Modifier.weight(1f),
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            ExtendedFloatingActionButton(
                onClick = {
                    if (sourceTerm.isNotBlank() && preferredTranslation.isNotBlank()) {
                        scope.launch {
                            viewModel.glossaryRepository.add(sourceTerm, preferredTranslation)
                            sourceTerm = ""
                            preferredTranslation = ""
                        }
                    }
                },
                text = { Text("Add entry") },
                icon = {},
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(entry.sourceTerm, style = MaterialTheme.typography.titleMedium)
                                Text(entry.preferredTranslation, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { scope.launch { viewModel.glossaryRepository.delete(entry.id) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.clear_transcript))
                            }
                        }
                    }
                }
            }
        }
    }
}
