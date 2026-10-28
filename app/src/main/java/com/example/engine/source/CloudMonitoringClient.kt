package com.example.engine.source

/**
 * Optional Cloud Monitoring Interface for future cloud integration.
 * Allows offloading periodic chapter scraping to serverless workers
 * without impacting the local-first autonomous operation of the app.
 */
interface CloudMonitoringClient {

    data class CloudMonitoredSeries(
        val cloudId: String,
        val localSeriesId: Long,
        val title: String,
        val sourceUrl: String,
        val checkFrequencyHours: Int = 6,
        val active: Boolean = true
    )

    data class CloudDetectedUpdate(
        val localSeriesId: Long,
        val chapterIdentifier: String,
        val chapterTitle: String,
        val chapterUrl: String,
        val detectedAt: Long
    )

    sealed class CloudResult<out T> {
        data class Success<out T>(val data: T) : CloudResult<T>()
        data class Error(val code: Int, val message: String) : CloudResult<Nothing>()
        object Offline : CloudResult<Nothing>()
    }

    /**
     * Registers a series with the optional backend scraper.
     */
    suspend fun registerSeries(series: CloudMonitoredSeries): CloudResult<String>

    /**
     * Unregisters a series from the backend scraper.
     */
    suspend fun unregisterSeries(cloudId: String): CloudResult<Boolean>

    /**
     * Polls the cloud backend for detected chapter updates.
     */
    suspend fun pollPendingUpdates(): CloudResult<List<CloudDetectedUpdate>>
}

/**
 * Default local-first implementation that operates without requiring any cloud server.
 */
class LocalFirstCloudMonitoringStub : CloudMonitoringClient {
    override suspend fun registerSeries(series: CloudMonitoringClient.CloudMonitoredSeries): CloudMonitoringClient.CloudResult<String> {
        return CloudMonitoringClient.CloudResult.Success("local-device-tracked-${series.localSeriesId}")
    }

    override suspend fun unregisterSeries(cloudId: String): CloudMonitoringClient.CloudResult<Boolean> {
        return CloudMonitoringClient.CloudResult.Success(true)
    }

    override suspend fun pollPendingUpdates(): CloudMonitoringClient.CloudResult<List<CloudMonitoringClient.CloudDetectedUpdate>> {
        return CloudMonitoringClient.CloudResult.Success(emptyList())
    }
}
