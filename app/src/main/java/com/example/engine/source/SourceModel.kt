package com.example.engine.source

/**
 * Capabilities are deliberately explicit: metadata-only sources must never be treated as
 * automatic release trackers.
 */
data class SourceCapabilities(
    val supportsMetadataLookup: Boolean = true,
    val supportsChapterList: Boolean = true,
    val supportsUpdateDetection: Boolean = true,
    val supportsRssFeed: Boolean = false,
    val isOfficialApi: Boolean = false,
    val rateLimitRequestsPerMinute: Int = 30
)

/** Normalized metadata returned by a source adapter. */
data class SourceSeriesMetadata(
    val title: String,
    val alternateTitles: List<String> = emptyList(),
    val coverUrl: String? = null,
    val description: String? = null,
    val author: String? = null,
    val status: String? = null,
    val latestChapterIdentifier: String? = null,
    val canonicalUrl: String? = null
)

/** A normalized, stable chapter identity. */
data class SourceChapter(
    val identifier: String,
    val number: Float? = null,
    val title: String,
    val url: String,
    val publishedAt: Long? = null
)

/**
 * Evidence extracted from a loaded URL without making a network request. Page title/metadata may
 * help label a URL, but must not be the sole evidence that a page is a chapter.
 */
data class SourceChapterEvidence(
    val sourceId: String,
    val canonicalSeriesUrl: String,
    val chapterIdentifier: String,
    val chapterNumber: Float?,
    val chapterTitle: String,
    val chapterUrl: String,
    val confidence: Float,
    val evidence: String,
    val seriesTitle: String? = null
)

/** Last in-process adapter outcome, used by the source-management UI. */
data class SourceRuntimeStatus(
    val sourceId: String,
    val lastStatus: String = "NOT_CHECKED",
    val lastSuccessAt: Long? = null,
    val lastErrorAt: Long? = null,
    val lastErrorMessage: String? = null,
    val lastCheckedAt: Long? = null
)

/** Distinct outcomes keep unsupported sources and parse failures from looking like empty feeds. */
sealed class SourceResult<out T> {
    data class Success<out T>(val data: T) : SourceResult<T>()
    data class NoNewChapters(
        val latestIdentifier: String,
        val checkedAt: Long = System.currentTimeMillis()
    ) : SourceResult<Nothing>()
    data class NetworkError(
        val message: String,
        val cause: Throwable? = null
    ) : SourceResult<Nothing>()
    data class SourceUnavailable(
        val statusCode: Int,
        val message: String
    ) : SourceResult<Nothing>()
    data class RateLimited(
        val retryAfterSeconds: Long? = null,
        val message: String = "Source rate limit exceeded"
    ) : SourceResult<Nothing>()
    data class ParsingError(
        val message: String,
        val rawSnippet: String? = null
    ) : SourceResult<Nothing>()
    data class Unsupported(
        val reason: String
    ) : SourceResult<Nothing>()
}
