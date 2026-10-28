package com.example.engine.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Official API Adapter for MangaDex (https://api.mangadex.org).
 * Uses official, permitted REST endpoints for series and chapter metadata.
 * Complies with rate limits (5 requests per second max) and API guidelines.
 */
class MangaDexSourceAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SourceAdapter {

    override val sourceId: String = "mangadex"
    override val displayName: String = "MangaDex (Official API)"
    override val baseUrl: String = "https://mangadex.org"
    override val capabilities: SourceCapabilities = SourceCapabilities(
        supportsMetadataLookup = true,
        supportsChapterList = true,
        supportsUpdateDetection = true,
        supportsRssFeed = false,
        isOfficialApi = true,
        rateLimitRequestsPerMinute = 30
    )

    private val uuidRegex = Regex("""([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})""")

    override fun canHandle(url: String): Boolean {
        return url.contains("mangadex.org", ignoreCase = true) ||
                url.contains("api.mangadex.org", ignoreCase = true)
    }

    /**
     * Extracts the MangaDex series UUID from a user-supplied URL or slug.
     */
    fun extractMangaId(url: String): String? {
        val match = uuidRegex.find(url)
        return match?.groupValues?.get(1)
    }

    override suspend fun fetchSeriesMetadata(sourceUrl: String): SourceResult<SourceSeriesMetadata> = withContext(Dispatchers.IO) {
        val mangaId = extractMangaId(sourceUrl)
            ?: return@withContext SourceResult.Unsupported("Could not extract MangaDex UUID from: $sourceUrl")

        val apiUrl = "https://api.mangadex.org/manga/$mangaId"
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", "MangaFlow-Android/1.0 (https://github.com/mangaflow)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> {
                        val retryHeader = response.header("Retry-After")?.toLongOrNull() ?: 10L
                        return@withContext SourceResult.RateLimited(retryHeader, "MangaDex rate limit reached")
                    }
                    response.code in 500..599 -> {
                        return@withContext SourceResult.SourceUnavailable(response.code, "MangaDex server error")
                    }
                    response.code == 404 -> {
                        return@withContext SourceResult.SourceUnavailable(404, "Series not found on MangaDex")
                    }
                    !response.isSuccessful -> {
                        return@withContext SourceResult.SourceUnavailable(response.code, "HTTP ${response.code}")
                    }
                }

                val body = response.body?.string()
                    ?: return@withContext SourceResult.ParsingError("Empty response body from MangaDex")

                val json = JSONObject(body)
                val dataObj = json.getJSONObject("data")
                val attr = dataObj.getJSONObject("attributes")

                // Extract title
                val titlesObj = attr.getJSONObject("title")
                val title = when {
                    titlesObj.has("en") -> titlesObj.getString("en")
                    titlesObj.has("ko") -> titlesObj.getString("ko")
                    titlesObj.has("ja") -> titlesObj.getString("ja")
                    titlesObj.keys().hasNext() -> titlesObj.getString(titlesObj.keys().next())
                    else -> "Unknown Title"
                }

                // Extract description
                val descObj = attr.optJSONObject("description")
                val desc = descObj?.optString("en", "")?.ifBlank { null }

                val status = attr.optString("status", "ongoing")
                val latestChapter = attr.optString("lastChapter", "")

                SourceResult.Success(
                    SourceSeriesMetadata(
                        title = title,
                        description = desc,
                        status = status,
                        latestChapterIdentifier = latestChapter.ifBlank { null }
                    )
                )
            }
        } catch (e: IOException) {
            SourceResult.NetworkError("Failed to connect to MangaDex API: ${e.message}", e)
        } catch (e: Exception) {
            SourceResult.ParsingError("Failed to parse MangaDex JSON: ${e.message}")
        }
    }

    override suspend fun fetchChapters(sourceUrl: String): SourceResult<List<SourceChapter>> = withContext(Dispatchers.IO) {
        val mangaId = extractMangaId(sourceUrl)
            ?: return@withContext SourceResult.Unsupported("Could not extract MangaDex UUID from: $sourceUrl")

        val apiUrl = "https://api.mangadex.org/manga/$mangaId/feed?translatedLanguage[]=en&order[chapter]=desc&limit=100"
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", "MangaFlow-Android/1.0 (https://github.com/mangaflow)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> {
                        val retryHeader = response.header("Retry-After")?.toLongOrNull() ?: 10L
                        return@withContext SourceResult.RateLimited(retryHeader, "MangaDex rate limit reached")
                    }
                    response.code in 500..599 -> {
                        return@withContext SourceResult.SourceUnavailable(response.code, "MangaDex server error")
                    }
                    !response.isSuccessful -> {
                        return@withContext SourceResult.SourceUnavailable(response.code, "HTTP ${response.code}")
                    }
                }

                val body = response.body?.string()
                    ?: return@withContext SourceResult.ParsingError("Empty response body from MangaDex")

                val json = JSONObject(body)
                val dataArray = json.getJSONArray("data")
                val chapters = mutableListOf<SourceChapter>()

                for (i in 0 until dataArray.length()) {
                    val chObj = dataArray.getJSONObject(i)
                    val chId = chObj.getString("id")
                    val attr = chObj.getJSONObject("attributes")

                    val rawChapterStr = attr.optString("chapter", "")
                    val rawTitle = attr.optString("title", "")
                    val publishAtStr = attr.optString("publishAt", "")

                    val (iden, num, formattedTitle) = ChapterNormalizer.normalizeChapter(
                        rawIdentifier = rawChapterStr.ifBlank { null },
                        rawTitle = if (rawTitle.isNotBlank()) "Chapter $rawChapterStr: $rawTitle" else "Chapter $rawChapterStr",
                        url = "https://mangadex.org/chapter/$chId"
                    )

                    val publishTimestamp = parseIso8601Timestamp(publishAtStr)

                    chapters.add(
                        SourceChapter(
                            identifier = iden,
                            number = num,
                            title = formattedTitle,
                            url = "https://mangadex.org/chapter/$chId",
                            publishedAt = publishTimestamp
                        )
                    )
                }

                if (chapters.isEmpty()) {
                    SourceResult.NoNewChapters("0")
                } else {
                    SourceResult.Success(ChapterNormalizer.sortChapters(chapters))
                }
            }
        } catch (e: IOException) {
            SourceResult.NetworkError("Failed to connect to MangaDex chapter feed: ${e.message}", e)
        } catch (e: Exception) {
            SourceResult.ParsingError("Failed to parse MangaDex chapter feed: ${e.message}")
        }
    }

    private fun parseIso8601Timestamp(isoString: String): Long? {
        if (isoString.isBlank()) return null
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(isoString.substringBefore("+").substringBefore("Z"))?.time
        } catch (_: Exception) {
            null
        }
    }
}
