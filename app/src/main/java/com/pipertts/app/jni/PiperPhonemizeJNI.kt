package com.pipertts.app.jni

import android.util.Log

/**
 * Option C Hybrid — JNI bridge to piper-phonemize native library.
 * Loads libpiper_phonemize.so (built via CMake / delivered by CI).
 */
class PiperPhonemizeJNI {

    companion object {
        private const val TAG = "PiperPhonemizeJNI"

        init {
            try {
                System.loadLibrary("piper_phonemize")
                Log.i(TAG, "Native library 'piper_phonemize' loaded (Option C)")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load piper_phonemize library", e)
            }
        }
    }

    /**
     * Native phonemization: converts raw text to phoneme sequence.
     * @param text Input utterance
     * @return Phonemized string (ARPABET / piper format)
     */
    external fun phonemize(text: String): String

    external fun loadLibrary(): Boolean
}
