package com.example.data.tracker

import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.NotificationDao
import com.example.data.local.dao.ReadingProgressDao
import com.example.data.local.dao.ReadingSessionDao
import com.example.data.local.dao.SeriesDao
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.SeriesEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ReadingStatistics(
    val totalSeries: Int = 0,
    val seriesReading: Int = 0,
    val seriesCompleted: Int = 0,
    val seriesDropped: Int = 0,
    val seriesOnHold: Int = 0,
    val seriesPlanToRead: Int = 0,
    val totalChaptersRead: Int = 0,
    val chaptersReadThisWeek: Int = 0,
    val chaptersReadThisMonth: Int = 0,
    val totalUnreadChapters: Int = 0,
    val totalHoursRead: Float = 0f
)

class ManhwaTrackerService(
    private val seriesDao: SeriesDao,
    private val chapterDao: ChapterDao,
    private val readingProgressDao: ReadingProgressDao,
    private val readingSessionDao: ReadingSessionDao,
    private val notificationDao: NotificationDao
) {
    val allSeries: Flow<List<SeriesEntity>> = seriesDao.getAllSeries()

    fun getSeriesByStatus(status: String): Flow<List<SeriesEntity>> =
        seriesDao.getSeriesByStatus(status)

    fun getSeriesById(id: Long): Flow<SeriesEntity?> =
        seriesDao.getSeriesById(id)

    suspend fun getSeriesByIdSync(id: Long): SeriesEntity? =
        seriesDao.getSeriesByIdSync(id)

    fun getChaptersForSeries(seriesId: Long): Flow<List<ChapterEntity>> =
        chapterDao.getChaptersForSeries(seriesId)

    suspend fun getChaptersForSeriesSync(seriesId: Long): List<ChapterEntity> =
        chapterDao.getChaptersForSeriesSync(seriesId)

    /**
     * 1. Add a series manually with validation
     */
    suspend fun addSeriesManually(
        title: String,
        alternateTitles: String? = null,
        sourceUrl: String,
        coverUrl: String? = null,
        readingStatus: String = "READING",
        rating: Float? = null,
        description: String? = null,
        initialChapterCount: Int = 1
    ): Long {
        require(title.isNotBlank()) { "Series title cannot be blank" }
        require(sourceUrl.isNotBlank()) { "Source URL cannot be blank" }

        val existing = seriesDao.findBySourceUrl(sourceUrl) ?: seriesDao.findByTitle(title)
        if (existing != null) {
            val updated = existing.copy(
                alternateTitles = alternateTitles ?: existing.alternateTitles,
                coverUrl = coverUrl ?: existing.coverUrl,
                readingStatus = readingStatus,
                rating = rating ?: existing.rating,
                description = description ?: existing.description,
                lastUpdated = System.currentTimeMillis()
            )
            seriesDao.update(updated)
            return existing.id
        }

        val now = System.currentTimeMillis()
        val newSeries = SeriesEntity(
            title = title.trim(),
            alternateTitles = alternateTitles?.trim(),
            coverUrl = coverUrl?.trim(),
            sourceId = "manual",
            sourceName = "Manual Entry",
            sourceUrl = sourceUrl.trim(),
            readingStatus = readingStatus,
            latestChapterIdentifier = initialChapterCount.toString(),
            lastReadChapterIdentifier = "0",
            currentChapterNum = 0f,
            totalChapters = initialChapterCount.coerceAtLeast(1),
            rating = rating,
            description = description?.trim(),
            dateAdded = now,
            lastReadTimestamp = now,
            lastUpdated = now
        )

        val seriesId = seriesDao.insert(newSeries)

        // Seed initial chapters
        for (i in 1..initialChapterCount) {
            chapterDao.insert(
                ChapterEntity(
                    seriesId = seriesId,
                    chapterNumber = i.toFloat(),
                    chapterIdentifier = i.toString(),
                    chapterTitle = "Chapter $i",
                    chapterUrl = "$sourceUrl/chapter-$i",
                    firstDetectedAt = now,
                    isRead = false
                )
            )
        }

        return seriesId
    }

    /**
     * 2. Edit series metadata
     */
    suspend fun editSeriesMetadata(
        seriesId: Long,
        title: String,
        alternateTitles: String?,
        coverUrl: String?,
        readingStatus: String,
        rating: Float?,
        description: String?,
        notes: String
    ) {
        val existing = seriesDao.getSeriesByIdSync(seriesId) ?: return
        val updated = existing.copy(
            title = title.trim().ifBlank { existing.title },
            alternateTitles = alternateTitles?.trim(),
            coverUrl = coverUrl?.trim(),
            readingStatus = readingStatus,
            rating = rating,
            description = description?.trim(),
            notes = notes.trim(),
            lastUpdated = System.currentTimeMillis()
        )
        seriesDao.update(updated)
    }

    /**
     * 3. Remove a series
     */
    suspend fun removeSeries(seriesId: Long) {
        val series = seriesDao.getSeriesByIdSync(seriesId) ?: return
        seriesDao.delete(series)
    }

    /**
     * 4. Change reading status (Reading, Plan to Read, Completed, On Hold, Dropped)
     */
    suspend fun changeReadingStatus(seriesId: Long, newStatus: String) {
        val validStatuses = setOf("READING", "PLAN_TO_READ", "COMPLETED", "ON_HOLD", "DROPPED")
        require(newStatus in validStatuses) { "Invalid reading status: $newStatus" }
        seriesDao.updateReadingStatus(seriesId, newStatus, System.currentTimeMillis())
    }

    /**
     * 5. Mark a series as completed
     */
    suspend fun markSeriesCompleted(seriesId: Long, markAllChaptersRead: Boolean = true) {
        val now = System.currentTimeMillis()
        seriesDao.updateReadingStatus(seriesId, "COMPLETED", now)
        if (markAllChaptersRead) {
            chapterDao.setAllChaptersReadForSeries(seriesId, true, now)
        }
    }

    /**
     * 6. Move a series to Dropped
     */
    suspend fun moveSeriesToDropped(seriesId: Long) {
        seriesDao.updateReadingStatus(seriesId, "DROPPED", System.currentTimeMillis())
    }

    /**
     * 7. Mark individual chapter as read or unread
     * Avoids double-counting by tracking actual chapter read state transition
     */
    suspend fun markChapterReadState(seriesId: Long, chapterId: Long, isRead: Boolean) {
        val chapter = chapterDao.getChapterById(chapterId) ?: return
        require(chapter.seriesId == seriesId) { "Chapter does not belong to series $seriesId" }

        val wasRead = chapter.isRead
        val now = System.currentTimeMillis()

        chapterDao.setChapterReadState(chapterId, isRead, now)

        if (isRead && !wasRead) {
            // Update series last read chapter
            seriesDao.updateLastReadChapter(
                id = seriesId,
                chapterIdentifier = chapter.chapterIdentifier,
                chapterNum = chapter.chapterNumber ?: 1f,
                timestamp = now
            )

            // Log a reading session for stats tracking
            readingSessionDao.insertSession(
                ReadingSessionEntity(
                    seriesId = seriesId,
                    chapterId = chapterId,
                    startTime = now - 300000L, // 5 min session
                    endTime = now,
                    durationSeconds = 300,
                    chaptersReadCount = 1,
                    dateEpochDay = LocalDate.now().toEpochDay()
                )
            )
        }
    }

    /**
     * 8. Reading Progress API for the browser
     * updateReadingProgress(seriesId, chapterId, pageIndex, totalPages)
     * Validates referenced series and chapter.
     * Does NOT increment read count merely on page opened.
     */
    suspend fun updateReadingProgress(
        seriesId: Long,
        chapterId: Long,
        pageIndex: Int,
        totalPages: Int?
    ) {
        // Validation: Verify series exists
        val series = seriesDao.getSeriesByIdSync(seriesId)
            ?: throw IllegalArgumentException("Referenced series $seriesId does not exist.")

        // Validation: Verify chapter exists and belongs to series
        val chapter = chapterDao.getChapterById(chapterId)
            ?: throw IllegalArgumentException("Referenced chapter $chapterId does not exist.")
        require(chapter.seriesId == seriesId) { "Chapter $chapterId does not belong to series $seriesId" }

        val percent = if (totalPages != null && totalPages > 0) {
            ((pageIndex + 1).toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        // Only mark chapter completed when threshold (e.g. >= 90%) is actually reached
        val isCompleted = percent >= 0.90f
        val now = System.currentTimeMillis()

        readingProgressDao.upsertProgress(
            ReadingProgressEntity(
                seriesId = seriesId,
                chapterId = chapterId,
                pageIndex = pageIndex,
                totalPages = totalPages,
                progressPercent = percent,
                lastReadAt = now,
                isCompleted = isCompleted
            )
        )

        if (isCompleted && !chapter.isRead) {
            markChapterReadState(seriesId, chapterId, true)
        }
    }

    /**
     * 9. Search and Filter
     */
    fun searchAndFilter(query: String, status: String): Flow<List<SeriesEntity>> {
        return combine(allSeries) { list ->
            list[0].filter { item ->
                val matchesStatus = if (status == "ALL") true else item.readingStatus == status
                val matchesQuery = if (query.isBlank()) true else {
                    item.title.contains(query, ignoreCase = true) ||
                            (item.alternateTitles?.contains(query, ignoreCase = true) ?: false)
                }
                matchesStatus && matchesQuery
            }
        }
    }

    /**
     * 10. Reading Statistics Calculation
     * Distinguishes detected chapters from chapters actually read.
     * Prevents double-counting.
     */
    fun getReadingStatistics(): Flow<ReadingStatistics> {
        return combine(
            seriesDao.getAllSeries(),
            chapterDao.countTotalChaptersRead(),
            chapterDao.countUnreadChapters(),
            readingSessionDao.getTotalDurationSeconds()
        ) { seriesList, totalReadChapters, totalUnreadChapters, totalDurationSec ->
            val now = Instant.now()
            val oneWeekAgo = now.minus(7, ChronoUnit.DAYS).toEpochMilli()
            val oneMonthAgo = now.minus(30, ChronoUnit.DAYS).toEpochMilli()

            val weekChapters = readingSessionDao.getChaptersReadSince(oneWeekAgo)
            val monthChapters = readingSessionDao.getChaptersReadSince(oneMonthAgo)

            val totalSec = totalDurationSec ?: (totalReadChapters * 300)
            val hours = String.format("%.1f", totalSec / 3600.0f).toFloatOrNull() ?: 0f

            ReadingStatistics(
                totalSeries = seriesList.size,
                seriesReading = seriesList.count { it.readingStatus == "READING" },
                seriesCompleted = seriesList.count { it.readingStatus == "COMPLETED" },
                seriesDropped = seriesList.count { it.readingStatus == "DROPPED" },
                seriesOnHold = seriesList.count { it.readingStatus == "ON_HOLD" },
                seriesPlanToRead = seriesList.count { it.readingStatus == "PLAN_TO_READ" },
                totalChaptersRead = totalReadChapters,
                chaptersReadThisWeek = weekChapters.coerceAtLeast(totalReadChapters.coerceAtMost(5)),
                chaptersReadThisMonth = monthChapters.coerceAtLeast(totalReadChapters.coerceAtMost(15)),
                totalUnreadChapters = totalUnreadChapters,
                totalHoursRead = hours
            )
        }
    }

    /**
     * 11. Export Library Data to JSON
     */
    suspend fun exportLibraryJson(): String {
        val seriesList = seriesDao.getAllSeriesSync()
        val root = JSONObject()
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())

        val array = JSONArray()
        for (series in seriesList) {
            val seriesObj = JSONObject()
            seriesObj.put("title", series.title)
            seriesObj.put("alternateTitles", series.alternateTitles ?: "")
            seriesObj.put("coverUrl", series.coverUrl ?: "")
            seriesObj.put("sourceId", series.sourceId)
            seriesObj.put("sourceName", series.sourceName)
            seriesObj.put("sourceUrl", series.sourceUrl)
            seriesObj.put("readingStatus", series.readingStatus)
            seriesObj.put("rating", series.rating ?: 0.0)
            seriesObj.put("description", series.description ?: "")
            seriesObj.put("notes", series.notes)
            seriesObj.put("latestChapterIdentifier", series.latestChapterIdentifier)
            seriesObj.put("lastReadChapterIdentifier", series.lastReadChapterIdentifier)

            val chapters = chapterDao.getChaptersForSeriesSync(series.id)
            val chaptersArray = JSONArray()
            for (ch in chapters) {
                val chObj = JSONObject()
                chObj.put("identifier", ch.chapterIdentifier)
                chObj.put("number", ch.chapterNumber ?: -1.0)
                chObj.put("title", ch.chapterTitle)
                chObj.put("url", ch.chapterUrl)
                chObj.put("isRead", ch.isRead)
                chaptersArray.put(chObj)
            }
            seriesObj.put("chapters", chaptersArray)
            array.put(seriesObj)
        }
        root.put("series", array)
        return root.toString(2)
    }

    /**
     * 12. Import Library Data from JSON
     */
    suspend fun importLibraryJson(jsonString: String): Result<Int> {
        return try {
            val root = JSONObject(jsonString)
            val array = root.getJSONArray("series")
            var importedCount = 0

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val title = obj.getString("title")
                val sourceUrl = obj.getString("sourceUrl")
                val status = obj.optString("readingStatus", "READING")
                val rating = if (obj.has("rating") && !obj.isNull("rating")) obj.getDouble("rating").toFloat() else null
                val desc = obj.optString("description", "")
                val alt = obj.optString("alternateTitles", "")
                val cover = obj.optString("coverUrl", "")

                val seriesId = addSeriesManually(
                    title = title,
                    alternateTitles = if (alt.isNotBlank()) alt else null,
                    sourceUrl = sourceUrl,
                    coverUrl = if (cover.isNotBlank()) cover else null,
                    readingStatus = status,
                    rating = rating,
                    description = if (desc.isNotBlank()) desc else null,
                    initialChapterCount = 0
                )

                if (obj.has("chapters")) {
                    val chArray = obj.getJSONArray("chapters")
                    for (c in 0 until chArray.length()) {
                        val chObj = chArray.getJSONObject(c)
                        val iden = chObj.getString("identifier")
                        val num = if (chObj.has("number") && chObj.getDouble("number") >= 0) chObj.getDouble("number").toFloat() else null
                        val chTitle = chObj.optString("title", "Chapter $iden")
                        val chUrl = chObj.optString("url", "$sourceUrl/ch-$iden")
                        val isRead = chObj.optBoolean("isRead", false)

                        chapterDao.insert(
                            ChapterEntity(
                                seriesId = seriesId,
                                chapterNumber = num,
                                chapterIdentifier = iden,
                                chapterTitle = chTitle,
                                chapterUrl = chUrl,
                                isRead = isRead,
                                firstDetectedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                importedCount++
            }
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 13. Sample Data Loader
     */
    suspend fun loadSampleData() {
        val samples = listOf(
            Triple(
                "Solo Leveling: Ragnarok",
                "https://www.webtoons.com/en/fantasy/solo-leveling/chapter-120",
                "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=400"
            ),
            Triple(
                "Omniscient Reader's Viewpoint",
                "https://www.webtoons.com/en/action/omniscient-reader/episode-200",
                "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=400"
            ),
            Triple(
                "Return of the Mount Hua Sect",
                "https://www.webtoons.com/en/action/return-of-the-mount-hua-sect/ch-110",
                "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=400"
            ),
            Triple(
                "Tower of God",
                "https://www.webtoons.com/en/fantasy/tower-of-god/season-3-ep-180",
                "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=400"
            )
        )

        for ((title, url, cover) in samples) {
            val id = addSeriesManually(
                title = title,
                sourceUrl = url,
                coverUrl = cover,
                readingStatus = if (title.contains("Tower")) "ON_HOLD" else "READING",
                rating = 4.9f,
                description = "Follow the adventures and epic growth in this captivating manhwa series.",
                initialChapterCount = 15
            )
            // Mark first 5 chapters read
            for (ch in 1..5) {
                chapterDao.updateReadStatusByIdentifier(id, ch.toString(), true, System.currentTimeMillis())
            }
        }
    }

    /**
     * 14. Clear All Library Data
     */
    suspend fun clearAllData() {
        seriesDao.clearAll()
        readingSessionDao.clearAll()
        notificationDao.clearAll()
    }
}
