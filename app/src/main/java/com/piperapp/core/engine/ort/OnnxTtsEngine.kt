package com.piperapp.core.engine.ort

import ai.onnxruntime.*
import java.io.File

// Blueprint §3.1 / §4.2 — session tuning
class OnnxTtsEngine(private val modelFile: File) : AutoCloseable {
    // §3.2 — must be called from Dispatchers.IO.limitedParallelism(1); never concurrent
    private val dispatcher = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1)
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelFile.absolutePath,
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)     // CPU EP NEON
            setInterOpNumThreads(1)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            addConfigEntry("session.intra_op.allow_spinning", "0")
            setMemoryPatternOptimization(false) // dynamic shapes
            setOptimizedModelFilePath(modelFile.resolveSibling("CACHE.opt").absolutePath)
        })

    fun synthesize(ids: LongArray, scales: FloatArray): FloatArray {
        // §4.3: input int64 [1,N], input_lengths int64 [1], scales float32 [3]
        return session.run(OrtSession.RunOptions(),
            mapOf(
                "input" to OnnxTensor.createTensor(env, java.nio.LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())),
                "input_lengths" to OnnxTensor.createTensor(env, longArrayOf(ids.size.toLong()), longArrayOf(1)),
                "scales" to OnnxTensor.createTensor(env, scales, longArrayOf(1, 3))
            )
        ).get(0)?.value as FloatArray
    }

    override fun close() { session.close(); env.close() }
}
