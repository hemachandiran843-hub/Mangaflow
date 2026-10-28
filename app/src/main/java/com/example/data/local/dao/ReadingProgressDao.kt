package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE seriesId = :seriesId AND chapterId = :chapterId LIMIT 1")
    fun getProgress(seriesId: Long, chapterId: Long): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE seriesId = :seriesId AND chapterId = :chapterId LIMIT 1")
    suspend fun getProgressSync(seriesId: Long, chapterId: Long): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE seriesId = :seriesId ORDER BY lastReadAt DESC LIMIT 1")
    suspend fun getLatestProgressForSeries(seriesId: Long): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: ReadingProgressEntity): Long

    @Query("DELETE FROM reading_progress WHERE seriesId = :seriesId")
    suspend fun deleteForSeries(seriesId: Long)
}
