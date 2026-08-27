package com.pipertts.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.pipertts.app.data.room.PiperTTSDatabase
import com.pipertts.app.domain.GenerateSpeechUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Option C Hybrid — Offline TTS Service.
 * Manages phonemization (JNI) and ONNX inference in background.
 */
class OfflineTTSService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var database: PiperTTSDatabase
    private lateinit var useCase: GenerateSpeechUseCase

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "OfflineTTSService created (Option C Hybrid)")
        database = PiperTTSDatabase.getDatabase(applicationContext)
        useCase = GenerateSpeechUseCase(database)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OfflineTTSService"
    }
}
