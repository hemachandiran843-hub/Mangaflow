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

    /**
     * Invoked when a webpage finishes loading in the browser.
     * Synchronizes reading progress with the library unless browsing in an isolated private tab.
     */
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
    private val trackerService: ManhwaTrackerService,
    private val scope: CoroutineScope
) : BrowserTrackerBridge {

    override val currentDetectedManhwa: StateFlow<DetectedManhwa?> = manhwaTracker.currentDetectedManhwa

    override fun isReadingPage(url: String): Boolean {
        if (url.startsWith("about:") || url.isBlank()) return false
        val lower = url.lowercase()
        return lower.contains("/chapter") ||
                lower.contains("/ch-") ||
                lower.contains("/ch.") ||
                lower.contains("/read/") ||
                lower.contains("episode_no=") ||
                lower.contains("episodeno=")
    }

    override fun identifySeriesAndChapter(url: String, pageTitle: String?): DetectedManhwa? {
        return manhwaTracker.detectFromUrl(url, pageTitle)
    }

    override fun onPageLoaded(url: String, pageTitle: String?, isPrivate: Boolean) {
        // If private tab, strictly avoid updating tracker or persisting reading state
        if (isPrivate) {
            manhwaTracker.clearDetection()
            return
        }

        // Delegate to tracker for detection and database sync
        manhwaTracker.onUrlLoaded(url, pageTitle, isPrivate = false)
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
