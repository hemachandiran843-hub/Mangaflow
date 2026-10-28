package com.example.engine.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MangaFlowApp
import com.example.data.local.MangaFlowDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background worker for scheduled manhwa update checks.
 * Uses Android WorkManager with network and battery constraints.
 * Respects user-configured intervals and platform limits.
 */
class ChapterMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = try { MangaFlowApp.instance } catch (_: Exception) { null }
            val db = app?.database ?: MangaFlowDatabase.getDatabase(applicationContext)
            val settingsDao = db.appSettingDao()
            if (settingsDao.getSetting("CHAPTER_MONITORING_ENABLED")?.toBoolean() == false) {
                return@withContext Result.success()
            }
            val notificationsAllowed =
                settingsDao.getSetting("NEW_CHAPTER_NOTIFICATIONS_ENABLED")?.toBoolean() ?: true
            val registry = app?.sourceRegistry ?: com.example.engine.source.SourceRegistry()
            val sourceManager = app?.sourceManager ?: com.example.engine.source.SourceManager(registry)
            val service = app?.chapterDetectionService ?: com.example.engine.source.ChapterDetectionService(
                seriesDao = db.seriesDao(),
                chapterDao = db.chapterDao(),
                notificationDao = db.notificationDao(),
                sourceRegistry = registry,
                privacyManager = app?.privacyManager ?: com.example.engine.privacy.PrivacyManager(),
                appContext = applicationContext,
                notificationsEnabled = { notificationsAllowed },
                sourceManager = sourceManager
            )
            service.checkAllSeries(force = false)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "MangaFlowChapterMonitor"
        const val KEY_CHECK_INTERVAL = "CHAPTER_CHECK_INTERVAL_HOURS"

        fun schedule(context: Context, intervalHours: Long = 6L) {
            // Android WorkManager periodic work requires a minimum interval of 15 minutes
            val sanitizedHours = intervalHours.coerceIn(1L, 72L)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ChapterMonitorWorker>(
                repeatInterval = sanitizedHours,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
