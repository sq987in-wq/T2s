package com.piperapp.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "renders",
    foreignKeys = [
        ForeignKey(entity = ScriptEntity::class, parentColumns = ["id"], childColumns = ["scriptId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = VoiceEntity::class, parentColumns = ["voiceId"], childColumns = ["voiceId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class RenderEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Int = 0,
    val scriptId: Int,
    val voiceId: String,
    val speed: Float,
    val expressiveness: Float,
    val stability: Float,
    val durationMs: Long,
    val uri: String,
    val format: String,
    val createdAt: Long
)
