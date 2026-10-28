package com.example.engine.browser

import android.content.Context
import android.os.Bundle
import com.example.domain.model.BrowserTab
import com.example.engine.privacy.PrivacyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.LinkedHashMap

data class ClosedTabInfo(
    val title: String,
    val url: String,
    val isPrivate: Boolean,
    val isDesktopMode: Boolean = false
)

/**
 * Owns tab metadata and bounded, in-memory WebView navigation snapshots. A single WebView is reused
 * by the UI; snapshots are separated by normal/private tab set and are discarded with private tabs.
 */
class BrowserSessionManager(
    private val privacyManager: PrivacyManager
) {
    companion object {
        const val MAX_TABS = 10
        const val DEFAULT_HOME_URL = "https://www.webtoons.com/en/"
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val MAX_SAVED_STATES_PER_MODE = 4
    }

    private var defaultHomeUrl = DEFAULT_HOME_URL
    private var desktopSiteByDefault = false
    private val pendingPrivateProfileCleanup = mutableSetOf<String>()
    private val activePrivateWebViews = mutableSetOf<String>()
    private val _privateSessionGeneration = MutableStateFlow(0L)
    val privateSessionGeneration: StateFlow<Long> = _privateSessionGeneration.asStateFlow()

    private val normalWebStates = lruStateMap()
    private val privateWebStates = lruStateMap()

    private val _normalTabs = MutableStateFlow(
        listOf(BrowserTab(title = "MangaFlow Home", url = DEFAULT_HOME_URL, isPrivate = false))
    )
    val normalTabs: StateFlow<List<BrowserTab>> = _normalTabs.asStateFlow()

    private val _privateTabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val privateTabs: StateFlow<List<BrowserTab>> = _privateTabs.asStateFlow()

    private val _currentTabId = MutableStateFlow(_normalTabs.value.first().id)
    val currentTabId: StateFlow<String> = _currentTabId.asStateFlow()

    private val _isPrivateActive = MutableStateFlow(false)
    val isPrivateActive: StateFlow<Boolean> = _isPrivateActive.asStateFlow()

    private val recentlyClosedTabs = ArrayDeque<ClosedTabInfo>()
    private val _canRestoreTab = MutableStateFlow(false)
    val canRestoreTab: StateFlow<Boolean> = _canRestoreTab.asStateFlow()

    fun getCurrentTab(): BrowserTab? = getTab(_currentTabId.value)

    fun getTab(tabId: String): BrowserTab? =
        _normalTabs.value.find { it.id == tabId } ?: _privateTabs.value.find { it.id == tabId }

    fun updateBrowserDefaults(homepage: String, desktopMode: Boolean) {
        val previousHome = defaultHomeUrl
        if (homepage.startsWith("https://") || homepage.startsWith("http://")) defaultHomeUrl = homepage
        desktopSiteByDefault = desktopMode
        if (previousHome != defaultHomeUrl) {
            _normalTabs.update { tabs ->
                tabs.map { tab ->
                    if (tab.url == previousHome && !tab.isLoading && tab.title in setOf("MangaFlow Home", "New Tab")) {
                        tab.copy(url = defaultHomeUrl)
                    } else tab
                }
            }
        }
    }

    fun openNewTab(
        url: String? = null,
        isPrivate: Boolean = _isPrivateActive.value,
        title: String? = null
    ): BrowserTab? {
        val activeList = if (isPrivate) _privateTabs.value else _normalTabs.value
        if (activeList.size >= MAX_TABS) return null
        if (isPrivate && _privateTabs.value.isEmpty()) {
            privacyManager.beginPrivateSession()
            _privateSessionGeneration.value += 1L
        }

        val newTab = BrowserTab(
            title = title ?: if (isPrivate) "Private Tab" else "New Tab",
            url = url ?: defaultHomeUrl,
            isPrivate = isPrivate,
            isDesktopMode = desktopSiteByDefault
        )

        if (isPrivate) {
            _privateTabs.update { it + newTab }
            _isPrivateActive.value = true
            privacyManager.enterPrivateSession()
        } else {
            _normalTabs.update { it + newTab }
            _isPrivateActive.value = false
            privacyManager.exitPrivateSession()
        }
        _currentTabId.value = newTab.id
        return newTab
    }

    fun saveWebViewState(tabId: String, state: Bundle) {
        val tab = getTab(tabId) ?: return
        val states = if (tab.isPrivate) privateWebStates else normalWebStates
        states[tabId] = Bundle(state)
        while (states.size > MAX_SAVED_STATES_PER_MODE) {
            val eldest = states.keys.firstOrNull() ?: break
            states.remove(eldest)
        }
    }

    fun getWebViewState(tabId: String): Bundle? {
        val tab = getTab(tabId) ?: return null
        val saved = (if (tab.isPrivate) privateWebStates else normalWebStates)[tabId] ?: return null
        return Bundle(saved)
    }

    fun onPrivateWebViewCreated(profileName: String) {
        activePrivateWebViews += profileName
    }

    fun shouldPurgePrivateProfile(profileName: String): Boolean =
        profileName in pendingPrivateProfileCleanup

    fun onBrowserWebViewReleased(profileName: String?) {
        if (profileName == null) return
        activePrivateWebViews -= profileName
        if (pendingPrivateProfileCleanup.remove(profileName)) {
            privacyManager.purgePrivateData(profileName)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun closeTab(tabId: String, context: Context) {
        val isPrivate = _privateTabs.value.any { it.id == tabId }
        val closedTab = getTab(tabId)
        normalWebStates.remove(tabId)
        privateWebStates.remove(tabId)

        if (closedTab != null && !closedTab.isPrivate && closedTab.url != "about:blank") {
            if (recentlyClosedTabs.size >= 10) recentlyClosedTabs.removeFirst()
            recentlyClosedTabs.addLast(
                ClosedTabInfo(
                    title = closedTab.title,
                    url = closedTab.url,
                    isPrivate = false,
                    isDesktopMode = closedTab.isDesktopMode
                )
            )
            _canRestoreTab.value = true
        }

        if (isPrivate) {
            val updated = _privateTabs.value.filterNot { it.id == tabId }
            _privateTabs.value = updated
            if (updated.isEmpty()) {
                val wasPrivateActive = _isPrivateActive.value
                val endedProfileName = privacyManager.getPrivateProfileName()
                _isPrivateActive.value = false
                privacyManager.exitPrivateSession()
                privateWebStates.clear()
                _currentTabId.value = _normalTabs.value.firstOrNull()?.id ?: openNewTab(isPrivate = false)?.id.orEmpty()
                if (wasPrivateActive && endedProfileName in activePrivateWebViews) {
                    pendingPrivateProfileCleanup += endedProfileName
                } else {
                    privacyManager.purgePrivateData(endedProfileName)
                }
            } else if (_currentTabId.value == tabId) {
                _currentTabId.value = updated.last().id
            }
        } else {
            val updated = _normalTabs.value.filterNot { it.id == tabId }
            if (updated.isEmpty()) {
                val fresh = BrowserTab(
                    title = "New Tab",
                    url = defaultHomeUrl,
                    isPrivate = false,
                    isDesktopMode = desktopSiteByDefault
                )
                _normalTabs.value = listOf(fresh)
                _currentTabId.value = fresh.id
            } else {
                _normalTabs.value = updated
                if (_currentTabId.value == tabId) _currentTabId.value = updated.last().id
            }
        }
    }

    fun restoreLastClosedTab(): BrowserTab? {
        if (recentlyClosedTabs.isEmpty()) {
            _canRestoreTab.value = false
            return null
        }
        val info = recentlyClosedTabs.removeLast()
        _canRestoreTab.value = recentlyClosedTabs.isNotEmpty()
        val tab = openNewTab(url = info.url, isPrivate = false, title = info.title)
        if (tab != null && tab.isDesktopMode != info.isDesktopMode) {
            updateTab(tab.id) { it.copy(isDesktopMode = info.isDesktopMode) }
            return getTab(tab.id)
        }
        return tab
    }

    fun switchTab(tabId: String) {
        val inPrivate = _privateTabs.value.any { it.id == tabId }
        if (getTab(tabId) == null) return
        _isPrivateActive.value = inPrivate
        _currentTabId.value = tabId
        if (inPrivate) privacyManager.enterPrivateSession() else privacyManager.exitPrivateSession()
    }

    fun setPrivateMode(isPrivate: Boolean) {
        if (_isPrivateActive.value == isPrivate) return
        _isPrivateActive.value = isPrivate
        if (isPrivate) {
            privacyManager.enterPrivateSession()
            if (_privateTabs.value.isEmpty()) {
                openNewTab(url = defaultHomeUrl, isPrivate = true)
            } else {
                _currentTabId.value = _privateTabs.value.first().id
            }
        } else {
            privacyManager.exitPrivateSession()
            if (_normalTabs.value.isEmpty()) {
                openNewTab(url = defaultHomeUrl, isPrivate = false)
            } else {
                _currentTabId.value = _normalTabs.value.first().id
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun closeAllPrivateTabs(context: Context) {
        if (_privateTabs.value.isEmpty()) return
        val wasPrivateActive = _isPrivateActive.value
        val endedProfileName = privacyManager.getPrivateProfileName()
        _privateTabs.value = emptyList()
        privateWebStates.clear()
        _isPrivateActive.value = false
        privacyManager.exitPrivateSession()
        if (_normalTabs.value.isEmpty()) openNewTab(isPrivate = false)
        else _currentTabId.value = _normalTabs.value.first().id
        if (wasPrivateActive && endedProfileName in activePrivateWebViews) {
            pendingPrivateProfileCleanup += endedProfileName
        } else {
            privacyManager.purgePrivateData(endedProfileName)
        }
    }

    fun toggleDesktopMode(tabId: String) {
        updateTab(tabId) { it.copy(isDesktopMode = !it.isDesktopMode) }
    }

    fun setTabError(tabId: String, error: String?) {
        updateTab(tabId) { it.copy(errorMessage = error, isLoading = false) }
    }

    fun handleRendererCrash(tabId: String) {
        updateTab(tabId) {
            it.copy(isCrashed = true, isLoading = false, errorMessage = "The webpage crashed or was terminated by the system.")
        }
    }

    fun reloadTab(tabId: String) {
        updateTab(tabId) { it.copy(isCrashed = false, errorMessage = null, isLoading = true, progress = 10) }
    }

    fun updateFindMatches(tabId: String, query: String, active: Int, total: Int) {
        updateTab(tabId) { it.copy(findQuery = query, findActiveMatch = active, findTotalMatches = total) }
    }

    fun clearFind(tabId: String) {
        updateTab(tabId) { it.copy(findQuery = null, findActiveMatch = 0, findTotalMatches = 0) }
    }

    fun updateCurrentTab(transform: (BrowserTab) -> BrowserTab) = updateTab(_currentTabId.value, transform)

    private fun updateTab(targetId: String, transform: (BrowserTab) -> BrowserTab) {
        val privateTarget = _privateTabs.value.any { it.id == targetId }
        if (privateTarget) {
            _privateTabs.update { list -> list.map { if (it.id == targetId) transform(it) else it } }
        } else {
            _normalTabs.update { list -> list.map { if (it.id == targetId) transform(it) else it } }
        }
    }

    private fun lruStateMap(): LinkedHashMap<String, Bundle> =
        LinkedHashMap(8, 0.75f, true)
}
