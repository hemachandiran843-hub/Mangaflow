package com.example

import android.app.Application
import com.example.data.local.MangaFlowDatabase
import com.example.data.repository.BrowserRepository
import com.example.data.repository.ManhwaRepository
import com.example.data.repository.ReadingStatsRepository
import com.example.engine.browser.BrowserSessionManager
import com.example.engine.filter.ContentFilterEngine
import com.example.engine.privacy.PrivacyManager
import com.example.engine.tracker.ManhwaTracker
import com.example.engine.translation.OcrTranslationEngine
import com.example.engine.worker.ChapterMonitorWorker
import com.example.engine.worker.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MangaFlowApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var database: MangaFlowDatabase
        private set

    lateinit var privacyManager: PrivacyManager
        private set

    lateinit var contentFilterEngine: ContentFilterEngine
        private set

    lateinit var browserSessionManager: BrowserSessionManager
        private set

    lateinit var manhwaTracker: ManhwaTracker
        private set

    lateinit var browserTrackerBridge: com.example.engine.tracker.BrowserTrackerBridge
        private set

    lateinit var translationManager: com.example.engine.translation.TranslationManager
        private set

    lateinit var ocrTranslationEngine: OcrTranslationEngine
        private set

    lateinit var manhwaRepository: ManhwaRepository
        private set

    lateinit var readingStatsRepository: ReadingStatsRepository
        private set

    lateinit var browserRepository: BrowserRepository
        private set

    lateinit var trackerService: com.example.data.tracker.ManhwaTrackerService
        private set

    lateinit var sourceRegistry: com.example.engine.source.SourceRegistry
        private set

    lateinit var chapterDetectionService: com.example.engine.source.ChapterDetectionService
        private set

    lateinit var cloudMonitoringClient: com.example.engine.source.CloudMonitoringClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = MangaFlowDatabase.getDatabase(this)
        contentFilterEngine = ContentFilterEngine()
        privacyManager = PrivacyManager()
        translationManager = com.example.engine.translation.TranslationManager(
            appSettingDao = database.appSettingDao(),
            scope = applicationScope
        )
        browserSessionManager = BrowserSessionManager(privacyManager)
        ocrTranslationEngine = OcrTranslationEngine(translationManager)
        sourceRegistry = com.example.engine.source.SourceRegistry()
        cloudMonitoringClient = com.example.engine.source.LocalFirstCloudMonitoringStub()

        chapterDetectionService = com.example.engine.source.ChapterDetectionService(
            seriesDao = database.seriesDao(),
            chapterDao = database.chapterDao(),
            notificationDao = database.notificationDao(),
            sourceRegistry = sourceRegistry,
            privacyManager = privacyManager,
            appContext = this
        )

        trackerService = com.example.data.tracker.ManhwaTrackerService(
            seriesDao = database.seriesDao(),
            chapterDao = database.chapterDao(),
            readingProgressDao = database.readingProgressDao(),
            readingSessionDao = database.readingSessionDao(),
            notificationDao = database.notificationDao()
        )

        manhwaTracker = ManhwaTracker(
            seriesDao = database.seriesDao(),
            chapterDao = database.chapterDao(),
            readingSessionDao = database.readingSessionDao(),
            scope = applicationScope
        )

        browserTrackerBridge = com.example.engine.tracker.BrowserTrackerBridgeImpl(
            manhwaTracker = manhwaTracker,
            trackerService = trackerService,
            scope = applicationScope
        )

        manhwaRepository = ManhwaRepository(database.seriesDao(), database.chapterDao(), trackerService)
        readingStatsRepository = ReadingStatsRepository(database.readingSessionDao())
        browserRepository = BrowserRepository(database.historyDao(), database.bookmarkDao())

        NotificationHelper.createNotificationChannel(this)

        try {
            ChapterMonitorWorker.schedule(this)
        } catch (_: Exception) {
            // WorkManager fallback
        }
    }

    companion object {
        lateinit var instance: MangaFlowApp
            private set
    }
}
