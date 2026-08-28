package com.pipertts.app.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// §4.3 / §5.1 — Envelope extractor for scrolling waveform + progress
@Composable
fun WaveformView(envelopes: List<Float>, progress: Float = 0f) {
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
        val w = size.width
        val h = size.height
        val step = if (envelopes.isEmpty()) 1f else w / envelopes.size
        for (i in envelopes.indices) {
            val x = i * step
            val amp = envelopes[i].coerceIn(0f, 1f) * h * 0.4f
            drawLine(
                color = Color(0xFF6200EE),
                start = Offset(x, h / 2 - amp),
                end = Offset(x, h / 2 + amp),
                strokeWidth = 2f
            )
        }
    }
}
