package com.piperapp.core.data.db
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
@Dao
interface RenderDao {
    @Insert suspend fun insert(r: RenderEntity): Long
    @Query("SELECT * FROM renders WHERE scriptId = :scriptId") suspend fun byScript(scriptId: Int): List<RenderEntity>
}
