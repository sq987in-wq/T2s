package com.piperapp.core.engine.pipeline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// §4.3 — clause-chunked streaming synthesis; 150-sample crossfade; 350 ms breath pause
class SynthesisPipeline {
    sealed class State { object Idle : State(); data class Synthesizing(val clause: Int, val total: Int) : State(); object Streaming : State() }
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    // Crossfade: 150 samples ~6.8 ms @ 22.05 kHz (Blueprint verified default)
    // Breath pause: 350 ms = 7,718 samples @ 22.05 kHz; paragraph = 700 ms
    fun processClause(pcm: ShortArray, isLastInSentence: Boolean, isParagraphBreak: Boolean): ShortArray {
        val crossfadeSamples = 150
        val breathSamples = (0.350 * 22050).toInt() // 7,718
        val paraSamples = (0.700 * 22050).toInt()
        // Stub: real implementation applies equal-power crossfade at joints,
        // inserts silence arrays for pauses, and normalizes to -1 dBFS peak
        return pcm // refined pipeline logic to be wired to AudioSink
    }
}
