package com.piperapp.core.engine.phonemize

import org.json.JSONObject

/**
 * Parses the model's `phoneme_id_map` (from `.onnx.json`) and reproduces
 * **piper's exact `phonemes_to_ids` semantics**:
 *
 * ```
 * [BOS, PAD, id(p0), PAD, id(p1), PAD, ..., id(pN), PAD, EOS]
 * ```
 * where BOS="^" (id 1), EOS="$" (id 2), PAD="_" (id 0) — all read from the
 * model's own map, never hardcoded.
 *
 * Critical correctness point: **phonemes absent from the model vocabulary are
 * DROPPED (like piper does), not mapped to the space token.** The previous
 * implementation fell back to `idMap[" "]`, silently inserting spurious pauses
 * into the token stream — a direct cause of the "robotic pacing" symptom.
 */
class PhonemeIdMap private constructor(
    private val idMap: Map<String, List<Int>>,
) {
    val bosId: Long
    val eosId: Long
    val padId: Long

    companion object {
        fun parse(json: JSONObject): PhonemeIdMap {
            val raw = json.getJSONObject("phoneme_id_map")
            val map = HashMap<String, List<Int>>()
            for (k in raw.keys()) {
                val arr = raw.getJSONArray(k)
                val ids = ArrayList<Int>(arr.length())
                for (i in 0 until arr.length()) ids.add(arr.getInt(i))
                map[k] = ids
            }
            val result = PhonemeIdMap(map)
            result.require("^")
            result.require("$")
            result.require("_")
            return result
        }
    }

    init {
        bosId = require("^").first().toLong()
        eosId = require("$").first().toLong()
        padId = require("_").first().toLong()
    }

    private fun require(symbol: String): List<Int> =
        idMap[symbol] ?: error("phoneme_id_map is missing required symbol '$symbol'")

    fun contains(phoneme: String): Boolean = idMap.containsKey(phoneme)

    fun idFor(phoneme: String): Int? = idMap[phoneme]?.firstOrNull()

    /** Total number of symbols the model expects (num_symbols). */
    val numSymbols: Int get() = idMap.values.flatten().maxOrNull()?.plus(1) ?: 0

    /**
     * piper's `phonemes_to_ids`. Missing phonemes are dropped; BOS + PAD + EOS
     * framing is applied around the surviving stream.
     */
    fun phonemesToIds(phonemes: List<String>): LongArray {
        val ids = ArrayList<Long>()
        ids.add(bosId)
        ids.add(padId)
        var dropped = 0
        for (ph in phonemes) {
            val id = idMap[ph]?.firstOrNull()
            if (id == null) {
                dropped++
                continue
            }
            ids.add(id.toLong())
            ids.add(padId)
        }
        ids.add(eosId)
        if (dropped > 0) {
            System.err.println("[PhonemeIdMap] dropped $dropped phoneme(s) not in model vocab")
        }
        return ids.toLongArray()
    }
}
