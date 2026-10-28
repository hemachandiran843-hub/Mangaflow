package com.example.engine.browser

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

enum class SearchEngine(val displayName: String, val searchUrlPrefix: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q=");

    companion object {
        fun fromDisplayName(name: String): SearchEngine {
            return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: DUCKDUCKGO
        }
    }
}

object UrlParser {

    // Regex for valid IPv4 addresses
    private val IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(:\\d{1,5})?(/.*)?$"
    )

    // Regex for domain names with valid TLDs (2+ letters) or localhost
    private val DOMAIN_PATTERN = Pattern.compile(
        "^(localhost|([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63})(:\\d{1,5})?(/.*)?$"
    )

    /**
     * Normalizes address input by differentiating between valid URLs and search queries.
     * Never mistakes natural search queries for websites.
     */
    fun parse(input: String, searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return "about:blank"
        }

        // Support internal schemes
        if (trimmed.startsWith("about:", ignoreCase = true)) {
            return trimmed.lowercase()
        }

        // Check if explicit scheme is provided
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            // Already has scheme, upgrade http to https if desirable or preserve
            return trimmed
        }

        // Queries with whitespace are always search queries
        if (trimmed.contains(" ") || trimmed.contains("\t") || trimmed.contains("\n")) {
            return buildSearchUrl(trimmed, searchEngine)
        }

        // Check for IP address or valid domain pattern
        if (IPV4_PATTERN.matcher(trimmed).matches() || DOMAIN_PATTERN.matcher(trimmed).matches()) {
            return "https://$trimmed"
        }

        // Fallback: treat as search query
        return buildSearchUrl(trimmed, searchEngine)
    }

    private fun buildSearchUrl(query: String, searchEngine: SearchEngine): String {
        val encoded = try {
            URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        } catch (_: Exception) {
            query.replace(" ", "+")
        }
        return "${searchEngine.searchUrlPrefix}$encoded"
    }

    /**
     * Extracts a clean domain host for display in the Omnibox.
     */
    fun extractDisplayHost(url: String): String {
        if (url.startsWith("about:", ignoreCase = true)) {
            return url
        }
        return try {
            val host = try {
                java.net.URI(url).host
            } catch (_: Exception) {
                null
            } ?: run {
                val afterScheme = url.substringAfter("://", "")
                if (afterScheme.isNotEmpty()) {
                    afterScheme.substringBefore("/").substringBefore("?").substringBefore(":")
                } else {
                    url.substringBefore("/").substringBefore("?").substringBefore(":")
                }
            } ?: url

            if (host.startsWith("www.", ignoreCase = true)) host.substring(4) else host
        } catch (_: Exception) {
            url
        }
    }
}
