package com.example.engine.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Public WEBTOON listing adapter. It does not access login-only pages or bypass site protections. */
class WebtoonsSourceAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
    .build()
) : SourceAdapter {

    override val sourceId: String = "webtoons"
    override val displayName: String = "WEBTOON (Public listings)"
    override val baseUrl: String = "https://www.webtoons.com"
    override val capabilities = SourceCapabilities(
        supportsMetadataLookup = true,
        supportsChapterList = true,
        supportsUpdateDetection = true,
        rateLimitRequestsPerMinute = 15
    )

    private val episodeListItem = Regex("""<li\b([^>]*)>([\s\S]*?)</li\s*>""", RegexOption.IGNORE_CASE)
    private val attribute = Regex("""([a-zA-Z_:][a-zA-Z0-9_.:-]*)\s*=\s*(["'])(.*?)\2""", RegexOption.DOT_MATCHES_ALL)
    private val titleNoPattern = Regex("""(?:^|[?&])title_no=([0-9]+)(?:&|$)""", RegexOption.IGNORE_CASE)
    private val episodeNoPattern = Regex("""(?:^|[?&])episode_no=([0-9]+(?:\.[0-9]+)?)(?:&|$)""", RegexOption.IGNORE_CASE)
    private val episodePathPattern = Regex("""(?:^|/)episode-?([0-9]+(?:\.[0-9]+)?)(?:/|$)""", RegexOption.IGNORE_CASE)

    override fun canHandle(url: String): Boolean {
        val uri = parseHttpUri(url) ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == "webtoons.com" || host.endsWith(".webtoons.com")
    }

    override fun canonicalizeSeriesUrl(sourceUrl: String): String? {
        if (!canHandle(sourceUrl)) return null
        val uri = parseHttpUri(sourceUrl) ?: return null
        val titleNo = queryValue(uri, "title_no") ?: return canonicalHttpUrl(sourceUrl)
        val path = uri.path.orEmpty()
            .replace(Regex("(?i)/(?:viewer|episode-\\d+)(?:/.*)?$"), "")
            .trimEnd('/')
        if (path.isBlank()) return null
        val seriesPath = if (path.endsWith("/list", ignoreCase = true)) path else "$path/list"
        return "https://www.webtoons.com$seriesPath?title_no=${encode(titleNo)}"
    }

    override fun identifyChapter(pageUrl: String, pageTitle: String?): SourceChapterEvidence? {
        if (!canHandle(pageUrl)) return null
        val uri = parseHttpUri(pageUrl) ?: return null
        val titleNo = queryValue(uri, "title_no") ?: return null
        val episodeNo = queryValue(uri, "episode_no")
            ?: episodePathPattern.find(uri.path.orEmpty())?.groupValues?.getOrNull(1)
            ?: return null
        val number = episodeNo.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: return null
        val canonicalSeries = canonicalizeSeriesUrl(pageUrl) ?: return null
        val rawPageTitle = pageTitle.orEmpty().trim()
        val cleanTitle = cleanPageTitle(rawPageTitle)
        val chapterTitle = if (Regex("""(?i)(?:episode|ep\\.?)\\s*\\d+""").containsMatchIn(rawPageTitle)) {
            rawPageTitle.take(MAX_TITLE_LENGTH)
        } else {
            "Episode $episodeNo"
        }
        return SourceChapterEvidence(
            sourceId = sourceId,
            canonicalSeriesUrl = canonicalSeries,
            chapterIdentifier = "webtoons-$titleNo-$episodeNo",
            chapterNumber = number,
            chapterTitle = chapterTitle,
            chapterUrl = canonicalHttpUrl(pageUrl) ?: pageUrl,
            confidence = 0.95f,
            evidence = "WEBTOON title_no and episode_no URL identifiers",
            seriesTitle = cleanTitle.takeIf(String::isNotBlank)
        )
    }

    override suspend fun fetchSeriesMetadata(sourceUrl: String): SourceResult<SourceSeriesMetadata> = withContext(Dispatchers.IO) {
        val listUrl = toListUrl(sourceUrl)
            ?: return@withContext SourceResult.Unsupported("A WEBTOON title/list URL is required")
        val html = fetchHtml(listUrl)
        if (html !is SourceResult.Success) return@withContext html.castFailure()
        parseMetadataPage(html.data, sourceUrl)
    }

    override suspend fun fetchChapters(sourceUrl: String): SourceResult<List<SourceChapter>> = withContext(Dispatchers.IO) {
        val listUrl = toListUrl(sourceUrl)
            ?: return@withContext SourceResult.Unsupported("A WEBTOON title/list URL is required")
        val html = fetchHtml(listUrl)
        if (html !is SourceResult.Success) return@withContext html.castFailure()
        parseChapterListing(html.data, listUrl)
    }

    internal fun parseMetadataPage(html: String, sourceUrl: String): SourceResult<SourceSeriesMetadata> {
        val title = metaValue(html, "og:title")
            ?.replace(Regex("""\s*\|\s*WEBTOON.*$""", RegexOption.IGNORE_CASE), "")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return SourceResult.ParsingError("WEBTOON page has no usable title metadata")
        val cover = metaValue(html, "og:image")?.let { resolveHttpUrl(sourceUrl, it) }
        val description = metaValue(html, "og:description")?.takeIf(String::isNotBlank)
        return SourceResult.Success(
            SourceSeriesMetadata(
                title = title.take(MAX_TITLE_LENGTH),
                coverUrl = cover,
                description = description?.take(MAX_DESCRIPTION_LENGTH),
                canonicalUrl = canonicalizeSeriesUrl(sourceUrl)
            )
        )
    }

    internal fun parseChapterListing(html: String, sourceUrl: String): SourceResult<List<SourceChapter>> {
        if (looksLikeAccessChallenge(html)) {
            return SourceResult.SourceUnavailable(403, "WEBTOON requires a browser verification step")
        }
        val sourceTitleNo = parseHttpUri(sourceUrl)?.let { queryValue(it, "title_no") }
            ?: return SourceResult.Unsupported("WEBTOON title_no is missing from the source URL")
        val chapters = mutableListOf<SourceChapter>()
        for (match in episodeListItem.findAll(html)) {
            val attributes = parseAttributes(match.groupValues[1])
            val itemHtml = match.groupValues[2]
            val listId = attributes["id"].orEmpty()
            val listEpisode = listId.substringAfter("episode_", "").takeIf { it.matches(Regex("[0-9]+")) }
                ?: attributes["data-episode-no"]?.takeIf { it.matches(Regex("[0-9]+")) }
                ?: continue
            val href = findEpisodeHref(itemHtml) ?: continue
            val resolvedUrl = resolveHttpUrl(sourceUrl, href) ?: continue
            if (!canHandle(resolvedUrl)) continue
            val episodeFromUrl = parseHttpUri(resolvedUrl)?.let { queryValue(it, "episode_no") } ?: listEpisode
            val episodeId = episodeFromUrl.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: continue
            val rawTitle = findElementTextByClass(itemHtml, "subj").ifBlank { "Episode $listEpisode" }
            val title = if (rawTitle.contains("episode", ignoreCase = true)) rawTitle else "Episode $listEpisode — $rawTitle"
            chapters += SourceChapter(
                identifier = "webtoons-$sourceTitleNo-$listEpisode",
                number = episodeId,
                title = title.take(MAX_TITLE_LENGTH),
                url = resolvedUrl
            )
        }
        if (chapters.isEmpty()) {
            return SourceResult.ParsingError("No recognizable public WEBTOON episode entries were found")
        }
        return SourceResult.Success(ChapterNormalizer.sortChapters(chapters))
    }

    private suspend fun fetchHtml(url: String): SourceResult<String> = withContext(Dispatchers.IO) {
        if (!canHandle(url)) return@withContext SourceResult.Unsupported("URL is not on a WEBTOON host")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", "$baseUrl/")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> SourceResult.RateLimited(response.header("Retry-After")?.toLongOrNull(), "WEBTOON rate limit exceeded")
                    response.code == 403 -> SourceResult.SourceUnavailable(403, "WEBTOON denied the public listing request")
                    response.code == 404 -> SourceResult.SourceUnavailable(404, "WEBTOON series page was not found")
                    response.code in 500..599 -> SourceResult.SourceUnavailable(response.code, "WEBTOON server error")
                    !response.isSuccessful -> SourceResult.SourceUnavailable(response.code, "WEBTOON returned HTTP ${response.code}")
                    else -> {
                        val body = response.body ?: return@withContext SourceResult.ParsingError("Empty WEBTOON response")
                        val bytes = body.byteStream().use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_HTML_BYTES) return@withContext SourceResult.ParsingError("WEBTOON response exceeded the size limit")
                                output.write(buffer, 0, read)
                            }
                            output.toByteArray()
                        }
                        val page = String(bytes, StandardCharsets.UTF_8)
                        if (page.isBlank()) SourceResult.ParsingError("Empty WEBTOON response")
                        else SourceResult.Success(page)
                    }
                }
            }
        } catch (failure: IOException) {
            SourceResult.NetworkError("Failed to connect to WEBTOON: ${failure.message ?: "network error"}", failure)
        } catch (failure: Exception) {
            SourceResult.ParsingError("Error reading WEBTOON page: ${failure.message ?: "invalid response"}")
        }
    }

    private fun toListUrl(url: String): String? {
        if (!canHandle(url)) return null
        val canonical = canonicalizeSeriesUrl(url) ?: return null
        return canonical
    }

    private fun findEpisodeHref(html: String): String? {
        val anchors = Regex("""<a\b([^>]*)>""", RegexOption.IGNORE_CASE).findAll(html)
        val candidates = anchors.mapNotNull { parseAttributes(it.groupValues[1])["href"] }.toList()
        return candidates.firstOrNull { it.contains("episode_no=", ignoreCase = true) }
            ?: candidates.firstOrNull { it.contains("/viewer", ignoreCase = true) }
            ?: candidates.firstOrNull()
    }

    private fun findElementTextByClass(html: String, className: String): String {
        val tag = Regex(
            """<([a-z][a-z0-9:-]*)\b(?=[^>]*\bclass\s*=\s*["'][^"']*\b${Regex.escape(className)}\b[^"']*["'])[^>]*>([\s\S]*?)</\1\s*>""",
            RegexOption.IGNORE_CASE
        )
        return tag.find(html)?.groupValues?.get(2)?.let(::stripHtml)?.let(::decodeEntities).orEmpty()
    }

    private fun metaValue(html: String, target: String): String? {
        val tags = Regex("""<meta\b([^>]*)>""", RegexOption.IGNORE_CASE).findAll(html)
        for (tag in tags) {
            val attrs = parseAttributes(tag.groupValues[1])
            val key = attrs["property"] ?: attrs["name"]
            if (key.equals(target, ignoreCase = true)) return attrs["content"]?.let(::decodeEntities)
        }
        return null
    }

    private fun parseAttributes(raw: String): Map<String, String> = buildMap {
        for (match in attribute.findAll(raw)) {
            put(match.groupValues[1].lowercase(Locale.ROOT), decodeEntities(match.groupValues[3]))
        }
    }

    private fun queryValue(uri: URI, key: String): String? = uri.rawQuery
        ?.split('&')
        ?.firstNotNullOfOrNull { part ->
            val name = part.substringBefore('=').replace("+", " ")
            val value = part.substringAfter('=', "").replace("+", " ")
            if (decodePercent(name).equals(key, ignoreCase = true)) decodePercent(value).takeIf(String::isNotBlank) else null
        }

    private fun resolveHttpUrl(base: String, candidate: String): String? = try {
        val decoded = decodeEntities(candidate.trim())
        val resolved = URI(base).resolve(decoded)
        val canonical = canonicalHttpUrl(resolved.toString())
        canonical?.takeIf { canHandle(it) || isHttpUrl(it) }
    } catch (_: Exception) {
        null
    }

    private fun cleanPageTitle(value: String): String = decodeEntities(value)
        .replace(Regex("""\s*\|\s*WEBTOON.*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*[-|:]\s*(?:episode|ep\.?|chapter|ch\.?)\s*\d+.*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun stripHtml(value: String): String = value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun decodeEntities(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&#x27;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)

    private fun decodePercent(value: String): String = try {
        java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        value
    }

    private fun looksLikeAccessChallenge(html: String): Boolean {
        val lower = html.lowercase(Locale.ROOT)
        return "checking your browser" in lower || "just a moment..." in lower ||
            "cf-browser-verification" in lower || "captcha" in lower
    }

    private fun parseHttpUri(url: String): URI? = try {
        URI(url.trim()).takeIf { it.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && it.userInfo == null && !it.host.isNullOrBlank() }
    } catch (_: Exception) {
        null
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

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
        private const val MAX_HTML_BYTES = 2 * 1024 * 1024
        private const val MAX_TITLE_LENGTH = 300
        private const val MAX_DESCRIPTION_LENGTH = 2_000
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
