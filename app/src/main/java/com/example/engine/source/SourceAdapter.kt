package com.example.engine.source

/**
 * Standard interface for manhwa source adapters.
 * Encapsulates source-specific retrieval, normalization, and capability limits.
 */
interface SourceAdapter {
    val sourceId: String
    val displayName: String
    val baseUrl: String
    val capabilities: SourceCapabilities

    /**
     * Determines whether this adapter can handle the given URL.
     */
    fun canHandle(url: String): Boolean

    /**
     * Retrieves normalized series metadata if supported.
     */
    suspend fun fetchSeriesMetadata(sourceUrl: String): SourceResult<SourceSeriesMetadata>

    /**
     * Retrieves normalized chapter listing for update detection.
     */
    suspend fun fetchChapters(sourceUrl: String): SourceResult<List<SourceChapter>>
}
