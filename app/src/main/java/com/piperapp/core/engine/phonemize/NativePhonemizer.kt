package com.piperapp.core.engine.phonemize

import java.nio.charset.Charset

class PhonemizerException : Exception("Phonemization failed")

interface Phonemizer : AutoCloseable {
    suspend fun phonemize(text: String): List<LongArray>
}

class NativePhonemizer(private val voice: String) : Phonemizer {
    override suspend fun phonemize(text: String): List<LongArray> {
        val bytes = text.toByteArray(Charset.forName("UTF-8")) // §6 #1: true UTF-8
        val arr = PhonemizerNative.phonemizeToIds(bytes)
            ?: throw PhonemizerException()
        return arr.split(-1L).filter { it.isNotEmpty() }
    }
    override fun close() {}
}
