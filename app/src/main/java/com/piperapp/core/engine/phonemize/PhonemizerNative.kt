package com.piperapp.core.engine.phonemize

internal object PhonemizerNative {
    init { System.loadLibrary("piper_phonemizer") }
    external fun init(dataPath: ByteArray, voice: ByteArray, idMapJson: ByteArray): Boolean
    external fun phonemizeToIds(textUtf8: ByteArray): LongArray?
}
