package com.example.engine.source

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry/locator only: it selects adapters but does not perform I/O or retain runtime health.
 * Built-ins are ordered by specificity, with generic metadata extraction as the final fallback.
 */
class SourceRegistry(
    customAdapters: List<SourceAdapter> = emptyList(),
    includeBuiltIns: Boolean = true
) {
    private val adapters = CopyOnWriteArrayList<SourceAdapter>()

    init {
        if (includeBuiltIns) {
            registerAdapter(MangaDexSourceAdapter())
            registerAdapter(RssFeedSourceAdapter())
            registerAdapter(WebtoonsSourceAdapter())
        }
        customAdapters.forEach(::registerAdapter)
        if (includeBuiltIns) registerAdapter(OpenWebMetaSourceAdapter())
    }

    fun getAllAdapters(): List<SourceAdapter> = adapters.toList()

    fun getAdapterById(sourceId: String): SourceAdapter? =
        adapters.firstOrNull { it.sourceId.equals(sourceId.trim(), ignoreCase = true) }

    /**
     * Returns the most specific adapter for a well-formed HTTP(S) URL. The metadata-only fallback
     * may match ordinary public pages, but SourceManager still checks capabilities before release
     * monitoring.
     */
    fun getAdapterForUrl(url: String): SourceAdapter? {
        if (!isHttpUrl(url)) return null
        return adapters.firstOrNull { adapter ->
            try { adapter.canHandle(url) } catch (_: Exception) { false }
        }
    }

    fun getUpdateAdapterForUrl(url: String): SourceAdapter? =
        getAdapterForUrl(url)?.takeIf {
            it.capabilities.supportsChapterList && it.capabilities.supportsUpdateDetection
        }

    /**
     * Adds a source before the generic fallback. Duplicate identifiers are rejected so a plugin
     * cannot silently shadow an existing implementation.
     */
    @Synchronized
    fun registerAdapter(adapter: SourceAdapter): Boolean {
        if (adapter.sourceId.isBlank() || adapters.any { it.sourceId.equals(adapter.sourceId, ignoreCase = true) }) {
            return false
        }
        val fallbackIndex = adapters.indexOfFirst { it.sourceId == "open_web" }
        if (fallbackIndex >= 0) adapters.add(fallbackIndex, adapter) else adapters.add(adapter)
        return true
    }

    fun canonicalizeSeriesUrl(url: String, preferredSourceId: String? = null): String? {
        val matched = getAdapterForUrl(url)
        val adapter = if (preferredSourceId.isNullOrBlank()) matched else {
            matched?.takeIf { it.sourceId.equals(preferredSourceId, ignoreCase = true) } ?: matched
        }
        return try {
            adapter?.canonicalizeSeriesUrl(url)?.takeIf(::isHttpUrl)
                ?: canonicalHttpUrl(url)
        } catch (_: Exception) {
            canonicalHttpUrl(url)
        }
    }

    fun identifyChapter(pageUrl: String, pageTitle: String? = null): SourceChapterEvidence? {
        val adapter = getAdapterForUrl(pageUrl) ?: return null
        return try {
            adapter.identifyChapter(pageUrl, pageTitle)?.takeIf { evidence ->
                evidence.sourceId == adapter.sourceId &&
                    isHttpUrl(evidence.canonicalSeriesUrl) &&
                    isHttpUrl(evidence.chapterUrl) &&
                    evidence.chapterIdentifier.isNotBlank() &&
                    evidence.confidence in 0f..1f
            }
        } catch (_: Exception) {
            null
        }
    }
}
