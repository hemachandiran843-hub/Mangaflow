package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ContentSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentSourceDao {
    @Query("SELECT * FROM content_sources ORDER BY displayName ASC")
    fun getAllSources(): Flow<List<ContentSourceEntity>>

    @Query("SELECT * FROM content_sources WHERE isEnabled = 1")
    suspend fun getEnabledSources(): List<ContentSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<ContentSourceEntity>)

    @Query("UPDATE content_sources SET isEnabled = :isEnabled WHERE sourceId = :sourceId")
    suspend fun setSourceEnabled(sourceId: String, isEnabled: Boolean)
}
