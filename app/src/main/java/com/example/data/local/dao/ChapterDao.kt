package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE seriesId = :seriesId ORDER BY chapterNumber ASC, firstDetectedAt ASC")
    fun getChaptersForSeries(seriesId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE seriesId = :seriesId ORDER BY chapterNumber ASC, firstDetectedAt ASC")
    suspend fun getChaptersForSeriesSync(seriesId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    suspend fun getChapterById(id: Long): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE seriesId = :seriesId AND chapterIdentifier = :identifier LIMIT 1")
    suspend fun findBySeriesAndIdentifier(seriesId: Long, identifier: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE seriesId = :seriesId AND chapterUrl = :url LIMIT 1")
    suspend fun findBySeriesAndUrl(seriesId: Long, url: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(chapter: ChapterEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(chapters: List<ChapterEntity>): List<Long>

    @Update
    suspend fun update(chapter: ChapterEntity)

    @Delete
    suspend fun delete(chapter: ChapterEntity)

    @Query("DELETE FROM chapters WHERE seriesId = :seriesId")
    suspend fun deleteForSeries(seriesId: Long)

    @Query("UPDATE chapters SET isRead = :isRead, lastReadAt = :timestamp WHERE id = :chapterId")
    suspend fun setChapterReadState(chapterId: Long, isRead: Boolean, timestamp: Long)

    @Query("UPDATE chapters SET isRead = :isRead, lastReadAt = :timestamp WHERE seriesId = :seriesId AND chapterIdentifier = :identifier")
    suspend fun updateReadStatusByIdentifier(seriesId: Long, identifier: String, isRead: Boolean, timestamp: Long)

    @Query("UPDATE chapters SET isRead = :isRead, lastReadAt = :timestamp WHERE seriesId = :seriesId")
    suspend fun setAllChaptersReadForSeries(seriesId: Long, isRead: Boolean, timestamp: Long)

    @Query("UPDATE chapters SET isDownloaded = :isDownloaded, localPath = :path WHERE id = :id")
    suspend fun updateDownloadStatus(id: Long, isDownloaded: Boolean, path: String?)

    // Distinct chapters read count to avoid double-counting
    @Query("SELECT COUNT(*) FROM chapters WHERE isRead = 1")
    fun countTotalChaptersRead(): Flow<Int>

    @Query("SELECT COUNT(*) FROM chapters WHERE isRead = 0")
    fun countUnreadChapters(): Flow<Int>

    @Query("SELECT COUNT(*) FROM chapters WHERE seriesId = :seriesId")
    fun countChaptersForSeries(seriesId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM chapters WHERE seriesId = :seriesId AND isRead = 1")
    fun countReadChaptersForSeries(seriesId: Long): Flow<Int>
}
