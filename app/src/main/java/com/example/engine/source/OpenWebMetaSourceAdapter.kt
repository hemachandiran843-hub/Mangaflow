package com.example.engine.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Metadata-only fallback for ordinary public HTTP(S) pages. It never enumerates releases and never
 * infers a chapter from Open Graph tags. Generic browsing remains owned by the WebView.
 */
class OpenWebMetaSourceAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
) : SourceAdapter {

    override val sourceId: String = "open_web"
    override val displayName: String = "Open Web (Metadata only)"
    override val baseUrl: String = ""
    override val capabilities = SourceCapabilities(
        supportsMetadataLookup = true,
        supportsChapterList = false,
        supportsUpdateDetection = false,
        rateLimitRequestsPerMinute = 15
    )

    private val metaTag = Regex("""<meta\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val titleTag = Regex("""<title\b[^>]*>([\s\S]*?)</title\s*>""", RegexOption.IGNORE_CASE)
    private val linkTag = Regex("""<link\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val chapterTitleEvidence = Regex("""(?i)(?:chapter|ch\.?|episode|ep\.?)\s*\d+(?:\.\d+)?""")
    private val attribute = Regex("""([a-zA-Z_:][a-zA-Z0-9_.:-]*)\s*=\s*(["'])(.*?)\2""", RegexOption.DOT_MATCHES_ALL)
    private val numericChapterPatterns = listOf(
        Regex("""/(?:series|manga|comic|read|manhwa)/([^/]+)/chapter-?(\d+(?:\.\d+)?)(?:-[^/]+)?/?$""", RegexOption.IGNORE_CASE),
        Regex("""/(?:series|manga|comic|read|manhwa)/([^/]+)/ch-?(\d+(?:\.\d+)?)/?$""", RegexOption.IGNORE_CASE),
        Regex("""/(?:series|manga|comic|read|manhwa)/([^/]+)/episode-?(\d+(?:\.\d+)?)/?$""", RegexOption.IGNORE_CASE),
        Regex("""/(?:series|manga|comic|read|manhwa)/([^/]+)/chapter/(\d+(?:\.\d+)?)/?$""", RegexOption.IGNORE_CASE)
    )

    override fun canHandle(url: String): Boolean {
        val uri = parseHttpUri(url) ?: return false
        return !isObviouslyPrivateHost(uri.host.orEmpty())
    }

    override fun canonicalizeSeriesUrl(sourceUrl: String): String? {
        if (!canHandle(sourceUrl)) return null
        val uri = parseHttpUri(sourceUrl) ?: return null
        val path = seriesPathFor(uri) ?: uri.path.orEmpty()
        val pathUrl = URI(uri.scheme, null, uri.host, uri.port, path.ifBlank { "/" }, uri.query, null).toString()
        return canonicalHttpUrl(pathUrl)
    }

    override fun identifyChapter(pageUrl: String, pageTitle: String?): SourceChapterEvidence? {
        if (!canHandle(pageUrl)) return null
        val uri = parseHttpUri(pageUrl) ?: return null
        val path = uri.path.orEmpty()
        val match = numericChapterPatterns.firstNotNullOfOrNull { it.find(path) } ?: return null
        val slug = match.groupValues[1].trim('/').takeIf(String::isNotBlank) ?: return null
        val rawNumber = match.groupValues[2]
        val chapterNumber = rawNumber.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: return null
        val seriesPath = seriesPathFor(uri) ?: return null
        val canonicalSeriesUrl = canonicalHttpUrl(
            URI(uri.scheme, null, uri.host, uri.port, seriesPath, null, null).toString()
        ) ?: return null
        val rawPageTitle = pageTitle.orEmpty().trim()
        val titleFromPage = if (chapterTitleEvidence.containsMatchIn(rawPageTitle)) rawPageTitle else ""
        val seriesTitle = slug.replace('-', ' ').replace('_', ' ').split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        val chapterTitle = if (titleFromPage.isNotBlank() && titleFromPage.length <= MAX_TITLE_LENGTH) {
            titleFromPage
        } else {
            "Chapter $rawNumber"
        }
        return SourceChapterEvidence(
            sourceId = sourceId,
            canonicalSeriesUrl = canonicalSeriesUrl,
            chapterIdentifier = rawNumber,
            chapterNumber = chapterNumber,
            chapterTitle = chapterTitle,
            chapterUrl = canonicalHttpUrl(pageUrl) ?: return null,
            confidence = 0.76f,
            evidence = "Numeric chapter identifier in a recognized URL path",
            seriesTitle = seriesTitle.takeIf(String::isNotBlank)
        )
    }

    override suspend fun fetchSeriesMetadata(sourceUrl: String): SourceResult<SourceSeriesMetadata> = withContext(Dispatchers.IO) {
        if (!canHandle(sourceUrl)) return@withContext SourceResult.Unsupported("Only public HTTP(S) pages can be inspected")
        val htmlResult = fetchHtml(sourceUrl)
        if (htmlResult !is SourceResult.Success) return@withContext htmlResult.castFailure()
        val html = htmlResult.data
        if (looksLikeAccessChallenge(html)) {
            return@withContext SourceResult.SourceUnavailable(403, "The website requires browser verification")
        }
        val title = metadataValue(html, "og:title", "name")
            ?: titleTag.find(html)?.groupValues?.get(1)?.let(::stripHtml)?.let(::decodeEntities)
            ?: return@withContext SourceResult.ParsingError("No usable title metadata was found")
        val description = metadataValue(html, "og:description", "description")
        val image = metadataValue(html, "og:image")?.let { resolveUrl(sourceUrl, it) }
        val canonical = canonicalLink(html, sourceUrl) ?: canonicalizeSeriesUrl(sourceUrl)
        SourceResult.Success(
            SourceSeriesMetadata(
                title = cleanTitle(title).take(MAX_TITLE_LENGTH),
                coverUrl = image,
                description = description?.take(MAX_DESCRIPTION_LENGTH),
                canonicalUrl = canonical
            )
        )
    }

    override suspend fun fetchChapters(sourceUrl: String): SourceResult<List<SourceChapter>> =
        SourceResult.Unsupported("Generic web pages do not have a verified chapter-list adapter; browse normally or track progress manually")

    private suspend fun fetchHtml(url: String): SourceResult<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> SourceResult.RateLimited(response.header("Retry-After")?.toLongOrNull(), "Website rate limited the metadata request")
                    response.code == 403 -> SourceResult.SourceUnavailable(403, "Website denied the metadata request")
                    response.code == 404 -> SourceResult.SourceUnavailable(404, "Page was not found")
                    response.code in 500..599 -> SourceResult.SourceUnavailable(response.code, "Website server error")
                    !response.isSuccessful -> SourceResult.SourceUnavailable(response.code, "Website returned HTTP ${response.code}")
                    else -> {
                        val body = response.body ?: return@withContext SourceResult.ParsingError("Empty HTML response")
                        val bytes = body.byteStream().use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var total = 0
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_HTML_BYTES) return@withContext SourceResult.ParsingError("HTML response exceeded the size limit")
                                output.write(buffer, 0, read)
                            }
                            output.toByteArray()
                        }
                        val html = String(bytes, StandardCharsets.UTF_8)
                        if (html.isBlank()) SourceResult.ParsingError("Empty HTML response") else SourceResult.Success(html)
                    }
                }
            }
        } catch (failure: IOException) {
            SourceResult.NetworkError("Failed to connect to website: ${failure.message ?: "network error"}", failure)
        } catch (failure: Exception) {
            SourceResult.ParsingError("Could not read page metadata: ${failure.message ?: "invalid response"}")
        }
    }

    private fun metadataValue(html: String, vararg keys: String): String? {
        for (tag in metaTag.findAll(html)) {
            val attrs = parseAttributes(tag.groupValues[1])
            val key = attrs["property"] ?: attrs["name"]
            if (keys.any { key.equals(it, ignoreCase = true) }) {
                attrs["content"]?.let(::decodeEntities)?.takeIf(String::isNotBlank)?.let { return it.trim() }
            }
        }
        return null
    }

    private fun canonicalLink(html: String, base: String): String? {
        for (tag in linkTag.findAll(html)) {
            val attrs = parseAttributes(tag.groupValues[1])
            if (attrs["rel"].orEmpty().split(Regex("\\s+")).any { it.equals("canonical", ignoreCase = true) }) {
                val href = attrs["href"] ?: continue
                return resolveUrl(base, href)
            }
        }
        return null
    }

    private fun parseAttributes(raw: String): Map<String, String> = buildMap {
        for (match in attribute.findAll(raw)) put(match.groupValues[1].lowercase(Locale.ROOT), match.groupValues[3])
    }

    private fun seriesPathFor(uri: URI): String? {
        val path = uri.path.orEmpty()
        val match = numericChapterPatterns.firstNotNullOfOrNull { it.find(path) } ?: return null
        // The regex capture identifies the series slug; take the path up to its segment.
        val slug = match.groupValues[1]
        val slugMarker = "/$slug/"
        val slugStart = path.indexOf(slugMarker, ignoreCase = true)
        if (slugStart < 0) return null
        val pathEnd = slugStart + slugMarker.length - 1
        return path.substring(0, pathEnd).trimEnd('/').ifBlank { null }
    }

    private fun resolveUrl(base: String, candidate: String): String? = try {
        val resolved = URI(base).resolve(decodeEntities(candidate.trim()))
        canonicalHttpUrl(resolved.toString())
    } catch (_: Exception) {
        null
    }

    private fun cleanTitle(raw: String): String = decodeEntities(stripHtml(raw))
        .replace(Regex("""\s*[-|:]\s*(?:chapter|ch\.?|episode|ep\.?)\s*[\d.]+.*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*[-|]\s*(?:read|manga|webtoon).*${'$'}""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun stripHtml(value: String): String = value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun decodeEntities(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&#x27;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)

    private fun looksLikeAccessChallenge(html: String): Boolean {
        val lower = html.lowercase(Locale.ROOT)
        return "checking your browser" in lower || "just a moment..." in lower || "cf-browser-verification" in lower || "captcha" in lower
    }

    private fun isObviouslyPrivateHost(rawHost: String): Boolean {
        val host = rawHost.lowercase(Locale.ROOT).removePrefix("[").removeSuffix("]").trimEnd('.')
        if (host.contains(':')) return true // Do not fetch directly addressed IPv6 hosts from the metadata fallback.
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) return true
        val octets = host.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size == 4 && octets.all { it in 0..255 }) {
            val (a, b) = octets
            return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) ||
                (a == 172 && b in 16..31) || (a == 192 && b == 168) || a >= 224
        }
        return host == "::1" || host.startsWith("fc", true) || host.startsWith("fd", true) || host.startsWith("fe80:", true)
    }

    private fun parseHttpUri(value: String): URI? = try {
        URI(value.trim()).takeIf {
            it.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null
        }
    } catch (_: Exception) {
        null
    }

    private fun <T, R> SourceResult<T>.castFailure(): SourceResult<R> = when (this) {
        is SourceResult.Success -> SourceResult.ParsingError("Unexpected response conversion")
        is SourceResult.NoNewChapters -> SourceResult.Unsupported("Chapter listing is not supported")
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
        private const val USER_AGENT = "MangaFlow/1.0 (public metadata lookup)"
    }
}
