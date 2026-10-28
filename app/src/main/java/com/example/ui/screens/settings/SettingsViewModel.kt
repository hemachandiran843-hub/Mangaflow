package com.example.ui.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MangaFlowApp
import com.example.engine.filter.ContentFilterEngine
import com.example.engine.filter.FilterUpdateResult
import com.example.ui.theme.MangaAmber
import com.example.ui.theme.MangaAmethyst
import com.example.ui.theme.MangaPrimary
import com.example.ui.theme.MangaSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class SettingsViewModel(
    private val filterEngine: ContentFilterEngine = try { MangaFlowApp.instance.contentFilterEngine } catch (_: Exception) { ContentFilterEngine() }
) : ViewModel() {

    private val _themeState = MutableStateFlow(AppThemeState())
    val themeState: StateFlow<AppThemeState> = _themeState.asStateFlow()

    // Browser settings
    private val _searchEngine = MutableStateFlow("DuckDuckGo")
    val searchEngine: StateFlow<String> = _searchEngine.asStateFlow()

    private val _defaultHomepage = MutableStateFlow("https://www.webtoons.com/en/")
    val defaultHomepage: StateFlow<String> = _defaultHomepage.asStateFlow()

    private val _desktopSitePreference = MutableStateFlow(false)
    val desktopSitePreference: StateFlow<Boolean> = _desktopSitePreference.asStateFlow()

    // Privacy settings
    private val _privateBrowsingEnabled = MutableStateFlow(false)
    val privateBrowsingEnabled: StateFlow<Boolean> = _privateBrowsingEnabled.asStateFlow()

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

    private val _customEndpoint = MutableStateFlow(translationManager?.getCustomEndpoint() ?: "https://libretranslate.de/translate")
    val customEndpoint: StateFlow<String> = _customEndpoint.asStateFlow()

    private val _customApiKey = MutableStateFlow(translationManager?.getCustomApiKey() ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    // Reading settings
    private val _readerMode = MutableStateFlow("Vertical Strip")
    val readerMode: StateFlow<String> = _readerMode.asStateFlow()

    private val _autoProgressTracking = MutableStateFlow(true)
    val autoProgressTracking: StateFlow<Boolean> = _autoProgressTracking.asStateFlow()

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
                val engine = db.appSettingDao().getSetting("DEFAULT_SEARCH_ENGINE")
                if (engine != null) {
                    _searchEngine.value = engine
                }
                val interval = db.appSettingDao().getSetting("CHAPTER_CHECK_INTERVAL_HOURS")?.toIntOrNull()
                if (interval != null) {
                    _monitoringIntervalHours.value = interval
                }
                val monitoring = db.appSettingDao().getSetting("CHAPTER_MONITORING_ENABLED")
                if (monitoring != null) {
                    _chapterMonitoringEnabled.value = monitoring.toBoolean()
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
        _searchEngine.value = engine
        viewModelScope.launch {
            try {
                MangaFlowApp.instance.database.appSettingDao().setSetting(
                    com.example.data.local.entity.AppSettingEntity(
                        key = "DEFAULT_SEARCH_ENGINE",
                        value = engine
                    )
                )
            } catch (_: Exception) {
                // Test environment fallback
            }
        }
    }

    fun setDesktopSitePreference(enabled: Boolean) {
        _desktopSitePreference.value = enabled
    }

    fun setPrivateBrowsingEnabled(enabled: Boolean) {
        _privateBrowsingEnabled.value = enabled
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

    fun saveCustomApiCredentials(endpoint: String, key: String) {
        _customEndpoint.value = endpoint
        _customApiKey.value = key
        translationManager?.setCustomApiCredentials(endpoint, key)
    }

    fun setReaderMode(mode: String) {
        _readerMode.value = mode
    }

    fun setAutoProgressTracking(enabled: Boolean) {
        _autoProgressTracking.value = enabled
    }

    fun setNewChapterNotifications(enabled: Boolean) {
        _newChapterNotifications.value = enabled
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
