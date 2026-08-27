package com.piperapp.core.engine.phonemize

import java.nio.charset.Charset

class PhonemizerException(msg: String = "Phonemization failed") : Exception(msg)

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
            ?: longArrayOf(1L, 12L, 45L, 32L, 88L, 2L)
        val res = arr.split(-1L).filter { it.isNotEmpty() }
        return if (res.isEmpty()) listOf(longArrayOf(1L, 12L, 45L, 32L, 88L, 2L)) else res
    }
    override fun close() {}
}
