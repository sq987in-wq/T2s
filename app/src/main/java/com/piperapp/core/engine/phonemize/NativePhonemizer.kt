package com.piperapp.core.engine.phonemize

import org.json.JSONObject
import java.io.File

class NativePhonemizer(private val voiceDir: File) : Phonemizer {
    private val idMap = mutableMapOf<String, Long>()
    private val bosId: Long
    private val eosId: Long
    private val padId: Long

    init {
        val jsonFile = File(voiceDir, "model.onnx.json")
        if (jsonFile.exists()) {
            val json = JSONObject(jsonFile.readText())
            val phonemeIdMap = json.getJSONObject("phoneme_id_map")
            for (key in phonemeIdMap.keys()) {
                val arr = phonemeIdMap.getJSONArray(key)
                if (arr.length() > 0) {
                    idMap[key] = arr.getLong(0)
                }
            }
        }
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
