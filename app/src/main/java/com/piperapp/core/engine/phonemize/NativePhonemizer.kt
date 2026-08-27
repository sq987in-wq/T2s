package com.piperapp.core.engine.phonemize

import org.json.JSONObject
import java.io.File

class PhonemizerException(msg: String = "Phonemization failed") : Exception(msg)

interface Phonemizer : AutoCloseable {
    suspend fun phonemize(text: String): List<LongArray>
}

class NativePhonemizer(private val voiceDir: File) : Phonemizer {
    constructor(voicePath: String) : this(File(voicePath))

    private val idMap = mutableMapOf<String, Long>()
    private var bosId: Long = 1L
    private var eosId: Long = 2L
    private var padId: Long = 0L

    init {
        try {
            val jsonFile = if (voiceDir.isDirectory) {
                File(voiceDir, "model.onnx.json")
            } else {
                File(voiceDir.parentFile ?: voiceDir, "model.onnx.json")
            }

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

    // Convert Hindi Devanagari Unicode directly to eSpeak IPA Phonemes
    private fun devanagariToIpa(text: String): List<String> {
        val consonants = mapOf(
            'क' to listOf("k"), 'ख' to listOf("k", "ʰ"), 'ग' to listOf("ɡ"), 'घ' to listOf("ɡ", "ʰ"), 'ङ' to listOf("ŋ"),
            'च' to listOf("t", "ʃ"), 'छ' to listOf("t", "ʃ", "ʰ"), 'ज' to listOf("d", "ʒ"), 'झ' to listOf("d", "ʒ", "ʰ"), 'ञ' to listOf("ɲ"),
            'ट' to listOf("ʈ"), 'ठ' to listOf("ʈ", "ʰ"), 'ड' to listOf("ɖ"), 'ढ' to listOf("ɖ", "ʰ"), 'ण' to listOf("ɳ"),
            'त' to listOf("t"), 'थ' to listOf("t", "ʰ"), 'द' to listOf("d"), 'ध' to listOf("d", "ʰ"), 'न' to listOf("n"),
            'प' to listOf("p"), 'फ' to listOf("p", "ʰ"), 'ब' to listOf("b"), 'भ' to listOf("b", "ʰ"), 'म' to listOf("m"),
            'य' to listOf("j"), 'र' to listOf("r"), 'ल' to listOf("l"), 'व' to listOf("ʋ"),
            'श' to listOf("ʃ"), 'ष' to listOf("ʂ"), 'स' to listOf("s"), 'ह' to listOf("ɦ"),
            'ड़' to listOf("ɽ"), 'ढ़' to listOf("ɽ", "ʰ"), 'फ़' to listOf("f"), 'ज़' to listOf("z"), 'ख़' to listOf("x"), 'ग़' to listOf("ɣ")
        )

        val vowels = mapOf(
            'अ' to listOf("ə"), 'आ' to listOf("a", "ː"), 'इ' to listOf("ɪ"), 'ई' to listOf("i", "ː"),
            'उ' to listOf("ʊ"), 'ऊ' to listOf("u", "ː"), 'ऋ' to listOf("r", "ɪ"),
            'ए' to listOf("e", "ː"), 'ऐ' to listOf("ɛ", "ː"), 'ओ' to listOf("o", "ː"), 'औ' to listOf("ɔ", "ː")
        )

        val matras = mapOf(
            'ा' to listOf("a", "ː"), 'ि' to listOf("ɪ"), 'ी' to listOf("i", "ː"),
            'ु' to listOf("ʊ"), 'ू' to listOf("u", "ː"), 'ृ' to listOf("r", "ɪ"),
            'े' to listOf("e", "ː"), 'ै' to listOf("ɛ", "ː"), 'ो' to listOf("o", "ː"), 'ौ' to listOf("ɔ", "ː")
        )

        val virama = '्'
        val anusvara = 'ं'
        val candrabindu = 'ँ'
        val visarga = 'ः'

        val phonemes = mutableListOf<String>()
        val len = text.length
        var i = 0

        while (i < len) {
            val c = text[i]

            if (c == ' ') {
                phonemes.add(" ")
                i++
                continue
            }

            if (consonants.containsKey(c)) {
                val ph = consonants[c]!!
                val nextChar = if (i + 1 < len) text[i + 1] else null

                phonemes.addAll(ph)

                if (nextChar == virama) {
                    i += 2
                    continue
                } else if (nextChar != null && matras.containsKey(nextChar)) {
                    phonemes.addAll(matras[nextChar]!!)
                    i += 2
                } else {
                    // Inherent schwa 'ə'
                    val isEnd = (i + 1 == len) || (text[i + 1] == ' ')
                    if (!isEnd) {
                        phonemes.add("ə")
                    }
                    i++
                }
                continue
            }

            if (vowels.containsKey(c)) {
                phonemes.addAll(vowels[c]!!)
                i++
                continue
            }

            if (c == anusvara || c == candrabindu) {
                phonemes.add("̃")
                i++
                continue
            }

            if (c == visarga) {
                phonemes.add("ɦ")
                i++
                continue
            }

            // Punctuation or ASCII fallback
            val s = c.toString()
            if (idMap.containsKey(s)) {
                phonemes.add(s)
            }
            i++
        }

        return phonemes
    }

    override suspend fun phonemize(text: String): List<LongArray> {
        val ipaList = devanagariToIpa(text)
        val ids = mutableListOf<Long>()
        
        ids.add(bosId)
        ids.add(padId)

        for (ph in ipaList) {
            val id = idMap[ph] ?: idMap[ph.lowercase()] ?: idMap[" "] ?: 0L
            if (id != 0L) {
                ids.add(id)
                ids.add(padId)
            }
        }

        ids.add(eosId)
        return listOf(ids.toLongArray())
    }

    override fun close() {}
}
