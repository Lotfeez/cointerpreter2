package com.cointerpreter.app.ui.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cointerpreter.app.R

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Section("What is sent to OpenAI", "While a session is active, your microphone audio is streamed to OpenAI's Realtime API to produce the interpreted speech and transcript. Audio stops the instant you press Stop or mute the microphone.")
            Section("What is stored locally", "Your settings, session glossary (if you choose to keep it), and any transcript you explicitly save are stored only on this device.")
            Section("What is not stored", "CoInterpreter does not record or retain raw audio after a session ends, does not upload transcripts to any cloud service of ours, and never stores an OpenAI API key on this device.")
            Section("When the microphone is active", "Only after you press Start, and Android's system microphone indicator is shown the entire time it is capturing audio.")
            Section("Deleting your data", "Use \"Clear transcript\" to remove the current session's transcript, or delete individual saved transcripts from Settings. Clearing the glossary removes it immediately.")
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(body, style = MaterialTheme.typography.bodyMedium)
    androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
}
