package com.example.engine.source

/**
 * Declares capabilities supported by a specific source adapter.
 */
data class SourceCapabilities(
    val supportsMetadataLookup: Boolean = true,
    val supportsChapterList: Boolean = true,
    val supportsUpdateDetection: Boolean = true,
    val supportsRssFeed: Boolean = false,
    val isOfficialApi: Boolean = false,
    val rateLimitRequestsPerMinute: Int = 30
)

/**
 * Normalized metadata for a tracked series retrieved from a source.
 */
data class SourceSeriesMetadata(
    val title: String,
    val alternateTitles: List<String> = emptyList(),
    val coverUrl: String? = null,
    val description: String? = null,
    val author: String? = null,
    val status: String? = null, // e.g. "ongoing", "completed"
    val latestChapterIdentifier: String? = null
)

/**
 * Normalized chapter item retrieved from a source.
 */
data class SourceChapter(
    val identifier: String, // e.g. "120", "12.5", "prologue", "extra-1"
    val number: Float? = null, // Numeric representation when available, null for non-numeric
    val title: String, // e.g. "Chapter 120 - Awakened Power"
    val url: String,
    val publishedAt: Long? = null // Epoch millis, null when unknown
)

/**
 * Sealed result type distinguishing different outcomes of source queries.
 * Prevents false positives, silent failures, and respects rate limits.
 */
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
