package com.piperapp.core.engine.pipeline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.pow

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
        const val BREATH_MS = 350
        const val PARA_MS = 700
    }

    fun crossfade(a: ShortArray, b: ShortArray): ShortArray {
        val len = minOf(a.size, b.size, CROSSFADE_SAMPLES)
        val out = ShortArray(a.size + b.size - len)
        for (i in out.indices) {
            val av = if (i < len) a[i].toFloat() * (1.0f - i / len.toFloat()) else a[i].toFloat()
            val bv = if (i >= a.size - len) b[i - (a.size - len)].toFloat() * ((i - (a.size - len)) / len.toFloat()) else 0.0f
            out[i] = (av + bv).toInt().toShort()
        }
        return out
    }

    fun silenceMs(ms: Long): ShortArray {
        val samples = (ms * sampleRate / 1000).toInt()
        return ShortArray(samples) { 0 }
    }

    fun normalizePeak(pcm: ShortArray, targetDb: Double = -1.0): ShortArray {
        var maxAmp = 0.0
        for (s in pcm) {
            val amp = abs(s.toInt()).toDouble()
            if (amp > maxAmp) {
                maxAmp = amp
            }
        }
        if (maxAmp < 1.0) return pcm
        val gain = (10.0.pow(targetDb / 20.0) * Short.MAX_VALUE.toDouble()) / maxAmp
        return ShortArray(pcm.size) { i ->
            (pcm[i].toDouble() * gain).toInt().toShort()
        }
    }
}
