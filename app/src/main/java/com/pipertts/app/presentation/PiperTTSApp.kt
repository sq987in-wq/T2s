package com.pipertts.app.presentation

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.piperapp.core.engine.ort.OnnxTtsEngine
import com.piperapp.core.engine.phonemize.NativePhonemizer
import com.piperapp.core.engine.pipeline.SynthesisPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiperTTSApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("नमस्ते, यह ऑफ़लाइन टीटीएस की आवाज़ है") }
    var selectedVoice by remember { mutableStateOf("hi_IN-priyamvada-medium") }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var expressiveness by remember { mutableFloatStateOf(0.667f) }
    var stability by remember { mutableFloatStateOf(0.8f) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Ready") }

    fun playPcm(pcmData: ShortArray, sampleRate: Int = 22050) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, pcmData.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcmData, 0, pcmData.size)
        track.play()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Piper TTS — Option C") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VoiceSelector(
                voices = listOf("hi_IN-priyamvada-medium"),
                selected = selectedVoice,
                onSelect = { selectedVoice = it }
            )

            SynthesizePanel(
                text = text,
                onTextChange = { text = it },
                speed = speed,
                onSpeedChange = { speed = it },
                expressiveness = expressiveness,
                onExpressivenessChange = { expressiveness = it },
                stability = stability,
                onStabilityChange = { stability = it },
                isProcessing = isProcessing,
                onSynthesize = {
                    if (isProcessing) return@SynthesizePanel
                    isProcessing = true
                    statusText = "Synthesizing..."

                    scope.launch(Dispatchers.IO) {
                        try {
                            val modelFile = File(context.filesDir, "models/$selectedVoice/model.onnx")
                            if (!modelFile.exists()) {
                                modelFile.parentFile?.mkdirs()
                                context.assets.open("models/$selectedVoice/model.onnx").use { input ->
                                    FileOutputStream(modelFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }

                            val phonemizer = NativePhonemizer(selectedVoice)
                            val phoneIdsList = phonemizer.phonemize(text)

                            val engine = OnnxTtsEngine(modelFile)
                            val pipeline = SynthesisPipeline(22050)

                            val allAudio = mutableListOf<Short>()
                            val scales = floatArrayOf(speed, expressiveness, stability)

                            for (ids in phoneIdsList) {
                                val floatPcm = engine.synthesize(ids, scales)
                                val shortPcm = ShortArray(floatPcm.size) { i ->
                                    (floatPcm[i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                                }
                                val normPcm = pipeline.normalizePeak(shortPcm)
                                for (sample in normPcm) allAudio.add(sample)
                            }

                            engine.close()

                            val finalPcm = allAudio.toShortArray()
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                statusText = "Playing audio (${finalPcm.size} samples)"
                                playPcm(finalPcm)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                statusText = "Error: ${e.message}"
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )

            Text(statusText, style = MaterialTheme.typography.bodyMedium)
            WaveformView(envelopes = listOf(0.3f, 0.8f, 0.4f, 0.9f, 0.2f))
        }
    }
}
