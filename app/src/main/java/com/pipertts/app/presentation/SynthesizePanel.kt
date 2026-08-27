package com.pipertts.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// §5.4 — Controls: speed=length_scale, expressiveness=noise_scale, stability=noise_scale_w
@Composable
fun SynthesizePanel(
    text: String,
    onTextChange: (String) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    expressiveness: Float,
    onExpressivenessChange: (Float) -> Unit,
    stability: Float,
    onStabilityChange: (Float) -> Unit,
    onSynthesize: () -> Unit,
    isProcessing: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = text, onValueChange = onTextChange, label = { Text("Utterance") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Slider(value = speed, onValueChange = onSpeedChange, valueRange = 0.5f..1.5f)
        Text("Speed (length_scale): $speed")
        Slider(value = expressiveness, onValueChange = onExpressivenessChange, valueRange = 0.1f..0.99f)
        Text("Expressiveness (noise_scale): $expressiveness")
        Slider(value = stability, onValueChange = onStabilityChange, valueRange = 0.3f..1.0f)
        Text("Stability (noise_scale_w): $stability")
        Button(onClick = onSynthesize, enabled = !isProcessing, modifier = Modifier.fillMaxWidth()) {
            Text(if (isProcessing) "Synthesizing…" else "Synthesize")
        }
    }
}
