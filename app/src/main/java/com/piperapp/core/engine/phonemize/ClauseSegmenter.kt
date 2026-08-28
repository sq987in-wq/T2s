package com.piperapp.core.engine.phonemize

/**
 * Splits normalized text into prosody clauses. Each clause is fed to the model
 * as one utterance with its own [BOS]...[EOS] span, which is what produces
 * natural intonation and pacing. Boundary punctuation: Devanagari danda (।),
 * double danda (॥), and Western `. ! ? ;`.
 */
object ClauseSegmenter {

    const val MAX_PHONEMES = 500

    private const val TERMINATORS = ".!?;।॥"

    data class Clause(val text: String, val isSentenceEnd: Boolean)

    fun segment(text: String): List<Clause> {
        val normalized = Normalizer.normalize(text)
        val parts = ArrayList<Clause>()
        val sb = StringBuilder()
        for (ch in normalized) {
            sb.append(ch)
            if (ch in TERMINATORS) {
                val t = sb.toString().trim()
                if (t.isNotEmpty()) parts.add(Clause(t, true))
                sb.setLength(0)
            }
        }
        if (sb.isNotBlank()) {
            parts.add(Clause(sb.toString().trim(), false))
        }
        return parts.filter { it.text.isNotEmpty() }
    }

    /**
     * Ensure no clause exceeds [MAX_PHONEMES]; split long clauses at the last
     * space within the cap (avoids mid-word breaks when possible).
     */
    fun splitAtCap(phonemes: List<String>): List<List<String>> {
        if (phonemes.size <= MAX_PHONEMES) return listOf(phonemes)
        val result = ArrayList<List<String>>()
        var i = 0
        while (i < phonemes.size) {
            val end = minOf(i + MAX_PHONEMES, phonemes.size)
            var cut = end
            for (j in (end - 1) downTo i) {
                if (phonemes[j] == " ") { cut = j; break }
            }
            if (cut <= i) cut = end // no space in range: hard cut
            result.add(phonemes.subList(i, cut))
            i = cut
        }
        return result
    }
}
