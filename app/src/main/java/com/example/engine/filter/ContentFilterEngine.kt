package com.example.engine.filter

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.InputStream

class ContentFilterEngine {

    // Master toggles
    private val _isAdBlockerEnabled = MutableStateFlow(true)
    val isAdBlockerEnabled: StateFlow<Boolean> = _isAdBlockerEnabled.asStateFlow()

    private val _isTrackerBlockerEnabled = MutableStateFlow(true)
    val isTrackerBlockerEnabled: StateFlow<Boolean> = _isTrackerBlockerEnabled.asStateFlow()

    private val _isCosmeticFilteringEnabled = MutableStateFlow(true)
    val isCosmeticFilteringEnabled: StateFlow<Boolean> = _isCosmeticFilteringEnabled.asStateFlow()

    private val _isAdditionalListEnabled = MutableStateFlow(false)
    val isAdditionalListEnabled: StateFlow<Boolean> = _isAdditionalListEnabled.asStateFlow()

    // Statistics
    private val _totalBlockedAds = MutableStateFlow(0)
    val totalBlockedAds: StateFlow<Int> = _totalBlockedAds.asStateFlow()

    private val _totalTrackersBlocked = MutableStateFlow(0)
    val totalTrackersBlocked: StateFlow<Int> = _totalTrackersBlocked.asStateFlow()

    private val _blockedByDomain = MutableStateFlow<Map<String, Int>>(emptyMap())
    val blockedByDomain: StateFlow<Map<String, Int>> = _blockedByDomain.asStateFlow()

    // Allowlist & Per-site controls
    private val _userAllowlist = MutableStateFlow<Set<String>>(emptySet())
    val userAllowlist: StateFlow<Set<String>> = _userAllowlist.asStateFlow()

    private val _disabledSites = MutableStateFlow<Set<String>>(emptySet())
    val disabledSites: StateFlow<Set<String>> = _disabledSites.asStateFlow()

    // Custom user rules
    private val _customRules = MutableStateFlow<List<String>>(emptyList())
    val customRules: StateFlow<List<String>> = _customRules.asStateFlow()

    // Filter update status
    private val _lastUpdateTime = MutableStateFlow(System.currentTimeMillis())
    val lastUpdateTime: StateFlow<Long> = _lastUpdateTime.asStateFlow()

    private val _lastUpdateError = MutableStateFlow<String?>(null)
    val lastUpdateError: StateFlow<String?> = _lastUpdateError.asStateFlow()

    // Built-in ad networks and aggressive manhwa popup hosts
    private val defaultBlockedHosts = mutableSetOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "popads.net",
        "popcash.net",
        "adsterra.com",
        "exoclick.com",
        "propellerads.com",
        "outbrain.com",
        "taboola.com",
        "cpmstar.com",
        "clickadu.com",
        "admaven.com",
        "hilltopads.com",
        "monetag.com",
        "juicyads.com",
        "trafficjunky.com",
        "mgid.com",
        "adnxs.com",
        "adcolony.com",
        "chartboost.com",
        "vungle.com",
        "inmobi.com",
        "criteo.com",
        "adform.net",
        "bidswitch.net",
        "adroll.com",
        "pubmatic.com",
        "openx.net",
        "rubiconproject.com",
        "smartadserver.com",
        "adkernel.com",
        "adtechus.com",
        "yandexadexchange.net",
        "aniview.com",
        "adsupply.com",
        "bidtheatre.com",
        "admob.com",
        "unityads.unity3d.com"
    )

    // Dedicated trackers and profiling domains
    private val trackerHosts = setOf(
        "hotjar.com",
        "clarity.ms",
        "segment.io",
        "mixpanel.com",
        "scorecardresearch.com",
        "quantserve.com",
        "branch.io",
        "appsflyer.com",
        "adjust.com",
        "onesignal.com"
    )

    // Optional additional list: Coin miners and aggressive redirects
    private val additionalAggressiveHosts = setOf(
        "coinhive.com",
        "coin-have.com",
        "crypto-loot.com",
        "jsecoin.com",
        "authedmine.com",
        "miner.pr0gramm.com",
        "webminepool.com"
    )

    // Compiled filter rules
    private val dynamicRules = mutableListOf<FilterRule>()

    // Keywords in URL paths indicating ads
    private val adPathKeywords = listOf(
        "/ads.js",
        "/ad_banner",
        "/banner_ad",
        "/popunder",
        "/adserver",
        "/adsystem",
        "/ad_frame",
        "/vast.xml",
        "/ad-tag",
        "/show_ads.js",
        "/ad_popup",
        "/native_ads",
        "/outbrain.js",
        "/taboola_loader.js"
    )

    fun setAdBlockerEnabled(enabled: Boolean) {
        _isAdBlockerEnabled.value = enabled
    }

    fun setTrackerBlockerEnabled(enabled: Boolean) {
        _isTrackerBlockerEnabled.value = enabled
    }

    fun setCosmeticFilteringEnabled(enabled: Boolean) {
        _isCosmeticFilteringEnabled.value = enabled
    }

    fun setAdditionalListEnabled(enabled: Boolean) {
        _isAdditionalListEnabled.value = enabled
    }

    // Allowlist operations
    fun addToAllowlist(domain: String) {
        val clean = normalizeDomain(domain)
        if (clean.isNotBlank()) {
            _userAllowlist.value = _userAllowlist.value + clean
        }
    }

    fun removeFromAllowlist(domain: String) {
        val clean = normalizeDomain(domain)
        _userAllowlist.value = _userAllowlist.value - clean
    }

    fun isDomainAllowlisted(domain: String?): Boolean {
        if (domain.isNullOrBlank()) return false
        val clean = normalizeDomain(domain)
        return _userAllowlist.value.any { clean == it || clean.endsWith(".$it") }
    }

    // Per-site toggle controls
    fun setSiteFilteringEnabled(siteHost: String, enabled: Boolean) {
        val clean = normalizeDomain(siteHost)
        if (clean.isBlank()) return
        if (enabled) {
            _disabledSites.value = _disabledSites.value - clean
        } else {
            _disabledSites.value = _disabledSites.value + clean
        }
    }

    fun isFilteringEnabledForSite(siteHost: String?): Boolean {
        if (!_isAdBlockerEnabled.value) return false
        if (siteHost.isNullOrBlank()) return true
        val clean = normalizeDomain(siteHost)
        if (_disabledSites.value.any { clean == it || clean.endsWith(".$it") }) return false
        if (isDomainAllowlisted(clean)) return false
        return true
    }

    // Custom user rules
    fun addCustomRule(ruleText: String): Boolean {
        val rule = FilterRule.parse(ruleText)
        return if (rule != null) {
            dynamicRules.add(rule)
            _customRules.value = _customRules.value + ruleText.trim()
            true
        } else {
            false
        }
    }

    fun removeCustomRule(ruleText: String) {
        _customRules.value = _customRules.value - ruleText.trim()
        dynamicRules.removeAll { it.rawText == ruleText.trim() }
    }

    // Filter update
    fun updateFilterList(content: String): FilterUpdateResult {
        return try {
            val lines = content.lines()
            var parsedCount = 0
            val newRules = mutableListOf<FilterRule>()

            for (line in lines) {
                val rule = FilterRule.parse(line)
                if (rule != null) {
                    newRules.add(rule)
                    parsedCount++
                }
            }

            if (parsedCount > 0) {
                synchronized(dynamicRules) {
                    dynamicRules.clear()
                    dynamicRules.addAll(newRules)
                }
                _lastUpdateTime.value = System.currentTimeMillis()
                _lastUpdateError.value = null
                FilterUpdateResult(success = true, rulesParsedCount = parsedCount)
            } else {
                val err = "No valid EasyList filter rules found in supplied data."
                _lastUpdateError.value = err
                FilterUpdateResult(success = false, rulesParsedCount = 0, errorMessage = err)
            }
        } catch (e: Exception) {
            val err = "Failed to parse filter list: ${e.message ?: "Unknown error"}"
            _lastUpdateError.value = err
            FilterUpdateResult(success = false, rulesParsedCount = 0, errorMessage = err)
        }
    }

    /**
     * Determines whether a network request should be intercepted and blocked.
     * Differentiates first-party vs third-party requests and respects site allowlists.
     */
    fun shouldBlockRequest(url: String, pageHost: String? = null): Boolean {
        if (!_isAdBlockerEnabled.value) return false
        if (!isFilteringEnabledForSite(pageHost)) return false

        val reqHost = extractHost(url) ?: return false

        // Check if request destination itself is explicitly allowlisted
        if (isDomainAllowlisted(reqHost)) return false

        val isThirdParty = if (pageHost != null) {
            val cleanPage = normalizeDomain(pageHost)
            val cleanReq = normalizeDomain(reqHost)
            cleanPage != cleanReq && !cleanReq.endsWith(".$cleanPage")
        } else {
            true
        }

        // 1. Check trackers
        if (_isTrackerBlockerEnabled.value) {
            if (trackerHosts.any { reqHost == it || reqHost.endsWith(".$it") }) {
                recordBlockedItem(reqHost, isTracker = true)
                return true
            }
        }

        // 2. Check default blocked ad hosts
        if (defaultBlockedHosts.any { reqHost == it || reqHost.endsWith(".$it") }) {
            recordBlockedItem(reqHost, isTracker = false)
            return true
        }

        // 3. Check optional aggressive additional hosts
        if (_isAdditionalListEnabled.value) {
            if (additionalAggressiveHosts.any { reqHost == it || reqHost.endsWith(".$it") }) {
                recordBlockedItem(reqHost, isTracker = false)
                return true
            }
        }

        // 4. Check dynamic parsed rules
        synchronized(dynamicRules) {
            for (rule in dynamicRules) {
                when (rule) {
                    is FilterRule.AllowlistRule -> {
                        if (reqHost == rule.domain || reqHost.endsWith(".${rule.domain}")) {
                            return false // Explicitly allowed
                        }
                    }
                    is FilterRule.DomainBlockRule -> {
                        val matchesDomain = reqHost == rule.domain || reqHost.endsWith(".${rule.domain}")
                        if (matchesDomain) {
                            if (!rule.thirdPartyOnly || isThirdParty) {
                                recordBlockedItem(reqHost, isTracker = false)
                                return true
                            }
                        }
                    }
                    is FilterRule.PathKeywordRule -> {
                        if (url.lowercase().contains(rule.keyword)) {
                            recordBlockedItem(reqHost, isTracker = false)
                            return true
                        }
                    }
                }
            }
        }

        // 5. Check standard ad path keywords
        val lowerUrl = url.lowercase()
        if (adPathKeywords.any { lowerUrl.contains(it) }) {
            recordBlockedItem(reqHost, isTracker = false)
            return true
        }

        return false
    }

    /**
     * Backwards compatible helper for existing callers.
     */
    fun isAdOrTracker(url: String): Boolean {
        return shouldBlockRequest(url, null)
    }

    private fun recordBlockedItem(domain: String, isTracker: Boolean) {
        _totalBlockedAds.value += 1
        if (isTracker) {
            _totalTrackersBlocked.value += 1
        }
        val current = _blockedByDomain.value.toMutableMap()
        current[domain] = (current[domain] ?: 0) + 1
        _blockedByDomain.value = current
    }

    fun resetStatistics() {
        _totalBlockedAds.value = 0
        _totalTrackersBlocked.value = 0
        _blockedByDomain.value = emptyMap()
    }

    fun getEmptyResponse(): Pair<String, InputStream> {
        return Pair("text/plain", ByteArrayInputStream(ByteArray(0)))
    }

    fun getCosmeticCss(): String {
        if (!_isCosmeticFilteringEnabled.value) return ""
        return """
            div[class*='ad-container'], 
            div[id*='ad-container'], 
            div[class*='ad-wrapper'], 
            div[id*='ad-wrapper'], 
            div[class*='banner-ad'], 
            div[class*='advertisement'], 
            iframe[src*='ads'], 
            iframe[src*='doubleclick'], 
            ins.adsbygoogle, 
            .adsbygoogle, 
            .ad-box, 
            .ad_unit,
            .top-banner-ad,
            .bottom-floating-ad,
            .popunder,
            .popads { 
                display: none !important; 
                height: 0 !important; 
                min-height: 0 !important; 
                opacity: 0 !important; 
                pointer-events: none !important; 
            }
        """.trimIndent()
    }

    private fun extractHost(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            uri.host?.lowercase()
        } catch (_: Exception) {
            val afterScheme = url.substringAfter("://", "")
            if (afterScheme.isNotEmpty()) {
                afterScheme.substringBefore("/").substringBefore("?").substringBefore(":").lowercase()
            } else null
        }
    }

    private fun normalizeDomain(domain: String): String {
        return domain.trim().lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
    }
}
