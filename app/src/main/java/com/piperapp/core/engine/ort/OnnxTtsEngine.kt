package com.piperapp.core.engine.ort

import ai.onnxruntime.*
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

class OnnxTtsEngine(private val modelFile: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        modelFile.absolutePath,
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            addConfigEntry("session.intra_op.allow_spinning", "0")
            setMemoryPatternOptimization(false)
        }
    )

    fun synthesize(ids: LongArray, scales: FloatArray): FloatArray {
        val inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong()))
        val lengthTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(ids.size.toLong())), longArrayOf(1))
        val scalesTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), longArrayOf(1, 3))

        val inputs = mapOf(
            "input" to inputTensor,
            "input_lengths" to lengthTensor,
            "scales" to scalesTensor
        )

        session.run(inputs).use { result ->
            val tensor = result[0] as? OnnxTensor ?: return FloatArray(0)
            val fb = tensor.floatBuffer
            val out = FloatArray(fb.remaining())
            fb.get(out)
            return out
        }
    }

    override fun close() {
        session.close()
        env.close()
    }
}
