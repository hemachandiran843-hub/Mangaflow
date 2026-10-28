package com.example.engine.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Adapter for WEBTOON (webtoons.com).
 * Uses public episode listings and HTML semantic metadata without bypassing logins or paywalls.
 */
class WebtoonsSourceAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SourceAdapter {

    override val sourceId: String = "webtoons"
    override val displayName: String = "WEBTOON (Public)"
    override val baseUrl: String = "https://www.webtoons.com"
    override val capabilities: SourceCapabilities = SourceCapabilities(
        supportsMetadataLookup = true,
        supportsChapterList = true,
        supportsUpdateDetection = true,
        supportsRssFeed = false,
        isOfficialApi = false,
        rateLimitRequestsPerMinute = 15
    )

    private val titleNoRegex = Regex("""title_no=(\d+)""")
    private val episodeItemRegex = Regex("""<li[^>]*id="episode_(\d+)"[^>]*>[\s\S]*?<span[^>]*class="subj"[^>]*><span>([^<]+)</span>[\s\S]*?<a[^>]*href="([^"]+)"[\s\S]*?<span[^>]*class="date"[^>]*>([^<]+)</span>""")
    private val pageTitleRegex = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""")
    private val pageCoverRegex = Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""")
    private val pageDescRegex = Regex("""<meta\s+property="og:description"\s+content="([^"]+)"""")

    override fun canHandle(url: String): Boolean {
        return url.contains("webtoons.com", ignoreCase = true)
    }

    override suspend fun fetchSeriesMetadata(sourceUrl: String): SourceResult<SourceSeriesMetadata> = withContext(Dispatchers.IO) {
        val listUrl = toListUrl(sourceUrl)
        val htmlResult = fetchHtml(listUrl)
        if (htmlResult !is SourceResult.Success) {
            @Suppress("UNCHECKED_CAST")
            return@withContext htmlResult as SourceResult<SourceSeriesMetadata>
        }

        val html = htmlResult.data
        val titleMatch = pageTitleRegex.find(html)
        val coverMatch = pageCoverRegex.find(html)
        val descMatch = pageDescRegex.find(html)

        val rawTitle = titleMatch?.groupValues?.get(1)?.replace(Regex("""\s*\|\s*WEBTOON.*"""), "")?.trim()
            ?: "Webtoon Series"

        SourceResult.Success(
            SourceSeriesMetadata(
                title = rawTitle,
                coverUrl = coverMatch?.groupValues?.get(1),
                description = descMatch?.groupValues?.get(1)
            )
        )
    }

    override suspend fun fetchChapters(sourceUrl: String): SourceResult<List<SourceChapter>> = withContext(Dispatchers.IO) {
        val listUrl = toListUrl(sourceUrl)
        val htmlResult = fetchHtml(listUrl)
        if (htmlResult !is SourceResult.Success) {
            @Suppress("UNCHECKED_CAST")
            return@withContext htmlResult as SourceResult<List<SourceChapter>>
        }

        val html = htmlResult.data
        val matches = episodeItemRegex.findAll(html).toList()
        val chapters = mutableListOf<SourceChapter>()

        for (match in matches) {
            val epNumStr = match.groupValues[1]
            val epTitle = match.groupValues[2].trim()
            val epUrl = match.groupValues[3].replace("&amp;", "&")

            val (iden, num, formattedTitle) = ChapterNormalizer.normalizeChapter(
                rawIdentifier = epNumStr,
                rawTitle = if (epTitle.isNotBlank()) "Episode $epNumStr - $epTitle" else "Episode $epNumStr",
                url = epUrl
            )

            chapters.add(
                SourceChapter(
                    identifier = iden,
                    number = num,
                    title = formattedTitle,
                    url = if (epUrl.startsWith("http")) epUrl else "https://www.webtoons.com$epUrl"
                )
            )
        }

        if (chapters.isEmpty()) {
            // Check if page blocked or anti-bot challenge occurred
            if (html.contains("cf-browser-verification") || html.contains("Checking your browser")) {
                SourceResult.SourceUnavailable(403, "Protected by Cloudflare verification")
            } else {
                SourceResult.NoNewChapters("0")
            }
        } else {
            SourceResult.Success(ChapterNormalizer.sortChapters(chapters))
        }
    }

    private suspend fun fetchHtml(url: String): SourceResult<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Referer", "https://www.webtoons.com/")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> SourceResult.RateLimited(60L, "WEBTOON rate limit exceeded")
                    response.code in 500..599 -> SourceResult.SourceUnavailable(response.code, "WEBTOON server error")
                    response.code == 404 -> SourceResult.SourceUnavailable(404, "Series page not found")
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
            SourceResult.NetworkError("Failed to connect to WEBTOON: ${e.message}", e)
        } catch (e: Exception) {
            SourceResult.ParsingError("Error reading WEBTOON page: ${e.message}")
        }
    }

    private fun toListUrl(url: String): String {
        // If already list url, keep it
        if (url.contains("/list?")) return url
        val titleNo = titleNoRegex.find(url)?.groupValues?.get(1) ?: return url
        val basePart = url.substringBefore("/viewer").substringBefore("/episode-")
        return "$basePart/list?title_no=$titleNo"
    }
}
