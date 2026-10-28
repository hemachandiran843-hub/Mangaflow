package com.example

import android.content.Context
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MangaFlowDatabase
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.SeriesEntity
import com.example.data.repository.BrowserRepository
import com.example.data.tracker.ManhwaTrackerService
import com.example.engine.browser.BrowserSessionManager
import com.example.engine.filter.ContentFilterEngine
import com.example.engine.privacy.PrivacyManager
import com.example.engine.tracker.BrowserTrackerBridgeImpl
import com.example.engine.tracker.ManhwaTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContentFilterAndPrivacyTest {

    private lateinit var context: Context
    private lateinit var database: MangaFlowDatabase
    private lateinit var privacyManager: PrivacyManager
    private lateinit var filterEngine: ContentFilterEngine
    private lateinit var sessionManager: BrowserSessionManager
    private lateinit var trackerService: ManhwaTrackerService
    private lateinit var manhwaTracker: ManhwaTracker
    private lateinit var trackerBridge: BrowserTrackerBridgeImpl
    private lateinit var browserRepository: BrowserRepository

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MangaFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        privacyManager = PrivacyManager()
        filterEngine = ContentFilterEngine()
        sessionManager = BrowserSessionManager(privacyManager)

        trackerService = ManhwaTrackerService(
            seriesDao = database.seriesDao(),
            chapterDao = database.chapterDao(),
            readingProgressDao = database.readingProgressDao(),
            readingSessionDao = database.readingSessionDao(),
            notificationDao = database.notificationDao(),
            database = database
        )

        manhwaTracker = ManhwaTracker(
            seriesDao = database.seriesDao(),
            chapterDao = database.chapterDao(),
            readingSessionDao = database.readingSessionDao(),
            scope = testScope
        )

        trackerBridge = BrowserTrackerBridgeImpl(
            manhwaTracker = manhwaTracker,
            trackerService = trackerService,
            scope = testScope
        )

        browserRepository = BrowserRepository(
            historyDao = database.historyDao(),
            bookmarkDao = database.bookmarkDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==========================================
    // PART A: AD BLOCKER & CONTENT FILTER TESTS
    // ==========================================

    @Test
    fun testAdBlocker_enableAndDisable() {
        // When enabled by default
        assertTrue(filterEngine.isAdBlockerEnabled.value)
        assertTrue(filterEngine.shouldBlockRequest("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(filterEngine.shouldBlockRequest("https://popads.net/serve/ad_banner.js"))

        // When disabled
        filterEngine.setAdBlockerEnabled(false)
        assertFalse(filterEngine.isAdBlockerEnabled.value)
        assertFalse(filterEngine.shouldBlockRequest("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertFalse(filterEngine.shouldBlockRequest("https://popads.net/serve/ad_banner.js"))

        // Re-enable
        filterEngine.setAdBlockerEnabled(true)
        assertTrue(filterEngine.shouldBlockRequest("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
    }

    @Test
    fun testAllowlist_behaviorAndOverrides() {
        val testAdUrl = "https://adsterra.com/tag/banner_ad.js"

        // Initially blocked
        assertTrue(filterEngine.shouldBlockRequest(testAdUrl))

        // Whitelist adsterra.com
        filterEngine.addToAllowlist("adsterra.com")
        assertTrue(filterEngine.isDomainAllowlisted("adsterra.com"))
        // Requests to whitelisted domain must now pass
        assertFalse(filterEngine.shouldBlockRequest(testAdUrl))

        // Remove from allowlist
        filterEngine.removeFromAllowlist("adsterra.com")
        assertFalse(filterEngine.isDomainAllowlisted("adsterra.com"))
        // Blocked again
        assertTrue(filterEngine.shouldBlockRequest(testAdUrl))
    }

    @Test
    fun testPerSiteBlockingControls() {
        val pageHost = "favorite-scanlator.org"
        val adUrl = "https://popcash.net/script.js"

        // Default: ad is blocked on any site
        assertTrue(filterEngine.shouldBlockRequest(adUrl, pageHost))

        // User pauses/disables shield for favorite-scanlator.org
        filterEngine.setSiteFilteringEnabled(pageHost, enabled = false)
        assertFalse(filterEngine.isFilteringEnabledForSite(pageHost))

        // On this specific site, requests are now allowed
        assertFalse(filterEngine.shouldBlockRequest(adUrl, pageHost))

        // On another site, ads are still blocked
        assertTrue(filterEngine.shouldBlockRequest(adUrl, "other-site.com"))

        // Re-enable shield for favorite-scanlator.org
        filterEngine.setSiteFilteringEnabled(pageHost, enabled = true)
        assertTrue(filterEngine.shouldBlockRequest(adUrl, pageHost))
    }

    @Test
    fun testFilterUpdates_parsingAndValidation() {
        val easyListSample = """
            ! Title: Custom Test EasyList
            ! Checksum: abcd1234
            ||malicious-tracking-network.com^
            ||crypto-drainer.xyz^${'$'}third-party
            /ad_interstitial_video.js
            @@||allowed-partner.com^
            [Malformed Header Line]

        """.trimIndent()

        val updateResult = filterEngine.updateFilterList(easyListSample)
        assertTrue(updateResult.success)
        assertTrue(updateResult.rulesParsedCount >= 4)
        assertNull(updateResult.errorMessage)

        // Verify newly parsed rules block target requests
        assertTrue(filterEngine.shouldBlockRequest("https://malicious-tracking-network.com/beacon"))
        assertTrue(filterEngine.shouldBlockRequest("https://content.com/ad_interstitial_video.js"))

        // Verify exception/allowlist rule parsed from EasyList
        assertFalse(filterEngine.shouldBlockRequest("https://allowed-partner.com/subresource.js"))
    }

    @Test
    fun testFilterUpdates_handlesMalformedDataSafely() {
        // Blank or comment-only list
        val emptyContent = "! Just comments\n! Nothing else"
        val res = filterEngine.updateFilterList(emptyContent)
        assertFalse(res.success)
        assertEquals(0, res.rulesParsedCount)
        assertNotNull(res.errorMessage)
    }

    @Test
    fun testBlockedStatisticsTracking() {
        filterEngine.resetStatistics()
        assertEquals(0, filterEngine.totalBlockedAds.value)
        assertEquals(0, filterEngine.totalTrackersBlocked.value)

        // Block an ad
        filterEngine.shouldBlockRequest("https://doubleclick.net/ad")
        assertEquals(1, filterEngine.totalBlockedAds.value)

        // Block a tracker
        filterEngine.shouldBlockRequest("https://clarity.ms/tag")
        assertEquals(2, filterEngine.totalBlockedAds.value)
        assertEquals(1, filterEngine.totalTrackersBlocked.value)

        filterEngine.resetStatistics()
        assertEquals(0, filterEngine.totalBlockedAds.value)
        assertEquals(0, filterEngine.totalTrackersBlocked.value)
    }

    // ==========================================
    // PART B: ISOLATED PRIVATE BROWSING TESTS
    // ==========================================

    @Test
    fun testNormalSessionPersistence_recordsHistory() = runBlocking {
        val normalUrl = "https://www.webtoons.com/en/fantasy/tower-of-god/episode-1"
        val title = "Tower of God Ep. 1"

        // In normal mode, history is persisted
        assertTrue(privacyManager.shouldPersistHistory(isPrivate = false))
        browserRepository.recordHistory(normalUrl, title, isPrivate = false)

        val historyItems = database.historyDao().getAllHistory().first()
        assertEquals(1, historyItems.size)
        assertEquals(normalUrl, historyItems.first().url)
        assertEquals(title, historyItems.first().title)
    }

    @Test
    fun testPrivateSessionIsolation_zeroHistoryInSQLite() = runBlocking {
        val secretUrl = "https://private-manga.org/secret-chapter-99"
        val secretTitle = "Secret Reading Chapter 99"

        // In private mode, shouldPersistHistory is FALSE
        assertFalse(privacyManager.shouldPersistHistory(isPrivate = true))

        // Record history called with isPrivate = true
        browserRepository.recordHistory(secretUrl, secretTitle, isPrivate = true)

        // Verify SQLite database contains NO records of secretUrl
        val historyItems = database.historyDao().getAllHistory().first()
        assertTrue(historyItems.none { it.url == secretUrl })
        assertEquals(0, historyItems.size)
    }

    @Test
    fun testPrivateBrowsing_zeroUpdatesToReadingStatistics() = runBlocking {
        // Add a tracked series
        val seriesId = trackerService.addSeriesManually(
            title = "Solo Leveling",
            sourceUrl = "https://example.com/series/solo-leveling",
            initialChapterCount = 100
        )

        val initialSeries = database.seriesDao().getSeriesByIdSync(seriesId)
        assertNotNull(initialSeries)
        val initialLastRead = initialSeries!!.lastReadChapterIdentifier

        // Simulate reading a chapter in PRIVATE mode
        val privateChapterUrl = "https://example.com/series/solo-leveling/chapter-50"
        trackerBridge.onPageLoaded(privateChapterUrl, "Solo Leveling - Chapter 50", isPrivate = true)

        // Wait brief instant for any async workers
        kotlinx.coroutines.delay(100)

        // 1. Reading sessions must NOT be inserted
        val sessions = database.readingSessionDao().getAllSessions().first().filter { it.seriesId == seriesId }
        assertEquals(0, sessions.size)

        // 2. Series progress must NOT be updated
        val afterSeries = database.seriesDao().getSeriesByIdSync(seriesId)
        assertEquals(initialLastRead, afterSeries!!.lastReadChapterIdentifier)

        // A normal page load detects a chapter but does not silently update reading progress.
        trackerBridge.onPageLoaded(privateChapterUrl, "Solo Leveling - Chapter 50", isPrivate = false)
        kotlinx.coroutines.delay(50)
        assertEquals(initialLastRead, database.seriesDao().getSeriesByIdSync(seriesId)!!.lastReadChapterIdentifier)
        val detection = trackerBridge.currentDetectedManhwa.value
        assertNotNull(detection)

        // Progress changes only after an explicit user confirmation; no fabricated reading duration is added.
        trackerService.markDetectedChapterRead(seriesId, detection!!)
        val afterConfirmedRead = database.seriesDao().getSeriesByIdSync(seriesId)
        assertEquals("50", afterConfirmedRead!!.lastReadChapterIdentifier)
        val chapter = database.chapterDao().findBySeriesAndIdentifier(seriesId, "50")
        assertTrue(chapter?.isRead == true)
        val normalSessions = database.readingSessionDao().getAllSessions().first().filter { it.seriesId == seriesId }
        assertEquals(0, normalSessions.size)
    }

    @Test
    fun testDiagnosticLogSanitization_noPrivateUrlsInLogs() {
        val sensitiveUrl = "https://manhwa-secret.com/read/chapter-10"
        val publicUrl = "https://public.com/about"

        val privateLog = privacyManager.sanitizeLog("Navigating to $sensitiveUrl", isPrivate = true)
        assertFalse("Private URL must not be present in diagnostic logs", privateLog.contains(sensitiveUrl))
        assertTrue(privateLog.contains("[PRIVATE_SESSION_REDACTED]"))

        val publicLog = privacyManager.sanitizeLog("Navigating to $publicUrl", isPrivate = false)
        assertTrue(publicLog.contains(publicUrl))
    }

    @Test
    fun testSessionDestruction_purgesPrivateTabsAndMemory() {
        // Create normal tab and private tab
        val normalTab = sessionManager.openNewTab("https://www.webtoons.com/en/", isPrivate = false)
        assertNotNull(normalTab)
        val privateTab = sessionManager.openNewTab("https://private.com", isPrivate = true)
        assertNotNull(privateTab)

        assertEquals(1, sessionManager.privateTabs.value.size)
        assertTrue(sessionManager.isPrivateActive.value)

        // Close all private tabs
        sessionManager.closeAllPrivateTabs(context)

        // Verify private session is destroyed
        assertEquals(0, sessionManager.privateTabs.value.size)
        assertFalse(sessionManager.isPrivateActive.value)
        assertTrue(sessionManager.normalTabs.value.any { it.id == sessionManager.currentTabId.value })
    }

    @Test
    fun testPrivateTabGroupsReceiveFreshProfileIdentifiers() {
        val first = sessionManager.openNewTab("https://private-one.example", isPrivate = true)
        assertNotNull(first)
        val firstProfile = privacyManager.getPrivateProfileName()
        sessionManager.closeAllPrivateTabs(context)

        val second = sessionManager.openNewTab("https://private-two.example", isPrivate = true)
        assertNotNull(second)
        val secondProfile = privacyManager.getPrivateProfileName()
        assertFalse(firstProfile == secondProfile)
    }

    @Test
    fun testTabSnapshotsAreBoundedForMemory() {
        val tabs = (1..6).mapNotNull { index ->
            sessionManager.openNewTab("https://normal-$index.example", isPrivate = false)
        }
        tabs.forEach { tab -> sessionManager.saveWebViewState(tab.id, Bundle().apply { putString("url", tab.url) }) }

        assertNull(sessionManager.getWebViewState(tabs.first().id))
        assertNotNull(sessionManager.getWebViewState(tabs.last().id))
    }

    @Test
    fun testPrivateTabsExclusionFromRestoration() {
        // Normal tab closed -> can be restored
        val normal = sessionManager.openNewTab("https://restore-me.com", isPrivate = false)
        assertNotNull(normal)
        sessionManager.closeTab(normal!!.id, context)
        assertTrue(sessionManager.canRestoreTab.value)

        val restored = sessionManager.restoreLastClosedTab()
        assertNotNull(restored)
        assertEquals("https://restore-me.com", restored!!.url)

        // Private tab closed -> MUST NEVER be saved to restoration history
        val privateTab = sessionManager.openNewTab("https://secret-never-restore.com", isPrivate = true)
        assertNotNull(privateTab)
        sessionManager.closeTab(privateTab!!.id, context)

        // Ensure restored tab is NOT the private tab
        val nextRestore = sessionManager.restoreLastClosedTab()
        assertNull("Private tabs must never be restorable", nextRestore)
    }
}
