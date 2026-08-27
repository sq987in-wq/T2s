package com.pipertts.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.pipertts.app.core.app.PiperTTSApp
import com.pipertts.app.ui.theme.PiperTTSTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiperTTSApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf("नमस्ते — Hello from offline Piper TTS (Option C Hybrid)") }
    var selectedVoice by remember { mutableStateOf("hi_IN-priyamvada-medium") }
    var speed by remember { mutableFloatStateOf(1.02f) }
    var expressiveness by remember { mutableFloatStateOf(0.45f) }
    var stability by remember { mutableFloatStateOf(0.8f) }
    var isProcessing by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Piper TTS — Option C") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            VoiceSelector(voices = listOf("hi_IN-priyamvada-medium"), selected = selectedVoice, onSelect = { selectedVoice = it })
            SynthesizePanel(
                text = text, onTextChange = { text = it },
                speed = speed, onSpeedChange = { speed = it },
                expressiveness = expressiveness, onExpressivenessChange = { expressiveness = it },
                stability = stability, onStabilityChange = { stability = it },
                onSynthesize = { isProcessing = true; isProcessing = false }, // §3.2 service call placeholder
                isProcessing = isProcessing
            )
            WaveformView(envelopes = listOf(0.3f, 0.8f, 0.4f, 0.9f, 0.2f))
            Text("First-run: ModelDownloader checks assets/models.json → filesDir/models/ (§2.2)", style = MaterialTheme.typography.labelSmall)
        }
    }
}
