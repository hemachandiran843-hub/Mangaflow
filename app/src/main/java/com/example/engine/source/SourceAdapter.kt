package com.example.engine.source

import java.net.URI
import java.util.Locale

/**
 * Contract for one website/feed integration. Implementations own source-specific parsing and
 * network access; SourceManager is responsible for dispatching calls off the UI thread and
 * normalizing failures.
 */
interface SourceAdapter {
    val sourceId: String
    val displayName: String
    val baseUrl: String
    val capabilities: SourceCapabilities

    /** Strictly matches a source URL. Implementations must compare parsed hosts, not substrings. */
    fun canHandle(url: String): Boolean

    /** Canonical series URL, or null when the URL is invalid or cannot be canonicalized. */
    fun canonicalizeSeriesUrl(sourceUrl: String): String? = canonicalHttpUrl(sourceUrl)

    /**
     * Pure, synchronous page identification. This must not perform network I/O and must return
     * evidence based on the URL/known source structure, not Open Graph metadata alone.
     */
    fun identifyChapter(pageUrl: String, pageTitle: String?): SourceChapterEvidence? = null

    /** Retrieves normalized series metadata if this adapter supports it. */
    suspend fun fetchSeriesMetadata(sourceUrl: String): SourceResult<SourceSeriesMetadata>

    /** Retrieves an explicitly supported public chapter/release listing. */
    suspend fun fetchChapters(sourceUrl: String): SourceResult<List<SourceChapter>>
}

internal fun canonicalHttpUrl(value: String, stripFragment: Boolean = true): String? = try {
    val uri = URI(value.trim()).normalize()
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")
    if (scheme !in setOf("http", "https") || host.isNullOrBlank() || uri.userInfo != null) {
        null
    } else {
        val defaultPort = (scheme == "http" && uri.port == 80) || (scheme == "https" && uri.port == 443)
        val port = if (uri.port >= 0 && !defaultPort) uri.port else -1
        val path = uri.rawPath.orEmpty().ifBlank { "/" }.trimEnd('/').ifBlank { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = if (!stripFragment) uri.rawFragment?.let { "#$it" }.orEmpty() else ""
        "$scheme://$host${if (port >= 0) ":$port" else ""}$path$query$fragment"
    }
} catch (_: Exception) {
    null
}

internal fun isHttpUrl(value: String): Boolean = try {
    val uri = URI(value.trim())
    uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
        !uri.host.isNullOrBlank() && uri.userInfo == null
} catch (_: Exception) {
    false
}
