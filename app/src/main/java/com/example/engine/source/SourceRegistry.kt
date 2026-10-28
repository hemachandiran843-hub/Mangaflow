package com.example.engine.source

/**
 * Registry and locator for manhwa source adapters.
 */
class SourceRegistry(
    customAdapters: List<SourceAdapter> = emptyList()
) {
    private val adapters = mutableListOf<SourceAdapter>()

    init {
        // Built-in adapters ordered by specificity and reliability
        adapters.add(MangaDexSourceAdapter())
        adapters.add(RssFeedSourceAdapter())
        adapters.add(WebtoonsSourceAdapter())
        adapters.addAll(customAdapters)
        // Fallback adapter for generic public web pages
        adapters.add(OpenWebMetaSourceAdapter())
    }

    fun getAllAdapters(): List<SourceAdapter> = adapters.toList()

    fun getAdapterById(sourceId: String): SourceAdapter? {
        return adapters.firstOrNull { it.sourceId.equals(sourceId, ignoreCase = true) }
    }

    fun getAdapterForUrl(url: String): SourceAdapter? {
        if (url.isBlank()) return null
        return adapters.firstOrNull { it.canHandle(url) }
    }

    fun registerAdapter(adapter: SourceAdapter) {
        if (!adapters.any { it.sourceId == adapter.sourceId }) {
            // Insert before the generic fallback adapter
            if (adapters.isNotEmpty()) {
                adapters.add(adapters.size - 1, adapter)
            } else {
                adapters.add(adapter)
            }
        }
    }
}
