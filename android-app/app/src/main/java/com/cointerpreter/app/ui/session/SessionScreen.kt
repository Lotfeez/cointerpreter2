package com.cointerpreter.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cointerpreter.app.R
import com.cointerpreter.app.model.TranscriptEntry
import com.cointerpreter.app.state.SessionState

@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    onBack: () -> Unit,
    onNavigateGlossary: () -> Unit,
) {
    val state by viewModel.sessionState.collectAsState()
    val duration by viewModel.sessionDurationSeconds.collectAsState()
    val micMuted by viewModel.micMuted.collectAsState()
    val voiceMuted by viewModel.voiceMuted.collectAsState()
    val transcript by viewModel.transcriptRepository.live.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formatDuration(duration)) },
            )
        },
        bottomBar = {
            SessionControls(
                state = state,
                micMuted = micMuted,
                voiceMuted = voiceMuted,
                onToggleMic = { viewModel.setMicMuted(!micMuted) },
                onToggleVoice = { viewModel.setVoiceMuted(!voiceMuted) },
                onStop = { viewModel.stop(); onBack() },
                onRetry = { viewModel.retryAfterError() },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            StateBanner(state)
            Spacer()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(transcript, key = { it.id }) { entry -> TranscriptCard(entry) }
            }
        }
    }
}

@Composable
private fun Spacer() = androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))

@Composable
private fun StateBanner(state: SessionState) {
    val (labelRes, color) = when (state) {
        SessionState.Ready -> R.string.state_ready to MaterialTheme.colorScheme.onSurfaceVariant
        SessionState.Listening -> R.string.state_listening to MaterialTheme.colorScheme.primary
        SessionState.Interpreting -> R.string.state_interpreting to MaterialTheme.colorScheme.primary
        SessionState.Speaking -> R.string.state_speaking to MaterialTheme.colorScheme.secondary
        SessionState.Connecting -> R.string.state_connecting to MaterialTheme.colorScheme.onSurfaceVariant
        SessionState.Reconnecting -> R.string.state_reconnecting to MaterialTheme.colorScheme.error
        SessionState.Authenticating -> R.string.state_connecting to MaterialTheme.colorScheme.onSurfaceVariant
        SessionState.RequestingPermission -> R.string.state_connecting to MaterialTheme.colorScheme.onSurfaceVariant
        is SessionState.Error -> R.string.state_error to MaterialTheme.colorScheme.error
        else -> R.string.state_ready to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TranscriptCard(entry: TranscriptEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = entry.sourceLanguage.englishName.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.originalText,
                style = MaterialTheme.typography.bodyLarge,
                // Real bidi handling, not a manual right-align hack: Compose's
                // Text already runs the ICU bidi algorithm per paragraph based
                // on the string's own directional characters.
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
            Text(
                text = entry.targetLanguage.englishName.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = entry.translatedText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SessionControls(
    state: SessionState,
    micMuted: Boolean,
    voiceMuted: Boolean,
    onToggleMic: () -> Unit,
    onToggleVoice: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onToggleMic) {
            Icon(
                if (micMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = stringResource(R.string.mic_muted),
            )
        }
        if (state is SessionState.Error) {
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        } else {
            Button(onClick = onStop) { Text(stringResource(R.string.stop_interpreting)) }
        }
        IconButton(onClick = onToggleVoice) {
            Icon(
                if (voiceMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = stringResource(R.string.voice_muted),
            )
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
