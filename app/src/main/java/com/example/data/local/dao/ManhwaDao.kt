package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ManhwaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManhwaDao {
    @Query("SELECT * FROM manhwa ORDER BY lastReadTimestamp DESC")
    fun getAllManhwa(): Flow<List<ManhwaEntity>>

    @Query("SELECT * FROM manhwa WHERE readingStatus = :status ORDER BY lastReadTimestamp DESC")
    fun getManhwaByStatus(status: String): Flow<List<ManhwaEntity>>

    @Query("SELECT * FROM manhwa WHERE id = :id LIMIT 1")
    fun getManhwaById(id: Long): Flow<ManhwaEntity?>

    @Query("SELECT * FROM manhwa WHERE id = :id LIMIT 1")
    suspend fun getManhwaByIdSync(id: Long): ManhwaEntity?

    @Query("SELECT * FROM manhwa WHERE sourceUrl = :url LIMIT 1")
    suspend fun findBySourceUrl(url: String): ManhwaEntity?

    @Query("SELECT * FROM manhwa WHERE title LIKE '%' || :title || '%' LIMIT 1")
    suspend fun findByTitle(title: String): ManhwaEntity?

    @Query("SELECT * FROM manhwa")
    suspend fun getAllManhwaSync(): List<ManhwaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manhwa: ManhwaEntity): Long

    @Update
    suspend fun update(manhwa: ManhwaEntity)

    @Delete
    suspend fun delete(manhwa: ManhwaEntity)

    @Query("UPDATE manhwa SET currentChapterNum = :chapterNum, lastReadTimestamp = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: Long, chapterNum: Float, timestamp: Long)

    @Query("UPDATE manhwa SET lastDetectedNewChapter = :newChapterNum, hasUnreadNewChapter = 1 WHERE id = :id")
    suspend fun setNewChapterDetected(id: Long, newChapterNum: Float)

    @Query("UPDATE manhwa SET hasUnreadNewChapter = 0 WHERE id = :id")
    suspend fun clearNewChapterNotification(id: Long)
}
