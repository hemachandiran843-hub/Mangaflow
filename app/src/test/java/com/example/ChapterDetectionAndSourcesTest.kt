package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MangaFlowDatabase
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.SeriesEntity
import com.example.engine.privacy.PrivacyManager
import com.example.engine.source.ChapterDetectionService
import com.example.engine.source.ChapterNormalizer
import com.example.engine.source.MangaDexSourceAdapter
import com.example.engine.source.RssFeedSourceAdapter
import com.example.engine.source.SourceAdapter
import com.example.engine.source.SourceCapabilities
import com.example.engine.source.SourceChapter
import com.example.engine.source.SourceRegistry
import com.example.engine.source.SourceResult
import com.example.engine.source.SourceSeriesMetadata
import com.example.engine.worker.ChapterMonitorWorker
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
class ChapterDetectionAndSourcesTest {

    private lateinit var database: MangaFlowDatabase
    private lateinit var privacyManager: PrivacyManager
    private lateinit var sourceRegistry: SourceRegistry
    private lateinit var detectionService: ChapterDetectionService
    private lateinit var context: Context

    // Mock Adapter for deterministic testing
    private val mockAdapter = object : SourceAdapter {
        override val sourceId: String = "test_source"
        override val displayName: String = "Test Source"
        override val baseUrl: String = "https://testsource.example.com"
        override val capabilities: SourceCapabilities = SourceCapabilities()

        var returnedResult: SourceResult<List<SourceChapter>> = SourceResult.Success(emptyList())

        override fun canHandle(url: String): Boolean = url.contains("testsource.example.com")

        override suspend fun fetchSeriesMetadata(sourceUrl: String): SourceResult<SourceSeriesMetadata> {
            return SourceResult.Success(SourceSeriesMetadata(title = "Test Manga", status = "ongoing"))
        }

        override suspend fun fetchChapters(sourceUrl: String): SourceResult<List<SourceChapter>> {
            return returnedResult
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MangaFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        privacyManager = PrivacyManager()
        sourceRegistry = SourceRegistry(listOf(mockAdapter))
        detectionService = ChapterDetectionService(
            seriesDao = database.seriesDao(),
            chapterDao = database.chapterDao(),
            notificationDao = database.notificationDao(),
            sourceRegistry = sourceRegistry,
            privacyManager = privacyManager,
            appContext = context
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testChapterNormalizer_handlesNumbersDecimalsAndSpecials() {
        // Standard chapter number
        val (id1, num1, title1) = ChapterNormalizer.normalizeChapter(null, "Chapter 104", "https://example.com/ch-104")
        assertEquals("104", id1)
        assertEquals(104f, num1)
        assertEquals("Chapter 104", title1)

        // Decimal chapter
        val (id2, num2, title2) = ChapterNormalizer.normalizeChapter("12.5", "Chapter 12.5 - Extra Lore", "https://example.com/12.5")
        assertEquals("12.5", id2)
        assertEquals(12.5f, num2)
        assertEquals("Chapter 12.5 - Extra Lore", title2)

        // Prologue
        val (id3, num3, title3) = ChapterNormalizer.normalizeChapter(null, "Prologue: In the Beginning", "https://example.com/prologue")
        assertEquals("prologue", id3)
        assertEquals(0.0f, num3)
        assertEquals("Prologue: In the Beginning", title3)

        // Side Story non-numeric
        val (id4, num4, title4) = ChapterNormalizer.normalizeChapter(null, "Side Story 3: The Aftermath", "https://example.com/side-3")
        assertEquals("side-story-3", id4)
        assertNull(num4)
        assertEquals("Side Story 3: The Aftermath", title4)
    }

    @Test
    fun testDuplicateChapterPrevention() {
        val existing = listOf(
            ChapterEntity(
                seriesId = 1L,
                chapterNumber = 1f,
                chapterIdentifier = "1",
                chapterTitle = "Chapter 1",
                chapterUrl = "https://example.com/ch-1"
            ),
            ChapterEntity(
                seriesId = 1L,
                chapterNumber = 2.5f,
                chapterIdentifier = "2.5",
                chapterTitle = "Chapter 2.5",
                chapterUrl = "https://example.com/ch-2.5"
            ),
            ChapterEntity(
                seriesId = 1L,
                chapterNumber = null,
                chapterIdentifier = "prologue",
                chapterTitle = "Prologue",
                chapterUrl = "https://example.com/prologue"
            )
        )

        val fetched = listOf(
            SourceChapter(identifier = "1", number = 1f, title = "Chapter 1 (Official)", url = "https://newdomain.com/ch-1"), // Duplicate by id & num
            SourceChapter(identifier = "2.5", number = 2.5f, title = "Chapter 2.5", url = "https://newdomain.com/ch-2.5"), // Duplicate by id & num
            SourceChapter(identifier = "prologue", number = null, title = "Prologue", url = "https://newdomain.com/prologue"), // Duplicate by title & id
            SourceChapter(identifier = "3", number = 3f, title = "Chapter 3 - The New World", url = "https://newdomain.com/ch-3") // Genuine new chapter
        )

        val unseen = ChapterNormalizer.findUnseenChapters(existing, fetched)
        assertEquals(1, unseen.size)
        assertEquals("3", unseen[0].identifier)
        assertEquals(3f, unseen[0].number)
    }

    @Test
    fun testNewChapterDetection_persistsAndUpdatesSeries() = runBlocking {
        val seriesId = database.seriesDao().insert(
            SeriesEntity(
                title = "Solo Leveling",
                sourceUrl = "https://testsource.example.com/solo",
                readingStatus = "READING",
                latestChapterIdentifier = "1",
                currentChapterNum = 1f,
                totalChapters = 1
            )
        )

        mockAdapter.returnedResult = SourceResult.Success(
            listOf(
                SourceChapter(identifier = "1", number = 1f, title = "Chapter 1", url = "https://testsource.example.com/solo/1"),
                SourceChapter(identifier = "2", number = 2f, title = "Chapter 2", url = "https://testsource.example.com/solo/2"),
                SourceChapter(identifier = "3", number = 3f, title = "Chapter 3", url = "https://testsource.example.com/solo/3")
            )
        )

        val result = detectionService.checkSeries(seriesId, force = true)
        assertEquals("SUCCESS", result.status)
        assertEquals(3, result.newChaptersCount)
        assertEquals("3", result.latestDetectedIdentifier)

        // Verify series was updated in database
        val updatedSeries = database.seriesDao().getSeriesByIdSync(seriesId)
        assertNotNull(updatedSeries)
        assertEquals("3", updatedSeries?.latestChapterIdentifier)
        assertTrue(updatedSeries?.hasUnreadNewChapter == true)

        // Verify chapter entities persisted
        val chapters = database.chapterDao().getChaptersForSeriesSync(seriesId)
        assertEquals(3, chapters.size)
        assertEquals("1", chapters[0].chapterIdentifier)
        assertEquals("2", chapters[1].chapterIdentifier)
        assertEquals("3", chapters[2].chapterIdentifier)

        // Verify notifications generated
        val notifs = database.notificationDao().getAllNotifications().first()
        assertEquals(3, notifs.size)
    }

    @Test
    fun testNotificationDeduplication_neverNotifiesTwiceForSameChapter() = runBlocking {
        val seriesId = database.seriesDao().insert(
            SeriesEntity(
                title = "Omniscient Reader",
                sourceUrl = "https://testsource.example.com/orv",
                readingStatus = "READING",
                latestChapterIdentifier = "10",
                currentChapterNum = 10f,
                totalChapters = 10
            )
        )

        mockAdapter.returnedResult = SourceResult.Success(
            listOf(
                SourceChapter(identifier = "11", number = 11f, title = "Chapter 11", url = "https://testsource.example.com/orv/11")
            )
        )

        // First check detects chapter 11 and queues 1 notification
        val res1 = detectionService.checkSeries(seriesId, force = true)
        assertEquals("SUCCESS", res1.status)
        assertEquals(1, res1.newChaptersCount)

        val notifs1 = database.notificationDao().getAllNotifications().first()
        assertEquals(1, notifs1.size)
        assertEquals("11", notifs1[0].chapterIdentifier)

        // Second check with same data: Chapter 11 is now in database, so it won't re-notify
        val res2 = detectionService.checkSeries(seriesId, force = true)
        assertEquals("NO_NEW_CHAPTERS", res2.status)
        assertEquals(0, res2.newChaptersCount)

        val notifs2 = database.notificationDao().getAllNotifications().first()
        assertEquals(1, notifs2.size) // Notification count unchanged
    }

    @Test
    fun testPrivateBrowsingIsolation_suppressesNotifications() = runBlocking {
        val seriesId = database.seriesDao().insert(
            SeriesEntity(
                title = "Secret Agent",
                sourceUrl = "https://testsource.example.com/secret",
                readingStatus = "READING",
                latestChapterIdentifier = "1"
            )
        )

        // Activate private browsing session
        privacyManager.enterPrivateSession()
        assertTrue(privacyManager.isPrivateSessionActive())

        mockAdapter.returnedResult = SourceResult.Success(
            listOf(
                SourceChapter(identifier = "2", number = 2f, title = "Chapter 2", url = "https://testsource.example.com/secret/2")
            )
        )

        detectionService.checkSeries(seriesId, force = true)

        // Notifications MUST be zero because private session was active
        val notifs = database.notificationDao().getAllNotifications().first()
        assertEquals(0, notifs.size)

        privacyManager.exitPrivateSession()
        assertFalse(privacyManager.isPrivateSessionActive())
    }

    @Test
    fun testPerSeriesNotificationDisabled_doesNotQueueNotification() = runBlocking {
        val seriesId = database.seriesDao().insert(
            SeriesEntity(
                title = "Muted Series",
                sourceUrl = "https://testsource.example.com/muted",
                readingStatus = "READING",
                notificationsEnabled = false // Notifications disabled for this series
            )
        )

        mockAdapter.returnedResult = SourceResult.Success(
            listOf(
                SourceChapter(identifier = "5", number = 5f, title = "Chapter 5", url = "https://testsource.example.com/muted/5")
            )
        )

        val res = detectionService.checkSeries(seriesId, force = true)
        assertEquals("SUCCESS", res.status)

        // Chapters are stored, but no notification created
        val notifs = database.notificationDao().getAllNotifications().first()
        assertEquals(0, notifs.size)
    }

    @Test
    fun testSourceErrorDistinction_networkAndUnavailable() = runBlocking {
        val seriesId = database.seriesDao().insert(
            SeriesEntity(
                title = "Failing Series",
                sourceUrl = "https://testsource.example.com/fail",
                readingStatus = "READING"
            )
        )

        // 1. Temporary Network Failure
        mockAdapter.returnedResult = SourceResult.NetworkError("Connection timed out")
        val netRes = detectionService.checkSeries(seriesId, force = true)
        assertEquals("NETWORK_ERROR", netRes.status)
        assertEquals("Connection timed out", netRes.errorMessage)

        val seriesAfterNet = database.seriesDao().getSeriesByIdSync(seriesId)
        assertEquals("NETWORK_ERROR", seriesAfterNet?.lastCheckStatus)

        // 2. HTTP 503 Source Unavailable
        mockAdapter.returnedResult = SourceResult.SourceUnavailable(503, "Service Maintenance")
        val unavailRes = detectionService.checkSeries(seriesId, force = true)
        assertEquals("SOURCE_UNAVAILABLE", unavailRes.status)

        val seriesAfter503 = database.seriesDao().getSeriesByIdSync(seriesId)
        assertEquals("SOURCE_UNAVAILABLE", seriesAfter503?.lastCheckStatus)
    }

    @Test
    fun testRateLimitBackoff_preventsAggressiveRetries() = runBlocking {
        val seriesId = database.seriesDao().insert(
            SeriesEntity(
                title = "Rate Limited Series",
                sourceUrl = "https://testsource.example.com/ratelimit",
                readingStatus = "READING"
            )
        )

        mockAdapter.returnedResult = SourceResult.RateLimited(retryAfterSeconds = 120L, message = "Too many requests")

        // First check triggers rate limit and sets backoff
        val res1 = detectionService.checkSeries(seriesId, force = true)
        assertEquals("RATE_LIMITED", res1.status)

        // Second check without force=true should be blocked by backoff
        val res2 = detectionService.checkSeries(seriesId, force = false)
        assertEquals("RATE_LIMITED", res2.status)
        assertTrue(res2.errorMessage?.contains("backoff active") == true)
    }

    @Test
    fun testRssFeedParser_parsesStandardXmlFeed() {
        val rssAdapter = RssFeedSourceAdapter()
        val sampleRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <title>The Great Mage Returns</title>
                    <description>Updates feed for The Great Mage</description>
                    <item>
                        <title>Chapter 180 - The Dragon Archmage</title>
                        <link>https://example.com/mage/ch-180</link>
                        <pubDate>Mon, 27 Oct 2026 12:00:00 GMT</pubDate>
                    </item>
                    <item>
                        <title>Chapter 179</title>
                        <link>https://example.com/mage/ch-179</link>
                        <pubDate>Mon, 20 Oct 2026 12:00:00 GMT</pubDate>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val (title, _, chapters) = rssAdapter.parseFeed(sampleRss)
        assertEquals("The Great Mage Returns", title)
        assertEquals(2, chapters.size)
        assertEquals("179", chapters[0].identifier)
        assertEquals(179f, chapters[0].number)
        assertEquals("180", chapters[1].identifier)
        assertEquals(180f, chapters[1].number)
    }

    @Test
    fun testMangaDexSourceAdapter_canHandleAndExtractUuid() {
        val mdAdapter = MangaDexSourceAdapter()
        val testUrl = "https://mangadex.org/title/32d76d19-8a05-4db0-9fc2-e0b0648fe9d0/solo-leveling"
        assertTrue(mdAdapter.canHandle(testUrl))
        val uuid = mdAdapter.extractMangaId(testUrl)
        assertEquals("32d76d19-8a05-4db0-9fc2-e0b0648fe9d0", uuid)
    }
}
