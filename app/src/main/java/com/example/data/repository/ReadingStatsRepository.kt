package com.example.data.repository

import com.example.data.local.dao.ReadingSessionDao
import com.example.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

class ReadingStatsRepository(
    private val readingSessionDao: ReadingSessionDao
) {
    val allStats: Flow<List<ReadingSessionEntity>> = readingSessionDao.getAllSessions()
    val totalSecondsRead: Flow<Int?> = readingSessionDao.getTotalDurationSeconds()

    /** Persists an already measured interval; there are intentionally no synthetic defaults. */
    suspend fun recordReadingSession(session: ReadingSessionEntity) {
        require(session.seriesId > 0L) { "A reading session must belong to a tracked series" }
        require(session.durationSeconds >= 0) { "Session duration cannot be negative" }
        require(session.endTime >= session.startTime) { "Session end time cannot precede start time" }
        require(session.chaptersReadCount >= 0) { "Chapter count cannot be negative" }
        if (session.durationSeconds == 0 && session.chaptersReadCount == 0) return
        readingSessionDao.insertSession(session)
    }
}
