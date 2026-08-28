package com.piperapp.core.engine.phonemize

import org.json.JSONObject
import java.io.File

class PhonemizerException(msg: String = "Phonemization failed") : Exception(msg)

interface Phonemizer : AutoCloseable {
    suspend fun phonemize(text: String): List<LongArray>
}

/**
 * Phonemization front-end.
 *
 * This builds a `List<LongArray>` of clause ID tensors: the input text is
 * normalized (NFC), segmented into prosody clauses (। . ! ? ; ॥), each clause
 * is converted to phoneme tokens by [DevanagariG2P] (all tokens guaranteed to
 * be in the model vocabulary), and each clause is framed with piper-exact
 * BOS/PAD/EOS padding by [PhonemeIdMap].
 *
 * Long clauses are capped at [ClauseSegmenter.MAX_PHONEMES] to protect model
 * quality (a leading cause of the old "robotic pacing").
 *
 * NOTE on the name: despite "Native", this is the pure-Kotlin engine. The
 * prebuilt `libpiper_phonemize.so` in this repo is a desktop-Linux/glibc
 * binary (needs libc.so.6, libespeak-ng.so.1) and cannot load on Android —
 * it was the real source of the historical NDK crashes. True byte-parity with
 * espeak-ng requires an Android-native piper-phonemize build; see
 * docs/DeepAudit-100x.md for the optional path. This Kotlin G2P is the
 * always-available, build-safe engine and fixes the mapping/pacing defects.
 */
class NativePhonemizer(private val voiceDir: File) : Phonemizer {
    constructor(voicePath: String) : this(File(voicePath))

    private val idMap: PhonemeIdMap
    private val g2p = DevanagariG2P()

    init {
        val jsonFile = if (voiceDir.isDirectory) {
            File(voiceDir, "model.onnx.json")
        } else {
            File(voiceDir.parentFile ?: voiceDir, "model.onnx.json")
        }
        if (!jsonFile.exists()) {
            throw PhonemizerException("Voice config not found: ${jsonFile.absolutePath}")
        }
        try {
            idMap = PhonemeIdMap.parse(JSONObject(jsonFile.readText()))
        } catch (e: Exception) {
            throw PhonemizerException("Failed to parse phoneme_id_map: ${e.message}")
        }
    }

    data class ClauseResult(val ids: LongArray, val isSentenceEnd: Boolean)

    /** Segment + phonemize into clause ID tensors, with sentence-end metadata. */
    suspend fun phonemizeClauses(text: String): List<ClauseResult> {
        val clauses = ClauseSegmenter.segment(text)
        if (clauses.isEmpty()) return emptyList()
        val result = ArrayList<ClauseResult>()
        for (clause in clauses) {
            val phonemes = g2p.phonemize(clause.text)
            for (segment in ClauseSegmenter.splitAtCap(phonemes)) {
                val ids = idMap.phonemesToIds(segment)
                if (ids.size > 2) { // > BOS+PAD+EOS means real content
                    result.add(ClauseResult(ids, clause.isSentenceEnd))
                }
            }
        }
        return result
    }

    override suspend fun phonemize(text: String): List<LongArray> =
        phonemizeClauses(text).map { it.ids }

    override fun close() {}
}
