package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {
    @Query("SELECT * FROM reading_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ReadingSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSessionEntity): Long

    @Query("SELECT SUM(durationSeconds) FROM reading_sessions")
    fun getTotalDurationSeconds(): Flow<Int?>

    @Query("SELECT COUNT(DISTINCT chapterId) FROM reading_sessions WHERE startTime >= :sinceTimestamp AND chapterId IS NOT NULL")
    suspend fun getChaptersReadSince(sinceTimestamp: Long): Int

    @Query("SELECT SUM(chaptersReadCount) FROM reading_sessions WHERE dateEpochDay = :epochDay")
    suspend fun getChaptersReadOnDay(epochDay: Long): Int?

    @Query("DELETE FROM reading_sessions")
    suspend fun clearAll()
}
