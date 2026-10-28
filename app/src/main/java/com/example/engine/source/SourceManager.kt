package com.example.engine.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * I/O and runtime-health layer above SourceRegistry. All adapter calls are dispatched to IO,
 * serialized per adapter, and converted to typed results so adapter failures cannot break browsing.
 */
class SourceManager(
    val registry: SourceRegistry,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val _statuses = MutableStateFlow(
        registry.getAllAdapters().associate { adapter ->
            adapter.sourceId to SourceRuntimeStatus(sourceId = adapter.sourceId)
        }
    )
    val statuses: StateFlow<Map<String, SourceRuntimeStatus>> = _statuses.asStateFlow()

    fun adapters(): List<SourceAdapter> = registry.getAllAdapters()

    fun adapterForUrl(url: String): SourceAdapter? = registry.getAdapterForUrl(url)

    fun adapterForUpdateUrl(url: String): SourceAdapter? = registry.getUpdateAdapterForUrl(url)

    fun automaticTrackingAvailable(url: String): Boolean = adapterForUpdateUrl(url) != null

    fun canonicalizeSeriesUrl(url: String, preferredSourceId: String? = null): String? =
        registry.canonicalizeSeriesUrl(url, preferredSourceId)

    fun identifyChapter(pageUrl: String, pageTitle: String? = null): SourceChapterEvidence? =
        registry.identifyChapter(pageUrl, pageTitle)

    suspend fun fetchSeriesMetadata(
        sourceUrl: String,
        preferredSourceId: String? = null
    ): SourceResult<SourceSeriesMetadata> {
        val adapter = registry.getAdapterForUrl(sourceUrl)
            ?: return SourceResult.Unsupported("No source adapter accepts this HTTP(S) URL")
        if (!adapter.capabilities.supportsMetadataLookup) {
            reportUnsupported(adapter.sourceId, "Metadata lookup is not supported")
            return SourceResult.Unsupported("${adapter.displayName} does not provide metadata lookup")
        }
        if (!preferredSourceId.isNullOrBlank() && !adapter.sourceId.equals(preferredSourceId, ignoreCase = true)) {
            // Never let a stored source id route an unrelated host to an adapter.
            reportUnsupported(adapter.sourceId, "URL host does not match the selected source")
            return SourceResult.Unsupported("The URL does not belong to the selected source")
        }
        return execute(adapter, metadata = true) { adapter.fetchSeriesMetadata(sourceUrl) }
    }

    suspend fun fetchChapters(
        sourceUrl: String,
        preferredSourceId: String? = null
    ): SourceResult<List<SourceChapter>> {
        val matchedAdapter = registry.getAdapterForUrl(sourceUrl)
            ?: return SourceResult.Unsupported("No source adapter accepts this HTTP(S) URL")
        if (!preferredSourceId.isNullOrBlank() && !matchedAdapter.sourceId.equals(preferredSourceId, ignoreCase = true)) {
            reportUnsupported(matchedAdapter.sourceId, "URL host does not match the selected source")
            return SourceResult.Unsupported("The URL does not belong to the selected source")
        }
        if (!matchedAdapter.capabilities.supportsChapterList || !matchedAdapter.capabilities.supportsUpdateDetection) {
            val reason = "${matchedAdapter.displayName} supports browsing/metadata only; release checks are unavailable"
            reportUnsupported(matchedAdapter.sourceId, reason)
            return SourceResult.Unsupported(reason)
        }
        return execute(matchedAdapter, metadata = false) { matchedAdapter.fetchChapters(sourceUrl) }
    }

    fun reportUnsupported(sourceId: String?, reason: String) {
        if (sourceId.isNullOrBlank()) return
        updateStatus(sourceId) { prior ->
            prior.copy(
                lastStatus = "UNSUPPORTED",
                lastErrorMessage = reason.take(MAX_STATUS_MESSAGE_LENGTH),
                lastCheckedAt = clock()
            )
        }
    }

    private suspend fun <T> execute(
        adapter: SourceAdapter,
        metadata: Boolean,
        action: suspend () -> SourceResult<T>
    ): SourceResult<T> {
        val result = try {
            locks.computeIfAbsent(adapter.sourceId) { Mutex() }.withLock {
                withContext(Dispatchers.IO) { action() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            SourceResult.NetworkError(io.message ?: "Network request failed", io)
        } catch (failure: Exception) {
            SourceResult.ParsingError(
                message = failure.message?.take(MAX_STATUS_MESSAGE_LENGTH)
                    ?: "Source adapter failed unexpectedly"
            )
        }
        recordOutcome(adapter.sourceId, result, metadata)
        return result
    }

    private fun <T> recordOutcome(sourceId: String, result: SourceResult<T>, metadata: Boolean) {
        val now = clock()
        updateStatus(sourceId) { prior ->
            when (result) {
                is SourceResult.Success -> prior.copy(
                    lastStatus = if (metadata) "METADATA_OK" else "SUCCESS",
                    lastSuccessAt = if (metadata) prior.lastSuccessAt else now,
                    lastErrorAt = if (metadata) prior.lastErrorAt else null,
                    lastErrorMessage = if (metadata) prior.lastErrorMessage else null,
                    lastCheckedAt = now
                )
                is SourceResult.NoNewChapters -> prior.copy(
                    lastStatus = "NO_NEW_CHAPTERS",
                    lastSuccessAt = now,
                    lastErrorAt = null,
                    lastErrorMessage = null,
                    lastCheckedAt = now
                )
                is SourceResult.NetworkError -> prior.copy(
                    lastStatus = "NETWORK_ERROR",
                    lastErrorAt = now,
                    lastErrorMessage = result.message.take(MAX_STATUS_MESSAGE_LENGTH),
                    lastCheckedAt = now
                )
                is SourceResult.SourceUnavailable -> prior.copy(
                    lastStatus = "SOURCE_UNAVAILABLE",
                    lastErrorAt = now,
                    lastErrorMessage = "HTTP ${result.statusCode}: ${result.message}".take(MAX_STATUS_MESSAGE_LENGTH),
                    lastCheckedAt = now
                )
                is SourceResult.RateLimited -> prior.copy(
                    lastStatus = "RATE_LIMITED",
                    lastErrorAt = now,
                    lastErrorMessage = result.message.take(MAX_STATUS_MESSAGE_LENGTH),
                    lastCheckedAt = now
                )
                is SourceResult.ParsingError -> prior.copy(
                    lastStatus = "PARSING_ERROR",
                    lastErrorAt = now,
                    lastErrorMessage = result.message.take(MAX_STATUS_MESSAGE_LENGTH),
                    lastCheckedAt = now
                )
                is SourceResult.Unsupported -> prior.copy(
                    lastStatus = "UNSUPPORTED",
                    lastErrorMessage = result.reason.take(MAX_STATUS_MESSAGE_LENGTH),
                    lastCheckedAt = now
                )
            }
        }
    }

    private fun updateStatus(sourceId: String, transform: (SourceRuntimeStatus) -> SourceRuntimeStatus) {
        val previous = _statuses.value[sourceId] ?: SourceRuntimeStatus(sourceId)
        _statuses.value = _statuses.value + (sourceId to transform(previous))
    }

    companion object {
        private const val MAX_STATUS_MESSAGE_LENGTH = 400
    }
}
