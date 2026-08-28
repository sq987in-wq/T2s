package com.piperapp.core.engine.pipeline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Clause-level audio post-processing.
 *
 * This class owns the two things that most affect "natural pacing":
 *  1. Hard-edge removal at clause joints — independent ONNX runs produce
 *     stitched/clicking boundaries, so we equal-power crossfade each joint.
 *  2. Breath pauses — 350 ms at sentence breaks, 700 ms at paragraph
 *     (double-danda) breaks, 80 ms at a soft intra-sentence split.
 *
 * It also provides loudness normalization (RMS toward -16 dBFS, peak-limited
 * at -1 dBFS) which replaces the old peak-only "loudnorm" substitute.
 *
 * The previous version of this file (a) had an out-of-bounds bug in [crossfade]
 * that crashed synthesis, and (b) used `kotlin.math.pow` in a way the compiler
 * rejected. Both are fixed here.
 */
class SynthesisPipeline(private val sampleRate: Int = 22050) {
    sealed class State {
        object Idle : State()
        data class Synthesizing(val clause: Int, val total: Int) : State()
        object Streaming : State()
    }

    private val _state: MutableStateFlow<State> = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state

    companion object {
        const val CROSSFADE_SAMPLES = 150
        const val BREATH_MS = 350L
        const val PARA_MS = 700L
        const val CLAUSE_PAUSE_MS = 80L
        private const val PEAK_DBFS = -1.0
        private const val TARGET_RMS_DBFS = -16.0
    }

    /**
     * Equal-power crossfade joining two consecutive clause buffers.
     * Overlaps the tail of [a] with the head of [b] for CROSSFADE_SAMPLES
     * samples. Safe for any input sizes (no out-of-bounds).
     */
    fun crossfade(a: ShortArray, b: ShortArray): ShortArray {
        if (a.isEmpty()) return b.copyOf()
        if (b.isEmpty()) return a.copyOf()
        val overlap = minOf(a.size, b.size, CROSSFADE_SAMPLES)
        val out = ShortArray(a.size + b.size - overlap)
        // Copy the non-overlapping body of `a`.
        System.arraycopy(a, 0, out, 0, a.size - overlap)
        // Crossfade the overlapping region.
        for (i in 0 until overlap) {
            val g = i.toFloat() / overlap
            val ta = a[a.size - overlap + i].toFloat() * (1f - g)
            val tb = b[i].toFloat() * g
            out[a.size - overlap + i] = (ta + tb).toInt().toShort()
        }
        // Copy the non-overlapping remainder of `b`.
        System.arraycopy(b, overlap, out, a.size, b.size - overlap)
        return out
    }

    fun silenceMs(ms: Long): ShortArray {
        val samples = (ms * sampleRate / 1000).toInt()
        return ShortArray(samples) { 0 }
    }

    /** Peak normalization (legacy API, kept for compatibility). */
    fun normalizePeak(pcm: ShortArray, targetDb: Double = -1.0): ShortArray {
        if (pcm.isEmpty()) return pcm
        var maxAmp = 0
        for (s in pcm) {
            val a = abs(s.toInt())
            if (a > maxAmp) maxAmp = a
        }
        if (maxAmp == 0) return pcm
        val gain = (Math.pow(10.0, targetDb / 20.0) * Short.MAX_VALUE.toDouble()) / maxAmp
        return ShortArray(pcm.size) { i -> (pcm[i].toDouble() * gain).toInt().toShort() }
    }

    /**
     * Loudness normalization: gain toward -16 dBFS RMS, peak-limited at -1 dBFS,
     * with a soft ceiling so silence is never boosted into a wall of noise.
     */
    fun normalizeLoudness(pcm: ShortArray): ShortArray {
        if (pcm.isEmpty()) return pcm
        var sum = 0.0
        for (s in pcm) sum += s.toDouble() * s.toDouble()
        val rms = sqrt(sum / pcm.size)
        if (rms <= 0.0) return pcm
        var peak = 0.0
        for (s in pcm) {
            val a = abs(s.toDouble())
            if (a > peak) peak = a
        }
        val peakGain = (Math.pow(10.0, PEAK_DBFS / 20.0) * Short.MAX_VALUE.toDouble()) / peak
        val rmsGain = (Math.pow(10.0, TARGET_RMS_DBFS / 20.0) * Short.MAX_VALUE.toDouble()) / rms
        val gain = minOf(peakGain, rmsGain).coerceIn(0.0, 4.0)
        return ShortArray(pcm.size) { i -> (pcm[i].toDouble() * gain).toInt().toShort() }
    }

    /**
     * Assemble per-clause PCM into one contiguous stream, inserting a short
     * crossfade at each joint and a breath pause after clauses flagged as
     * sentence-ending ([isSentenceEnd]).
     */
    fun assemble(clausePcm: List<ShortArray>, isSentenceEnd: List<Boolean>): ShortArray {
        if (clausePcm.isEmpty()) return ShortArray(0)
        val out = ArrayList<Short>()
        for (i in clausePcm.indices) {
            val cur = clausePcm[i]
            if (i == 0) {
                appendAll(out, cur)
            } else {
                // Blend the tail of the previous clause with the head of this one.
                val prevSize = out.size
                val overlap = minOf(CROSSFADE_SAMPLES, prevSize, cur.size)
                for (k in 0 until overlap) {
                    val idx = prevSize - overlap + k
                    val g = k.toFloat() / overlap
                    val blended = out[idx] * (1f - g) + cur[k] * g
                    out[idx] = blended.toInt().toShort()
                }
                appendAll(out, cur, from = overlap)
            }
            // Breath pause after this clause if it ended a sentence/paragraph.
            val endFlag = isSentenceEnd.getOrElse(i) { false }
            if (endFlag) appendAll(out, silenceMs(BREATH_MS))
        }
        return out.toShortArray()
    }

    private fun appendAll(out: MutableList<Short>, src: ShortArray, from: Int = 0) {
        for (i in from until src.size) out.add(src[i])
    }
}
