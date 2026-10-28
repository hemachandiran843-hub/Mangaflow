package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MangaFlowDatabase
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.SeriesEntity
import com.example.data.tracker.ManhwaTrackerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ManhwaTrackerServiceTest {

    private lateinit var database: MangaFlowDatabase
    private lateinit var trackerService: ManhwaTrackerService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MangaFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        trackerService = ManhwaTrackerService(
            seriesDao = database.seriesDao(),
            chapterDao = database.chapterDao(),
            readingProgressDao = database.readingProgressDao(),
            readingSessionDao = database.readingSessionDao(),
            notificationDao = database.notificationDao(),
            database = database
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testDatabaseInitialization_allEntitiesPersist() = runBlocking {
        // Verify series dao
        val seriesId = trackerService.addSeriesManually(
            title = "Solo Leveling",
            sourceUrl = "https://example.com/solo",
            initialChapterCount = 5
        )
        assertTrue(seriesId > 0)

        val series = trackerService.getSeriesByIdSync(seriesId)
        assertNotNull(series)
        assertEquals("Solo Leveling", series?.title)

        // Verify chapters were automatically seeded
        val chapters = trackerService.getChaptersForSeriesSync(seriesId)
        assertEquals(5, chapters.size)
        assertEquals("1", chapters[0].chapterIdentifier)
    }

    @Test
    fun testAddSeriesManuallyAndEditMetadata() = runBlocking {
        val seriesId = trackerService.addSeriesManually(
            title = "Omniscient Reader",
            alternateTitles = "ORV, Three Ways to Survive",
            sourceUrl = "https://webtoons.com/orv",
            coverUrl = "https://example.com/cover.jpg",
            readingStatus = "READING",
            rating = 4.8f,
            description = "A gripping survival story.",
            initialChapterCount = 10
        )

        val initial = trackerService.getSeriesByIdSync(seriesId)
        assertNotNull(initial)
        assertEquals("ORV, Three Ways to Survive", initial?.alternateTitles)
        assertEquals(4.8f, initial?.rating)

        // Edit metadata
        trackerService.editSeriesMetadata(
            seriesId = seriesId,
            title = "Omniscient Reader's Viewpoint",
            alternateTitles = "ORV",
            coverUrl = "https://example.com/new_cover.jpg",
            readingStatus = "ON_HOLD",
            rating = 5.0f,
            description = "Dokja Kim's masterpiece journey.",
            notes = "Must read season 2"
        )

        val updated = trackerService.getSeriesByIdSync(seriesId)
        assertEquals("Omniscient Reader's Viewpoint", updated?.title)
        assertEquals("ON_HOLD", updated?.readingStatus)
        assertEquals(5.0f, updated?.rating)
        assertEquals("Must read season 2", updated?.notes)
    }

    @Test
    fun testStatusTransitionsAndMarkCompleted() = runBlocking {
        val seriesId = trackerService.addSeriesManually(
            title = "The Boxer",
            sourceUrl = "https://example.com/boxer",
            readingStatus = "READING",
            initialChapterCount = 3
        )

        // Initial status
        assertEquals("READING", trackerService.getSeriesByIdSync(seriesId)?.readingStatus)

        // Move to Plan to Read
        trackerService.changeReadingStatus(seriesId, "PLAN_TO_READ")
        assertEquals("PLAN_TO_READ", trackerService.getSeriesByIdSync(seriesId)?.readingStatus)

        // Mark Completed with chapters marked read
        trackerService.markSeriesCompleted(seriesId, markAllChaptersRead = true)
        val completedSeries = trackerService.getSeriesByIdSync(seriesId)
        assertEquals("COMPLETED", completedSeries?.readingStatus)

        val chapters = trackerService.getChaptersForSeriesSync(seriesId)
        assertTrue(chapters.all { it.isRead })

        // Move to Dropped
        trackerService.moveSeriesToDropped(seriesId)
        assertEquals("DROPPED", trackerService.getSeriesByIdSync(seriesId)?.readingStatus)
    }

    @Test
    fun testNonNumericChapterSupport() = runBlocking {
        val seriesId = trackerService.addSeriesManually(
            title = "Tower of God Special",
            sourceUrl = "https://example.com/tog",
            initialChapterCount = 0
        )

        // Insert non-numeric chapters: Prologue, Side Story A, Epilogue
        val prologue = ChapterEntity(
            seriesId = seriesId,
            chapterNumber = null,
            chapterIdentifier = "Prologue",
            chapterTitle = "The Beginning of the Tower",
            chapterUrl = "https://example.com/tog/prologue",
            isRead = false,
            firstDetectedAt = System.currentTimeMillis()
        )
        val chId = database.chapterDao().insert(prologue)
        assertTrue(chId > 0)

        val sideStory = ChapterEntity(
            seriesId = seriesId,
            chapterNumber = null,
            chapterIdentifier = "Side-Story-1",
            chapterTitle = "Rak's Hunting Trip",
            chapterUrl = "https://example.com/tog/side-story-1",
            isRead = false,
            firstDetectedAt = System.currentTimeMillis()
        )
        database.chapterDao().insert(sideStory)

        val retrieved = trackerService.getChaptersForSeriesSync(seriesId)
        assertEquals(2, retrieved.size)
        assertNull(retrieved[0].chapterNumber)
        assertEquals("Prologue", retrieved[0].chapterIdentifier)
        assertNull(retrieved[1].chapterNumber)
        assertEquals("Side-Story-1", retrieved[1].chapterIdentifier)

        // Mark non-numeric chapter as read
        trackerService.markChapterReadState(seriesId, chId, true)
        val updatedCh = database.chapterDao().getChapterById(chId)
        assertTrue(updatedCh?.isRead == true)
    }

    @Test
    fun testDuplicatePrevention() = runBlocking {
        val seriesId = trackerService.addSeriesManually(
            title = "Nano Machine",
            sourceUrl = "https://example.com/nano",
            initialChapterCount = 2
        )

        // Adding again with same URL updates rather than creates duplicate
        val sameId = trackerService.addSeriesManually(
            title = "Nano Machine (Official)",
            sourceUrl = "https://example.com/nano",
            initialChapterCount = 2
        )
        assertEquals(seriesId, sameId)

        val all = trackerService.allSeries.first()
        assertEquals(1, all.size)
    }

    @Test
    fun testReadingProgressApiValidationAndThreshold() = runBlocking {
        val seriesId = trackerService.addSeriesManually(
            title = "Return of Mount Hua Sect",
            sourceUrl = "https://example.com/mount-hua",
            initialChapterCount = 1
        )
        val chapter = trackerService.getChaptersForSeriesSync(seriesId).first()

        // 1. Validation test: Invalid series ID throws IllegalArgumentException
        try {
            trackerService.updateReadingProgress(seriesId = 9999L, chapterId = chapter.id, pageIndex = 5, totalPages = 20)
            fail("Expected IllegalArgumentException for invalid series ID")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        // 2. Validation test: Chapter not matching series throws IllegalArgumentException
        try {
            trackerService.updateReadingProgress(seriesId = seriesId, chapterId = 8888L, pageIndex = 5, totalPages = 20)
            fail("Expected IllegalArgumentException for non-existent chapter ID")
        } catch (_: IllegalArgumentException) {
            // Expected
        }

        // 3. Early progress (page 5 of 30 = 20%) should NOT mark chapter as completed
        trackerService.updateReadingProgress(seriesId = seriesId, chapterId = chapter.id, pageIndex = 5, totalPages = 30)
        val chEarly = database.chapterDao().getChapterById(chapter.id)
        assertFalse(chEarly?.isRead == true)

        // 4. Progress >= 90% (page 28 of 30 = 96%) SHOULD mark chapter as completed
        trackerService.updateReadingProgress(seriesId = seriesId, chapterId = chapter.id, pageIndex = 28, totalPages = 30)
        val chComplete = database.chapterDao().getChapterById(chapter.id)
        assertTrue(chComplete?.isRead == true)
    }

    @Test
    fun testDistinctReadingStatistics_noDoubleCounting() = runBlocking {
        val seriesId = trackerService.addSeriesManually(
            title = "Eleceed",
            sourceUrl = "https://example.com/eleceed",
            initialChapterCount = 5
        )
        val chapters = trackerService.getChaptersForSeriesSync(seriesId)
        val ch1 = chapters[0]

        // Mark ch1 as read
        trackerService.markChapterReadState(seriesId, ch1.id, true)
        var stats = trackerService.getReadingStatistics().first()
        assertEquals(1, stats.totalChaptersRead)
        assertEquals(4, stats.totalUnreadChapters)
        assertEquals(0f, stats.totalHoursRead, 0f)

        // Mark ch1 as unread
        trackerService.markChapterReadState(seriesId, ch1.id, false)
        stats = trackerService.getReadingStatistics().first()
        assertEquals(0, stats.totalChaptersRead)
        assertEquals(5, stats.totalUnreadChapters)

        // Mark ch1 as read again (must not double count)
        trackerService.markChapterReadState(seriesId, ch1.id, true)
        trackerService.markChapterReadState(seriesId, ch1.id, true) // redundant call
        stats = trackerService.getReadingStatistics().first()
        assertEquals(1, stats.totalChaptersRead)
    }

    @Test
    fun testLibraryJsonExportAndImportRoundTrip() = runBlocking {
        val seriesId = trackerService.addSeriesManually(
            title = "Legend of the Northern Blade",
            alternateTitles = "Bukgeomjeon",
            sourceUrl = "https://example.com/northern-blade",
            readingStatus = "READING",
            rating = 4.9f,
            description = "Jin Mu-Won restores the Northern Heavenly Sect.",
            initialChapterCount = 3
        )
        val chapters = trackerService.getChaptersForSeriesSync(seriesId)
        trackerService.markChapterReadState(seriesId, chapters[0].id, true)

        // Export to JSON
        val exportedJson = trackerService.exportLibraryJson()
        assertNotNull(exportedJson)
        assertTrue(exportedJson.contains("Legend of the Northern Blade"))
        assertTrue(exportedJson.contains("Bukgeomjeon"))

        // Clear all data
        trackerService.clearAllData()
        assertEquals(0, trackerService.allSeries.first().size)

        // Import back from JSON
        val result = trackerService.importLibraryJson(exportedJson)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val restoredList = trackerService.allSeries.first()
        assertEquals(1, restoredList.size)
        val restored = restoredList[0]
        assertEquals("Legend of the Northern Blade", restored.title)
        assertEquals("Bukgeomjeon", restored.alternateTitles)
        assertEquals("READING", restored.readingStatus)

        val restoredChapters = trackerService.getChaptersForSeriesSync(restored.id)
        assertEquals(3, restoredChapters.size)
        assertTrue(restoredChapters[0].isRead)
        assertFalse(restoredChapters[1].isRead)
    }

    @Test
    fun testImportPreviewValidatesAndSkipsDuplicatesWithoutOverwriting() = runBlocking {
        val json = """
            {
              "format":"mangaflow-library",
              "version":3,
              "series":[
                {"title":"New Series","sourceUrl":"https://example.com/new-series","readingStatus":"READING","chapters":[]},
                {"title":"Bad URL","sourceUrl":"javascript:alert(1)","readingStatus":"READING","chapters":[]}
              ]
            }
        """.trimIndent()

        val preview = trackerService.previewLibraryJson(json).getOrThrow()
        assertEquals(2, preview.totalEntries)
        assertEquals(1, preview.importableCount)
        assertEquals(1, preview.invalidEntries.size)
        assertEquals(1, trackerService.applyLibraryImport(preview).getOrThrow())

        trackerService.changeReadingStatus(
            database.seriesDao().findBySourceUrl("https://example.com/new-series")!!.id,
            "COMPLETED"
        )
        val duplicatePreview = trackerService.previewLibraryJson(json).getOrThrow()
        assertEquals(0, duplicatePreview.importableCount)
        assertEquals(1, duplicatePreview.duplicateCount)
        assertEquals(0, trackerService.applyLibraryImport(duplicatePreview).getOrThrow())
        assertEquals("COMPLETED", database.seriesDao().findBySourceUrl("https://example.com/new-series")!!.readingStatus)

        assertTrue(trackerService.previewLibraryJson("{ malformed json").isFailure)
    }
}
