package com.pipertts.app.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TTSDao {
    @Insert
    suspend fun insertVoice(config: VoiceConfig): Long

    @Insert
    suspend fun insertUtterance(utterance: Utterance): Long

    @Query("SELECT * FROM voice_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultVoice(): VoiceConfig?

    @Query("SELECT * FROM utterances WHERE voiceId = :voiceId ORDER BY timestamp DESC LIMIT 50")
    suspend fun getUtterancesForVoice(voiceId: Int): List<Utterance>

    @Query("DELETE FROM utterances WHERE id = :id")
    suspend fun deleteUtterance(id: Int)
}
