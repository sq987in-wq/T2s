package com.pipertts.app.core.app

import android.app.Application
import android.util.Log

class PiperTTSApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PiperTTSApp initialized — Option C Hybrid (Kotlin + ONNX + JNI bridge)")
    }

    companion object {
        private const val TAG = "PiperTTSApp"
    }
}
