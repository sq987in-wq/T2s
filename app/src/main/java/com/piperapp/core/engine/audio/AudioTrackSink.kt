package com.piperapp.core.engine.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

// §5.1 — 22,050 Hz mono PCM16 MODE_STREAM; deep buffer; prime before play()
class AudioTrackSink(private val sampleRate: Int = 22050) : AutoCloseable {
    private var track: AudioTrack? = null

    fun open() {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer * 4) // underrun immunity (§5.1)
            .build()
        track?.play()
    }

    fun write(pcm16: ShortArray) {
        track?.write(pcm16, 0, pcm16.size)
    }

    fun stop() { track?.stop() }
    fun pause() { track?.pause() }
    fun resume() { track?.play() }
    override fun close() { track?.release(); track = null }
}
