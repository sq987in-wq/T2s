package com.pipertts.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// §4.1 / §4.4 — Voice catalog from Room (VoiceEntity) / ModelDownloader
@Composable
fun VoiceSelector(
    voices: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text("Voice", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        voices.forEach { v ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                RadioButton(selected = v == selected, onClick = { onSelect(v) })
                Text(v, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
