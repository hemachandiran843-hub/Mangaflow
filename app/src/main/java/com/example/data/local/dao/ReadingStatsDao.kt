package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ReadingStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingStatsDao {
    @Query("SELECT * FROM reading_stats ORDER BY dateEpochDay DESC")
    fun getAllStats(): Flow<List<ReadingStatsEntity>>

    @Query("SELECT * FROM reading_stats WHERE dateEpochDay = :epochDay LIMIT 1")
    suspend fun getStatsForDay(epochDay: Long): ReadingStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stats: ReadingStatsEntity)

    @Query("SELECT SUM(durationMinutes) FROM reading_stats")
    fun getTotalMinutesRead(): Flow<Int?>

    @Query("SELECT SUM(chaptersReadCount) FROM reading_stats")
    fun getTotalChaptersRead(): Flow<Int?>

    @Query("SELECT COUNT(DISTINCT dateEpochDay) FROM reading_stats WHERE durationMinutes > 0 OR chaptersReadCount > 0")
    fun getActiveDaysCount(): Flow<Int>
}
