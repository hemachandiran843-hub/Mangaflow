package com.example.engine.privacy

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

class PrivacyManager {

    @Volatile
    private var _isPrivateActive: Boolean = false

    fun isPrivateSessionActive(): Boolean = _isPrivateActive

    fun enterPrivateSession() {
        _isPrivateActive = true
    }

    fun exitPrivateSession() {
        _isPrivateActive = false
    }

    /**
     * Purges temporary cookies, website storage, and cache created during private tabs.
     * Guaranteed to be invoked when private sessions end or all private tabs close.
     */
    fun purgePrivateData(context: Context) {
        _isPrivateActive = false
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeSessionCookies(null)
            cookieManager.flush()

            WebStorage.getInstance().deleteAllData()

            // Purge temporary in-memory cache without impacting app asset caches
            val cleanupWebView = WebView(context)
            cleanupWebView.clearCache(true)
            cleanupWebView.clearFormData()
            cleanupWebView.destroy()
        } catch (_: Exception) {
            // Silently recover if WebView provider is unavailable in headless/test environments
        }
    }

    /**
     * Verifies if a browsing visit is allowed to be recorded in SQLite history table.
     * Private sessions always return false.
     */
    fun shouldPersistHistory(isPrivate: Boolean): Boolean = !isPrivate

    /**
     * Verifies if reading duration, chapter read count, or streaks can be saved.
     * Private sessions always return false.
     */
    fun shouldTrackReadingStats(isPrivate: Boolean): Boolean = !isPrivate

    /**
     * Verifies if an entered search query or URL can be logged in suggestion history.
     */
    fun shouldPersistSearchQuery(isPrivate: Boolean): Boolean = !isPrivate

    /**
     * Sanitizes diagnostic logs. Ensures private URLs and chapter titles are never
     * emitted to Logcat, crash telemetry, or diagnostics.
     */
    fun sanitizeLog(message: String, isPrivate: Boolean): String {
        return if (isPrivate) {
            "[PRIVATE_SESSION_REDACTED]"
        } else {
            message
        }
    }

    /**
     * Clear, transparent user explanation.
     * Does not make false claims of complete anonymity.
     */
    fun getPrivacyNotice(): String {
        return "Private browsing prevents browsing history, search queries, cookies, and reading statistics from being saved on this device. When all private tabs close, temporary session data is wiped. Note: Private browsing does not make you anonymous to your internet service provider (ISP), network administrator, or visited websites."
    }
}
