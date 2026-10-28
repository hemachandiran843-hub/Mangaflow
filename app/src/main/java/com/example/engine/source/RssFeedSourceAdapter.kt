package com.example.engine.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Adapter for public RSS/Atom update feeds.
 * Works with any manhwa or comic site providing public syndication feeds (e.g. /feed, /rss, /atom.xml).
 */
class RssFeedSourceAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : SourceAdapter {

    override val sourceId: String = "rss"
    override val displayName: String = "Public RSS / Web Feed"
    override val baseUrl: String = ""
    override val capabilities: SourceCapabilities = SourceCapabilities(
        supportsMetadataLookup = true,
        supportsChapterList = true,
        supportsUpdateDetection = true,
        supportsRssFeed = true,
        isOfficialApi = false,
        rateLimitRequestsPerMinute = 20
    )

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.endsWith(".rss") ||
                lower.endsWith("/feed") ||
                lower.endsWith("/feed/") ||
                lower.endsWith("/rss.xml") ||
                lower.endsWith("/atom.xml") ||
                lower.contains("format=rss") ||
                lower.contains("rss=true")
    }

    override suspend fun fetchSeriesMetadata(sourceUrl: String): SourceResult<SourceSeriesMetadata> = withContext(Dispatchers.IO) {
        val rawFeedResult = fetchRawFeed(sourceUrl)
        if (rawFeedResult !is SourceResult.Success) {
            @Suppress("UNCHECKED_CAST")
            return@withContext rawFeedResult as SourceResult<SourceSeriesMetadata>
        }

        try {
            val (channelTitle, channelDesc, _) = parseFeed(rawFeedResult.data)
            SourceResult.Success(
                SourceSeriesMetadata(
                    title = channelTitle.ifBlank { "Syndicated Feed" },
                    description = channelDesc.ifBlank { null }
                )
            )
        } catch (e: Exception) {
            SourceResult.ParsingError("Failed to parse RSS feed metadata: ${e.message}")
        }
    }

    override suspend fun fetchChapters(sourceUrl: String): SourceResult<List<SourceChapter>> = withContext(Dispatchers.IO) {
        val rawFeedResult = fetchRawFeed(sourceUrl)
        if (rawFeedResult !is SourceResult.Success) {
            @Suppress("UNCHECKED_CAST")
            return@withContext rawFeedResult as SourceResult<List<SourceChapter>>
        }

        try {
            val (_, _, chapters) = parseFeed(rawFeedResult.data)
            if (chapters.isEmpty()) {
                SourceResult.NoNewChapters("0")
            } else {
                SourceResult.Success(ChapterNormalizer.sortChapters(chapters))
            }
        } catch (e: Exception) {
            SourceResult.ParsingError("Failed to parse RSS items: ${e.message}")
        }
    }

    private suspend fun fetchRawFeed(url: String): SourceResult<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) MangaFlow/1.0 RSS Reader")
            .header("Accept", "application/rss+xml, application/atom+xml, text/xml, application/xml")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> SourceResult.RateLimited(60L, "Feed host rate limited")
                    response.code in 500..599 -> SourceResult.SourceUnavailable(response.code, "Feed server error")
                    response.code == 404 -> SourceResult.SourceUnavailable(404, "Feed not found")
                    !response.isSuccessful -> SourceResult.SourceUnavailable(response.code, "HTTP ${response.code}")
                    else -> {
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) {
                            SourceResult.ParsingError("Empty feed response")
                        } else {
                            SourceResult.Success(body)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            SourceResult.NetworkError("Failed to fetch feed: ${e.message}", e)
        } catch (e: Exception) {
            SourceResult.ParsingError("Error reading feed content: ${e.message}")
        }
    }

    /**
     * Parses standard RSS 2.0 or Atom 1.0 XML feeds using XmlPullParser.
     */
    fun parseFeed(xmlString: String): Triple<String, String, List<SourceChapter>> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlString))

        var eventType = parser.eventType
        var channelTitle = ""
        var channelDesc = ""
        val chapters = mutableListOf<SourceChapter>()

        var insideItem = false
        var currentTag = ""
        var currentTitle = ""
        var currentLink = ""
        var currentPubDate = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name?.lowercase(Locale.ROOT) ?: ""
                    when (currentTag) {
                        "item", "entry" -> {
                            insideItem = true
                            currentTitle = ""
                            currentLink = ""
                            currentPubDate = ""
                        }
                        "link" -> {
                            if (insideItem) {
                                val href = parser.getAttributeValue(null, "href")
                                if (!href.isNullOrBlank()) {
                                    currentLink = href
                                }
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotEmpty()) {
                        if (!insideItem) {
                            if (currentTag == "title" && channelTitle.isEmpty()) {
                                channelTitle = text
                            } else if ((currentTag == "description" || currentTag == "subtitle") && channelDesc.isEmpty()) {
                                channelDesc = text
                            }
                        } else {
                            when (currentTag) {
                                "title" -> currentTitle = text
                                "link" -> if (currentLink.isEmpty()) currentLink = text
                                "pubdate", "updated", "published" -> currentPubDate = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val endTag = parser.name?.lowercase(Locale.ROOT) ?: ""
                    if (endTag == "item" || endTag == "entry") {
                        if (currentTitle.isNotBlank()) {
                            val (iden, num, formattedTitle) = ChapterNormalizer.normalizeChapter(
                                rawIdentifier = null,
                                rawTitle = currentTitle,
                                url = currentLink
                            )
                            val timestamp = parsePubDate(currentPubDate)

                            chapters.add(
                                SourceChapter(
                                    identifier = iden,
                                    number = num,
                                    title = formattedTitle,
                                    url = currentLink,
                                    publishedAt = timestamp
                                )
                            )
                        }
                        insideItem = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return Triple(channelTitle, channelDesc, ChapterNormalizer.sortChapters(chapters))
    }

    private fun parsePubDate(rawDate: String): Long? {
        if (rawDate.isBlank()) return null
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                return sdf.parse(rawDate)?.time
            } catch (_: Exception) {}
        }
        return null
    }
}
