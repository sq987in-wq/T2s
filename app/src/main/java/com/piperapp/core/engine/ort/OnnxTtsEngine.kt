package com.piperapp.core.engine.ort

import ai.onnxruntime.*
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

class OnnxTtsEngine(private val modelFile: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setInterOpNumThreads(1)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
        }
        session = env.createSession(modelFile.absolutePath, opts)
    }

    fun synthesize(ids: LongArray, scales: FloatArray): FloatArray {
        val inputNames = session.inputNames

        val inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong()))
        val lengthTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(ids.size.toLong())), longArrayOf(1))
        val scalesTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), longArrayOf(1, 3))

        val inputs = mutableMapOf<String, OnnxTensor>()
        if (inputNames.contains("input")) inputs["input"] = inputTensor
        if (inputNames.contains("input_lengths")) inputs["input_lengths"] = lengthTensor
        if (inputNames.contains("scales")) inputs["scales"] = scalesTensor

        // कुछ Piper मॉडल्स में speaker id (sid) जरूरी होता है
        if (inputNames.contains("sid")) {
            val sidTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(0L)), longArrayOf(1))
            inputs["sid"] = sidTensor
        }

        try {
            session.run(inputs).use { result ->
                if (result.size() == 0) return FloatArray(0)
                val tensor = result[0] as? OnnxTensor ?: return FloatArray(0)
                val fb = tensor.floatBuffer
                val out = FloatArray(fb.remaining())
                fb.get(out)
                return out
            }
        } finally {
            inputs.values.forEach { runCatching { it.close() } }
        }
    }

    override fun close() {
        runCatching { session.close() }
        runCatching { env.close() }
    }
}
