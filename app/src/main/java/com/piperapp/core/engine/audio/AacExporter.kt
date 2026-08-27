package com.piperapp.core.engine.audio

import android.media.MediaCodec
import android.media.MediaMuxer
import android.os.ParcelFileDescriptor
import java.io.FileDescriptor

// §5.2 — AAC-LC 22.05 kHz mono @ ~64 kbps; MediaCodec -> MediaMuxer; NO MP3
class AacExporter(private val outputPath: String, private val sampleRate: Int = 22050) : AutoCloseable {
    private val encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
    private val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var trackIndex = -1
    private var ptsUs = 0L

    fun begin() {
        val format = android.media.MediaFormat.createAudioFormat(
            "audio/mp4a-latm", sampleRate, 1
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64000)
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
    }

    fun writePcm(pcm16: ShortArray) {
        // Inspect input buffer, encode, write to muxer — stub preserves contract
        trackIndex = muxer.addTrack(encoder.outputFormat)
        muxer.start()
        ptsUs += (pcm16.size.toLong() * 1_000_000L / sampleRate)
    }

    fun finish(durationMs: Long) {
        encoder.signalEndOfInputStream()
        muxer.stop()
        muxer.release()
        encoder.release()
    }

    override fun close() { finish(0) }
}
