package com.example.data.repository

import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.SeriesDao
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.SeriesEntity
import com.example.data.tracker.ManhwaTrackerService
import kotlinx.coroutines.flow.Flow

class ManhwaRepository(
    private val seriesDao: SeriesDao,
    private val chapterDao: ChapterDao,
    val trackerService: ManhwaTrackerService
) {
    val allManhwa: Flow<List<SeriesEntity>> = seriesDao.getAllSeries()

    fun getManhwaByStatus(status: String): Flow<List<SeriesEntity>> =
        seriesDao.getSeriesByStatus(status)

    fun getManhwaById(id: Long): Flow<SeriesEntity?> =
        seriesDao.getSeriesById(id)

    suspend fun getManhwaByIdSync(id: Long): SeriesEntity? =
        seriesDao.getSeriesByIdSync(id)

    fun getChapters(seriesId: Long): Flow<List<ChapterEntity>> =
        chapterDao.getChaptersForSeries(seriesId)

    suspend fun addManhwa(manhwa: SeriesEntity): Long {
        return trackerService.addSeriesManually(
            title = manhwa.title,
            alternateTitles = manhwa.alternateTitles,
            sourceUrl = manhwa.sourceUrl,
            coverUrl = manhwa.coverUrl,
            readingStatus = manhwa.readingStatus,
            rating = manhwa.rating,
            description = manhwa.description,
            initialChapterCount = manhwa.totalChapters.coerceAtLeast(0),
            sourceId = manhwa.sourceId,
            sourceName = manhwa.sourceName
        )
    }

    suspend fun updateStatus(id: Long, newStatus: String) {
        trackerService.changeReadingStatus(id, newStatus)
    }

    suspend fun updateRatingAndNotes(id: Long, rating: Float?, notes: String) {
        val existing = seriesDao.getSeriesByIdSync(id) ?: return
        trackerService.editSeriesMetadata(
            seriesId = id,
            title = existing.title,
            alternateTitles = existing.alternateTitles,
            coverUrl = existing.coverUrl,
            readingStatus = existing.readingStatus,
            rating = rating,
            description = existing.description,
            notes = notes
        )
    }

    suspend fun markChapterRead(seriesId: Long, chapterId: Long, isRead: Boolean) {
        trackerService.markChapterReadState(seriesId, chapterId, isRead)
    }

    suspend fun markChapterReadByNumber(seriesId: Long, chapterNumber: Float, isRead: Boolean) {
        val existing = chapterDao.findBySeriesAndIdentifier(seriesId, chapterNumber.toInt().toString())
        if (existing != null) {
            trackerService.markChapterReadState(seriesId, existing.id, isRead)
        } else {
            val now = System.currentTimeMillis()
            val newId = chapterDao.insert(
                ChapterEntity(
                    seriesId = seriesId,
                    chapterNumber = chapterNumber,
                    chapterIdentifier = chapterNumber.toInt().toString(),
                    chapterTitle = "Chapter $chapterNumber",
                    chapterUrl = "",
                    isRead = isRead,
                    lastReadAt = now
                )
            )
            if (isRead && newId > 0) {
                trackerService.markChapterReadState(seriesId, newId, true)
            }
        }
    }

    suspend fun deleteManhwa(manhwa: SeriesEntity) {
        trackerService.removeSeries(manhwa.id)
    }

    suspend fun clearNewChapterNotification(id: Long) {
        seriesDao.clearNewChapterNotification(id)
    }

    suspend fun updateMonitoringSettings(id: Long, isMonitoringEnabled: Boolean, notificationsEnabled: Boolean) {
        seriesDao.updateMonitoringSettings(id, isMonitoringEnabled, notificationsEnabled)
    }
}
