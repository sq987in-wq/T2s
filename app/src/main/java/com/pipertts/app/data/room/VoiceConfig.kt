package com.pipertts.app.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_configs")
data class VoiceConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val modelPath: String,
    val isDefault: Boolean = false,
    val languageCode: String = "en-US"
)
