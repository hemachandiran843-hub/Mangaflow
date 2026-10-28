package com.example.ui.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MangaFlowApp
import com.example.engine.filter.ContentFilterEngine
import com.example.engine.browser.BrowserPreferences
import com.example.engine.filter.FilterUpdateResult
import com.example.ui.theme.MangaAmber
import com.example.ui.theme.MangaAmethyst
import com.example.ui.theme.MangaPrimary
import com.example.ui.theme.MangaSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppThemeState(
    val isDark: Boolean = true,
    val themeMode: String = "DARK", // DARK, LIGHT
    val accentIndex: Int = 0,
    val uiDensity: String = "NORMAL" // COMPACT, NORMAL, SPACIOUS
) {
    val accentColor: Color
        get() = when (accentIndex) {
            1 -> MangaSecondary
            2 -> MangaAmethyst
            3 -> MangaAmber
            else -> MangaPrimary
        }
}

data class SourceManagementSummary(
    val sourceId: String,
    val displayName: String,
    val supportsMetadataLookup: Boolean,
    val automaticTrackingAvailable: Boolean,
    val trackedSeriesCount: Int,
    val status: String,
    val lastSuccessAt: Long?,
    val lastCheckedAt: Long?,
    val lastError: String?
)

class SettingsViewModel(
    private val filterEngine: ContentFilterEngine = try { MangaFlowApp.instance.contentFilterEngine } catch (_: Exception) { ContentFilterEngine() }
) : ViewModel() {

    private val appInstance: MangaFlowApp? = try { MangaFlowApp.instance } catch (_: Exception) { null }

    val sourceManagement: StateFlow<List<SourceManagementSummary>> = appInstance?.let { app ->
        combine(app.sourceManager.statuses, app.trackerService.allSeries) { health, seriesList ->
            app.sourceRegistry.getAllAdapters().map { adapter ->
                val relatedSeries = seriesList.filter { series ->
                    series.sourceId.equals(adapter.sourceId, ignoreCase = true) ||
                        app.sourceManager.adapterForUrl(series.sourceUrl)?.sourceId == adapter.sourceId
                }
                val persistedSuccess = relatedSeries
                    .filter { it.lastCheckStatus in setOf("SUCCESS", "NO_NEW_CHAPTERS") }
                    .mapNotNull { it.lastCheckTimestamp }
                    .maxOrNull()
                val latestPersistedCheck = relatedSeries.maxByOrNull { it.lastCheckTimestamp ?: 0L }
                val latestPersistedError = relatedSeries
                    .filter { it.lastCheckError != null && it.lastCheckStatus !in setOf("SUCCESS", "NO_NEW_CHAPTERS") }
                    .maxByOrNull { it.lastCheckTimestamp ?: 0L }
                val runtime = health[adapter.sourceId]
                val auto = adapter.capabilities.supportsChapterList && adapter.capabilities.supportsUpdateDetection
                val status = if (!auto) "MANUAL_ONLY" else {
                    runtime?.lastStatus?.takeUnless { it == "NOT_CHECKED" }
                        ?: latestPersistedCheck?.lastCheckStatus
                        ?: "NOT_CHECKED"
                }
                SourceManagementSummary(
                    sourceId = adapter.sourceId,
                    displayName = adapter.displayName,
                    supportsMetadataLookup = adapter.capabilities.supportsMetadataLookup,
                    automaticTrackingAvailable = auto,
                    trackedSeriesCount = relatedSeries.size,
                    status = status,
                    lastSuccessAt = listOfNotNull(runtime?.lastSuccessAt, persistedSuccess).maxOrNull(),
                    lastCheckedAt = listOfNotNull(runtime?.lastCheckedAt, latestPersistedCheck?.lastCheckTimestamp).maxOrNull(),
                    lastError = runtime?.lastErrorMessage ?: latestPersistedError?.lastCheckError
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    } ?: MutableStateFlow(emptyList()).asStateFlow()

    private val _themeState = MutableStateFlow(AppThemeState())
    val themeState: StateFlow<AppThemeState> = _themeState.asStateFlow()

    // Browser preferences are shared with the WebView host; controls shown in Settings are persisted.
    val browserPreferences: StateFlow<BrowserPreferences> = try {
        MangaFlowApp.instance.browserSettingsStore.settings
    } catch (_: Exception) {
        MutableStateFlow(BrowserPreferences()).asStateFlow()
    }

    // Content Filter settings (wired to ContentFilterEngine)
    val adBlockerEnabled: StateFlow<Boolean> = filterEngine.isAdBlockerEnabled
    val trackerBlockerEnabled: StateFlow<Boolean> = filterEngine.isTrackerBlockerEnabled
    val cosmeticFilteringEnabled: StateFlow<Boolean> = filterEngine.isCosmeticFilteringEnabled
    val additionalListEnabled: StateFlow<Boolean> = filterEngine.isAdditionalListEnabled
    val totalBlockedAds: StateFlow<Int> = filterEngine.totalBlockedAds
    val totalTrackersBlocked: StateFlow<Int> = filterEngine.totalTrackersBlocked
    val userAllowlist: StateFlow<Set<String>> = filterEngine.userAllowlist
    val lastUpdateTime: StateFlow<Long> = filterEngine.lastUpdateTime

    private val _filterUpdateStatus = MutableStateFlow<String?>(null)
    val filterUpdateStatus: StateFlow<String?> = _filterUpdateStatus.asStateFlow()

    private val translationManager = try { MangaFlowApp.instance.translationManager } catch (_: Exception) { null }

    val translationProviderId: StateFlow<String> = translationManager?.activeProviderId ?: MutableStateFlow("on_device").asStateFlow()
    val translationTargetLanguage: StateFlow<String> = translationManager?.targetLanguage ?: MutableStateFlow("en").asStateFlow()
    private val _translationLanguage = MutableStateFlow("English")
    val translationLanguage: StateFlow<String> = _translationLanguage.asStateFlow()
    val translationSourceLanguage: StateFlow<String> = translationManager?.sourceLanguage ?: MutableStateFlow("auto").asStateFlow()
    val allowCloudTranslation: StateFlow<Boolean> = translationManager?.allowCloudTranslation ?: MutableStateFlow(false).asStateFlow()
    val cacheTranslations: StateFlow<Boolean> = translationManager?.cacheTranslations ?: MutableStateFlow(true).asStateFlow()
    val cachedTranslationsCount: StateFlow<Int> = translationManager?.cachedItemsCount ?: MutableStateFlow(0).asStateFlow()

    val customEndpoint: StateFlow<String> = translationManager?.customEndpoint
        ?: MutableStateFlow("https://libretranslate.com/translate").asStateFlow()
    val customApiKey: StateFlow<String> = translationManager?.customApiKey
        ?: MutableStateFlow("").asStateFlow()
    val credentialWarning: StateFlow<String?> = translationManager?.credentialWarning
        ?: MutableStateFlow(null).asStateFlow()

    // Notification & Chapter Monitoring settings
    private val _newChapterNotifications = MutableStateFlow(true)
    val newChapterNotifications: StateFlow<Boolean> = _newChapterNotifications.asStateFlow()

    private val _chapterMonitoringEnabled = MutableStateFlow(true)
    val chapterMonitoringEnabled: StateFlow<Boolean> = _chapterMonitoringEnabled.asStateFlow()

    private val _monitoringIntervalHours = MutableStateFlow(6)
    val monitoringIntervalHours: StateFlow<Int> = _monitoringIntervalHours.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val db = MangaFlowApp.instance.database
                val interval = db.appSettingDao().getSetting("CHAPTER_CHECK_INTERVAL_HOURS")?.toIntOrNull()
                if (interval != null) {
                    _monitoringIntervalHours.value = interval
                }
                val monitoring = db.appSettingDao().getSetting("CHAPTER_MONITORING_ENABLED")
                if (monitoring != null) {
                    _chapterMonitoringEnabled.value = monitoring.toBoolean()
                }
                val notifications = db.appSettingDao().getSetting("NEW_CHAPTER_NOTIFICATIONS_ENABLED")
                if (notifications != null) {
                    _newChapterNotifications.value = notifications.toBoolean()
                    MangaFlowApp.instance.updateNewChapterNotificationsEnabled(notifications.toBoolean())
                }
            } catch (_: Exception) {
                // Test environment fallback
            }
        }
    }

    fun setThemeMode(mode: String) {
        val isDark = mode == "DARK"
        _themeState.value = _themeState.value.copy(isDark = isDark, themeMode = mode)
    }

    fun setAccentIndex(index: Int) {
        _themeState.value = _themeState.value.copy(accentIndex = index)
    }

    fun setUiDensity(density: String) {
        _themeState.value = _themeState.value.copy(uiDensity = density)
    }

    fun setSearchEngine(engine: String) {
        try { MangaFlowApp.instance.browserSettingsStore.setSearchEngine(engine) } catch (_: Exception) {}
    }

    fun setDefaultHomepage(homepage: String): Boolean = try {
        MangaFlowApp.instance.browserSettingsStore.setHomepage(homepage)
    } catch (_: Exception) {
        false
    }

    fun setDesktopSitePreference(enabled: Boolean) {
        try { MangaFlowApp.instance.browserSettingsStore.setDesktopSiteByDefault(enabled) } catch (_: Exception) {}
    }

    fun setTextZoomPercent(percent: Int) {
        try { MangaFlowApp.instance.browserSettingsStore.setTextZoomPercent(percent) } catch (_: Exception) {}
    }

    fun setAcceptCookies(enabled: Boolean) {
        try { MangaFlowApp.instance.browserSettingsStore.setAcceptCookies(enabled) } catch (_: Exception) {}
    }

    fun setAcceptThirdPartyCookies(enabled: Boolean) {
        try { MangaFlowApp.instance.browserSettingsStore.setAcceptThirdPartyCookies(enabled) } catch (_: Exception) {}
    }

    fun setWebsitePermissionPrompts(enabled: Boolean) {
        try { MangaFlowApp.instance.browserSettingsStore.setAllowWebsitePermissionPrompts(enabled) } catch (_: Exception) {}
    }

    fun setOpenExternalLinks(enabled: Boolean) {
        try { MangaFlowApp.instance.browserSettingsStore.setOpenExternalLinks(enabled) } catch (_: Exception) {}
    }

    fun clearBrowsingData(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = try {
                val app = MangaFlowApp.instance
                app.browserSessionManager.closeAllPrivateTabs(app)
                app.browserDownloadManager.clearPrivateSessionMetadata()
                val cleared = app.privacyManager.clearAllBrowsingData(app)
                app.browserRepository.clearHistory()
                cleared
            } catch (_: Exception) {
                false
            }
            onComplete(success)
        }
    }

    fun setAdBlockerEnabled(enabled: Boolean) {
        filterEngine.setAdBlockerEnabled(enabled)
    }

    fun setTrackerBlockerEnabled(enabled: Boolean) {
        filterEngine.setTrackerBlockerEnabled(enabled)
    }

    fun setCosmeticFilteringEnabled(enabled: Boolean) {
        filterEngine.setCosmeticFilteringEnabled(enabled)
    }

    fun setAdditionalListEnabled(enabled: Boolean) {
        filterEngine.setAdditionalListEnabled(enabled)
    }

    fun addToAllowlist(domain: String) {
        filterEngine.addToAllowlist(domain)
    }

    fun removeFromAllowlist(domain: String) {
        filterEngine.removeFromAllowlist(domain)
    }

    fun resetFilterStatistics() {
        filterEngine.resetStatistics()
    }

    fun refreshFilterLists() {
        // Built-in verified EasyList bundle rules
        val sampleEasyListRules = """
            ! MangaFlow EasyList Base Rules
            ||doubleclick.net^
            ||googlesyndication.com^
            ||popads.net^
            ||popcash.net^
            ||adsterra.com^
            ||exoclick.com^
            ||propellerads.com^
            ||taboola.com^
            ||outbrain.com^
            ||mgid.com^
            ||trafficjunky.com^
            ||juicyads.com^
            ||cpmstar.com^
            ||clickadu.com^
            ||admaven.com^
            ||hilltopads.com^
            ||monetag.com^
            /banner_ad/
            /popunder/
            /show_ads.js
            @@||webtoons.com^
        """.trimIndent()

        val res = filterEngine.updateFilterList(sampleEasyListRules)
        if (res.success) {
            _filterUpdateStatus.value = "Filter list updated (${res.rulesParsedCount} rules active)"
        } else {
            _filterUpdateStatus.value = res.errorMessage ?: "Failed to update filter list"
        }
    }

    fun setTranslationLanguage(lang: String) {
        _translationLanguage.value = lang
        setTranslationTargetLanguage(
            when (lang.lowercase()) {
                "spanish" -> "es"
                "french" -> "fr"
                "german" -> "de"
                "indonesian" -> "id"
                "portuguese" -> "pt"
                else -> "en"
            }
        )
    }

    fun setTranslationProvider(providerId: String) {
        translationManager?.setActiveProvider(providerId)
    }

    fun setTranslationTargetLanguage(langCode: String) {
        translationManager?.setTargetLanguage(langCode)
    }

    fun setTranslationSourceLanguage(langCode: String) {
        translationManager?.setSourceLanguage(langCode)
    }

    fun setAllowCloudTranslation(allowed: Boolean) {
        translationManager?.setAllowCloudTranslation(allowed)
    }

    fun setCacheTranslations(enabled: Boolean) {
        translationManager?.setCacheTranslations(enabled)
    }

    fun clearTranslationCache() {
        translationManager?.clearCache()
    }

    fun saveCustomApiCredentials(endpoint: String, key: String): Boolean =
        translationManager?.setCustomApiCredentials(endpoint, key) ?: false

    fun setNewChapterNotifications(enabled: Boolean) {
        _newChapterNotifications.value = enabled
        try { MangaFlowApp.instance.updateNewChapterNotificationsEnabled(enabled) } catch (_: Exception) { }
        viewModelScope.launch {
            try {
                MangaFlowApp.instance.database.appSettingDao().setSetting(
                    com.example.data.local.entity.AppSettingEntity(
                        key = "NEW_CHAPTER_NOTIFICATIONS_ENABLED",
                        value = enabled.toString()
                    )
                )
            } catch (_: Exception) { }
        }
    }

    fun setChapterMonitoringEnabled(enabled: Boolean) {
        _chapterMonitoringEnabled.value = enabled
        viewModelScope.launch {
            try {
                val app = MangaFlowApp.instance
                app.database.appSettingDao().setSetting(
                    com.example.data.local.entity.AppSettingEntity(
                        key = "CHAPTER_MONITORING_ENABLED",
                        value = enabled.toString()
                    )
                )
                if (enabled) {
                    com.example.engine.worker.ChapterMonitorWorker.schedule(app, _monitoringIntervalHours.value.toLong())
                } else {
                    com.example.engine.worker.ChapterMonitorWorker.cancel(app)
                }
            } catch (_: Exception) {}
        }
    }

    fun setMonitoringIntervalHours(hours: Int) {
        _monitoringIntervalHours.value = hours
        viewModelScope.launch {
            try {
                val app = MangaFlowApp.instance
                app.database.appSettingDao().setSetting(
                    com.example.data.local.entity.AppSettingEntity(
                        key = "CHAPTER_CHECK_INTERVAL_HOURS",
                        value = hours.toString()
                    )
                )
                if (_chapterMonitoringEnabled.value) {
                    com.example.engine.worker.ChapterMonitorWorker.schedule(app, hours.toLong())
                }
            } catch (_: Exception) {}
        }
    }

    fun checkAllSeriesNow() {
        viewModelScope.launch {
            try {
                MangaFlowApp.instance.chapterDetectionService.checkAllSeries(force = true)
            } catch (_: Exception) {}
        }
    }
}
