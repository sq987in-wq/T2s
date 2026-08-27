package com.piperapp.core.data.db
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
@Dao
interface VoiceDao {
    @Insert suspend fun insert(v: VoiceEntity)
    @Query("SELECT * FROM voices WHERE voiceId = :id") suspend fun get(id: String): VoiceEntity?
}
