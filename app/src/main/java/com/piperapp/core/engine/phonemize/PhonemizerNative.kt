package com.piperapp.core.engine.phonemize

/**
 * Optional Android-native espeak-ng surface (byte-parity path).
 *
 * This is a thin, safe wrapper around `libpiper_phonemizer.so`. It is designed
 * to degrade gracefully: if the native library is not present (the default
 * build), [loadIfAvailable] returns false and the app falls back to the
 * pure-Kotlin [DevanagariG2P]. No crash, no hard dependency.
 *
 * The JNI method names match the C++ in app/src/main/cpp/piper_phonemize_bridge.cpp.
 * `phonemizeToIds` returns a flat long array; `-1` separates clause boundaries.
 */
object PhonemizerNative {

    @Volatile private var loaded = false

    init {
        try {
            System.loadLibrary("piper_phonemizer")
            loaded = true
        } catch (t: Throwable) {
            loaded = false
        }
    }

    val isAvailable: Boolean get() = loaded

    /**
     * Initialize espeak-ng with the voice data directory and the model's
     * `phoneme_id_map` JSON. Must be called before [phonemizeToIds].
     * @return true if native espeak-ng is ready.
     */
    fun loadIfAvailable(espeakDataPath: String, voice: String, idMapJson: String): Boolean {
        if (!loaded) return false
        return try {
            nativeInit(
                espeakDataPath.toByteArray(Charsets.UTF_8),
                voice.toByteArray(Charsets.UTF_8),
                idMapJson.toByteArray(Charsets.UTF_8),
            )
        } catch (t: Throwable) {
            loaded = false
            false
        }
    }

    /** @return flat clause ids, `-1` = clause boundary; null if native unavailable/empty. */
    fun phonemizeToIds(text: String): LongArray? {
        if (!loaded) return null
        return try {
            nativePhonemizeToIds(text.toByteArray(Charsets.UTF_8))
        } catch (t: Throwable) {
            null
        }
    }

    private external fun nativeInit(dataPath: ByteArray, voice: ByteArray, idMapJson: ByteArray): Boolean
    private external fun nativePhonemizeToIds(textUtf8: ByteArray): LongArray?
}
