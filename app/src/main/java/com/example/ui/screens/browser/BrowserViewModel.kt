package com.example.ui.screens.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MangaFlowApp
import com.example.data.local.entity.SeriesEntity
import com.example.domain.model.BrowserTab
import com.example.domain.model.DetectedManhwa
import com.example.domain.model.OcrBox
import com.example.engine.browser.BrowserSessionManager
import com.example.engine.browser.BrowserPreferences
import com.example.engine.browser.ReaderArticle
import com.example.engine.browser.ReaderExtractionResult
import com.example.engine.browser.SearchEngine
import com.example.engine.browser.UrlParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrowserViewModel : ViewModel() {

    private val app = MangaFlowApp.instance
    private val sessionManager = app.browserSessionManager
    private val filterEngine = app.contentFilterEngine
    private val trackerBridge = app.browserTrackerBridge
    private val ocrEngine = app.ocrTranslationEngine
    private val browserRepo = app.browserRepository
    private val manhwaRepo = app.manhwaRepository
    private val trackerService = app.trackerService

    val normalTabs = sessionManager.normalTabs
    val privateTabs = sessionManager.privateTabs
    val isPrivateMode = sessionManager.isPrivateActive
    val blockedAdsCount = filterEngine.totalBlockedAds
    val trackersBlockedCount = filterEngine.totalTrackersBlocked
    val userAllowlist = filterEngine.userAllowlist
    val disabledSites = filterEngine.disabledSites
    val detectedManhwa = trackerBridge.currentDetectedManhwa
    val canRestoreTab = sessionManager.canRestoreTab
    val privateSessionGeneration = sessionManager.privateSessionGeneration
    val browserPreferences = app.browserSettingsStore.settings

    private val _searchEngine = MutableStateFlow(SearchEngine.DUCKDUCKGO)
    val searchEngine: StateFlow<SearchEngine> = _searchEngine.asStateFlow()

    private val _addressInput = MutableStateFlow("")
    val addressInput: StateFlow<String> = _addressInput.asStateFlow()

    private val _isEditingAddress = MutableStateFlow(false)
    val isEditingAddress: StateFlow<Boolean> = _isEditingAddress.asStateFlow()

    private val _showTabSwitcher = MutableStateFlow(false)
    val showTabSwitcher: StateFlow<Boolean> = _showTabSwitcher.asStateFlow()

    private val _showShieldSheet = MutableStateFlow(false)
    val showShieldSheet: StateFlow<Boolean> = _showShieldSheet.asStateFlow()

    private val _showHistorySheet = MutableStateFlow(false)
    val showHistorySheet: StateFlow<Boolean> = _showHistorySheet.asStateFlow()

    private val _showBookmarksSheet = MutableStateFlow(false)
    val showBookmarksSheet: StateFlow<Boolean> = _showBookmarksSheet.asStateFlow()

    // Find in Page state
    private val _showFindInPage = MutableStateFlow(false)
    val showFindInPage: StateFlow<Boolean> = _showFindInPage.asStateFlow()

    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> = _findQuery.asStateFlow()

    private val _findActiveMatch = MutableStateFlow(0)
    val findActiveMatch: StateFlow<Int> = _findActiveMatch.asStateFlow()

    private val _findTotalMatches = MutableStateFlow(0)
    val findTotalMatches: StateFlow<Int> = _findTotalMatches.asStateFlow()

    private val _downloadStatus = MutableStateFlow<String?>(null)
    val downloadStatus: StateFlow<String?> = _downloadStatus.asStateFlow()

    private val _readerArticle = MutableStateFlow<ReaderArticle?>(null)
    val readerArticle: StateFlow<ReaderArticle?> = _readerArticle.asStateFlow()

    private val _readerError = MutableStateFlow<String?>(null)
    val readerError: StateFlow<String?> = _readerError.asStateFlow()

    private val _detectedSeriesMatch = MutableStateFlow<com.example.data.local.entity.SeriesEntity?>(null)
    val detectedSeriesMatch: StateFlow<com.example.data.local.entity.SeriesEntity?> = _detectedSeriesMatch.asStateFlow()

    private val _detectedUnreadCount = MutableStateFlow(0)
    val detectedUnreadCount: StateFlow<Int> = _detectedUnreadCount.asStateFlow()

    private val _webViewGeneration = MutableStateFlow(0)
    val webViewGeneration: StateFlow<Int> = _webViewGeneration.asStateFlow()

    // OCR State
    private val _isOcrActive = MutableStateFlow(false)
    val isOcrActive: StateFlow<Boolean> = _isOcrActive.asStateFlow()

    private val _isOcrLoading = MutableStateFlow(false)
    val isOcrLoading: StateFlow<Boolean> = _isOcrLoading.asStateFlow()

    private val _ocrBoxes = MutableStateFlow<List<OcrBox>>(emptyList())
    val ocrBoxes: StateFlow<List<OcrBox>> = _ocrBoxes.asStateFlow()

    private var lastOcrBitmap: Bitmap? = null

    // Webpage Translation State
    private val _isPageTranslated = MutableStateFlow(false)
    val isPageTranslated: StateFlow<Boolean> = _isPageTranslated.asStateFlow()

    private val _isPageTranslating = MutableStateFlow(false)
    val isPageTranslating: StateFlow<Boolean> = _isPageTranslating.asStateFlow()

    private val _pageTranslationError = MutableStateFlow<String?>(null)
    val pageTranslationError: StateFlow<String?> = _pageTranslationError.asStateFlow()

    private val _selectedTextTranslation = MutableStateFlow<com.example.engine.translation.TranslationResponse?>(null)
    val selectedTextTranslation: StateFlow<com.example.engine.translation.TranslationResponse?> = _selectedTextTranslation.asStateFlow()

    private var pageTranslationJob: kotlinx.coroutines.Job? = null

    val translationManager = app.translationManager

    val currentTab: StateFlow<BrowserTab?> = combine(
        sessionManager.normalTabs,
        sessionManager.privateTabs,
        sessionManager.currentTabId,
        sessionManager.isPrivateActive
    ) { normal, priv, currentId, inPriv ->
        val list = if (inPriv) priv else normal
        list.find { it.id == currentId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionManager.getCurrentTab())

    val isCurrentUrlBookmarked: StateFlow<Boolean> = combine(
        currentTab,
        browserRepo.bookmarks
    ) { tab, bookmarks ->
        val url = tab?.url ?: return@combine false
        bookmarks.any { it.url == url }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            app.browserSettingsStore.settings.collectLatest { settings ->
                _searchEngine.value = SearchEngine.fromDisplayName(settings.searchEngine)
                sessionManager.updateBrowserDefaults(settings.homepage, settings.desktopSiteByDefault)
            }
        }
        viewModelScope.launch {
            detectedManhwa.collectLatest { detected ->
                if (detected == null || isPrivateMode.value) {
                    _detectedSeriesMatch.value = null
                    _detectedUnreadCount.value = 0
                } else {
                    val match = try { trackerService.findMatchingSeries(detected) } catch (_: Exception) { null }
                    _detectedSeriesMatch.value = match
                    _detectedUnreadCount.value = if (match == null) 0 else try {
                        trackerService.getChaptersForSeriesSync(match.id).count { !it.isRead }
                    } catch (_: Exception) { 0 }
                }
            }
        }
    }

    fun setSearchEngine(engine: SearchEngine) {
        _searchEngine.value = engine
        app.browserSettingsStore.setSearchEngine(engine.displayName)
    }

    fun onAddressInputChanged(newText: String) {
        _addressInput.value = newText
    }

    fun startEditingAddress() {
        val activeUrl = currentTab.value?.url ?: ""
        _addressInput.value = if (activeUrl.startsWith("about:blank")) "" else activeUrl
        _isEditingAddress.value = true
    }

    fun stopEditingAddress() {
        _isEditingAddress.value = false
    }

    fun openNormalAddress(urlOrQuery: String): String {
        val current = sessionManager.getCurrentTab()
        if (current?.isPrivate == true) {
            val normalTab = sessionManager.normalTabs.value.firstOrNull()
            if (normalTab != null) sessionManager.switchTab(normalTab.id)
            else sessionManager.openNewTab(isPrivate = false)
        }
        return submitAddress(urlOrQuery)
    }

    fun submitAddress(urlOrQuery: String): String {
        _isEditingAddress.value = false
        _isPageTranslated.value = false
        _pageTranslationError.value = null
        val finalUrl = UrlParser.parse(urlOrQuery, _searchEngine.value)
        sessionManager.updateCurrentTab {
            it.copy(
                url = finalUrl,
                title = "Loading...",
                isLoading = true,
                progress = 10,
                errorMessage = null,
                isCrashed = false
            )
        }
        return finalUrl
    }

    fun loadUrl(url: String) {
        _isPageTranslated.value = false
        _pageTranslationError.value = null
        sessionManager.updateCurrentTab {
            it.copy(
                url = url,
                title = "Loading...",
                isLoading = true,
                progress = 10,
                errorMessage = null,
                isCrashed = false
            )
        }
    }

    fun stopLoading() {
        sessionManager.updateCurrentTab { it.copy(isLoading = false) }
    }

    fun reload() {
        val tab = currentTab.value ?: return
        _isPageTranslated.value = false
        _pageTranslationError.value = null
        sessionManager.reloadTab(tab.id)
    }

    fun toggleDesktopMode() {
        val tab = currentTab.value ?: return
        sessionManager.toggleDesktopMode(tab.id)
    }

    fun openNewTab(url: String? = null, isPrivate: Boolean = isPrivateMode.value): BrowserTab? {
        val tab = sessionManager.openNewTab(url ?: browserPreferences.value.homepage, isPrivate)
        _showTabSwitcher.value = false
        return tab
    }

    fun saveWebViewState(tabId: String, state: Bundle) = sessionManager.saveWebViewState(tabId, state)

    fun getWebViewState(tabId: String): Bundle? = sessionManager.getWebViewState(tabId)

    fun onPrivateWebViewCreated(profileName: String) = sessionManager.onPrivateWebViewCreated(profileName)

    fun shouldPurgePrivateProfile(profileName: String): Boolean = sessionManager.shouldPurgePrivateProfile(profileName)

    fun onBrowserWebViewReleased(privateProfileName: String?) =
        sessionManager.onBrowserWebViewReleased(privateProfileName)

    fun switchTab(tabId: String) {
        sessionManager.switchTab(tabId)
        _showTabSwitcher.value = false
        closeFindInPage()
        closeReaderMode()
        _isPageTranslated.value = false
        _pageTranslationError.value = null
    }

    fun closeTab(tabId: String, context: Context) {
        val wasPrivate = sessionManager.privateTabs.value.any { it.id == tabId }
        sessionManager.closeTab(tabId, context)
        if (wasPrivate && sessionManager.privateTabs.value.isEmpty()) {
            app.browserDownloadManager.clearPrivateSessionMetadata()
        }
    }

    fun restoreLastClosedTab() {
        sessionManager.restoreLastClosedTab()
    }

    fun closeAllPrivateTabs(context: Context) {
        sessionManager.closeAllPrivateTabs(context)
        app.browserDownloadManager.clearPrivateSessionMetadata()
    }

    fun togglePrivateMode(isPrivate: Boolean) {
        sessionManager.setPrivateMode(isPrivate)
    }

    fun setShowTabSwitcher(show: Boolean) {
        _showTabSwitcher.value = show
    }

    fun setShowShieldSheet(show: Boolean) {
        _showShieldSheet.value = show
    }

    fun setShowHistorySheet(show: Boolean) {
        _showHistorySheet.value = show
    }

    fun setShowBookmarksSheet(show: Boolean) {
        _showBookmarksSheet.value = show
    }

    // Find in Page
    fun startFindInPage() {
        _showFindInPage.value = true
        _findQuery.value = ""
        _findActiveMatch.value = 0
        _findTotalMatches.value = 0
    }

    fun onFindQueryChanged(query: String) {
        _findQuery.value = query
    }

    fun updateFindMatchesCount(active: Int, total: Int) {
        _findActiveMatch.value = active
        _findTotalMatches.value = total
        currentTab.value?.let {
            sessionManager.updateFindMatches(it.id, _findQuery.value, active, total)
        }
    }

    fun closeFindInPage() {
        _showFindInPage.value = false
        _findQuery.value = ""
        _findActiveMatch.value = 0
        _findTotalMatches.value = 0
        currentTab.value?.let {
            sessionManager.clearFind(it.id)
        }
    }

    fun setDownloadStatus(message: String?) {
        _downloadStatus.value = message
    }

    fun handleRendererCrash() {
        val tab = currentTab.value ?: return
        sessionManager.handleRendererCrash(tab.id)
        _webViewGeneration.value += 1
    }

    fun setTabError(errorMessage: String) {
        val tab = currentTab.value ?: return
        sessionManager.setTabError(tab.id, errorMessage)
    }

    fun setReaderExtractionResult(result: ReaderExtractionResult) {
        when (result) {
            is ReaderExtractionResult.Success -> {
                _readerError.value = null
                _readerArticle.value = result.article
                sessionManager.updateCurrentTab { it.copy(isReaderModeActive = true) }
            }
            is ReaderExtractionResult.Failure -> {
                _readerArticle.value = null
                _readerError.value = result.reason
                sessionManager.updateCurrentTab { it.copy(isReaderModeActive = false) }
            }
        }
    }

    fun closeReaderMode() {
        _readerArticle.value = null
        _readerError.value = null
        sessionManager.updateCurrentTab { it.copy(isReaderModeActive = false) }
    }

    fun dismissReaderError() {
        _readerError.value = null
    }

    fun runOcrOnSnapshot(bitmap: Bitmap?) {
        if (bitmap == null) return
        lastOcrBitmap = bitmap
        _isOcrLoading.value = true
        _isOcrActive.value = true
        viewModelScope.launch {
            try {
                val boxes = ocrEngine.analyzeAndTranslate(bitmap, isPrivate = isPrivateMode.value)
                _ocrBoxes.value = boxes
            } finally {
                _isOcrLoading.value = false
            }
        }
    }

    fun retryOcr() {
        val bmp = lastOcrBitmap ?: return
        runOcrOnSnapshot(bmp)
    }

    fun dismissOcr() {
        _isOcrActive.value = false
        _ocrBoxes.value = emptyList()
    }

    fun getActiveTranslationProviderName(): String {
        return translationManager.getActiveProvider().displayName
    }

    fun startPageTranslation(extractedTexts: List<String>, onReady: (Map<String, String>) -> Unit) {
        if (extractedTexts.isEmpty()) {
            _pageTranslationError.value = "No readable text found on page to translate"
            return
        }
        pageTranslationJob?.cancel()
        _isPageTranslating.value = true
        _pageTranslationError.value = null

        pageTranslationJob = viewModelScope.launch {
            try {
                when (val result = translationManager.translateBatch(extractedTexts, isPrivate = isPrivateMode.value)) {
                    is com.example.engine.translation.BatchTranslationResult.Success -> {
                        _isPageTranslated.value = true
                        _pageTranslationError.value = null
                        onReady(result.translations)
                    }
                    is com.example.engine.translation.BatchTranslationResult.PrivateSessionRestricted -> {
                        _pageTranslationError.value = "Translation Restricted: External cloud translation is disabled in Private Browsing to protect your privacy."
                    }
                    is com.example.engine.translation.BatchTranslationResult.CloudTranslationNotPermitted -> {
                        _pageTranslationError.value = "Cloud translation not permitted by user settings. Enable 'Allow Cloud Translation' in Settings."
                    }
                    is com.example.engine.translation.BatchTranslationResult.Error -> {
                        _pageTranslationError.value = "Translation failed: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _pageTranslationError.value = "Error translating page: ${e.localizedMessage ?: "Unknown error"}"
                }
            } finally {
                _isPageTranslating.value = false
            }
        }
    }

    fun cancelPageTranslation() {
        pageTranslationJob?.cancel()
        _isPageTranslating.value = false
    }

    fun setPageRestored() {
        _isPageTranslated.value = false
        _pageTranslationError.value = null
    }

    fun dismissTranslationError() {
        _pageTranslationError.value = null
    }

    fun translateSelectedText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            when (val result = translationManager.translateText(text, isPrivate = isPrivateMode.value)) {
                is com.example.engine.translation.TranslationResult.Success -> {
                    _selectedTextTranslation.value = result.response
                }
                is com.example.engine.translation.TranslationResult.PrivateSessionRestricted -> {
                    _pageTranslationError.value = "Cloud translation blocked in Private Browsing."
                }
                is com.example.engine.translation.TranslationResult.CloudTranslationNotPermitted -> {
                    _pageTranslationError.value = "Enable cloud translation in Settings to use external providers."
                }
                is com.example.engine.translation.TranslationResult.Error -> {
                    _pageTranslationError.value = "Translation error: ${result.message}"
                }
            }
        }
    }

    fun clearSelectedTextTranslation() {
        _selectedTextTranslation.value = null
    }

    fun saveDetectedToLibrary(detected: DetectedManhwa) {
        if (isPrivateMode.value) return
        viewModelScope.launch {
            manhwaRepo.addManhwa(
                SeriesEntity(
                    title = detected.title,
                    sourceUrl = detected.sourceUrl,
                    currentChapterNum = 0f,
                    latestChapterIdentifier = detected.chapterIdentifier,
                    lastReadChapterIdentifier = "0",
                    totalChapters = 0,
                    readingStatus = "READING",
                    lastReadTimestamp = 0L
                )
            )
            val match = trackerService.findMatchingSeries(detected)
            _detectedSeriesMatch.value = match
            _detectedUnreadCount.value = match?.let { trackerService.getChaptersForSeriesSync(it.id).count { chapter -> !chapter.isRead } } ?: 0
        }
    }

    fun confirmDetectedChapterRead(detected: DetectedManhwa) {
        if (isPrivateMode.value) return
        val series = _detectedSeriesMatch.value ?: return
        viewModelScope.launch {
            try {
                trackerService.markDetectedChapterRead(series.id, detected)
                trackerBridge.clearDetection()
            } catch (e: Exception) {
                _downloadStatus.value = "Progress was not updated: ${e.message ?: "Please try again"}"
            }
        }
    }

    fun toggleBookmarkCurrentPage(url: String, title: String) {
        viewModelScope.launch {
            browserRepo.toggleBookmark(url, title, false)
        }
    }

    fun toggleSiteFiltering(host: String, enabled: Boolean) {
        filterEngine.setSiteFilteringEnabled(host, enabled)
    }

    fun toggleAllowlist(host: String) {
        if (filterEngine.isDomainAllowlisted(host)) {
            filterEngine.removeFromAllowlist(host)
        } else {
            filterEngine.addToAllowlist(host)
        }
    }

    fun isDomainAllowlisted(host: String?): Boolean = filterEngine.isDomainAllowlisted(host)

    fun isFilteringEnabledForSite(host: String?): Boolean = filterEngine.isFilteringEnabledForSite(host)
}
