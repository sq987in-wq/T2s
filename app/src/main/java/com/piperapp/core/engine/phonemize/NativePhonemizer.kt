package com.piperapp.core.engine.phonemize

import org.json.JSONObject
import java.io.File

class PhonemizerException(msg: String = "Phonemization failed") : Exception(msg)

interface Phonemizer : AutoCloseable {
    suspend fun phonemize(text: String): List<LongArray>
}

class NativePhonemizer(private val voiceDir: File) : Phonemizer {
    private val idMap = mutableMapOf<String, Long>()
    private var bosId: Long = 1L
    private var eosId: Long = 2L
    private var padId: Long = 0L

    init {
        try {
            val jsonFile = File(voiceDir, "model.onnx.json")
            if (jsonFile.exists()) {
                val json = JSONObject(jsonFile.readText())
                if (json.has("phoneme_id_map")) {
                    val phonemeIdMap = json.getJSONObject("phoneme_id_map")
                    for (key in phonemeIdMap.keys()) {
                        val arr = phonemeIdMap.getJSONArray(key)
                        if (arr.length() > 0) {
                            idMap[key] = arr.getLong(0)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        bosId = idMap["^"] ?: 1L
        eosId = idMap["$"] ?: 2L
        padId = idMap["_"] ?: 0L
    }

    override suspend fun phonemize(text: String): List<LongArray> {
        val ids = mutableListOf<Long>()
        ids.add(bosId)
        ids.add(padId)

        for (ch in text) {
            val s = ch.toString()
            val id = idMap[s] ?: idMap[s.lowercase()] ?: idMap[" "] ?: 0L
            ids.add(id)
            ids.add(padId)
        }

        ids.add(eosId)
        return listOf(ids.toLongArray())
    }

    override fun close() {}
}
