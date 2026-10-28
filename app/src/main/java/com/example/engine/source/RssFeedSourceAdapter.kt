package com.example.engine.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Adapter for explicitly supplied public RSS 2.0 or Atom feeds. */
class RssFeedSourceAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
) : SourceAdapter {

    override val sourceId: String = "rss"
    override val displayName: String = "Public RSS / Atom Feed"
    override val baseUrl: String = ""
    override val capabilities = SourceCapabilities(
        supportsMetadataLookup = true,
        supportsChapterList = true,
        supportsUpdateDetection = true,
        supportsRssFeed = true,
        rateLimitRequestsPerMinute = 20
    )

    override fun canHandle(url: String): Boolean {
        val uri = parseHttpUri(url) ?: return false
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        val query = uri.rawQuery.orEmpty().lowercase(Locale.ROOT)
        return path.endsWith(".rss") || path.endsWith("/feed") || path.endsWith("/feed/") ||
            path.endsWith("/rss.xml") || path.endsWith("/atom.xml") ||
            query.contains("format=rss") || query.contains("rss=true") || query.contains("feed=atom")
    }

    override fun canonicalizeSeriesUrl(sourceUrl: String): String? =
        if (canHandle(sourceUrl)) canonicalHttpUrl(sourceUrl) else null

    override suspend fun fetchSeriesMetadata(sourceUrl: String): SourceResult<SourceSeriesMetadata> = withContext(Dispatchers.IO) {
        val feed = fetchRawFeed(sourceUrl)
        if (feed !is SourceResult.Success) return@withContext feed.castFailure()
        try {
            val (title, description, _) = parseFeed(feed.data, sourceUrl)
            if (title.isBlank()) SourceResult.ParsingError("Feed has no usable title")
            else SourceResult.Success(SourceSeriesMetadata(title = title, description = description.ifBlank { null }, canonicalUrl = canonicalizeSeriesUrl(sourceUrl)))
        } catch (failure: Exception) {
            SourceResult.ParsingError("Failed to parse feed metadata: ${failure.message ?: "invalid XML"}")
        }
    }

    override suspend fun fetchChapters(sourceUrl: String): SourceResult<List<SourceChapter>> = withContext(Dispatchers.IO) {
        val feed = fetchRawFeed(sourceUrl)
        if (feed !is SourceResult.Success) return@withContext feed.castFailure()
        try {
            val chapters = parseFeed(feed.data, sourceUrl).third
            SourceResult.Success(ChapterNormalizer.sortChapters(chapters))
        } catch (failure: Exception) {
            SourceResult.ParsingError("Failed to parse feed items: ${failure.message ?: "invalid XML"}")
        }
    }

    private suspend fun fetchRawFeed(url: String): SourceResult<String> = withContext(Dispatchers.IO) {
        if (!canHandle(url)) return@withContext SourceResult.Unsupported("Enter a URL that explicitly points to an RSS or Atom feed")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MangaFlow/1.0 (public feed reader)")
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> SourceResult.RateLimited(response.header("Retry-After")?.toLongOrNull(), "Feed host rate limited the request")
                    response.code == 404 -> SourceResult.SourceUnavailable(404, "Feed was not found")
                    response.code == 403 -> SourceResult.SourceUnavailable(403, "Feed access was denied")
                    response.code in 500..599 -> SourceResult.SourceUnavailable(response.code, "Feed server error")
                    !response.isSuccessful -> SourceResult.SourceUnavailable(response.code, "Feed host returned HTTP ${response.code}")
                    else -> {
                        val body = response.body ?: return@withContext SourceResult.ParsingError("Empty feed response")
                        val bytes = body.byteStream().use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_FEED_BYTES) return@withContext SourceResult.ParsingError("Feed response exceeded the size limit")
                                output.write(buffer, 0, read)
                            }
                            output.toByteArray()
                        }
                        val xml = bytes.toString(Charsets.UTF_8)
                        if (xml.isBlank()) SourceResult.ParsingError("Empty feed response") else SourceResult.Success(xml)
                    }
                }
            }
        } catch (failure: IOException) {
            SourceResult.NetworkError("Failed to fetch public feed: ${failure.message ?: "network error"}", failure)
        } catch (failure: Exception) {
            SourceResult.ParsingError("Error reading public feed: ${failure.message ?: "invalid response"}")
        }
    }

    /** Parses RSS/Atom and resolves relative links against the feed URL when it is supplied. */
    fun parseFeed(xmlString: String, sourceUrl: String? = null): Triple<String, String, List<SourceChapter>> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        try { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) } catch (_: Exception) { }
        try { parser.setFeature(XmlPullParser.FEATURE_VALIDATION, false) } catch (_: Exception) { }
        parser.setInput(StringReader(xmlString))

        var channelTitle = StringBuilder()
        var channelDescription = StringBuilder()
        var itemTitle = StringBuilder()
        var itemLink = StringBuilder()
        var itemDate = StringBuilder()
        var itemIdentity = StringBuilder()
        var insideItem = false
        var activeField: String? = null
        var recognizedFeed = false
        val chapters = mutableListOf<SourceChapter>()

        fun localName(): String = (parser.name ?: "").substringAfter(':').lowercase(Locale.ROOT)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = localName()
                    if (tag == "rss" || tag == "feed") recognizedFeed = true
                    if (tag == "item" || tag == "entry") {
                        insideItem = true
                        activeField = null
                        itemTitle = StringBuilder()
                        itemLink = StringBuilder()
                        itemDate = StringBuilder()
                        itemIdentity = StringBuilder()
                    } else if (insideItem) {
                        when (tag) {
                            "title" -> activeField = "item-title"
                            "link" -> {
                                val href = try { parser.getAttributeValue(null, "href") } catch (_: Exception) { null }
                                if (!href.isNullOrBlank()) itemLink.append(href)
                                else activeField = "item-link"
                            }
                            "pubdate", "updated", "published" -> activeField = "item-date"
                            "guid", "id" -> activeField = "item-id"
                        }
                    } else {
                        when (tag) {
                            "title" -> if (channelTitle.isEmpty()) activeField = "channel-title"
                            "description", "subtitle" -> if (channelDescription.isEmpty()) activeField = "channel-description"
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val text = parser.text.orEmpty()
                    when (activeField) {
                        "channel-title" -> channelTitle.append(text)
                        "channel-description" -> channelDescription.append(text)
                        "item-title" -> itemTitle.append(text)
                        "item-link" -> itemLink.append(text)
                        "item-date" -> itemDate.append(text)
                        "item-id" -> itemIdentity.append(text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = localName()
                    when (tag) {
                        "title" -> if (activeField == "item-title" || activeField == "channel-title") activeField = null
                        "description", "subtitle" -> if (activeField == "channel-description") activeField = null
                        "link" -> if (activeField == "item-link") activeField = null
                        "pubdate", "updated", "published" -> if (activeField == "item-date") activeField = null
                        "guid", "id" -> if (activeField == "item-id") activeField = null
                        "item", "entry" -> {
                            val title = cleanText(itemTitle.toString())
                            val link = resolveFeedLink(sourceUrl, cleanText(itemLink.toString()))
                            if (title.isNotBlank() && link != null) {
                                val (inferredId, number, normalizedTitle) = ChapterNormalizer.normalizeChapter(
                                    rawIdentifier = null,
                                    rawTitle = title,
                                    url = link
                                )
                                val stableId = cleanText(itemIdentity.toString()).ifBlank { inferredId }
                                chapters += SourceChapter(
                                    identifier = stableId.take(MAX_IDENTIFIER_LENGTH),
                                    number = number,
                                    title = normalizedTitle.take(MAX_TITLE_LENGTH),
                                    url = link,
                                    publishedAt = parsePubDate(cleanText(itemDate.toString()))
                                )
                            }
                            insideItem = false
                            activeField = null
                        }
                    }
                }
            }
            event = parser.next()
        }
        if (!recognizedFeed) throw IllegalArgumentException("Document root is not RSS or Atom")
        return Triple(
            cleanText(channelTitle.toString()),
            cleanText(channelDescription.toString()),
            ChapterNormalizer.sortChapters(chapters)
        )
    }

    private fun resolveFeedLink(base: String?, link: String): String? = try {
        if (link.isBlank()) return null
        val resolved = if (base.isNullOrBlank()) URI(link) else URI(base).resolve(link)
        canonicalHttpUrl(resolved.toString())
    } catch (_: Exception) {
        null
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
                val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                formatter.parse(rawDate)?.time?.let { return it }
            } catch (_: Exception) { }
        }
        return null
    }

    private fun cleanText(raw: String): String = raw
        .replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun parseHttpUri(url: String): URI? = try {
        URI(url.trim()).takeIf {
            it.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && it.userInfo == null && !it.host.isNullOrBlank()
        }
    } catch (_: Exception) {
        null
    }

    private fun <T, R> SourceResult<T>.castFailure(): SourceResult<R> = when (this) {
        is SourceResult.Success -> SourceResult.ParsingError("Unexpected response conversion")
        is SourceResult.NoNewChapters -> SourceResult.Unsupported("Chapter listing is not available")
        is SourceResult.NetworkError -> this
        is SourceResult.SourceUnavailable -> this
        is SourceResult.RateLimited -> this
        is SourceResult.ParsingError -> this
        is SourceResult.Unsupported -> this
    }

    companion object {
        private const val MAX_FEED_BYTES = 2 * 1024 * 1024
        private const val MAX_IDENTIFIER_LENGTH = 200
        private const val MAX_TITLE_LENGTH = 500
    }
}
