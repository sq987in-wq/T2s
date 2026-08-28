package com.pipertts.app.domain

import com.piperapp.core.data.db.ScriptEntity
import com.piperapp.core.data.db.PiperTTSDatabase
import com.piperapp.core.engine.phonemize.PhonemizerNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenerateSpeechUseCase(private val database: PiperTTSDatabase) {
    suspend fun execute(text: String, voiceId: String = "hi_IN-priyamvada-medium"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val phonemizer = com.piperapp.core.engine.phonemize.NativePhonemizer(voiceId)
            val phonemes = phonemizer.phonemize(text)
            val script = ScriptEntity(
                title = text.take(60),
                body = text,
                lang = "hi",
                lastVoiceId = voiceId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val id = database.scriptDao().insert(script)
            Result.success("Phonemized clause IDs for script $id; voice=$voiceId")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
