package com.example.engine.browser

import android.net.Uri
import com.example.data.local.dao.AppSettingDao
import com.example.data.local.entity.AppSettingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Persisted browser preferences shared by Settings and the WebView host. */
data class BrowserPreferences(
    val homepage: String = BrowserSessionManager.DEFAULT_HOME_URL,
    val searchEngine: String = SearchEngine.DUCKDUCKGO.displayName,
    val desktopSiteByDefault: Boolean = false,
    val textZoomPercent: Int = 100,
    val acceptCookies: Boolean = true,
    val acceptThirdPartyCookies: Boolean = false,
    val allowWebsitePermissionPrompts: Boolean = false,
    val openExternalLinks: Boolean = true
)

class BrowserSettingsStore(
    private val appSettingDao: AppSettingDao,
    private val scope: CoroutineScope
) {
    private val _settings = MutableStateFlow(BrowserPreferences())
    val settings: StateFlow<BrowserPreferences> = _settings.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            try {
                val homepage = appSettingDao.getSetting(KEY_HOME_PAGE)
                    ?.let(::validatedHomepage)
                    ?: BrowserPreferences().homepage
                val engine = appSettingDao.getSetting(KEY_SEARCH_ENGINE)
                    ?.let { SearchEngine.fromDisplayName(it).displayName }
                    ?: BrowserPreferences().searchEngine
                val desktop = appSettingDao.getSetting(KEY_DESKTOP_DEFAULT)?.toBooleanStrictOrNull() ?: false
                val zoom = appSettingDao.getSetting(KEY_TEXT_ZOOM)?.toIntOrNull()?.coerceIn(MIN_TEXT_ZOOM, MAX_TEXT_ZOOM) ?: 100
                val cookies = appSettingDao.getSetting(KEY_ACCEPT_COOKIES)?.toBooleanStrictOrNull() ?: true
                val thirdParty = appSettingDao.getSetting(KEY_THIRD_PARTY_COOKIES)?.toBooleanStrictOrNull() ?: false
                val permissions = appSettingDao.getSetting(KEY_SITE_PERMISSIONS)?.toBooleanStrictOrNull() ?: false
                val external = appSettingDao.getSetting(KEY_EXTERNAL_LINKS)?.toBooleanStrictOrNull() ?: true
                _settings.value = BrowserPreferences(
                    homepage = homepage,
                    searchEngine = engine,
                    desktopSiteByDefault = desktop,
                    textZoomPercent = zoom,
                    acceptCookies = cookies,
                    acceptThirdPartyCookies = thirdParty,
                    allowWebsitePermissionPrompts = permissions,
                    openExternalLinks = external
                )
            } catch (_: Exception) {
                // Keep in-memory defaults if the database is unavailable during a test/startup.
            }
        }
    }

    fun setHomepage(value: String): Boolean {
        val valid = validatedHomepage(value) ?: return false
        update(KEY_HOME_PAGE, valid) { it.copy(homepage = valid) }
        return true
    }

    fun setSearchEngine(value: String) {
        val canonical = SearchEngine.fromDisplayName(value).displayName
        update(KEY_SEARCH_ENGINE, canonical) { it.copy(searchEngine = canonical) }
    }

    fun setDesktopSiteByDefault(enabled: Boolean) =
        update(KEY_DESKTOP_DEFAULT, enabled.toString()) { it.copy(desktopSiteByDefault = enabled) }

    fun setTextZoomPercent(percent: Int) {
        val zoom = percent.coerceIn(MIN_TEXT_ZOOM, MAX_TEXT_ZOOM)
        update(KEY_TEXT_ZOOM, zoom.toString()) { it.copy(textZoomPercent = zoom) }
    }

    fun setAcceptCookies(enabled: Boolean) =
        update(KEY_ACCEPT_COOKIES, enabled.toString()) { it.copy(acceptCookies = enabled) }

    fun setAcceptThirdPartyCookies(enabled: Boolean) =
        update(KEY_THIRD_PARTY_COOKIES, enabled.toString()) { it.copy(acceptThirdPartyCookies = enabled) }

    fun setAllowWebsitePermissionPrompts(enabled: Boolean) =
        update(KEY_SITE_PERMISSIONS, enabled.toString()) { it.copy(allowWebsitePermissionPrompts = enabled) }

    fun setOpenExternalLinks(enabled: Boolean) =
        update(KEY_EXTERNAL_LINKS, enabled.toString()) { it.copy(openExternalLinks = enabled) }

    private inline fun update(
        key: String,
        value: String,
        transform: (BrowserPreferences) -> BrowserPreferences
    ) {
        _settings.value = transform(_settings.value)
        scope.launch(Dispatchers.IO) {
            try {
                appSettingDao.setSetting(AppSettingEntity(key = key, value = value))
            } catch (_: Exception) {
                // The current session still uses the updated preference if persistence fails.
            }
        }
    }

    private fun validatedHomepage(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.length !in 8..2048) return null
        return try {
            val uri = Uri.parse(trimmed)
            val scheme = uri.scheme?.lowercase()
            if (scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null) {
                trimmed
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val MIN_TEXT_ZOOM = 75
        const val MAX_TEXT_ZOOM = 200

        private const val KEY_HOME_PAGE = "BROWSER_HOME_PAGE"
        private const val KEY_SEARCH_ENGINE = "DEFAULT_SEARCH_ENGINE"
        private const val KEY_DESKTOP_DEFAULT = "BROWSER_DESKTOP_SITE_DEFAULT"
        private const val KEY_TEXT_ZOOM = "BROWSER_TEXT_ZOOM_PERCENT"
        private const val KEY_ACCEPT_COOKIES = "BROWSER_ACCEPT_COOKIES"
        private const val KEY_THIRD_PARTY_COOKIES = "BROWSER_THIRD_PARTY_COOKIES"
        private const val KEY_SITE_PERMISSIONS = "BROWSER_SITE_PERMISSION_PROMPTS"
        private const val KEY_EXTERNAL_LINKS = "BROWSER_EXTERNAL_LINKS"
    }
}
