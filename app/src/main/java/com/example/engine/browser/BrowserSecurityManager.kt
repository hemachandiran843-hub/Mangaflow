package com.example.engine.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI

object BrowserSecurityManager {

    private val SAFE_WEB_SCHEMES = setOf("http", "https", "about")
    private val EXTERNAL_SCHEMES = setOf("market", "intent", "mailto", "tel", "sms")

    /**
     * Determines if a URL uses a safe web scheme that can be rendered directly by WebView.
     */
    fun isSafeWebScheme(url: String): Boolean {
        if (url.startsWith("about:blank", ignoreCase = true)) return true
        val scheme = getScheme(url) ?: return false
        return SAFE_WEB_SCHEMES.contains(scheme.lowercase())
    }

    /**
     * Checks if the URL targets an external app or system action.
     */
    fun isExternalActionScheme(url: String): Boolean {
        val scheme = getScheme(url) ?: return false
        return EXTERNAL_SCHEMES.contains(scheme.lowercase())
    }

    /**
     * Creates an external launch Intent if safe and valid.
     */
    fun createExternalIntent(url: String): Intent? {
        return try {
            if (url.startsWith("intent:", ignoreCase = true)) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                }
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Attempts to resolve and launch an external intent safely without crashing the app.
     */
    fun handleExternalNavigation(context: Context, url: String): Boolean {
        val intent = createExternalIntent(url) ?: return false
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getScheme(url: String): String? {
        return try {
            val uri = URI(url)
            uri.scheme
        } catch (_: Exception) {
            try {
                Uri.parse(url).scheme
            } catch (_: Exception) {
                val idx = url.indexOf(':')
                if (idx > 0) url.substring(0, idx) else null
            }
        }
    }
}
