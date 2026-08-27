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
            setOptimizedModelFilePath(modelFile.resolveSibling("CACHE.opt").absolutePath)
        }
    )

    fun synthesize(ids: LongArray, scales: FloatArray): FloatArray {
        val inputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong()))
        val lengthTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(ids.size.toLong())), longArrayOf(1))
        val scalesTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(scales), longArrayOf(scales.size.toLong()))

        val inputs = mapOf(
            "input" to inputTensor,
            "input_lengths" to lengthTensor,
            "scales" to scalesTensor
        )

        val result = session.run(inputs)
        val output = result[0].value
        return when (output) {
            is Array<*> -> {
                val subArray = output[0]
                when (subArray) {
                    is Array<*> -> (subArray[0] as FloatArray)
                    is FloatArray -> subArray
                    else -> FloatArray(0)
                }
            }
            is FloatArray -> output
            else -> FloatArray(0)
        }
    }

    override fun close() {
        session.close()
        env.close()
    }
}
