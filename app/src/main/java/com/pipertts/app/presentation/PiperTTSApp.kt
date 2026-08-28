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
import kotlinx.coroutines.Job
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

    var text by remember { mutableStateOf("नमस्ते, यह ऑफ़लाइन टीटीएस अब पूरी तरह काम कर रहा है।") }
    var selectedVoice by remember { mutableStateOf("hi_IN-priyamvada-medium") }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var expressiveness by remember { mutableFloatStateOf(0.667f) }
    var stability by remember { mutableFloatStateOf(0.8f) }
    
    var isProcessing by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Ready") }

    var currentTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var synthJob by remember { mutableStateOf<Job?>(null) }

    fun stopPlayback() {
        try {
            currentTrack?.stop()
            currentTrack?.release()
            currentTrack = null
        } catch (_: Exception) {}
        isPlaying = false
        statusText = "Stopped"
    }

    /**
     * Streams the full PCM via a MODE_STREAM AudioTrack (no MODE_STATIC size
     * limit, so long scripts play without buffer errors). Writes are blocking
     * and backpressured by the track; playback completes asynchronously.
     */
    fun streamPlayback(pcmData: ShortArray, sampleRate: Int = 22050) {
        if (pcmData.isEmpty()) return
        stopPlayback()

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
            .setBufferSizeInBytes(maxOf(minBuf, minBuf * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onPeriodicNotification(track: AudioTrack?) {}
            override fun onMarkerReached(track: AudioTrack?) {
                isPlaying = false
                statusText = "Playback finished"
                try { track?.stop(); track?.release() } catch (_: Exception) {}
                if (currentTrack === track) currentTrack = null
            }
        })
        // mono PCM16 => 1 frame == 1 sample
        track.notificationMarkerPosition = pcmData.size

        currentTrack = track
        isPlaying = true
        track.play()

        // Write in chunks (blocking); must be called off the main thread.
        val buf = ShortArray(2048)
        var idx = 0
        while (idx < pcmData.size) {
            val len = minOf(buf.size, pcmData.size - idx)
            System.arraycopy(pcmData, idx, buf, 0, len)
            track.write(buf, 0, len)
            idx += len
        }
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

                    synthJob = scope.launch(Dispatchers.IO) {
                        try {
                            val modelDir = File(context.filesDir, "models/$selectedVoice")
                            modelDir.mkdirs()
                            val modelFile = File(modelDir, "model.onnx")
                            val jsonFile = File(modelDir, "model.onnx.json")

                            val client = OkHttpClient.Builder()
                                .connectTimeout(60, TimeUnit.SECONDS)
                                .readTimeout(120, TimeUnit.SECONDS)
                                .build()

                            // 1. Model Download (.onnx)
                            if (!modelFile.exists() || modelFile.length() < 10_000_000L) {
                                withContext(Dispatchers.Main) {
                                    statusText = "Downloading Voice Model (~60MB)..."
                                }
                                val url = "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/priyamvada/medium/hi_IN-priyamvada-medium.onnx"
                                val request = Request.Builder().url(url).build()
                                val response = client.newCall(request).execute()
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
                                                    statusText = "Downloading: ${mb}MB / ${totalMb}MB (${(prog * 100).toInt()}%)"
                                                }
                                            }
                                        }
                                    }
                                }
                                if (modelFile.exists()) modelFile.delete()
                                tempFile.renameTo(modelFile)
                            }

                            // 2. Config Download (.json)
                            if (!jsonFile.exists() || jsonFile.length() < 100L) {
                                withContext(Dispatchers.Main) {
                                    statusText = "Downloading Voice Config..."
                                }
                                val jsonUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/priyamvada/medium/hi_IN-priyamvada-medium.onnx.json"
                                val jsonReq = Request.Builder().url(jsonUrl).build()
                                val jsonResp = client.newCall(jsonReq).execute()
                                val jsonBody = jsonResp.body ?: throw Exception("Empty json body")
                                jsonFile.writeBytes(jsonBody.bytes())
                            }

                            // 3. Phonemization (clause-segmented)
                            withContext(Dispatchers.Main) {
                                statusText = "Processing phonemes..."
                            }
                            val phonemizer = NativePhonemizer(modelDir)
                            val clauseResults = phonemizer.phonemizeClauses(text)
                            if (clauseResults.isEmpty()) {
                                throw IllegalStateException("No pronounceable text found")
                            }

                            // 4. ONNX Synthesis per clause
                            withContext(Dispatchers.Main) {
                                statusText = "Synthesizing ${clauseResults.size} clause(s)..."
                            }
                            val engine = OnnxTtsEngine(modelFile)
                            val pipeline = SynthesisPipeline(22050)
                            val scales = floatArrayOf(expressiveness, speed, stability)

                            val clausePcm = ArrayList<ShortArray>()
                            val isSentenceEnd = ArrayList<Boolean>()
                            for (cr in clauseResults) {
                                val floatPcm = engine.synthesize(cr.ids, scales)
                                val shortPcm = ShortArray(floatPcm.size) { i ->
                                    (floatPcm[i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                                }
                                clausePcm.add(pipeline.normalizeLoudness(shortPcm))
                                isSentenceEnd.add(cr.isSentenceEnd)
                            }
                            engine.close()

                            // 5. Assemble with crossfade + breath pauses, then stream
                            val finalPcm = pipeline.assemble(clausePcm, isSentenceEnd)
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                statusText = "Speaking (${finalPcm.size} samples)..."
                            }
                            streamPlayback(finalPcm)
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

            // Playback Stop Button
            if (isPlaying) {
                Button(
                    onClick = { stopPlayback() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop Audio")
                }
            }

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
