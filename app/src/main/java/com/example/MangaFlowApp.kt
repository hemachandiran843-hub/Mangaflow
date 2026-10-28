package com.example

import android.app.Application
import com.example.data.local.MangaFlowDatabase
import com.example.data.repository.BrowserRepository
import com.example.data.repository.ManhwaRepository
import com.example.data.repository.ReadingStatsRepository
import com.example.engine.browser.BrowserSessionManager
import com.example.engine.browser.BrowserSettingsStore
import com.example.engine.browser.BrowserDownloadManager
import com.example.engine.filter.ContentFilterEngine
import com.example.engine.privacy.PrivacyManager
import com.example.engine.security.SecureCredentialVault
import com.example.engine.tracker.ManhwaTracker
import com.example.engine.translation.OcrTranslationEngine
import com.example.engine.worker.ChapterMonitorWorker
import com.example.engine.worker.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class MangaFlowApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    var newChapterNotificationsEnabled: Boolean = true
        private set

    fun updateNewChapterNotificationsEnabled(enabled: Boolean) {
        newChapterNotificationsEnabled = enabled
    }

    lateinit var database: MangaFlowDatabase
        private set

    lateinit var privacyManager: PrivacyManager
        private set

    lateinit var contentFilterEngine: ContentFilterEngine
        private set

    lateinit var browserSessionManager: BrowserSessionManager
        private set

    lateinit var browserSettingsStore: BrowserSettingsStore
        private set

    lateinit var browserDownloadManager: BrowserDownloadManager
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

    lateinit var sourceManager: com.example.engine.source.SourceManager
        private set

    lateinit var readingSessionTracker: com.example.engine.tracker.ReadingSessionTracker
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
        privacyManager.purgeOrphanedPrivateProfiles()
        browserSettingsStore = BrowserSettingsStore(database.appSettingDao(), applicationScope)
        browserDownloadManager = BrowserDownloadManager(this)
        translationManager = com.example.engine.translation.TranslationManager(
            appSettingDao = database.appSettingDao(),
            scope = applicationScope,
            credentialVault = SecureCredentialVault(this)
        )
        browserSessionManager = BrowserSessionManager(privacyManager)
        applicationScope.launch {
            browserSettingsStore.settings.collect { settings ->
                browserSessionManager.updateBrowserDefaults(settings.homepage, settings.desktopSiteByDefault)
            }
        }
        ocrTranslationEngine = OcrTranslationEngine(translationManager)
        sourceRegistry = com.example.engine.source.SourceRegistry()
        sourceManager = com.example.engine.source.SourceManager(sourceRegistry)
        cloudMonitoringClient = com.example.engine.source.LocalFirstCloudMonitoringStub()

        chapterDetectionService = com.example.engine.source.ChapterDetectionService(
            seriesDao = database.seriesDao(),
            chapterDao = database.chapterDao(),
            notificationDao = database.notificationDao(),
            sourceRegistry = sourceRegistry,
            privacyManager = privacyManager,
            appContext = this,
            notificationsEnabled = { newChapterNotificationsEnabled },
            sourceManager = sourceManager
        )

        trackerService = com.example.data.tracker.ManhwaTrackerService(
            seriesDao = database.seriesDao(),
            chapterDao = database.chapterDao(),
            readingProgressDao = database.readingProgressDao(),
            readingSessionDao = database.readingSessionDao(),
            notificationDao = database.notificationDao(),
            database = database,
            sourceManager = sourceManager
        )

        manhwaTracker = ManhwaTracker(
            seriesDao = database.seriesDao(),
            chapterDao = database.chapterDao(),
            readingSessionDao = database.readingSessionDao(),
            scope = applicationScope,
            sourceManager = sourceManager
        )

        browserTrackerBridge = com.example.engine.tracker.BrowserTrackerBridgeImpl(
            manhwaTracker = manhwaTracker,
            trackerService = trackerService,
            scope = applicationScope
        )

        manhwaRepository = ManhwaRepository(database.seriesDao(), database.chapterDao(), trackerService)
        readingStatsRepository = ReadingStatsRepository(database.readingSessionDao())
        readingSessionTracker = com.example.engine.tracker.ReadingSessionTracker(
            scope = applicationScope,
            persist = readingStatsRepository::recordReadingSession
        )
        browserRepository = BrowserRepository(database.historyDao(), database.bookmarkDao())

        NotificationHelper.createNotificationChannel(this)

        applicationScope.launch {
            try {
                val settingsDao = database.appSettingDao()
                newChapterNotificationsEnabled =
                    settingsDao.getSetting("NEW_CHAPTER_NOTIFICATIONS_ENABLED")?.toBoolean() ?: true
                val monitoringEnabled = settingsDao.getSetting("CHAPTER_MONITORING_ENABLED")?.toBoolean() ?: true
                val intervalHours = settingsDao.getSetting("CHAPTER_CHECK_INTERVAL_HOURS")?.toLongOrNull() ?: 6L
                if (monitoringEnabled) {
                    ChapterMonitorWorker.schedule(this@MangaFlowApp, intervalHours)
                } else {
                    ChapterMonitorWorker.cancel(this@MangaFlowApp)
                }
            } catch (_: Exception) {
                // Do not start periodic work if persisted preferences could not be read safely.
                try { ChapterMonitorWorker.cancel(this@MangaFlowApp) } catch (_: Exception) { }
            }
        }
    }

    companion object {
        lateinit var instance: MangaFlowApp
            private set
    }
}
