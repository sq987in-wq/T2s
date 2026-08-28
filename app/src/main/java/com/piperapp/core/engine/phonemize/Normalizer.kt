package com.piperapp.core.engine.phonemize

import java.text.Normalizer as JNormalizer

/**
 * First stage of the pipeline. Fixes the broken text normalization:
 *  1. NFC normalization (IME/clipboard input is frequently NFD; espeak and
 *     the model expect NFC — decomposed Devanagari mangles both).
 *  2. Strips zero-width joiners / bidi / invisible format chars.
 *  3. Collapses whitespace and canonicalizes smart punctuation.
 */
object Normalizer {

    private val ZERO_WIDTH = listOf(
        '\u200B', '\uFEFF', '\u200C', '\u200D', '\u2060',
        '\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', '\u061C',
    )

    fun normalize(raw: String): String {
        var s = JNormalizer.normalize(raw, JNormalizer.Form.NFC)
        s = s.filter { it !in ZERO_WIDTH }
        s = s.replace(Regex("[ \\t\\u00A0\\u2000-\\u200A]+"), " ")
        s = s
            .replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201C', '"').replace('\u201D', '"')
        return s.trim()
    }
}
