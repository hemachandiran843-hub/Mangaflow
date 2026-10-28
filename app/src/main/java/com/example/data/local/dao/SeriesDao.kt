package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series ORDER BY lastReadTimestamp DESC")
    fun getAllSeries(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE readingStatus = :status ORDER BY lastReadTimestamp DESC")
    fun getSeriesByStatus(status: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    fun getSeriesById(id: Long): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    suspend fun getSeriesByIdSync(id: Long): SeriesEntity?

    @Query("SELECT * FROM series WHERE sourceUrl = :url LIMIT 1")
    suspend fun findBySourceUrl(url: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE title LIKE '%' || :title || '%' OR alternateTitles LIKE '%' || :title || '%' LIMIT 1")
    suspend fun findByTitle(title: String): SeriesEntity?

    @Query("SELECT * FROM series")
    suspend fun getAllSeriesSync(): List<SeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(series: SeriesEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<SeriesEntity>): List<Long>

    @Update
    suspend fun update(series: SeriesEntity)

    @Delete
    suspend fun delete(series: SeriesEntity)

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE series SET readingStatus = :status, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateReadingStatus(id: Long, status: String, timestamp: Long)

    @Query("UPDATE series SET lastReadChapterIdentifier = :chapterId, currentChapterNum = :chapterNum, lastReadTimestamp = :timestamp, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: Long, chapterId: String, chapterNum: Float, timestamp: Long)

    @Query("UPDATE series SET latestChapterIdentifier = :chapterId, totalChapters = :total, lastUpdated = :timestamp, hasUnreadNewChapter = :hasUnread WHERE id = :id")
    suspend fun updateLatestChapter(id: Long, chapterId: String, total: Int, timestamp: Long, hasUnread: Boolean)

    @Query("UPDATE series SET lastReadChapterIdentifier = :chapterIdentifier, currentChapterNum = :chapterNum, lastReadTimestamp = :timestamp, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateLastReadChapter(id: Long, chapterIdentifier: String, chapterNum: Float, timestamp: Long)

    @Query("UPDATE series SET lastReadChapterIdentifier = :chapterIdentifier, lastReadTimestamp = :timestamp, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateLastReadChapterIdentifierOnly(id: Long, chapterIdentifier: String, timestamp: Long)

    @Query("UPDATE series SET hasUnreadNewChapter = 1, latestChapterIdentifier = :chapterNum, lastUpdated = :timestamp WHERE id = :id")
    suspend fun setNewChapterDetected(id: Long, chapterNum: Float, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM series")
    suspend fun getAllManhwaSync(): List<SeriesEntity>

    @Query("UPDATE series SET hasUnreadNewChapter = 0 WHERE id = :id")
    suspend fun clearNewChapterNotification(id: Long)

    // Statistics queries
    @Query("SELECT COUNT(*) FROM series")
    fun countTotalSeries(): Flow<Int>

    @Query("SELECT COUNT(*) FROM series WHERE readingStatus = :status")
    fun countSeriesByStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM series WHERE readingStatus = 'READING'")
    fun countReadingSeries(): Flow<Int>

    @Query("SELECT COUNT(*) FROM series WHERE readingStatus = 'COMPLETED'")
    fun countCompletedSeries(): Flow<Int>

    @Query("SELECT COUNT(*) FROM series WHERE readingStatus = 'DROPPED'")
    fun countDroppedSeries(): Flow<Int>

    @Query("SELECT COUNT(*) FROM series WHERE readingStatus = 'ON_HOLD'")
    fun countOnHoldSeries(): Flow<Int>

    @Query("UPDATE series SET isMonitoringEnabled = :isMonitoringEnabled, notificationsEnabled = :notificationsEnabled, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateMonitoringSettings(id: Long, isMonitoringEnabled: Boolean, notificationsEnabled: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE series SET lastCheckTimestamp = :timestamp, lastCheckStatus = :status, lastCheckError = :error WHERE id = :id")
    suspend fun updateCheckStatus(id: Long, timestamp: Long, status: String, error: String?)

    @Query("SELECT * FROM series WHERE isMonitoringEnabled = 1")
    suspend fun getMonitoredSeriesSync(): List<SeriesEntity>

    @Query("DELETE FROM series")
    suspend fun clearAll()
}
