package com.piperapp.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val body: String,
    val lang: String = "hi",
    val lastVoiceId: String?,
    val createdAt: Long,
    val updatedAt: Long
)
