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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

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
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Ready") }

    fun playPcm(pcmData: ShortArray, sampleRate: Int = 22050) {
        if (pcmData.isEmpty()) return
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
                    downloadProgress = 0f

                    scope.launch(Dispatchers.IO) {
                        try {
                            val modelDir = File(context.filesDir, "models/$selectedVoice")
                            modelDir.mkdirs()
                            val modelFile = File(modelDir, "model.onnx")

                            // 1. Download with explicit progress tracking
                            if (!modelFile.exists() || modelFile.length() < 10_000_000L) {
                                withContext(Dispatchers.Main) {
                                    statusText = "Connecting to download server..."
                                }
                                val client = OkHttpClient.Builder()
                                    .connectTimeout(60, TimeUnit.SECONDS)
                                    .readTimeout(120, TimeUnit.SECONDS)
                                    .build()

                                val url = "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/priyamvada/medium/hi_IN-priyamvada-medium.onnx"
                                val request = Request.Builder().url(url).build()
                                val response = client.newCall(request).execute()

                                if (!response.isSuccessful) throw Exception("HTTP error: ${response.code}")
                                val body = response.body ?: throw Exception("Empty response body")
                                val totalBytes = body.contentLength()
                                val tempFile = File(modelDir, "model.onnx.tmp")

                                var downloaded = 0L
                                body.byteStream().use { input ->
                                    FileOutputStream(tempFile).use { output ->
                                        val buffer = ByteArray(32 * 1024)
                                        var read: Int
                                        while (input.read(buffer).also { read = it } != -1) {
                                            output.write(buffer, 0, read)
                                            downloaded += read
                                            if (totalBytes > 0) {
                                                val prog = downloaded.toFloat() / totalBytes
                                                val mb = downloaded / (1024 * 1024)
                                                val totalMb = totalBytes / (1024 * 1024)
                                                withContext(Dispatchers.Main) {
                                                    downloadProgress = prog
                                                    statusText = "Downloading model: ${mb}MB / ${totalMb}MB (${(prog * 100).toInt()}%)"
                                                }
                                            }
                                        }
                                    }
                                }

                                if (modelFile.exists()) modelFile.delete()
                                tempFile.renameTo(modelFile)
                            }

                            // 2. Phonemization
                            withContext(Dispatchers.Main) {
                                statusText = "Phonemizing text..."
                            }
                            val phonemizer = NativePhonemizer(selectedVoice)
                            val phoneIdsList = phonemizer.phonemize(text)

                            // 3. ONNX Synthesis
                            withContext(Dispatchers.Main) {
                                statusText = "Synthesizing audio on-device..."
                            }
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
                        } catch (e: Throwable) {
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                statusText = "Error: ${e.localizedMessage ?: e.message}"
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )

            if (isProcessing && downloadProgress > 0f) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(statusText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
