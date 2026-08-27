package com.piperapp.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voices")
data class VoiceEntity(
    @PrimaryKey val voiceId: String,
    val name: String,
    val gender: String,
    val lang: String,
    val localPath: String,
    val sizeBytes: Long,
    val version: String,
    val status: String = "available"
)
