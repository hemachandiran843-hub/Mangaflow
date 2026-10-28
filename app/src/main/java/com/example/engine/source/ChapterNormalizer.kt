package com.example.engine.source

import com.example.data.local.entity.ChapterEntity
import java.util.Locale

/** Deterministic chapter identity normalization and de-duplication shared by all adapters. */
object ChapterNormalizer {
    private val chapterNumberRegex = Regex(
        """(?:chapter|ch\.?|episode|ep\.?|chp\.?)\s*(\d+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE
    )
    private val decimalRegex = Regex("""^(\d+(?:\.\d+)?)$""")
    private val sideStoryRegex = Regex(
        """(?:side[\s_-]*story|side|extra|special|bonus|omake)\s*(\d+(?:\.\d+)?)?""",
        RegexOption.IGNORE_CASE
    )

    /** Returns identifier, finite numeric value when known, and a non-empty display title. */
    fun normalizeChapter(rawIdentifier: String?, rawTitle: String, url: String): Triple<String, Float?, String> {
        val cleanTitle = rawTitle.trim().take(MAX_TITLE_LENGTH)
        val identifierCandidate = rawIdentifier?.trim()?.take(MAX_IDENTIFIER_LENGTH).orEmpty()
        val decimalMatch = decimalRegex.matchEntire(identifierCandidate)
        if (decimalMatch != null) {
            val number = finiteNumber(decimalMatch.groupValues[1])
            if (number != null) {
                val raw = decimalMatch.groupValues[1]
                val pieces = raw.split('.', limit = 2)
                val integer = pieces[0].trimStart('0').ifBlank { "0" }
                val canonical = integer + pieces.getOrNull(1)?.let { ".$it" }.orEmpty()
                return Triple(canonical, number, cleanTitle.ifBlank { "Chapter $canonical" })
            }
        }

        val titleMatch = chapterNumberRegex.find(cleanTitle)
        if (titleMatch != null) {
            val rawNumber = titleMatch.groupValues[1]
            val number = finiteNumber(rawNumber)
            if (number != null) return Triple(rawNumber, number, cleanTitle)
        }

        val lowerTitle = cleanTitle.lowercase(Locale.ROOT)
        if (lowerTitle.contains("prologue") || lowerTitle.contains("intro") || lowerTitle.contains("chapter 0")) {
            return Triple("prologue", 0f, cleanTitle.ifBlank { "Prologue" })
        }
        if (lowerTitle.contains("epilogue") || lowerTitle.contains("afterword")) {
            return Triple("epilogue", null, cleanTitle.ifBlank { "Epilogue" })
        }

        val sideMatch = sideStoryRegex.find(cleanTitle)
        if (sideMatch != null) {
            val suffix = sideMatch.groupValues.getOrNull(1).orEmpty()
            val id = if (suffix.isNotBlank()) "side-story-$suffix" else "side-story"
            return Triple(id, null, cleanTitle.ifBlank { "Side Story" })
        }

        val urlMatch = chapterNumberRegex.find(url)
        if (urlMatch != null) {
            val rawNumber = urlMatch.groupValues[1]
            val number = finiteNumber(rawNumber)
            if (number != null) return Triple(rawNumber, number, cleanTitle.ifBlank { "Chapter $rawNumber" })
        }

        val stableFallback = identifierCandidate.ifBlank {
            cleanTitle.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(MAX_IDENTIFIER_LENGTH)
                .ifBlank { "ch-unknown" }
        }
        return Triple(stableFallback, null, cleanTitle.ifBlank { "Special Chapter" })
    }

    /**
     * Compares normalized chapter identities. A source-provided identifier (for example an API UUID)
     * is kept stable; parsed title numbers are used as secondary duplicate evidence.
     */
    fun findUnseenChapters(
        existingChapters: List<ChapterEntity>,
        fetchedChapters: List<SourceChapter>
    ): List<SourceChapter> {
        val existingIdentifiers = existingChapters
            .map { it.chapterIdentifier.trim().lowercase(Locale.ROOT) }
            .filter(String::isNotBlank)
            .toSet()
        // A numeric chapter number is only a safe identity fallback when the stored identifier is
        // itself numeric. Stable source IDs (for example two scanlation UUIDs for chapter 1) must
        // not be collapsed merely because they share a display number.
        val existingNumbers = existingChapters
            .filter { it.chapterIdentifier.trim().matches(Regex("\\d+(?:\\.\\d+)?")) }
            .mapNotNull { it.chapterNumber?.takeIf(Float::isFinite) }
            .toSet()

        val seenIdentifiers = mutableSetOf<String>()
        val seenNumbers = mutableSetOf<Float>()
        val unseen = mutableListOf<SourceChapter>()

        for (fetched in fetchedChapters) {
            val normalized = normalizeChapter(fetched.identifier, fetched.title, fetched.url)
            val identifier = fetched.identifier.trim().take(MAX_IDENTIFIER_LENGTH).ifBlank { normalized.first }
            val number = fetched.number?.takeIf(Float::isFinite) ?: normalized.second
            val title = fetched.title.trim().take(MAX_TITLE_LENGTH).ifBlank { normalized.third }
            val lowerIdentifier = identifier.lowercase(Locale.ROOT)

            if (lowerIdentifier in existingIdentifiers || lowerIdentifier in seenIdentifiers) continue
            val numericIdentity = identifier.matches(Regex("\\d+(?:\\.\\d+)?"))
            if (numericIdentity && number != null && (number in existingNumbers || number in seenNumbers)) continue

            seenIdentifiers += lowerIdentifier
            if (numericIdentity && number != null) seenNumbers += number
            unseen += fetched.copy(identifier = identifier, number = number, title = title)
        }
        return sortChapters(unseen)
    }

    fun sortChapters(chapters: List<SourceChapter>): List<SourceChapter> = chapters.sortedWith { a, b ->
        when {
            a.identifier.equals("prologue", ignoreCase = true) && !b.identifier.equals("prologue", ignoreCase = true) -> -1
            !a.identifier.equals("prologue", ignoreCase = true) && b.identifier.equals("prologue", ignoreCase = true) -> 1
            a.number != null && b.number != null -> a.number.compareTo(b.number)
            a.number != null && b.number == null -> -1
            a.number == null && b.number != null -> 1
            else -> a.identifier.compareTo(b.identifier)
        }
    }

    fun isChapterNewer(candidateNum: Float?, candidateId: String, currentNum: Float?, currentId: String): Boolean {
        if (candidateNum != null && currentNum != null) return candidateNum > currentNum
        if (candidateNum != null && currentNum == null) return true
        if (candidateId.equals("prologue", ignoreCase = true)) return false
        return candidateId != currentId
    }

    private fun finiteNumber(value: String): Float? = value.toFloatOrNull()?.takeIf(Float::isFinite)

    private const val MAX_IDENTIFIER_LENGTH = 200
    private const val MAX_TITLE_LENGTH = 500
}
