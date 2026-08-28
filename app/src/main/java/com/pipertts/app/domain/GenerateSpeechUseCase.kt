package com.pipertts.app.domain

import com.piperapp.core.data.db.PiperTTSDatabase
import com.piperapp.core.data.db.ScriptEntity
import com.piperapp.core.engine.phonemize.ClauseSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persists a synthesis request and reports how the text was segmented into
 * clauses. The actual audio synthesis runs in the UI synthesis path
 * (PiperTTSApp); this use case is the persistence/logging front-end.
 */
class GenerateSpeechUseCase(private val database: PiperTTSDatabase) {
    suspend fun execute(text: String, voiceId: String = "hi_IN-priyamvada-medium"): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val clauses = ClauseSegmenter.segment(text)
                val script = ScriptEntity(
                    title = text.take(60),
                    body = text,
                    lang = "hi",
                    lastVoiceId = voiceId,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val id = database.scriptDao().insert(script)
                Result.success("Segmented ${clauses.size} clause(s); script $id; voice=$voiceId")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
