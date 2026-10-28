package com.example.engine.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Adapter for open web pages using permitted public semantic metadata (OpenGraph & HTML5).
 * Does not bypass bot protections, CAPTCHAs, or paywalls.
 */
class OpenWebMetaSourceAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SourceAdapter {

    override val sourceId: String = "open_web"
    override val displayName: String = "Open Web (Metadata)"
    override val baseUrl: String = ""
    override val capabilities: SourceCapabilities = SourceCapabilities(
        supportsMetadataLookup = true,
        supportsChapterList = false,
        supportsUpdateDetection = true,
        supportsRssFeed = false,
        isOfficialApi = false,
        rateLimitRequestsPerMinute = 15
    )

    private val ogTitleRegex = Regex("""<meta\s+(?:property|name)=["']og:title["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ogImageRegex = Regex("""<meta\s+(?:property|name)=["']og:image["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ogDescRegex = Regex("""<meta\s+(?:property|name)=["'](?:og:description|description)["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val titleTagRegex = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)

    override fun canHandle(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    override suspend fun fetchSeriesMetadata(sourceUrl: String): SourceResult<SourceSeriesMetadata> = withContext(Dispatchers.IO) {
        val htmlResult = fetchHtml(sourceUrl)
        if (htmlResult !is SourceResult.Success) {
            @Suppress("UNCHECKED_CAST")
            return@withContext htmlResult as SourceResult<SourceSeriesMetadata>
        }

        val html = htmlResult.data
        val title = ogTitleRegex.find(html)?.groupValues?.get(1)
            ?: titleTagRegex.find(html)?.groupValues?.get(1)?.trim()
            ?: "Web Series"

        val cleanTitle = title.replace(Regex("""\s*[-|]\s*Chapter.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[-|]\s*Read.*""", RegexOption.IGNORE_CASE), "")
            .trim()

        val cover = ogImageRegex.find(html)?.groupValues?.get(1)
        val desc = ogDescRegex.find(html)?.groupValues?.get(1)

        SourceResult.Success(
            SourceSeriesMetadata(
                title = cleanTitle,
                coverUrl = cover,
                description = desc
            )
        )
    }

    override suspend fun fetchChapters(sourceUrl: String): SourceResult<List<SourceChapter>> = withContext(Dispatchers.IO) {
        val htmlResult = fetchHtml(sourceUrl)
        if (htmlResult !is SourceResult.Success) {
            @Suppress("UNCHECKED_CAST")
            return@withContext htmlResult as SourceResult<List<SourceChapter>>
        }

        val html = htmlResult.data

        // Check for Cloudflare / anti-bot challenges
        if (html.contains("Checking your browser") || html.contains("Just a moment...") || html.contains("cf-browser-verification")) {
            return@withContext SourceResult.SourceUnavailable(403, "Protected by Cloudflare verification")
        }

        // Try extracting chapter from title or open-graph
        val rawTitle = ogTitleRegex.find(html)?.groupValues?.get(1)
            ?: titleTagRegex.find(html)?.groupValues?.get(1)
            ?: ""

        val (iden, num, formattedTitle) = ChapterNormalizer.normalizeChapter(
            rawIdentifier = null,
            rawTitle = rawTitle,
            url = sourceUrl
        )

        if (num != null || iden != "ch-unknown") {
            SourceResult.Success(
                listOf(
                    SourceChapter(
                        identifier = iden,
                        number = num,
                        title = formattedTitle,
                        url = sourceUrl,
                        publishedAt = System.currentTimeMillis()
                    )
                )
            )
        } else {
            SourceResult.NoNewChapters("0")
        }
    }

    private suspend fun fetchHtml(url: String): SourceResult<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> SourceResult.RateLimited(60L, "Rate limited by host")
                    response.code in 500..599 -> SourceResult.SourceUnavailable(response.code, "Host server error")
                    response.code == 404 -> SourceResult.SourceUnavailable(404, "Page not found")
                    response.code == 403 -> SourceResult.SourceUnavailable(403, "Access forbidden or protected")
                    !response.isSuccessful -> SourceResult.SourceUnavailable(response.code, "HTTP ${response.code}")
                    else -> {
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) {
                            SourceResult.ParsingError("Empty HTML response")
                        } else {
                            SourceResult.Success(body)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            SourceResult.NetworkError("Failed to connect: ${e.message}", e)
        } catch (e: Exception) {
            SourceResult.ParsingError("Error reading webpage: ${e.message}")
        }
    }
}
