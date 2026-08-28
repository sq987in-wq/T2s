package com.piperapp.core.engine.phonemize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end unit tests for the Devanagari G2P. These prove (on real JVM/CI)
 * that:
 *   - no consonants are dropped (the "m" in नमस्ते is preserved),
 *   - schwa deletion works,
 *   - anusvāra place assimilation works,
 *   - nukta/digits/Hinglish map to model tokens,
 *   - padding & chunking terminate and produce correct BOS/PAD/EOS framing.
 */
class DevanagariG2PTest {

    private val g2p = DevanagariG2P()

    // ---- letter preservation & schwa ----

    @Test
    fun `namaste preserves all consonants`() {
        val ph = g2p.phonemize("नमस्ते")
        // MUST contain m (the bug report claimed it was dropped).
        assertTrue("expected 'm' in $ph", ph.contains("m"))
        // Full expected sequence.
        assertEquals(listOf("n", "ə", "m", "s", "t", "e", "ː"), ph)
    }

    @Test
    fun `kaam schwa deleted word final`() {
        assertEquals(listOf("k", "a", "ː", "m"), g2p.phonemize("काम"))
    }

    @Test
    fun `hindi anusvara homorganic nasal`() {
        // हिंदी -> ɦ ɪ n d i ː (nasal n before dental द)
        assertEquals(listOf("ɦ", "ɪ", "n", "d", "i", "ː"), g2p.phonemize("हिंदी"))
    }

    @Test
    fun `kampani anusvara m before p`() {
        // कंपनी -> k ə m p ə n i ː
        assertEquals(listOf("k", "ə", "m", "p", "ə", "n", "i", "ː"), g2p.phonemize("कंपनी"))
    }

    @Test
    fun `nukta z`() {
        // ज़रूर -> z ə r u ː r
        assertEquals(listOf("z", "ə", "r", "u", "ː", "r"), g2p.phonemize("ज़रूर"))
    }

    @Test
    fun `digits and hinglish`() {
        val ph = g2p.phonemize("the year 1984")
        // Latin letters map to model tokens; digits map to digit tokens.
        assertTrue("contains letter t", ph.contains("t"))
        assertTrue("contains letter h", ph.contains("h"))
        assertTrue("contains letter e", ph.contains("e"))
        assertTrue("contains digit 1", ph.contains("1"))
        assertTrue("contains digit 9", ph.contains("9"))
        assertTrue("contains digit 8", ph.contains("8"))
        assertTrue("contains digit 4", ph.contains("4"))
    }

    @Test
    fun `devanagari digits map to ascii digits`() {
        val ph = g2p.phonemize("१९८४")
        assertEquals(listOf("1", "9", "8", "4"), ph)
    }

    // ---- termination / no infinite loop ----

    @Test
    fun `sentence with danda terminates`() {
        val s = "नमस्ते, यह ऑफ़लाइन टीटीएस अब पूरी तरह काम कर रहा है।"
        val ph = g2p.phonemize(s)
        assertTrue("non-empty", ph.isNotEmpty())
        assertTrue("has no danda in output", !ph.contains("।"))
    }

    @Test
    fun `danda-only and empty terminate`() {
        assertEquals(emptyList<String>(), g2p.phonemize(""))
        assertEquals(emptyList<String>(), g2p.phonemize("।"))
        assertEquals(emptyList<String>(), g2p.phonemize("॥।"))
    }

    @Test
    fun `no letter is dropped - count consonants`() {
        // नमस्ते has 4 consonant graphemes (न म स त).
        val ph = g2p.phonemize("नमस्ते")
        val consonants = listOf("n", "m", "s", "t")
        for (c in consonants) {
            assertTrue("missing $c in $ph", ph.contains(c))
        }
    }
}

/** Padding / chunking unit tests for the piper-exact ID framing. */
class PhonemeIdMapTest {

    @Test
    fun `piper padding bos pad id pad eos`() {
        // Build a map equivalent to the model's tokens used by the G2P.
        val ids = phonemeIdsOf(listOf("n", "ə", "m", "s", "t", "e", "ː"))
        // Expect: [BOS=1, PAD=0, n=26, PAD, ə=59, PAD, m=25, PAD, s=31, PAD,
        //          t=32, PAD, e=18, PAD, ː=122, PAD, EOS=2]
        val expected = longArrayOf(
            1, 0, 26, 0, 59, 0, 25, 0, 31, 0, 32, 0, 18, 0, 122, 0, 2,
        )
        assertEquals(expected.toList(), ids.toList())
    }

    @Test
    fun `chunking never exceeds cap and terminates`() {
        val big = MutableList(1200) { "a" }
        val chunks = ClauseSegmenter.splitAtCap(big)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.size <= ClauseSegmenter.MAX_PHONEMES })
        assertEquals(1200, chunks.sumOf { it.size })
    }

    private fun phonemeIdsOf(phonemes: List<String>): LongArray {
        // A minimal PhonemeIdMap-like framing: BOS PAD id PAD ... PAD EOS
        val map = mapOf(
            "^" to listOf(1), "\$" to listOf(2), "_" to listOf(0),
            "n" to listOf(26), "ə" to listOf(59), "m" to listOf(25),
            "s" to listOf(31), "t" to listOf(32), "e" to listOf(18), "ː" to listOf(122),
        )
        val idMap = PhonemeIdMap.parse(jsonObjectOf(map))
        return idMap.phonemesToIds(phonemes)
    }

    private fun jsonObjectOf(map: Map<String, List<Int>>): org.json.JSONObject {
        val ids = org.json.JSONObject()
        for ((k, v) in map) {
            val arr = org.json.JSONArray()
            for (x in v) arr.put(x)
            ids.put(k, arr)
        }
        val root = org.json.JSONObject()
        root.put("phoneme_id_map", ids)
        return root
    }
}
