package com.example.engine.privacy

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class PrivacyManager {
    @Volatile
    private var _isPrivateActive: Boolean = false
    @Volatile
    private var activePrivateProfileName: String = newPrivateProfileName()
    private val privateProfiles = ConcurrentHashMap<String, Profile>()

    fun isPrivateSessionActive(): Boolean = _isPrivateActive

    fun enterPrivateSession() {
        _isPrivateActive = true
    }

    /** Starts a new isolated storage profile for a new private-tab group. */
    @Synchronized
    fun beginPrivateSession(): String {
        activePrivateProfileName = newPrivateProfileName()
        _isPrivateActive = true
        return activePrivateProfileName
    }

    fun getPrivateProfileName(): String = activePrivateProfileName

    fun exitPrivateSession() {
        _isPrivateActive = false
    }

    fun supportsIsolatedPrivateProfile(): Boolean = try {
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    } catch (_: Exception) {
        false
    }

    /**
     * Removes stale MangaFlow private profiles left by a process kill, before this process loads
     * any private Profile objects or creates WebViews. Profile deletion may be rejected while a
     * provider still considers that profile loaded, so failures remain best-effort.
     */
    fun purgeOrphanedPrivateProfiles() {
        if (!supportsIsolatedPrivateProfile()) return
        try {
            val store = ProfileStore.getInstance()
            store.getAllProfileNames()
                .filter { it.startsWith(PRIVATE_PROFILE_PREFIX) }
                .forEach { profileName ->
                    try { store.deleteProfile(profileName) } catch (_: Exception) { }
                }
        } catch (_: Exception) {
            // Browsing remains fail-closed if profile isolation is unavailable at actual use time.
        }
    }

    /** Must be called on a new WebView before it is attached, configured, or navigated. */
    fun configureWebViewProfile(
        webView: WebView,
        isPrivate: Boolean,
        profileName: String = activePrivateProfileName
    ): Boolean {
        if (!isPrivate) return true
        if (!supportsIsolatedPrivateProfile()) return false
        return try {
            WebViewCompat.setProfile(webView, profileName)
            privateProfiles[profileName] = WebViewCompat.getProfile(webView)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getPrivateCookieManager(webView: WebView, profileName: String): CookieManager? {
        if (!supportsIsolatedPrivateProfile()) return null
        return try {
            val profile = privateProfiles[profileName] ?: WebViewCompat.getProfile(webView).also {
                privateProfiles[profileName] = it
            }
            profile.cookieManager
        } catch (_: Exception) {
            null
        }
    }

    /** Clear profile-owned data from the final private WebView before that view is destroyed. */
    fun clearPrivateProfileData(webView: WebView, profileName: String = activePrivateProfileName) {
        if (!supportsIsolatedPrivateProfile()) return
        try {
            val profile = WebViewCompat.getProfile(webView)
            privateProfiles[profileName] = profile
            clearProfileData(profile)
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
        } catch (_: Exception) {
            // Profile deletion remains a best-effort cleanup on WebView provider failures.
        }
    }

    /** Deletes the isolated private WebView profile after its WebView has been destroyed. */
    fun purgePrivateData(profileName: String = activePrivateProfileName) {
        _isPrivateActive = false
        if (supportsIsolatedPrivateProfile()) {
            val profile = privateProfiles.remove(profileName)
            if (profile != null) {
                try { clearProfileData(profile) } catch (_: Exception) { }
                try {
                    ProfileStore.getInstance().deleteProfile(profileName)
                } catch (_: Exception) {
                    // Never clear the normal profile as a fallback; the next private session gets
                    // a fresh, unrelated profile name even if provider deletion fails.
                }
            }
            return
        }
        // If profile isolation is unavailable, private pages are blocked by BrowserScreen rather
        // than sharing the default profile; no default-profile data should be purged here.
    }

    /** Explicit user-requested clear-data action; does not delete library data or downloaded files. */
    suspend fun clearAllBrowsingData(context: Context): Boolean {
        var cleared = true
        try {
            val defaultCookies = CookieManager.getInstance()
            val defaultCookieClearCompleted = withTimeoutOrNull(COOKIE_CLEAR_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    defaultCookies.removeAllCookies {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            } != null
            defaultCookies.flush()
            if (!defaultCookieClearCompleted) cleared = false
            WebStorage.getInstance().deleteAllData()
            GeolocationPermissions.getInstance().clearAll()
            val cleanup = WebView(context)
            cleanup.clearCache(true)
            cleanup.clearFormData()
            cleanup.clearHistory()
            cleanup.destroy()
        } catch (_: Exception) {
            cleared = false
        }
        if (supportsIsolatedPrivateProfile()) {
            privateProfiles.entries.toList().forEach { (profileName, profile) ->
                privateProfiles.remove(profileName)
                if (!clearProfileDataAndAwait(profile)) cleared = false
                try {
                    ProfileStore.getInstance().deleteProfile(profileName)
                } catch (_: Exception) {
                    cleared = false
                }
            }
        }
        return cleared
    }

    private suspend fun clearProfileDataAndAwait(profile: Profile): Boolean {
        var cleared = true
        try {
            val cookies = profile.cookieManager
            val completed = withTimeoutOrNull(COOKIE_CLEAR_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    cookies.removeAllCookies {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            } != null
            cookies.flush()
            if (!completed) cleared = false
            profile.webStorage.deleteAllData()
            profile.geolocationPermissions.clearAll()
        } catch (_: Exception) {
            cleared = false
        }
        return cleared
    }

    private fun clearProfileData(profile: Profile) {
        profile.cookieManager.removeAllCookies(null)
        profile.cookieManager.flush()
        profile.webStorage.deleteAllData()
        profile.geolocationPermissions.clearAll()
    }

    fun shouldPersistHistory(isPrivate: Boolean): Boolean = !isPrivate

    fun shouldTrackReadingStats(isPrivate: Boolean): Boolean = !isPrivate

    fun shouldPersistSearchQuery(isPrivate: Boolean): Boolean = !isPrivate

    fun sanitizeLog(message: String, isPrivate: Boolean): String =
        if (isPrivate) "[PRIVATE_SESSION_REDACTED]" else message

    fun getPrivacyNotice(): String {
        return if (supportsIsolatedPrivateProfile()) {
            "Each private-tab group uses a fresh, separate WebView profile when supported by the installed WebView. MangaFlow does not save private browsing history or reading progress. When the last private tab closes, MangaFlow clears the profile's cookies, site storage and cache and requests profile deletion; WebView providers may delay or reject physical deletion, so secure erasure from storage cannot be guaranteed. Leftover MangaFlow private profiles are also targeted for deletion at the next app start. Files you download remain in the app's Downloads folder after private tabs close, and Android may retain its own download notification/history. Private browsing does not hide activity from websites, your network administrator, or your internet provider."
        } else {
            "This WebView provider cannot isolate private storage, so MangaFlow blocks webpage loading in Private Browsing rather than sharing normal cookies, local storage or cache. Private page history and reading progress are not saved. Downloaded files remain in the app's Downloads folder, and Android may retain its own download notification/history. Private browsing does not provide network anonymity."
        }
    }

    companion object {
        private const val COOKIE_CLEAR_TIMEOUT_MILLIS = 5_000L
        private const val PRIVATE_PROFILE_PREFIX = "mangaflow_private_"
        private fun newPrivateProfileName(): String = "$PRIVATE_PROFILE_PREFIX${UUID.randomUUID()}"
    }
}
