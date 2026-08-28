package com.piperapp.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VoiceEntity::class, ScriptEntity::class, RenderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PiperTTSDatabase : RoomDatabase() {
    abstract fun voiceDao(): VoiceDao
    abstract fun scriptDao(): ScriptDao
    abstract fun renderDao(): RenderDao

    companion object {
        @Volatile private var INSTANCE: PiperTTSDatabase? = null
        fun getDatabase(context: Context): PiperTTSDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PiperTTSDatabase::class.java,
                    "piper_tts_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
