package com.example.engine.tracker

import android.net.Uri
import com.example.data.local.dao.ChapterDao
import com.example.data.local.dao.ReadingSessionDao
import com.example.data.local.dao.SeriesDao
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.domain.model.DetectedManhwa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class ManhwaTracker(
    private val seriesDao: SeriesDao,
    private val chapterDao: ChapterDao,
    private val readingSessionDao: ReadingSessionDao,
    private val scope: CoroutineScope
) {
    private val _currentDetectedManhwa = MutableStateFlow<DetectedManhwa?>(null)
    val currentDetectedManhwa: StateFlow<DetectedManhwa?> = _currentDetectedManhwa.asStateFlow()

    private val chapterPatterns = listOf(
        Regex("""/(?:series|manga|comic|read|manhwa)/([^/]+)/chapter-?(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
        Regex("""/chapter-?(\d+(?:\.\d+)?)-([^/]+)""", RegexOption.IGNORE_CASE),
        Regex("""/([^/]+)/ch-?(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
        Regex("""/read/([^/]+)/(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    )

    fun onUrlLoaded(url: String, pageTitle: String?, isPrivate: Boolean) {
        if (url.startsWith("about:") || url.isEmpty()) {
            _currentDetectedManhwa.value = null
            return
        }

        val detected = detectFromUrl(url, pageTitle)
        _currentDetectedManhwa.value = detected

        if (detected != null && !isPrivate) {
            // Automatically sync reading progress in database if series exists in library
            scope.launch(Dispatchers.IO) {
                try {
                    val existing = seriesDao.findByTitle(detected.title)
                        ?: seriesDao.findBySourceUrl(detected.sourceUrl)

                    if (existing != null) {
                        val now = System.currentTimeMillis()
                        // Update current reading progress
                        seriesDao.updateLastReadChapter(
                            id = existing.id,
                            chapterIdentifier = detected.chapterNumber.toInt().toString(),
                            chapterNum = detected.chapterNumber,
                            timestamp = now
                        )

                        // Record chapter as read in chapter table
                        chapterDao.insert(
                            ChapterEntity(
                                seriesId = existing.id,
                                chapterNumber = detected.chapterNumber,
                                chapterIdentifier = detected.chapterNumber.toInt().toString(),
                                chapterTitle = detected.chapterTitle,
                                chapterUrl = url,
                                isRead = true,
                                lastReadAt = now
                            )
                        )

                        // Log reading session
                        val epochDay = LocalDate.now().toEpochDay()
                        readingSessionDao.insertSession(
                            ReadingSessionEntity(
                                seriesId = existing.id,
                                startTime = now - 300000L,
                                endTime = now,
                                durationSeconds = 300,
                                chaptersReadCount = 1,
                                dateEpochDay = epochDay
                            )
                        )
                    }
                } catch (_: Exception) {
                    // Non-fatal background sync failure
                }
            }
        }
    }

    fun detectFromUrl(url: String, pageTitle: String?): DetectedManhwa? {
        try {
            val uri = Uri.parse(url)
            val path = uri.path ?: return null

            for (pattern in chapterPatterns) {
                val match = pattern.find(path)
                if (match != null) {
                    val rawTitleSlug = match.groupValues[1]
                    val chapterNum = match.groupValues[2].toFloatOrNull() ?: 1.0f

                    val cleanTitle = formatSlugToTitle(rawTitleSlug)
                    return DetectedManhwa(
                        title = if (!pageTitle.isNullOrBlank() && !pageTitle.contains("http") && pageTitle.length < 50) {
                            cleanPageTitle(pageTitle)
                        } else {
                            cleanTitle
                        },
                        chapterNumber = chapterNum,
                        chapterTitle = "Chapter $chapterNum",
                        sourceUrl = "${uri.scheme}://${uri.host}/series/$rawTitleSlug"
                    )
                }
            }

            // Fallback for Webtoons URL query: episode_no=12
            val episodeParam = uri.getQueryParameter("episode_no") ?: uri.getQueryParameter("episodeNo")
            if (episodeParam != null) {
                val chapterNum = episodeParam.toFloatOrNull() ?: 1.0f
                val title = if (!pageTitle.isNullOrBlank()) cleanPageTitle(pageTitle) else "Webtoon Comic"
                return DetectedManhwa(
                    title = title,
                    chapterNumber = chapterNum,
                    chapterTitle = "Episode $chapterNum",
                    sourceUrl = url
                )
            }
        } catch (_: Exception) {
            return null
        }
        return null
    }

    private fun formatSlugToTitle(slug: String): String {
        return slug.split("-", "_")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    private fun cleanPageTitle(title: String): String {
        return title.replace(Regex("""(?i)\s*-\s*Chapter.*"""), "")
            .replace(Regex("""(?i)\s*\|\s*Chapter.*"""), "")
            .replace(Regex("""(?i)\s*-\s*Read.*"""), "")
            .trim()
    }

    fun clearDetection() {
        _currentDetectedManhwa.value = null
    }
}
