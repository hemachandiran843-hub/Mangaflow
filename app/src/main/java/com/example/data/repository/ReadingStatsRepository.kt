package com.example.data.repository

import com.example.data.local.dao.ReadingSessionDao
import com.example.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class ReadingStatsRepository(
    private val readingSessionDao: ReadingSessionDao
) {
    val allStats: Flow<List<ReadingSessionEntity>> = readingSessionDao.getAllSessions()
    val totalSecondsRead: Flow<Int?> = readingSessionDao.getTotalDurationSeconds()

    suspend fun recordReadingSession(seriesId: Long, durationSeconds: Int = 300, chaptersRead: Int = 1) {
        val now = System.currentTimeMillis()
        readingSessionDao.insertSession(
            ReadingSessionEntity(
                seriesId = seriesId,
                startTime = now - (durationSeconds * 1000L),
                endTime = now,
                durationSeconds = durationSeconds,
                chaptersReadCount = chaptersRead,
                dateEpochDay = LocalDate.now().toEpochDay()
            )
        )
    }
}
