package com.pipertts.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.piperapp.core.engine.pipeline.SynthesisPipeline
import com.piperapp.core.engine.audio.AudioTrackSink
import com.piperapp.core.engine.audio.AacExporter
import com.piperapp.core.engine.ort.OnnxTtsEngine
import com.piperapp.core.data.db.PiperTTSDatabase
import com.pipertts.app.domain.GenerateSpeechUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * §4.3 / §3.2 — Foreground service (type mediaPlayback) for clause-chunked synthesis.
 * Pipeline: phonemize (JNI) → ONNX session (OrtSession) → SynthesisPipeline (crossfade/pause)
 * → AudioTrackSink (live) / AacExporter (export). Cancellation between clauses only.
 */
class OfflineTTSService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var database: PiperTTSDatabase
    private lateinit var useCase: GenerateSpeechUseCase
    private val pipeline = SynthesisPipeline(sampleRate = 22050)
    private val sink = AudioTrackSink(sampleRate = 22050)

    override fun onCreate() {
        super.onCreate()
        Log.i("OfflineTTSService", "Service started — Option C Hybrid (§3.2 / §4.3)")
        database = PiperTTSDatabase.getDatabase(applicationContext)
        useCase = GenerateSpeechUseCase(database)
        sink.open()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            // §2.3 — close session + drop waveform buffers on low memory
            try { sink.close() } catch (_: Exception) { }
            scope.cancel()
        }
    }

    override fun onDestroy() { scope.cancel(); sink.close(); super.onDestroy() }

    companion object { private const val TAG = "OfflineTTSService" }
}
