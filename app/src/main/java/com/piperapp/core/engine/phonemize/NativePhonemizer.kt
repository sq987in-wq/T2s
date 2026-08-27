package com.piperapp.core.engine.phonemize

import java.nio.charset.Charset

class PhonemizerException : Exception("Phonemization failed")

interface Phonemizer : AutoCloseable {
    suspend fun phonemize(text: String): List<LongArray>
}

private fun LongArray.split(delimiter: Long): List<LongArray> {
    val result = mutableListOf<LongArray>()
    val current = mutableListOf<Long>()
    for (item in this) {
        if (item == delimiter) {
            if (current.isNotEmpty()) {
                result.add(current.toLongArray())
                current.clear()
            }
        } else {
            current.add(item)
        }
    }
    if (current.isNotEmpty()) {
        result.add(current.toLongArray())
    }
    return result
}

class NativePhonemizer(private val voice: String) : Phonemizer {
    override suspend fun phonemize(text: String): List<LongArray> {
        val bytes = text.toByteArray(Charset.forName("UTF-8"))
        val arr = PhonemizerNative.phonemizeToIds(bytes)
            ?: throw PhonemizerException()
        return arr.split(-1L).filter { it.isNotEmpty() }
    }
    override fun close() {}
}
