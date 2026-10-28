package com.example.engine.source

import android.content.Context
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.NotificationDao
import com.example.data.local.dao.SeriesDao
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.SeriesEntity
import com.example.engine.privacy.PrivacyManager
import com.example.engine.worker.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Result of checking a series for new chapters.
 */
data class DetectionResult(
    val seriesId: Long,
    val seriesTitle: String,
    val status: String, // "SUCCESS", "NO_NEW_CHAPTERS", "NETWORK_ERROR", "SOURCE_UNAVAILABLE", "RATE_LIMITED", "UNSUPPORTED_SOURCE", "PARSING_ERROR"
    val newChaptersCount: Int = 0,
    val latestDetectedIdentifier: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class DetectionServiceState(
    val isChecking: Boolean = false,
    val lastCheckTimestamp: Long = 0L,
    val totalSeriesChecked: Int = 0,
    val newChaptersFoundTotal: Int = 0,
    val failedChecksCount: Int = 0,
    val recentResults: Map<Long, DetectionResult> = emptyMap()
)

/**
 * Core engine for automatic manhwa update detection.
 * Enforces deduplication, exponential backoff, rate limits, and private session privacy.
 */
class ChapterDetectionService(
    private val seriesDao: SeriesDao,
    private val chapterDao: ChapterDao,
    private val notificationDao: NotificationDao,
    sourceRegistry: SourceRegistry,
    private val privacyManager: PrivacyManager,
    private val appContext: Context? = null,
    private val notificationsEnabled: () -> Boolean = { true },
    private val sourceManager: SourceManager = SourceManager(sourceRegistry)
) {
    private val _serviceState = MutableStateFlow(DetectionServiceState())
    val serviceState: StateFlow<DetectionServiceState> = _serviceState.asStateFlow()

    // Rate-limiting & exponential backoff tracking per host domain
    private val backoffMap = ConcurrentHashMap<String, BackoffEntry>()

    data class BackoffEntry(
        val consecutiveFailures: Int,
        val backoffUntil: Long
    )

    private suspend fun <T> withSeriesLock(seriesId: Long, action: suspend () -> T): T =
        SERIES_LOCKS.computeIfAbsent(seriesId) { Mutex() }.withLock { action() }

    /**
     * Checks all series where isMonitoringEnabled is true.
     */
    suspend fun checkAllSeries(force: Boolean = false): Map<Long, DetectionResult> = withContext(Dispatchers.IO) {
        _serviceState.value = _serviceState.value.copy(isChecking = true)
        val monitoredSeries = seriesDao.getMonitoredSeriesSync()
        val results = mutableMapOf<Long, DetectionResult>()
        var newChaptersTotal = 0
        var failedCount = 0

        try {
            for (series in monitoredSeries) {
                val res = withSeriesLock(series.id) { checkSeriesInternal(series, force) }
                results[series.id] = res
                newChaptersTotal += res.newChaptersCount
                if (res.status !in listOf("SUCCESS", "NO_NEW_CHAPTERS", "PRIVATE_SESSION_SUPPRESSED")) {
                    failedCount++
                }
            }
        } finally {
            val now = System.currentTimeMillis()
            _serviceState.value = DetectionServiceState(
                isChecking = false,
                lastCheckTimestamp = now,
                totalSeriesChecked = monitoredSeries.size,
                newChaptersFoundTotal = newChaptersTotal,
                failedChecksCount = failedCount,
                recentResults = results
            )
        }
        results
    }

    /**
     * Checks a single series by ID.
     */
    suspend fun checkSeries(seriesId: Long, force: Boolean = false): DetectionResult = withContext(Dispatchers.IO) {
        val series = seriesDao.getSeriesByIdSync(seriesId)
            ?: return@withContext DetectionResult(
                seriesId = seriesId,
                seriesTitle = "Unknown",
                status = "UNSUPPORTED_SOURCE",
                errorMessage = "Series not found"
            )

        val res = withSeriesLock(seriesId) { checkSeriesInternal(series, force) }
        val updatedMap = _serviceState.value.recentResults.toMutableMap()
        updatedMap[seriesId] = res
        _serviceState.value = _serviceState.value.copy(
            lastCheckTimestamp = System.currentTimeMillis(),
            recentResults = updatedMap
        )
        res
    }

    private suspend fun checkSeriesInternal(series: SeriesEntity, force: Boolean): DetectionResult {
        if (privacyManager.isPrivateSessionActive()) {
            return DetectionResult(
                seriesId = series.id,
                seriesTitle = series.title,
                status = "PRIVATE_SESSION_SUPPRESSED",
                errorMessage = "Automatic tracking is paused while private browsing is active"
            )
        }
        val now = System.currentTimeMillis()
        val domain = extractDomain(series.sourceUrl)

        // 1. Check if backoff active for this source domain
        val backoff = backoffMap[domain]
        if (!force && backoff != null && now < backoff.backoffUntil) {
            val remainingSec = (backoff.backoffUntil - now) / 1000
            val status = "RATE_LIMITED"
            val error = "Rate limit backoff active ($remainingSec seconds remaining)"
            seriesDao.updateCheckStatus(series.id, now, status, error)
            return DetectionResult(
                seriesId = series.id,
                seriesTitle = series.title,
                status = status,
                errorMessage = error
            )
        }

        // 2. Resolve a supported adapter from the URL and fetch on the source manager's IO dispatcher.
        val fetchResult = sourceManager.fetchChapters(series.sourceUrl)
        if (privacyManager.isPrivateSessionActive()) {
            return DetectionResult(
                seriesId = series.id,
                seriesTitle = series.title,
                status = "PRIVATE_SESSION_SUPPRESSED",
                errorMessage = "Automatic tracking is paused while private browsing is active"
            )
        }

        return when (fetchResult) {
            is SourceResult.Success -> {
                recordSuccess(domain)
                processDetectedChapters(series, fetchResult.data, now)
            }
            is SourceResult.NoNewChapters -> {
                recordSuccess(domain)
                seriesDao.updateCheckStatus(series.id, now, "NO_NEW_CHAPTERS", null)
                DetectionResult(
                    seriesId = series.id,
                    seriesTitle = series.title,
                    status = "NO_NEW_CHAPTERS",
                    latestDetectedIdentifier = series.latestChapterIdentifier
                )
            }
            is SourceResult.NetworkError -> {
                recordFailure(domain, baseBackoffMillis = 60_000L) // 1 min initial backoff
                val status = "NETWORK_ERROR"
                val error = fetchResult.message
                seriesDao.updateCheckStatus(series.id, now, status, error)
                DetectionResult(
                    seriesId = series.id,
                    seriesTitle = series.title,
                    status = status,
                    errorMessage = error
                )
            }
            is SourceResult.SourceUnavailable -> {
                recordFailure(domain, baseBackoffMillis = 180_000L) // 3 min backoff
                val status = "SOURCE_UNAVAILABLE"
                val error = "HTTP ${fetchResult.statusCode}: ${fetchResult.message}"
                seriesDao.updateCheckStatus(series.id, now, status, error)
                DetectionResult(
                    seriesId = series.id,
                    seriesTitle = series.title,
                    status = status,
                    errorMessage = error
                )
            }
            is SourceResult.RateLimited -> {
                val waitSeconds = fetchResult.retryAfterSeconds ?: 60L
                recordFailure(domain, baseBackoffMillis = waitSeconds * 1000L)
                val status = "RATE_LIMITED"
                val error = "${fetchResult.message} (wait $waitSeconds s)"
                seriesDao.updateCheckStatus(series.id, now, status, error)
                DetectionResult(
                    seriesId = series.id,
                    seriesTitle = series.title,
                    status = status,
                    errorMessage = error
                )
            }
            is SourceResult.ParsingError -> {
                recordFailure(domain, baseBackoffMillis = 300_000L)
                val status = "PARSING_ERROR"
                val error = fetchResult.message
                seriesDao.updateCheckStatus(series.id, now, status, error)
                DetectionResult(
                    seriesId = series.id,
                    seriesTitle = series.title,
                    status = status,
                    errorMessage = error
                )
            }
            is SourceResult.Unsupported -> {
                val status = "UNSUPPORTED_SOURCE"
                val error = fetchResult.reason
                seriesDao.updateCheckStatus(series.id, now, status, error)
                DetectionResult(
                    seriesId = series.id,
                    seriesTitle = series.title,
                    status = status,
                    errorMessage = error
                )
            }
        }
    }

    /**
     * Enforces the 8-step detection flow for newly fetched chapters.
     */
    private suspend fun processDetectedChapters(
        series: SeriesEntity,
        fetchedChapters: List<SourceChapter>,
        checkTimestamp: Long
    ): DetectionResult {
        val existingChapters = chapterDao.getChaptersForSeriesSync(series.id)
        val hasReleaseBaseline = series.lastCheckStatus in setOf("SUCCESS", "NO_NEW_CHAPTERS") ||
            series.latestChapterIdentifier.isNotBlank() && series.latestChapterIdentifier != "0"
        val unseenChapters = ChapterNormalizer.findUnseenChapters(existingChapters, fetchedChapters)

        // A first successful listing establishes a baseline. Its entries are genuine fetched release
        // rows, but are not "new" since this app began monitoring; never alert on the whole archive.
        val newEntities = unseenChapters.map { ch ->
            ChapterEntity(
                seriesId = series.id,
                chapterNumber = ch.number,
                chapterIdentifier = ch.identifier,
                chapterTitle = ch.title,
                chapterUrl = ch.url,
                publicationTimestamp = ch.publishedAt,
                firstDetectedAt = checkTimestamp,
                isRead = false
            )
        }
        val insertedIds = chapterDao.insertAll(newEntities)
        val insertedChapters = unseenChapters.zip(insertedIds)
            .filter { (_, insertedId) -> insertedId > 0L }
            .map { (chapter, _) -> chapter }
        if (insertedChapters.isEmpty()) {
            seriesDao.updateCheckStatus(series.id, checkTimestamp, "NO_NEW_CHAPTERS", null)
            return DetectionResult(
                seriesId = series.id,
                seriesTitle = series.title,
                status = "NO_NEW_CHAPTERS",
                latestDetectedIdentifier = series.latestChapterIdentifier
            )
        }

        val allStoredChapters = chapterDao.getChaptersForSeriesSync(series.id)
        val latestCandidate = insertedChapters.last()
        val latestIden = latestCandidate.identifier
        val totalCount = allStoredChapters.size

        // Update the series' latest detected chapter and set hasUnreadNewChapter = true
        seriesDao.updateLatestChapter(
            id = series.id,
            chapterId = latestIden,
            total = totalCount,
            timestamp = checkTimestamp,
            hasUnread = true
        )
        seriesDao.updateCheckStatus(series.id, checkTimestamp, "SUCCESS", null)

        // Queue notifications if series notifications are enabled AND not in private session
        if (series.notificationsEnabled && notificationsEnabled() && !privacyManager.isPrivateSessionActive()) {
            for (ch in insertedChapters) {
                // Prevent duplicate notifications
                val existingNotifCount = notificationDao.hasNotification(series.id, ch.identifier)
                if (existingNotifCount == 0) {
                    val notif = NotificationEntity(
                        seriesId = series.id,
                        chapterTitle = ch.title,
                        chapterIdentifier = ch.identifier,
                        seriesTitle = series.title,
                        message = "${ch.title} has released for ${series.title}!",
                        sourceUrl = ch.url.ifBlank { series.sourceUrl },
                        timestamp = checkTimestamp,
                        isRead = false
                    )
                    notificationDao.insertNotification(notif)

                    // Deliver Android system notification
                    if (appContext != null) {
                        NotificationHelper.showNewChapterNotification(
                            context = appContext,
                            seriesId = series.id,
                            manhwaTitle = series.title,
                            chapterTitle = ch.title,
                            chapterIdentifier = ch.identifier,
                            url = ch.url.ifBlank { series.sourceUrl }
                        )
                    }
                }
            }
        }

        return DetectionResult(
            seriesId = series.id,
            seriesTitle = series.title,
            status = "SUCCESS",
            newChaptersCount = insertedChapters.size,
            latestDetectedIdentifier = latestIden
        )
    }

    private fun recordSuccess(domain: String) {
        backoffMap.remove(domain)
    }

    private fun recordFailure(domain: String, baseBackoffMillis: Long) {
        val existing = backoffMap[domain]
        val failures = (existing?.consecutiveFailures ?: 0) + 1
        // Exponential multiplier: 1x, 2x, 4x, capped at 1 hour
        val multiplier = (1 shl (failures - 1).coerceAtMost(5)).toLong()
        val backoffDelay = (baseBackoffMillis * multiplier).coerceAtMost(3600_000L)
        backoffMap[domain] = BackoffEntry(
            consecutiveFailures = failures,
            backoffUntil = System.currentTimeMillis() + backoffDelay
        )
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            uri.host?.lowercase() ?: "generic"
        } catch (_: Exception) {
            "generic"
        }
    }

    private companion object {
        val SERIES_LOCKS = ConcurrentHashMap<Long, Mutex>()
    }
}
