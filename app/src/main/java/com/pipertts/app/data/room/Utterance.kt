package com.pipertts.app.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "utterances",
    foreignKeys = [
        ForeignKey(
            entity = VoiceConfig::class,
            parentColumns = ["id"],
            childColumns = ["voiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Utterance(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String,
    val phonemes: String? = null,
    val audioFilePath: String? = null,
    val voiceId: Int = 0,
    val timestamp: Long = Date().time
)
