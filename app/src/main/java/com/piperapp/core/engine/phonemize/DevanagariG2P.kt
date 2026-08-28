package com.piperapp.core.engine.phonemize

/**
 * Rules-based Devanagari (Hindi) -> phoneme-token converter.
 *
 * Every token it emits is guaranteed to be in the piper hi_IN model vocabulary
 * (the decomposed espeak IPA set: aspiration "ʰ", length "ː", nasal "̃",
 * affricates "tʃ"/"dʒ", etc.). This is the crucial difference from the old
 * map, which emitted tokens the model didn't know and silently turned them
 * into spurious spaces.
 *
 * It fixes the audible defects of a naive char-map:
 *  1. Schwa deletion — word-final and before consonant clusters ("काम" -> k aː m,
 *     not k aː m ə; "नमस्ते" -> n ə m s t eː).
 *  2. Virāma conjuncts — क् + ष + अ = one cluster (no spurious schwa between).
 *  3. Anusvāra (ं) — becomes the homorganic nasal before a stop (हिंदी -> h ɪ n d iː);
 *     word-final anusvāra / candrabindu nasalize the vowel.
 *  4. Nukta — ज़ z, फ़ f, क़ q, ड़ ɽ, ...
 *  5. Numerals (Western & Devanagari) map to the model's digit tokens.
 *  6. Latin/Hinglish fallback maps letters to existing model tokens.
 */
class DevanagariG2P {

    fun phonemize(text: String): List<String> {
        val out = ArrayList<String>()
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            when {
                c == ' ' -> { out.add(" "); i++ }
                c in '0'..'9' || c in '\u0966'..'\u096F' -> {
                    val ascii = if (c in '0'..'9') c else ('0' + (c - '\u0966'))
                    out.add(ascii.toString())
                    i++
                }
                c == '।' || c == '॥' -> { i++ } // danda/double-danda: clause-boundary
                // marker. Skip it here (breath-pause prosody is handled upstream
                // by ClauseSegmenter). CRITICAL: danda (U+0964) and double-danda
                // (U+0965) live INSIDE the Devanagari block U+0900..U+097F, so
                // they must be handled BEFORE the generic Devanagari branch, or
                // the word-reader below cannot advance past them -> infinite loop.
                c in '\u0900'..'\u097F' -> {
                    var j = i
                    while (j < n && !text[j].isWhitespace() && text[j] != '।' && text[j] != '॥') j++
                    if (j > i) phonemizeWord(text.substring(i, j), out)
                    i = if (j > i) j else i + 1 // always make forward progress
                }
                c.isLetter() -> {
                    var j = i
                    while (j < n && text[j].isLetter() && text[j] !in '\u0900'..'\u097F') j++
                    phonemizeEnglish(text.substring(i, j), out)
                    i = j
                }
                else -> { i++ } // punctuation is dropped here (clause splitter handles prosody)
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    //  Word (syllable) parser
    // ------------------------------------------------------------------

    private class Syllable {
        val consonants = ArrayList<List<String>>() // each = one consonant's tokens
        var coda: List<String>? = null             // homorganic nasal from anusvāra
        var vowel: List<String>? = null
        var hasExplicitVowel = false
        var nasalVowel = false                     // candrabindu / word-final anusvāra
        var visarga = false
        var dead = false                           // ended in virāma
        var schwaDeleted = false
        fun hasContent(): Boolean =
            consonants.isNotEmpty() || coda != null || hasExplicitVowel || nasalVowel || visarga
    }

    private fun phonemizeWord(word: String, out: MutableList<String>) {
        val sylls = ArrayList<Syllable>()
        var cur = Syllable()
        var i = 0
        val n = word.length

        while (i < n) {
            val c = word[i]
            when {
                c == '\u094D' -> { cur.dead = true; i++ }          // virāma
                c in MATRAS -> { cur.vowel = MATRAS[c]; cur.hasExplicitVowel = true; i++ }
                c == '\u0902' -> {                                  // anusvāra
                    val next = nextConsonant(word, i)
                    if (next != null) cur.coda = listOf(homorganicNasal(next))
                    else cur.nasalVowel = true
                    i++
                }
                c == '\u0901' -> { cur.nasalVowel = true; i++ }     // candrabindu
                c == '\u0903' -> { cur.visarga = true; i++ }        // visarga
                isConsonantStart(c) -> {
                    val (tokens, consumed) = readConsonant(word, i)
                    if (cur.dead) {
                        cur.consonants.add(tokens); cur.dead = false
                    } else {
                        if (cur.hasContent()) sylls.add(cur)
                        cur = Syllable()
                        cur.consonants.add(tokens)
                    }
                    i += consumed
                }
                c in INDEPENDENT_VOWELS -> {
                    if (cur.hasContent()) sylls.add(cur)
                    cur = Syllable()
                    cur.vowel = INDEPENDENT_VOWELS[c]
                    cur.hasExplicitVowel = true
                    i++
                }
                else -> { i++ }
            }
        }
        if (cur.hasContent()) sylls.add(cur)
        applySchwaDeletion(sylls)
        emit(sylls, out)
    }

    private fun emit(sylls: List<Syllable>, out: MutableList<String>) {
        for (s in sylls) {
            for (con in s.consonants) out.addAll(con)
            if (!s.dead) {
                val vowel: List<String> = when {
                    s.hasExplicitVowel -> s.vowel ?: emptyList()
                    s.schwaDeleted -> emptyList()
                    else -> listOf("ə")
                }
                if (vowel.isNotEmpty()) {
                    out.addAll(vowel)
                    if (s.nasalVowel) out.add("\u0303") // nasalize vowel
                }
            }
            s.coda?.let { out.addAll(it) }
            if (s.visarga) out.add("h")
        }
    }

    private fun applySchwaDeletion(sylls: List<Syllable>) {
        val n = sylls.size
        if (n <= 1) return // single-syllable words keep their schwa
        for (i in 0 until n) {
            val s = sylls[i]
            if (s.hasExplicitVowel || s.dead) continue
            if (i == n - 1) { s.schwaDeleted = true; continue } // word-final
            val next = sylls[i + 1]
            if (next.consonants.size >= 2 || next.dead) s.schwaDeleted = true
        }
    }

    private fun nextConsonant(word: String, from: Int): Char? {
        for (i in (from + 1) until word.length) {
            val c = word[i]
            if (isConsonantStart(c)) return c
            if (c == '।' || c == '॥' || c.isWhitespace()) return null
        }
        return null
    }

    private fun homorganicNasal(next: Char): String = when (next) {
        in "कखगघ" -> "ŋ"
        in "चछजझ" -> "ɲ"
        in "टठडढ" -> "ɳ"
        in "तथदध" -> "n"
        in "पफबभ" -> "m"
        else -> "n"
    }

    private fun isConsonantStart(c: Char): Boolean =
        c in CONSONANTS || c in NUKTA_COMPAT

    private fun readConsonant(word: String, i: Int): Pair<List<String>, Int> {
        val c = word[i]
        // Precomposed nukta compatibility char (U+0958..U+095F).
        NUKTA_COMPAT[c]?.let { return it to 1 }
        // Base consonant + nukta (U+093C).
        val hasNukta = i + 1 < word.length && word[i + 1] == '\u093C'
        if (hasNukta) {
            val ph = NUKTA[c] ?: CONSONANTS[c]
            if (ph != null) return ph to 2
        }
        return (CONSONANTS[c] ?: emptyList()) to 1
    }

    // ------------------------------------------------------------------
    //  Hinglish (Latin) fallback — letter to existing model tokens
    // ------------------------------------------------------------------

    private fun phonemizeEnglish(word: String, out: MutableList<String>) {
        for (ch in word.lowercase()) {
            LATIN[ch]?.let { out.addAll(it) }
        }
    }

    // ------------------------------------------------------------------
    //  Tables — every phoneme string here exists in the hi_IN model map
    // ------------------------------------------------------------------

    private val CONSONANTS = mapOf(
        '\u0915' to listOf("k"), '\u0916' to listOf("k", "ʰ"),
        '\u0917' to listOf("ɡ"), '\u0918' to listOf("ɡ", "ʰ"),
        '\u0919' to listOf("ŋ"),
        '\u091A' to listOf("t", "ʃ"), '\u091B' to listOf("t", "ʃ", "ʰ"),
        '\u091C' to listOf("d", "ʒ"), '\u091D' to listOf("d", "ʒ", "ʰ"),
        '\u091E' to listOf("ɲ"),
        '\u091F' to listOf("ʈ"), '\u0920' to listOf("ʈ", "ʰ"),
        '\u0921' to listOf("ɖ"), '\u0922' to listOf("ɖ", "ʰ"),
        '\u0923' to listOf("ɳ"),
        '\u0924' to listOf("t"), '\u0925' to listOf("t", "ʰ"),
        '\u0926' to listOf("d"), '\u0927' to listOf("d", "ʰ"),
        '\u0928' to listOf("n"),
        '\u092A' to listOf("p"), '\u092B' to listOf("p", "ʰ"),
        '\u092C' to listOf("b"), '\u092D' to listOf("b", "ʰ"),
        '\u092E' to listOf("m"),
        '\u092F' to listOf("j"), '\u0930' to listOf("r"), '\u0932' to listOf("l"),
        '\u0931' to listOf("r"), '\u0933' to listOf("l"), '\u0934' to listOf("l"),
        '\u0935' to listOf("ʋ"), '\u0936' to listOf("ʃ"), '\u0937' to listOf("ʂ"),
        '\u0938' to listOf("s"), '\u0939' to listOf("ɦ"),
    )

    private val NUKTA = mapOf( // base consonant + ़
        '\u0915' to listOf("q"), '\u0916' to listOf("x"),
        '\u0917' to listOf("ɣ"), '\u091C' to listOf("z"),
        '\u0921' to listOf("ɽ"), '\u0922' to listOf("ɽ", "ʰ"),
        '\u092B' to listOf("f"),
    )

    private val NUKTA_COMPAT = mapOf( // precomposed U+0958..U+095F
        '\u0958' to listOf("q"), '\u0959' to listOf("x"), '\u095A' to listOf("ɣ"),
        '\u095B' to listOf("z"), '\u095C' to listOf("ɽ"), '\u095D' to listOf("ɽ", "ʰ"),
        '\u095E' to listOf("f"),
    )

    private val INDEPENDENT_VOWELS = mapOf(
        '\u0905' to listOf("ə"), '\u0906' to listOf("a", "ː"),
        '\u0907' to listOf("ɪ"), '\u0908' to listOf("i", "ː"),
        '\u0909' to listOf("ʊ"), '\u090A' to listOf("u", "ː"),
        '\u090B' to listOf("r", "ɪ"), '\u090C' to listOf("l", "ɪ"),
        '\u090F' to listOf("e", "ː"), '\u0910' to listOf("ɛ", "ː"),
        '\u0911' to listOf("ɔ", "ː"),
        '\u0913' to listOf("o", "ː"), '\u0914' to listOf("ɔ", "ː"),
    )

    private val MATRAS = mapOf(
        '\u093E' to listOf("a", "ː"), '\u093F' to listOf("ɪ"), '\u0940' to listOf("i", "ː"),
        '\u0941' to listOf("ʊ"), '\u0942' to listOf("u", "ː"), '\u0943' to listOf("r", "ɪ"),
        '\u0947' to listOf("e", "ː"), '\u0948' to listOf("ɛ", "ː"),
        '\u0949' to listOf("ɔ", "ː"),
        '\u094B' to listOf("o", "ː"), '\u094C' to listOf("ɔ", "ː"),
    )

    private val LATIN = mapOf(
        'a' to listOf("a"), 'b' to listOf("b"), 'c' to listOf("k"),
        'd' to listOf("d"), 'e' to listOf("e"), 'f' to listOf("f"),
        'g' to listOf("ɡ"), 'h' to listOf("h"), 'i' to listOf("ɪ"),
        'j' to listOf("dʒ"), 'k' to listOf("k"), 'l' to listOf("l"),
        'm' to listOf("m"), 'n' to listOf("n"), 'o' to listOf("ɒ"),
        'p' to listOf("p"), 'q' to listOf("k"), 'r' to listOf("r"),
        's' to listOf("s"), 't' to listOf("t"), 'u' to listOf("ʊ"),
        'v' to listOf("v"), 'w' to listOf("w"), 'x' to listOf("k", "s"),
        'y' to listOf("j"), 'z' to listOf("z"),
    )
}
