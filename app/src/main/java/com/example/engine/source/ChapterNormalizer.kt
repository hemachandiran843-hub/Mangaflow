package com.example.engine.source

import com.example.data.local.entity.ChapterEntity
import java.util.Locale

/**
 * Normalizes raw chapter titles, strings, and metadata from heterogeneous sources.
 * Enforces deduplication and deterministic ordering across decimals, specials, and side stories.
 */
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

    /**
     * Extracts a canonical identifier, numeric float (if any), and clean title from raw input.
     */
    fun normalizeChapter(
        rawIdentifier: String?,
        rawTitle: String,
        url: String
    ): Triple<String, Float?, String> {
        val cleanTitle = rawTitle.trim()

        // 1. Check if rawIdentifier is already a clean number/decimal
        val idenCandidate = rawIdentifier?.trim() ?: ""
        val decimalMatch = decimalRegex.find(idenCandidate)
        if (decimalMatch != null) {
            val num = decimalMatch.groupValues[1].toFloatOrNull()
            return Triple(decimalMatch.groupValues[1], num, cleanTitle.ifBlank { "Chapter ${decimalMatch.groupValues[1]}" })
        }

        // 2. Check title for "Chapter X" or "Ch. X" or "Episode X"
        val chMatch = chapterNumberRegex.find(cleanTitle)
        if (chMatch != null) {
            val numStr = chMatch.groupValues[1]
            val num = numStr.toFloatOrNull()
            return Triple(numStr, num, cleanTitle)
        }

        // 3. Check for Prologue / Intro
        val lowerTitle = cleanTitle.lowercase(Locale.ROOT)
        if (lowerTitle.contains("prologue") || lowerTitle.contains("intro") || lowerTitle.contains("chapter 0")) {
            return Triple("prologue", 0.0f, if (cleanTitle.isNotBlank()) cleanTitle else "Prologue")
        }

        // 4. Check for Epilogue / Afterword
        if (lowerTitle.contains("epilogue") || lowerTitle.contains("afterword")) {
            return Triple("epilogue", null, if (cleanTitle.isNotBlank()) cleanTitle else "Epilogue")
        }

        // 5. Check for Side Story / Special / Extra
        val sideMatch = sideStoryRegex.find(cleanTitle)
        if (sideMatch != null) {
            val subNum = sideMatch.groupValues[1].ifBlank { null }
            val iden = if (subNum != null) "side-story-$subNum" else "side-story"
            return Triple(iden, null, cleanTitle)
        }

        // 6. Try parsing numbers from URL slug if title didn't contain anything
        val urlMatch = chapterNumberRegex.find(url)
        if (urlMatch != null) {
            val numStr = urlMatch.groupValues[1]
            val num = numStr.toFloatOrNull()
            return Triple(numStr, num, cleanTitle.ifBlank { "Chapter $numStr" })
        }

        // 7. Fallback to sanitized identifier
        val fallbackIden = idenCandidate.ifBlank {
            cleanTitle.lowercase(Locale.ROOT)
                .replace(Regex("""[^a-z0-9]+"""), "-")
                .trim('-')
                .ifBlank { "ch-unknown" }
        }
        return Triple(fallbackIden, null, cleanTitle.ifBlank { "Special Chapter" })
    }

    /**
     * Identifies newly detected chapters by comparing fetched source chapters against stored records.
     * Prevents false positives where only URL changed.
     * Prevents duplicate chapter records.
     */
    fun findUnseenChapters(
        existingChapters: List<ChapterEntity>,
        fetchedChapters: List<SourceChapter>
    ): List<SourceChapter> {
        val existingIdentifiers = existingChapters.map { it.chapterIdentifier.trim().lowercase(Locale.ROOT) }.toSet()
        val existingNumbers = existingChapters.mapNotNull { it.chapterNumber }.toSet()
        val existingTitles = existingChapters.map { it.chapterTitle.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .toSet()

        val unseen = mutableListOf<SourceChapter>()
        val seenInCurrentBatch = mutableSetOf<String>()

        for (fetched in fetchedChapters) {
            val (canonId, canonNum, _) = normalizeChapter(fetched.identifier, fetched.title, fetched.url)
            val lowerId = canonId.lowercase(Locale.ROOT)

            // Check if already in database by identifier
            if (existingIdentifiers.contains(lowerId)) continue

            // Check if already in database by exact numeric value
            if (canonNum != null && existingNumbers.contains(canonNum)) continue

            // Check if already in database by exact title (for non-numeric chapters like "Prologue")
            val lowerTitle = fetched.title.trim().lowercase(Locale.ROOT)
            if (lowerTitle.isNotBlank() && existingTitles.contains(lowerTitle)) continue

            // Deduplicate within the same fetched batch
            if (seenInCurrentBatch.contains(lowerId)) continue
            seenInCurrentBatch.add(lowerId)

            unseen.add(fetched.copy(identifier = canonId, number = canonNum))
        }

        return sortChapters(unseen)
    }

    /**
     * Orders chapters in ascending reading order:
     * - Prologue first
     * - Numeric chapters in ascending order (0.1, 1, 2, 2.5, 3...)
     * - Side stories and Epilogues after main chapters
     */
    fun sortChapters(chapters: List<SourceChapter>): List<SourceChapter> {
        return chapters.sortedWith { a, b ->
            when {
                a.identifier.equals("prologue", ignoreCase = true) -> -1
                b.identifier.equals("prologue", ignoreCase = true) -> 1
                a.number != null && b.number != null -> a.number.compareTo(b.number)
                a.number != null && b.number == null -> -1
                a.number == null && b.number != null -> 1
                else -> a.identifier.compareTo(b.identifier)
            }
        }
    }

    /**
     * Compares two chapter numbers or identifiers to see if chapter A is newer than chapter B.
     */
    fun isChapterNewer(candidateNum: Float?, candidateId: String, currentNum: Float?, currentId: String): Boolean {
        if (candidateNum != null && currentNum != null) {
            return candidateNum > currentNum
        }
        if (candidateNum != null && currentNum == null) {
            return true
        }
        if (candidateId.equals("prologue", ignoreCase = true)) {
            return false
        }
        return candidateId != currentId
    }
}
