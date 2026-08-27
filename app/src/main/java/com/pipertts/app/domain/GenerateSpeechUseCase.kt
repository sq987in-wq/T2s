package com.pipertts.app.domain

import com.pipertts.app.data.room.PiperTTSDatabase
import com.pipertts.app.data.room.Utterance
import com.pipertts.app.jni.PiperPhonemizeJNI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Domain use case: generate speech offline using Option C Hybrid pipeline.
 * Pipeline: Text → JNI phonemize → ONNX inference → WAV/Audio file.
 */
class GenerateSpeechUseCase(private val database: PiperTTSDatabase) {

    private val phonemizer = PiperPhonemizeJNI()

    suspend fun execute(text: String, voiceId: Int = 0): Result<Utterance> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Phonemize via JNI bridge (piper-phonemize)
            val phonemes = phonemizer.phonemize(text)

            // Step 2: In production, run ONNX Runtime inference here.
            // For skeleton, persist phonemized result and return.
            val utterance = Utterance(
                text = text,
                phonemes = phonemes,
                voiceId = voiceId,
                audioFilePath = null // Set after ONNX inference completes in full build
            )

            val id = database.ttsDao().insertUtterance(utterance)
            Result.success(utterance.copy(id = id.toInt()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
