package com.piperapp.core.engine.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer

class AacExporter(private val outputPath: String, private val sampleRate: Int = 22050) : AutoCloseable {
    private val encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
    private val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var trackIndex = -1
    private var ptsUs = 0L

    fun begin() {
        val format = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64000)
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
    }

    fun writePcm(pcm16: ShortArray) {
        trackIndex = muxer.addTrack(encoder.outputFormat)
        muxer.start()
        ptsUs += (pcm16.size.toLong() * 1_000_000L / sampleRate)
    }

    fun finish(durationMs: Long) {
        try {
            encoder.signalEndOfInputStream()
            muxer.stop()
            muxer.release()
            encoder.release()
        } catch (_: Exception) {}
    }

    override fun close() { finish(0) }
}
