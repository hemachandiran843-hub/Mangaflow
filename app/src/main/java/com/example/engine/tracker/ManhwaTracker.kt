package com.example.engine.tracker

import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.ReadingSessionDao
import com.example.data.local.dao.SeriesDao
import com.example.domain.model.DetectedManhwa
import com.example.engine.source.SourceManager
import com.example.engine.source.SourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pure, transient page detector. It never writes progress/history; persistence requires a separate
 * explicit user action or an evidence-backed, lifecycle-timed normal-browser session.
 */
class ManhwaTracker(
    @Suppress("unused") private val seriesDao: SeriesDao,
    @Suppress("unused") private val chapterDao: ChapterDao,
    @Suppress("unused") private val readingSessionDao: ReadingSessionDao,
    @Suppress("unused") private val scope: CoroutineScope,
    private val sourceManager: SourceManager = SourceManager(SourceRegistry())
) {
    private val _currentDetectedManhwa = MutableStateFlow<DetectedManhwa?>(null)
    val currentDetectedManhwa: StateFlow<DetectedManhwa?> = _currentDetectedManhwa

    fun onUrlLoaded(url: String, pageTitle: String?, isPrivate: Boolean) {
        if (isPrivate || url.isBlank() || url.startsWith("about:", ignoreCase = true)) {
            _currentDetectedManhwa.value = null
            return
        }
        _currentDetectedManhwa.value = detectFromUrl(url, pageTitle)
    }

    /** Uses URL structure from the matched adapter; title metadata alone can never identify a chapter. */
    fun detectFromUrl(url: String, pageTitle: String?): DetectedManhwa? {
        val evidence = sourceManager.identifyChapter(url, pageTitle) ?: return null
        val identifier = evidence.chapterIdentifier.trim().takeIf(String::isNotBlank) ?: return null
        val number = evidence.chapterNumber?.takeIf { it.isFinite() && it >= 0f }
        val title = evidence.chapterTitle.trim().takeIf(String::isNotBlank) ?: "Chapter $identifier"
        return DetectedManhwa(
            title = evidence.seriesTitle?.takeIf(String::isNotBlank)?.trim()?.take(300)
                ?: seriesTitleFromUrl(evidence.canonicalSeriesUrl, title),
            chapterNumber = number,
            chapterTitle = title,
            sourceUrl = evidence.canonicalSeriesUrl,
            chapterIdentifier = evidence.chapterIdentifier,
            chapterUrl = evidence.chapterUrl,
            confidence = evidence.confidence.coerceIn(0f, 1f),
            sourceId = evidence.sourceId
        )
    }

    fun clearDetection() {
        _currentDetectedManhwa.value = null
    }

    private fun seriesTitleFromUrl(seriesUrl: String, chapterTitle: String): String {
        val hostTitle = try {
            java.net.URI(seriesUrl).path.orEmpty().trim('/').substringAfterLast('/').replace('-', ' ').replace('_', ' ')
                .split(Regex("\\s+"))
                .filter(String::isNotBlank)
                .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        } catch (_: Exception) {
            ""
        }
        if (hostTitle.isNotBlank()) return hostTitle.take(300)
        return chapterTitle.substringBefore("—").substringBefore("-").trim().ifBlank { "Tracked Series" }.take(300)
    }
}
