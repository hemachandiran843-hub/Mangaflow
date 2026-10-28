package com.example.engine.browser

import android.content.Context
import com.example.domain.model.BrowserTab
import com.example.engine.privacy.PrivacyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ClosedTabInfo(
    val title: String,
    val url: String,
    val isPrivate: Boolean,
    val isDesktopMode: Boolean = false
)

class BrowserSessionManager(
    private val privacyManager: PrivacyManager
) {
    companion object {
        const val MAX_TABS = 10
        const val DEFAULT_HOME_URL = "https://www.webtoons.com/en/"
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    private val _normalTabs = MutableStateFlow<List<BrowserTab>>(
        listOf(
            BrowserTab(
                title = "MangaFlow Home",
                url = DEFAULT_HOME_URL,
                isPrivate = false
            )
        )
    )
    val normalTabs: StateFlow<List<BrowserTab>> = _normalTabs.asStateFlow()

    private val _privateTabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val privateTabs: StateFlow<List<BrowserTab>> = _privateTabs.asStateFlow()

    private val _currentTabId = MutableStateFlow<String>(_normalTabs.value.first().id)
    val currentTabId: StateFlow<String> = _currentTabId.asStateFlow()

    private val _isPrivateActive = MutableStateFlow(false)
    val isPrivateActive: StateFlow<Boolean> = _isPrivateActive.asStateFlow()

    // History of recently closed tabs for safe restoration
    private val recentlyClosedTabs = ArrayDeque<ClosedTabInfo>()

    private val _canRestoreTab = MutableStateFlow(false)
    val canRestoreTab: StateFlow<Boolean> = _canRestoreTab.asStateFlow()

    fun getCurrentTab(): BrowserTab? {
        val list = if (_isPrivateActive.value) _privateTabs.value else _normalTabs.value
        return list.find { it.id == _currentTabId.value } ?: list.firstOrNull()
    }

    fun openNewTab(
        url: String = DEFAULT_HOME_URL,
        isPrivate: Boolean = _isPrivateActive.value,
        title: String? = null
    ): BrowserTab? {
        val activeList = if (isPrivate) _privateTabs.value else _normalTabs.value
        if (activeList.size >= MAX_TABS) {
            // Sensible tab limit reached
            return null
        }

        val newTab = BrowserTab(
            title = title ?: if (isPrivate) "Private Tab" else "New Tab",
            url = url,
            isPrivate = isPrivate
        )

        if (isPrivate) {
            _privateTabs.update { it + newTab }
            _isPrivateActive.value = true
        } else {
            _normalTabs.update { it + newTab }
            _isPrivateActive.value = false
        }
        _currentTabId.value = newTab.id
        return newTab
    }

    fun closeTab(tabId: String, context: Context) {
        val isPrivate = _privateTabs.value.any { it.id == tabId }
        val closedTab = if (isPrivate) {
            _privateTabs.value.find { it.id == tabId }
        } else {
            _normalTabs.value.find { it.id == tabId }
        }

        // Save for restoration if not blank and not private
        if (closedTab != null && !closedTab.isPrivate && closedTab.url != "about:blank") {
            if (recentlyClosedTabs.size >= 10) {
                recentlyClosedTabs.removeFirst()
            }
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
            val updated = _privateTabs.value.filter { it.id != tabId }
            _privateTabs.value = updated
            if (updated.isEmpty()) {
                _isPrivateActive.value = false
                _currentTabId.value = _normalTabs.value.firstOrNull()?.id ?: openNewTab(isPrivate = false)?.id ?: ""
                privacyManager.purgePrivateData(context)
            } else if (_currentTabId.value == tabId) {
                _currentTabId.value = updated.last().id
            }
        } else {
            val updated = _normalTabs.value.filter { it.id != tabId }
            if (updated.isEmpty()) {
                val fresh = BrowserTab(title = "New Tab", url = DEFAULT_HOME_URL, isPrivate = false)
                _normalTabs.value = listOf(fresh)
                _currentTabId.value = fresh.id
            } else {
                _normalTabs.value = updated
                if (_currentTabId.value == tabId) {
                    _currentTabId.value = updated.last().id
                }
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
        return openNewTab(
            url = info.url,
            isPrivate = info.isPrivate,
            title = info.title
        )?.copy(isDesktopMode = info.isDesktopMode)
    }

    fun switchTab(tabId: String) {
        val inPrivate = _privateTabs.value.any { it.id == tabId }
        _isPrivateActive.value = inPrivate
        _currentTabId.value = tabId
    }

    fun setPrivateMode(isPrivate: Boolean) {
        if (_isPrivateActive.value == isPrivate) return
        _isPrivateActive.value = isPrivate

        if (isPrivate) {
            if (_privateTabs.value.isEmpty()) {
                openNewTab(url = DEFAULT_HOME_URL, isPrivate = true)
            } else {
                _currentTabId.value = _privateTabs.value.first().id
            }
        } else {
            if (_normalTabs.value.isEmpty()) {
                openNewTab(url = DEFAULT_HOME_URL, isPrivate = false)
            } else {
                _currentTabId.value = _normalTabs.value.first().id
            }
        }
    }

    fun closeAllPrivateTabs(context: Context) {
        _privateTabs.value = emptyList()
        _isPrivateActive.value = false
        if (_normalTabs.value.isEmpty()) {
            openNewTab(isPrivate = false)
        } else {
            _currentTabId.value = _normalTabs.value.first().id
        }
        privacyManager.purgePrivateData(context)
    }

    fun toggleDesktopMode(tabId: String) {
        updateTab(tabId) { it.copy(isDesktopMode = !it.isDesktopMode) }
    }

    fun setTabError(tabId: String, error: String?) {
        updateTab(tabId) { it.copy(errorMessage = error, isLoading = false) }
    }

    fun handleRendererCrash(tabId: String) {
        updateTab(tabId) {
            it.copy(
                isCrashed = true,
                isLoading = false,
                errorMessage = "The webpage crashed or was terminated by the system."
            )
        }
    }

    fun reloadTab(tabId: String) {
        updateTab(tabId) {
            it.copy(
                isCrashed = false,
                errorMessage = null,
                isLoading = true,
                progress = 10
            )
        }
    }

    fun updateFindMatches(tabId: String, query: String, active: Int, total: Int) {
        updateTab(tabId) {
            it.copy(
                findQuery = query,
                findActiveMatch = active,
                findTotalMatches = total
            )
        }
    }

    fun clearFind(tabId: String) {
        updateTab(tabId) {
            it.copy(
                findQuery = null,
                findActiveMatch = 0,
                findTotalMatches = 0
            )
        }
    }

    fun updateCurrentTab(transform: (BrowserTab) -> BrowserTab) {
        val targetId = _currentTabId.value
        updateTab(targetId, transform)
    }

    private fun updateTab(targetId: String, transform: (BrowserTab) -> BrowserTab) {
        if (_isPrivateActive.value) {
            _privateTabs.update { list ->
                list.map { if (it.id == targetId) transform(it) else it }
            }
        } else {
            _normalTabs.update { list ->
                list.map { if (it.id == targetId) transform(it) else it }
            }
        }
    }
}
