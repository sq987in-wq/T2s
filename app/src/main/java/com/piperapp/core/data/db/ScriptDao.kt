package com.piperapp.core.data.db
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
@Dao
interface ScriptDao {
    @Insert suspend fun insert(s: ScriptEntity): Long
    @Query("SELECT * FROM scripts WHERE id = :id") suspend fun get(id: Int): ScriptEntity?
}
