package com.example.engine.tracker

import com.example.data.tracker.ManhwaTrackerService
import com.example.domain.model.DetectedManhwa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface BrowserTrackerBridge {
    val currentDetectedManhwa: StateFlow<DetectedManhwa?>

    /**
     * Identifies if the given URL corresponds to a reading chapter/episode
     * rather than an index, search, or general webpage.
     */
    fun isReadingPage(url: String): Boolean

    /**
     * Extracts series and chapter metadata from the active URL and page title.
     */
    fun identifySeriesAndChapter(url: String, pageTitle: String?): DetectedManhwa?

    /** Invoked after load to update the transient detection banner only; it never saves progress. */
    fun onPageLoaded(url: String, pageTitle: String?, isPrivate: Boolean)

    /**
     * Prepares a destination URL for opening a tracked series/chapter in the browser.
     */
    fun getChapterNavigationUrl(chapterUrl: String): String

    /**
     * Clears current detection state.
     */
    fun clearDetection()
}

class BrowserTrackerBridgeImpl(
    private val manhwaTracker: ManhwaTracker,
    @Suppress("unused") private val trackerService: ManhwaTrackerService,
    @Suppress("unused") private val scope: CoroutineScope
) : BrowserTrackerBridge {

    override val currentDetectedManhwa: StateFlow<DetectedManhwa?> = manhwaTracker.currentDetectedManhwa

    override fun isReadingPage(url: String): Boolean =
        !url.startsWith("about:", ignoreCase = true) && identifySeriesAndChapter(url, null) != null

    override fun identifySeriesAndChapter(url: String, pageTitle: String?): DetectedManhwa? {
        return manhwaTracker.detectFromUrl(url, pageTitle)
    }

    override fun onPageLoaded(url: String, pageTitle: String?, isPrivate: Boolean) {
        manhwaTracker.onUrlLoaded(url, pageTitle, isPrivate)
    }

    override fun getChapterNavigationUrl(chapterUrl: String): String {
        return if (chapterUrl.startsWith("http://") || chapterUrl.startsWith("https://")) {
            chapterUrl
        } else {
            "https://$chapterUrl"
        }
    }

    override fun clearDetection() {
        manhwaTracker.clearDetection()
    }
}
