package com.example.engine.filter

sealed class FilterRule {
    abstract val rawText: String

    /**
     * Domain blocking rule (e.g., ||doubleclick.net^ or ||popads.net^$third-party)
     */
    data class DomainBlockRule(
        override val rawText: String,
        val domain: String,
        val thirdPartyOnly: Boolean = false
    ) : FilterRule()

    /**
     * Exception / Allowlist rule (e.g., @@||allowed-manhwa.com^)
     */
    data class AllowlistRule(
        override val rawText: String,
        val domain: String
    ) : FilterRule()

    /**
     * URL path or keyword pattern rule (e.g., /banner_ad or /popunder)
     */
    data class PathKeywordRule(
        override val rawText: String,
        val keyword: String
    ) : FilterRule()

    companion object {
        /**
         * Parses a single rule line in EasyList format.
         * Returns null if comment, empty, unsupported, or malformed.
         */
        fun parse(line: String): FilterRule? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) {
                return null
            }

            // Exception rule: @@||domain^
            if (trimmed.startsWith("@@||")) {
                val cleanDomain = trimmed.substring(4)
                    .substringBefore("^")
                    .substringBefore("$")
                    .lowercase()
                if (cleanDomain.isNotBlank()) {
                    return AllowlistRule(trimmed, cleanDomain)
                }
            }

            // Domain rule: ||domain^
            if (trimmed.startsWith("||")) {
                val rest = trimmed.substring(2)
                val domain = rest.substringBefore("^").substringBefore("/").substringBefore("$").lowercase()
                val isThirdParty = rest.contains("\$third-party", ignoreCase = true)
                if (domain.isNotBlank()) {
                    return DomainBlockRule(trimmed, domain, isThirdParty)
                }
            }

            // Path keyword rule: /keyword/
            if (trimmed.startsWith("/") && trimmed.length > 3) {
                val clean = trimmed.trim('/').lowercase()
                if (clean.length >= 3 && !clean.contains(" ")) {
                    return PathKeywordRule(trimmed, clean)
                }
            }

            // Plain keyword match if not CSS cosmetic rule
            if (!trimmed.contains("##") && !trimmed.contains("#@#") && trimmed.length >= 4 && !trimmed.contains(" ")) {
                return PathKeywordRule(trimmed, trimmed.lowercase())
            }

            return null
        }
    }
}

data class FilterUpdateResult(
    val success: Boolean,
    val rulesParsedCount: Int,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
