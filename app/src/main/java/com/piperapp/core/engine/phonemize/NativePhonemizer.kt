package com.piperapp.core.engine.phonemize

import org.json.JSONObject
import java.io.File

class PhonemizerException(msg: String = "Phonemization failed") : Exception(msg)

interface Phonemizer : AutoCloseable {
    suspend fun phonemize(text: String): List<LongArray>
}

/**
 * Phonemization front-end — hybrid "native-first" design.
 *
 * PRIMARY PATH (byte-parity): if an Android-native `libpiper_phonemizer.so`
 * (espeak-ng via piper-phonemize) is present, every clause is phonemized by
 * espeak-ng itself — the exact stream the Piper models were trained on,
 * including schwa deletion, stress/tone markers and anusvara assimilation.
 * This is the only route to 100% studio-grade parity.
 *
 * FALLBACK PATH (always available): otherwise the corrected pure-Kotlin
 * [DevanagariG2P] is used — normalized, clause-segmented, mapped only to tokens
 * present in the model's `phoneme_id_map`, and framed with piper-exact
 * BOS/PAD/EOS padding by [PhonemeIdMap]. This never crashes and never drops a
 * consonant (proven by the JVM unit tests), it just approximates espeak.
 *
 * Long clauses are capped at [ClauseSegmenter.MAX_PHONEMES] to protect model
 * quality.
 */
class NativePhonemizer(
    private val voiceDir: File,
    /** Path to extracted `espeak-ng-data` for the native path (may be empty). */
    private val espeakDataPath: String = "",
) : Phonemizer {
    constructor(voicePath: String) : this(File(voicePath))

    private val idMap: PhonemeIdMap
    private val g2p = DevanagariG2P()
    private val nativeReady: Boolean

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

        nativeReady = PhonemizerNative.loadIfAvailable(
            espeakDataPath = espeakDataPath,
            voice = "hi", // from the model's espeak.voice
            idMapJson = jsonFile.readText(),
        )
    }

    val isNative: Boolean get() = nativeReady

    data class ClauseResult(val ids: LongArray, val isSentenceEnd: Boolean)

    /** Segment + phonemize into clause ID tensors, with sentence-end metadata. */
    suspend fun phonemizeClauses(text: String): List<ClauseResult> {
        if (nativeReady) {
            return nativePhonemizeClauses(text)
        }
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

    /**
     * Native path: espeak-ng already frames each sentence with BOS/PAD/EOS and
     * returns one clause per sentence, separated by -1 in the flat array.
     * We treat every native clause as a sentence end (that is how espeak
     * segments) so the pipeline inserts a natural breath pause between them.
     */
    private fun nativePhonemizeClauses(text: String): List<ClauseResult> {
        val flat = PhonemizerNative.phonemizeToIds(text) ?: return emptyList()
        val result = ArrayList<ClauseResult>()
        var start = 0
        for (i in flat.indices) {
            if (flat[i] == -1L) {
                if (i > start) {
                    result.add(ClauseResult(flat.copyOfRange(start, i), true))
                }
                start = i + 1
            }
        }
        if (start < flat.size) {
            result.add(ClauseResult(flat.copyOfRange(start, flat.size), true))
        }
        return result
    }

    override suspend fun phonemize(text: String): List<LongArray> =
        phonemizeClauses(text).map { it.ids }

    override fun close() {}
}
