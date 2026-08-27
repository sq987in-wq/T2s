package com.pipertts.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pipertts.app.data.room.PiperTTSDatabase
import com.pipertts.app.domain.GenerateSpeechUseCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiperTTSApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { PiperTTSDatabase.getDatabase(context) }
    val useCase = remember { GenerateSpeechUseCase(database) }

    var text by remember { mutableStateOf("Hello from offline Piper TTS (Option C)") }
    var result by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Piper TTS — Option C Hybrid") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Offline Hybrid Pipeline (Kotlin + ONNX + JNI)",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Utterance text") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Button(
                onClick = {
                    isProcessing = true
                    // Note: real implementation would use LaunchedEffect / ViewModel
                    // For skeleton, simulate result
                    result = "Phonemized via JNI bridge → ONNX inference ready"
                    isProcessing = false
                },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Synthesize (Offline)")
            }

            if (result.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Result", style = MaterialTheme.typography.labelLarge)
                        Text(result, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Model: piper-phonemize → onnxruntime-android",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
