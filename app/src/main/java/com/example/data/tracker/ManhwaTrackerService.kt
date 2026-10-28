package com.example.data.tracker

import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.NotificationDao
import com.example.data.local.dao.ReadingProgressDao
import com.example.data.local.dao.ReadingSessionDao
import com.example.data.local.dao.SeriesDao
import com.example.data.local.MangaFlowDatabase
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.ReadingProgressEntity
import com.example.data.local.entity.SeriesEntity
import com.example.domain.model.DetectedManhwa
import com.example.engine.source.SourceManager
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import java.time.LocalDate
import java.time.ZoneId

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

data class PortableLibraryChapter(
    val identifier: String,
    val number: Float?,
    val title: String,
    val url: String,
    val isRead: Boolean,
    val progressPercent: Float,
    val lastReadAt: Long
)

data class PortableLibrarySeries(
    val title: String,
    val alternateTitles: String?,
    val coverUrl: String?,
    val sourceId: String,
    val sourceName: String,
    val sourceUrl: String,
    val readingStatus: String,
    val latestChapterIdentifier: String,
    val lastReadChapterIdentifier: String,
    val currentChapterNum: Float,
    val totalChapters: Int,
    val rating: Float?,
    val notes: String,
    val dateAdded: Long,
    val lastReadTimestamp: Long,
    val chapters: List<PortableLibraryChapter>
)

data class LibraryImportPreview(
    val sourceVersion: Int,
    val totalEntries: Int,
    val importableSeries: List<PortableLibrarySeries>,
    val duplicateCount: Int,
    val invalidEntries: List<String>
) {
    val importableCount: Int get() = importableSeries.size
    val chapterCount: Int get() = importableSeries.sumOf { it.chapters.size }
}

class ManhwaTrackerService(
    private val seriesDao: SeriesDao,
    private val chapterDao: ChapterDao,
    private val readingProgressDao: ReadingProgressDao,
    private val readingSessionDao: ReadingSessionDao,
    private val notificationDao: NotificationDao,
    private val database: MangaFlowDatabase? = null,
    private val sourceManager: SourceManager? = null
) {
    private val importMutex = Mutex()
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

    /** Add a library entry without inventing chapter rows or read progress. */
    suspend fun addSeriesManually(
        title: String,
        alternateTitles: String? = null,
        sourceUrl: String,
        coverUrl: String? = null,
        readingStatus: String = "READING",
        rating: Float? = null,
        description: String? = null,
        initialChapterCount: Int = 0,
        sourceId: String = "manual",
        sourceName: String = "Manual Entry"
    ): Long {
        require(title.isNotBlank()) { "Series title cannot be blank" }
        require(isHttpUrl(sourceUrl)) { "Source URL must be an HTTP(S) URL" }

        val matchedAdapter = sourceManager?.adapterForUrl(sourceUrl)
        val selectedAdapter = matchedAdapter?.takeIf {
            sourceId.equals("manual", ignoreCase = true) || it.sourceId.equals(sourceId, ignoreCase = true)
        }
        val effectiveSourceId = selectedAdapter?.sourceId ?: sourceId.takeIf { it.isNotBlank() } ?: "manual"
        val effectiveSourceName = if (sourceName == "Manual Entry" && selectedAdapter != null) {
            selectedAdapter.displayName
        } else sourceName
        val canonicalUrl = selectedAdapter?.canonicalizeSeriesUrl(sourceUrl)?.takeIf(::isHttpUrl) ?: sourceUrl.trim()
        val canonicalKey = canonicalUrl(canonicalUrl, effectiveSourceId)
        val existing = seriesDao.findBySourceUrl(sourceUrl)
            ?: seriesDao.findBySourceUrl(canonicalUrl)
            ?: seriesDao.getAllSeriesSync().firstOrNull { canonicalUrl(it.sourceUrl, it.sourceId) == canonicalKey }
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
            sourceId = effectiveSourceId,
            sourceName = effectiveSourceName,
            sourceUrl = canonicalUrl,
            readingStatus = readingStatus,
            latestChapterIdentifier = "0",
            lastReadChapterIdentifier = "0",
            currentChapterNum = 0f,
            totalChapters = initialChapterCount.coerceIn(0, MAX_KNOWN_CHAPTER_COUNT),
            rating = rating,
            description = description?.trim(),
            dateAdded = now,
            lastReadTimestamp = 0L,
            lastUpdated = now
        )
        return seriesDao.insert(newSeries)
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
        if (wasRead == isRead) return
        val now = System.currentTimeMillis()

        chapterDao.setChapterReadState(chapterId, isRead, now)

        if (isRead) {
            // Update series last read chapter
            if (chapter.chapterNumber != null) {
                seriesDao.updateLastReadChapter(
                    id = seriesId,
                    chapterIdentifier = chapter.chapterIdentifier,
                    chapterNum = chapter.chapterNumber,
                    timestamp = now
                )
            } else {
                seriesDao.updateLastReadChapterIdentifierOnly(
                    id = seriesId,
                    chapterIdentifier = chapter.chapterIdentifier,
                    timestamp = now
                )
            }

            // Explicit progress changes update chapter state only. No synthetic reading duration is recorded.
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
        require(pageIndex >= 0) { "Page index cannot be negative" }
        require(totalPages == null || totalPages > 0) { "Total pages must be positive when provided" }
        require(totalPages == null || pageIndex < totalPages) { "Page index must be less than the page count" }
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

    /** Counts only currently-read chapter records whose explicit read timestamp falls in the range. */
    suspend fun getReadChapterCountBetween(startInclusive: Long, endExclusive: Long): Int =
        chapterDao.countReadChaptersBetween(startInclusive, endExclusive)

    suspend fun getReadChapterTimestamps(): List<Long> = chapterDao.getReadChapterTimestamps()

    /** Reading totals are derived from read chapter rows and measured session durations only. */
    fun getReadingStatistics(): Flow<ReadingStatistics> = combine(
        seriesDao.getAllSeries(),
        chapterDao.countTotalChaptersRead(),
        chapterDao.countUnreadChapters(),
        readingSessionDao.getTotalDurationSeconds()
    ) { seriesList, totalReadChapters, totalUnreadChapters, totalDurationSec ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthStart = today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekChapters = chapterDao.countReadChaptersBetween(weekStart, end)
        val monthChapters = chapterDao.countReadChaptersBetween(monthStart, end)
        val totalSeconds = (totalDurationSec ?: 0).coerceAtLeast(0)
        val totalHours = String.format(Locale.ROOT, "%.1f", totalSeconds / 3600.0).toFloat()

        ReadingStatistics(
            totalSeries = seriesList.size,
            seriesReading = seriesList.count { it.readingStatus == "READING" },
            seriesCompleted = seriesList.count { it.readingStatus == "COMPLETED" },
            seriesDropped = seriesList.count { it.readingStatus == "DROPPED" },
            seriesOnHold = seriesList.count { it.readingStatus == "ON_HOLD" },
            seriesPlanToRead = seriesList.count { it.readingStatus == "PLAN_TO_READ" },
            totalChaptersRead = totalReadChapters,
            chaptersReadThisWeek = weekChapters.coerceAtLeast(0),
            chaptersReadThisMonth = monthChapters.coerceAtLeast(0),
            totalUnreadChapters = totalUnreadChapters,
            totalHoursRead = totalHours
        )
    }

    suspend fun findMatchingSeries(detected: DetectedManhwa): SeriesEntity? {
        val canonicalDetected = sourceManager?.canonicalizeSeriesUrl(detected.sourceUrl, detected.sourceId)
            ?: canonicalUrl(detected.sourceUrl, detected.sourceId)
        seriesDao.findBySourceUrl(canonicalDetected)?.let { return it }
        val detectedHost = hostOf(canonicalDetected)
        val detectedTitle = normalizedTitle(detected.title)
        return seriesDao.getAllSeriesSync().firstOrNull { series ->
            val seriesCanonical = sourceManager?.canonicalizeSeriesUrl(series.sourceUrl, series.sourceId)
                ?: canonicalUrl(series.sourceUrl, series.sourceId)
            seriesCanonical == canonicalDetected ||
                (detectedHost != null && hostOf(seriesCanonical) == detectedHost &&
                    (normalizedTitle(series.title) == detectedTitle ||
                        series.alternateTitles.orEmpty().split(',', ';', '|').any { normalizedTitle(it) == detectedTitle }))
        }
    }

    suspend fun findChapterIdForSession(seriesId: Long, identifier: String, chapterUrl: String): Long? {
        chapterDao.findBySeriesAndIdentifier(seriesId, identifier)?.let { return it.id }
        if (isHttpUrl(chapterUrl)) chapterDao.findBySeriesAndUrl(seriesId, chapterUrl)?.let { return it.id }
        return null
    }

    /** Progress is recorded only after an explicit user confirmation in the browser. */
    suspend fun markDetectedChapterRead(seriesId: Long, detected: DetectedManhwa) {
        require(detected.chapterNumber == null || detected.chapterNumber.isFinite() && detected.chapterNumber >= 0f) {
            "Detected chapter number is invalid"
        }
        val identifier = detected.chapterIdentifier.trim()
        require(identifier.isNotBlank() && identifier.length <= MAX_CHAPTER_IDENTIFIER_LENGTH) { "Invalid detected chapter identifier" }
        val chapterUrl = detected.chapterUrl.takeIf(::isHttpUrl)
            ?: detected.sourceUrl.takeIf(::isHttpUrl)
            ?: throw IllegalArgumentException("Detected chapter has no valid HTTP(S) URL")
        val title = detected.chapterTitle.trim().ifBlank { "Chapter $identifier" }.take(MAX_CHAPTER_TITLE_LENGTH)
        val operation: suspend () -> Unit = {
            upsertReadChapter(seriesId, identifier, detected.chapterNumber, title, chapterUrl)
        }
        if (database != null) database.withTransaction { operation() } else operation()
    }

    /** Manual progress path for unsupported websites; no source release checks are implied. */
    suspend fun markManualChapterRead(
        seriesId: Long,
        identifier: String,
        title: String = "",
        chapterUrl: String? = null
    ) {
        val cleanIdentifier = identifier.trim()
        require(cleanIdentifier.isNotBlank() && cleanIdentifier.length <= MAX_CHAPTER_IDENTIFIER_LENGTH) {
            "Enter a chapter number or stable identifier"
        }
        val series = seriesDao.getSeriesByIdSync(seriesId)
            ?: throw IllegalArgumentException("Series $seriesId is no longer in the library")
        val number = cleanIdentifier.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
        val cleanTitle = title.trim().ifBlank { "Chapter $cleanIdentifier" }.take(MAX_CHAPTER_TITLE_LENGTH)
        val url = chapterUrl?.trim()?.takeIf(String::isNotBlank)?.also {
            require(isHttpUrl(it)) { "Chapter URL must be HTTP(S)" }
        } ?: series.sourceUrl
        val operation: suspend () -> Unit = {
            upsertReadChapter(seriesId, cleanIdentifier, number, cleanTitle, url)
        }
        if (database != null) database.withTransaction { operation() } else operation()
    }

    private suspend fun upsertReadChapter(
        seriesId: Long,
        identifier: String,
        number: Float?,
        title: String,
        chapterUrl: String
    ) {
        val series = seriesDao.getSeriesByIdSync(seriesId)
            ?: throw IllegalArgumentException("Series $seriesId is no longer in the library")
        val previous = chapterDao.findBySeriesAndIdentifier(seriesId, identifier)
            ?: chapterUrl.takeUnless { it == series.sourceUrl }?.let { chapterDao.findBySeriesAndUrl(seriesId, it) }
        if (previous?.isRead == true && previous.chapterIdentifier == identifier) return
        val now = System.currentTimeMillis()
        if (previous != null) {
            chapterDao.update(
                previous.copy(
                    chapterNumber = number ?: previous.chapterNumber,
                    chapterIdentifier = identifier,
                    chapterTitle = title,
                    chapterUrl = chapterUrl,
                    isRead = true,
                    lastReadAt = now
                )
            )
        } else {
            chapterDao.insert(
                ChapterEntity(
                    seriesId = seriesId,
                    chapterNumber = number,
                    chapterIdentifier = identifier,
                    chapterTitle = title,
                    chapterUrl = chapterUrl,
                    isRead = true,
                    firstDetectedAt = now,
                    lastReadAt = now
                )
            )
        }
        val chapterCount = chapterDao.getChaptersForSeriesSync(seriesId).size
        seriesDao.update(
            series.copy(
                lastReadChapterIdentifier = identifier,
                currentChapterNum = number ?: series.currentChapterNum,
                totalChapters = maxOf(series.totalChapters, chapterCount),
                lastReadTimestamp = now,
                lastUpdated = now
            )
        )
    }

    /** Exports library metadata/progress only; page content, browser history, and private data are excluded. */
    suspend fun exportLibraryJson(): String {
        val root = JSONObject().apply {
            put("format", IMPORT_FORMAT)
            put("version", EXPORT_VERSION)
            put("exportedAt", System.currentTimeMillis())
        }
        val seriesArray = JSONArray()
        for (series in seriesDao.getAllSeriesSync()) {
            val seriesObject = JSONObject().apply {
                put("title", series.title)
                put("alternateTitles", series.alternateTitles ?: JSONObject.NULL)
                put("coverUrl", series.coverUrl ?: JSONObject.NULL)
                put("sourceId", series.sourceId)
                put("sourceName", series.sourceName)
                put("sourceUrl", series.sourceUrl)
                put("readingStatus", series.readingStatus)
                put("rating", series.rating ?: JSONObject.NULL)
                put("notes", series.notes)
                put("latestChapterIdentifier", series.latestChapterIdentifier)
                put("lastReadChapterIdentifier", series.lastReadChapterIdentifier)
                put("currentChapterNum", series.currentChapterNum)
                put("totalChapters", series.totalChapters)
                put("dateAdded", series.dateAdded)
                put("lastReadTimestamp", series.lastReadTimestamp)
            }
            val chapterArray = JSONArray()
            for (chapter in chapterDao.getChaptersForSeriesSync(series.id)) {
                chapterArray.put(JSONObject().apply {
                    put("identifier", chapter.chapterIdentifier)
                    put("number", chapter.chapterNumber ?: JSONObject.NULL)
                    put("title", chapter.chapterTitle)
                    put("url", chapter.chapterUrl)
                    put("isRead", chapter.isRead)
                    put("progressPercent", chapter.readProgressPercent)
                    put("lastReadAt", chapter.lastReadAt)
                })
            }
            seriesObject.put("chapters", chapterArray)
            seriesArray.put(seriesObject)
        }
        root.put("series", seriesArray)
        return root.toString(2)
    }

    /** Parses and validates the entire portable file without changing the database. */
    suspend fun previewLibraryJson(jsonString: String): Result<LibraryImportPreview> = runCatching {
        require(jsonString.length <= MAX_IMPORT_CHARACTERS) { "Import file is too large" }
        val root = JSONObject(jsonString)
        val format = root.optString("format", "")
        require(format.isBlank() || format == IMPORT_FORMAT) { "This is not a MangaFlow library export" }
        val version = root.optInt("version", 0)
        require(version in 2..EXPORT_VERSION) { "Unsupported library export version: $version" }
        val seriesArray = root.optJSONArray("series") ?: throw IllegalArgumentException("Missing series list")
        require(seriesArray.length() <= MAX_IMPORT_SERIES) { "Import contains too many series" }

        val existing = seriesDao.getAllSeriesSync()
        val knownUrls = existing.mapTo(mutableSetOf()) { canonicalUrl(it.sourceUrl) }
        val knownTitles = existing.flatMapTo(mutableSetOf()) { series ->
            buildList {
                add(normalizedTitle(series.title))
                series.alternateTitles.orEmpty().split(',', ';', '|').mapTo(this) { normalizedTitle(it) }
            }
        }
        val seenUrls = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()
        val valid = mutableListOf<PortableLibrarySeries>()
        val errors = mutableListOf<String>()
        var duplicates = 0

        for (index in 0 until seriesArray.length()) {
            val obj = seriesArray.optJSONObject(index)
            if (obj == null) {
                errors += "Entry ${index + 1}: expected a series object"
                continue
            }
            val item = try {
                parsePortableSeries(obj)
            } catch (e: Exception) {
                errors += "Entry ${index + 1}: ${e.message ?: "invalid metadata"}"
                continue
            }
            val urlKey = canonicalUrl(item.sourceUrl)
            val titleKey = normalizedTitle(item.title)
            if (urlKey in knownUrls || titleKey in knownTitles || urlKey in seenUrls || titleKey in seenTitles) {
                duplicates++
            } else {
                valid += item
                seenUrls += urlKey
                seenTitles += titleKey
            }
        }
        LibraryImportPreview(
            sourceVersion = version,
            totalEntries = seriesArray.length(),
            importableSeries = valid,
            duplicateCount = duplicates,
            invalidEntries = errors.take(MAX_PREVIEW_ERRORS)
        )
    }

    /** Applies only validated new entries, never overwriting duplicates or deleting existing data. */
    suspend fun applyLibraryImport(preview: LibraryImportPreview): Result<Int> = runCatching {
        importMutex.withLock {
            val apply: suspend () -> Int = {
                val existing = seriesDao.getAllSeriesSync()
                val knownUrls = existing.mapTo(mutableSetOf()) { canonicalUrl(it.sourceUrl) }
                val knownTitles = existing.mapTo(mutableSetOf()) { normalizedTitle(it.title) }
                var imported = 0
                for (item in preview.importableSeries) {
                    val urlKey = canonicalUrl(item.sourceUrl)
                    val titleKey = normalizedTitle(item.title)
                    if (urlKey in knownUrls || titleKey in knownTitles) continue
                    val now = System.currentTimeMillis()
                    val id = seriesDao.insert(
                        SeriesEntity(
                            title = item.title,
                            alternateTitles = item.alternateTitles,
                            coverUrl = item.coverUrl,
                            sourceId = item.sourceId,
                            sourceName = item.sourceName,
                            sourceUrl = item.sourceUrl,
                            readingStatus = item.readingStatus,
                            latestChapterIdentifier = item.latestChapterIdentifier,
                            lastReadChapterIdentifier = item.lastReadChapterIdentifier,
                            currentChapterNum = item.currentChapterNum,
                            totalChapters = maxOf(item.totalChapters, item.chapters.size),
                            rating = item.rating,
                            notes = item.notes,
                            dateAdded = item.dateAdded.takeIf { it > 0L } ?: now,
                            lastReadTimestamp = item.lastReadTimestamp.coerceAtLeast(0L),
                            lastUpdated = now
                        )
                    )
                    val chapters = item.chapters.map { chapter ->
                        ChapterEntity(
                            seriesId = id,
                            chapterNumber = chapter.number,
                            chapterIdentifier = chapter.identifier,
                            chapterTitle = chapter.title,
                            chapterUrl = chapter.url,
                            isRead = chapter.isRead,
                            readProgressPercent = chapter.progressPercent,
                            firstDetectedAt = now,
                            lastReadAt = chapter.lastReadAt.coerceAtLeast(0L)
                        )
                    }
                    if (chapters.isNotEmpty()) chapterDao.insertAll(chapters)
                    knownUrls += urlKey
                    knownTitles += titleKey
                    imported++
                }
                imported
            }
            if (database != null) database.withTransaction { apply() } else apply()
        }
    }

    /** Backwards-compatible safe import entry point; preview/apply is preferred by the UI. */
    suspend fun importLibraryJson(jsonString: String): Result<Int> {
        val preview = previewLibraryJson(jsonString)
        if (preview.isFailure) return Result.failure(preview.exceptionOrNull()!!)
        return applyLibraryImport(preview.getOrThrow())
    }

    private fun parsePortableSeries(obj: JSONObject): PortableLibrarySeries {
        val title = obj.optString("title").trim()
        require(title.isNotBlank() && title.length <= 300) { "title is missing or too long" }
        val sourceUrl = obj.optString("sourceUrl").trim()
        require(isHttpUrl(sourceUrl)) { "source URL must be HTTP or HTTPS" }
        val status = obj.optString("readingStatus", "READING").uppercase()
        require(status in ALLOWED_READING_STATUSES) { "invalid reading status" }
        val rating = if (!obj.has("rating") || obj.isNull("rating")) null else obj.optDouble("rating", Double.NaN).toFloat()
        require(rating == null || rating.isFinite() && rating in 0f..5f) { "rating must be between 0 and 5" }
        val cover = optionalHttpUrl(obj.optString("coverUrl", ""), "cover URL")
        val chapterArray = obj.optJSONArray("chapters") ?: JSONArray()
        require(chapterArray.length() <= MAX_CHAPTERS_PER_SERIES) { "too many chapters" }
        val chapterList = mutableListOf<PortableLibraryChapter>()
        val seenIdentifiers = mutableSetOf<String>()
        for (index in 0 until chapterArray.length()) {
            val chapter = chapterArray.optJSONObject(index) ?: throw IllegalArgumentException("chapter ${index + 1} is malformed")
            val identifier = chapter.optString("identifier").trim()
            require(identifier.isNotBlank() && identifier.length <= 120) { "chapter identifier is missing or too long" }
            if (!seenIdentifiers.add(identifier)) continue
            val url = chapter.optString("url", sourceUrl).ifBlank { sourceUrl }
            require(isHttpUrl(url)) { "chapter URL must be HTTP or HTTPS" }
            val numberValue = if (!chapter.has("number") || chapter.isNull("number")) null else chapter.optDouble("number", Double.NaN)
            require(numberValue == null || numberValue.isFinite() && numberValue in 0.0..100_000_000.0) { "invalid chapter number" }
            val progress = chapter.optDouble("progressPercent", 0.0).toFloat()
            require(progress.isFinite() && progress in 0f..1f) { "chapter progress must be between 0 and 1" }
            val chapterTitle = chapter.optString("title", "Chapter $identifier").take(500)
            chapterList += PortableLibraryChapter(
                identifier = identifier,
                number = numberValue?.toFloat(),
                title = chapterTitle,
                url = url,
                isRead = chapter.optBoolean("isRead", false),
                progressPercent = progress,
                lastReadAt = chapter.optLong("lastReadAt", 0L).coerceAtLeast(0L)
            )
        }
        val currentChapter = obj.optDouble("currentChapterNum", 0.0).toFloat()
        require(currentChapter.isFinite() && currentChapter in 0f..100_000_000f) { "invalid progress value" }
        val totalChapters = obj.optInt("totalChapters", chapterList.size).coerceIn(0, MAX_CHAPTERS_PER_SERIES)
        val notes = obj.optString("notes", "")
        require(notes.length <= MAX_NOTES_CHARACTERS) { "notes are too long" }
        return PortableLibrarySeries(
            title = title,
            alternateTitles = optionalText(obj, "alternateTitles", 1_000),
            coverUrl = cover,
            sourceId = obj.optString("sourceId", "web").take(120),
            sourceName = obj.optString("sourceName", "Web Source").take(200),
            sourceUrl = sourceUrl,
            readingStatus = status,
            latestChapterIdentifier = obj.optString("latestChapterIdentifier", "0").take(120),
            lastReadChapterIdentifier = obj.optString("lastReadChapterIdentifier", "0").take(120),
            currentChapterNum = currentChapter,
            totalChapters = totalChapters,
            rating = rating,
            notes = notes,
            dateAdded = obj.optLong("dateAdded", System.currentTimeMillis()).coerceAtLeast(0L),
            lastReadTimestamp = obj.optLong("lastReadTimestamp", 0L).coerceAtLeast(0L),
            chapters = chapterList
        )
    }

    private fun optionalText(obj: JSONObject, key: String, maxLength: Int): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optString(key).trim()
        require(value.length <= maxLength) { "$key is too long" }
        return value.takeIf(String::isNotBlank)
    }

    private fun optionalHttpUrl(value: String, label: String): String? {
        if (value.isBlank() || value == "null") return null
        require(isHttpUrl(value)) { "$label must be HTTP or HTTPS" }
        return value
    }

    private fun isHttpUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) {
        false
    }

    private fun canonicalUrl(value: String, preferredSourceId: String? = null): String =
        sourceManager?.canonicalizeSeriesUrl(value, preferredSourceId)
            ?: try {
                val uri = URI(value).normalize()
                "${uri.scheme?.lowercase()}://${uri.host?.lowercase()}${uri.path.orEmpty().trimEnd('/')}" +
                    (uri.query?.let { "?$it" } ?: "")
            } catch (_: Exception) {
                value.trim().trimEnd('/').lowercase()
            }

    private fun hostOf(value: String): String? = try {
        URI(value).host?.lowercase()?.removePrefix("www.")
    } catch (_: Exception) {
        null
    }

    private fun normalizedTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

    companion object {
        private const val IMPORT_FORMAT = "mangaflow-library"
        private const val EXPORT_VERSION = 3
        private const val MAX_IMPORT_CHARACTERS = 8 * 1024 * 1024
        private const val MAX_IMPORT_SERIES = 2_000
        private const val MAX_CHAPTERS_PER_SERIES = 10_000
        private const val MAX_PREVIEW_ERRORS = 100
        private const val MAX_NOTES_CHARACTERS = 10_000
        private const val MAX_KNOWN_CHAPTER_COUNT = 100_000
        private const val MAX_CHAPTER_IDENTIFIER_LENGTH = 200
        private const val MAX_CHAPTER_TITLE_LENGTH = 500
        private val ALLOWED_READING_STATUSES = setOf("READING", "PLAN_TO_READ", "COMPLETED", "ON_HOLD", "DROPPED")
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
