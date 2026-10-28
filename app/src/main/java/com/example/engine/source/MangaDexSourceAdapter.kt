package com.example.engine.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Official MangaDex API integration. It does not scrape reader pages or bypass access controls. */
class MangaDexSourceAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
) : SourceAdapter {

    override val sourceId: String = "mangadex"
    override val displayName: String = "MangaDex (Official API)"
    override val baseUrl: String = "https://mangadex.org"
    override val capabilities = SourceCapabilities(
        supportsMetadataLookup = true,
        supportsChapterList = true,
        supportsUpdateDetection = true,
        isOfficialApi = true,
        rateLimitRequestsPerMinute = 30
    )

    private val validHosts = setOf("mangadex.org", "www.mangadex.org", "api.mangadex.org")
    private val uuidRegex = Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""")

    override fun canHandle(url: String): Boolean {
        val uri = parseHttpUri(url) ?: return false
        return uri.host?.lowercase(Locale.ROOT) in validHosts
    }

    override fun canonicalizeSeriesUrl(sourceUrl: String): String? {
        if (!canHandle(sourceUrl)) return null
        val id = extractMangaId(sourceUrl) ?: return canonicalHttpUrl(sourceUrl)
        return "$baseUrl/title/$id"
    }

    override fun identifyChapter(pageUrl: String, pageTitle: String?): SourceChapterEvidence? {
        if (!canHandle(pageUrl)) return null
        val uri = parseHttpUri(pageUrl) ?: return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val chapterIndex = segments.indexOfFirst { it.equals("chapter", ignoreCase = true) }
        val chapterUuid = segments.getOrNull(chapterIndex + 1)?.takeIf(uuidRegex::matches)
            ?.lowercase(Locale.ROOT) ?: return null
        // MangaDex chapter URLs normally omit the parent title. Only identify pages where the
        // series UUID is also present in a recognized title route; never guess the parent series.
        val mangaId = extractMangaId(pageUrl) ?: return null
        val rawTitle = pageTitle.orEmpty().trim().take(300)
        val number = Regex("""(?i)(?:chapter|ch\\.?|episode|ep\\.?)\\s*([0-9]+(?:\\.[0-9]+)?)""")
            .find(rawTitle)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it >= 0f }
        val chapterTitle = rawTitle.ifBlank { "MangaDex chapter $chapterUuid" }
        return SourceChapterEvidence(
            sourceId = sourceId,
            canonicalSeriesUrl = "$baseUrl/title/$mangaId",
            chapterIdentifier = "mangadex-$chapterUuid",
            chapterNumber = number,
            chapterTitle = chapterTitle,
            chapterUrl = canonicalHttpUrl(pageUrl) ?: return null,
            confidence = if (number != null) 0.98f else 0.90f,
            evidence = "MangaDex chapter UUID within a recognized title route",
            seriesTitle = rawTitle.takeIf(String::isNotBlank)
                ?.replace(Regex("(?i)\\s*[-|:]\\s*MangaDex.*$"), "")
                ?.takeIf(String::isNotBlank)
        )
    }

    /** Extracts an ID only from a MangaDex domain and a recognized title/API path. */
    fun extractMangaId(url: String): String? {
        val uri = parseHttpUri(url) ?: return null
        if (uri.host?.lowercase(Locale.ROOT) !in validHosts) return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val candidate = when {
            segments.size >= 2 && segments[0].equals("title", ignoreCase = true) -> segments[1]
            segments.size >= 2 && segments[0].equals("manga", ignoreCase = true) -> segments[1]
            else -> null
        }
        return candidate?.takeIf(uuidRegex::matches)?.lowercase(Locale.ROOT)
    }

    override suspend fun fetchSeriesMetadata(sourceUrl: String): SourceResult<SourceSeriesMetadata> = withContext(Dispatchers.IO) {
        val mangaId = extractMangaId(sourceUrl)
            ?: return@withContext SourceResult.Unsupported("A MangaDex title URL with a valid title UUID is required")
        val request = apiRequest("manga/$mangaId")

        try {
            client.newCall(request).execute().use { response ->
                responseError(response.code)?.let { return@withContext it }
                val body = response.body?.string()
                    ?: return@withContext SourceResult.ParsingError("Empty response body from MangaDex")
                val root = JSONObject(body)
                val data = root.optJSONObject("data")
                    ?: return@withContext SourceResult.ParsingError("MangaDex response is missing manga data")
                val attributes = data.optJSONObject("attributes")
                    ?: return@withContext SourceResult.ParsingError("MangaDex response is missing manga attributes")
                val title = chooseLocalizedText(attributes.optJSONObject("title"))
                    ?: return@withContext SourceResult.ParsingError("MangaDex response has no usable title")
                val description = chooseLocalizedText(attributes.optJSONObject("description"))
                val canonical = canonicalizeSeriesUrl(sourceUrl)

                SourceResult.Success(
                    SourceSeriesMetadata(
                        title = title,
                        description = description,
                        status = attributes.optString("status").takeIf(String::isNotBlank),
                        latestChapterIdentifier = attributes.optString("lastChapter").takeIf(String::isNotBlank),
                        canonicalUrl = canonical
                    )
                )
            }
        } catch (failure: IOException) {
            SourceResult.NetworkError("Failed to connect to MangaDex API: ${failure.message ?: "network error"}", failure)
        } catch (failure: Exception) {
            SourceResult.ParsingError("Failed to parse MangaDex metadata: ${failure.message ?: "invalid response"}")
        }
    }

    override suspend fun fetchChapters(sourceUrl: String): SourceResult<List<SourceChapter>> = withContext(Dispatchers.IO) {
        val mangaId = extractMangaId(sourceUrl)
            ?: return@withContext SourceResult.Unsupported("A MangaDex title URL with a valid title UUID is required")
        val request = apiRequest("manga/$mangaId/feed") {
            addQueryParameter("translatedLanguage[]", "en")
            addQueryParameter("order[chapter]", "desc")
            addQueryParameter("limit", "100")
        }

        try {
            client.newCall(request).execute().use { response ->
                responseError(response.code)?.let { return@withContext it }
                val body = response.body?.string()
                    ?: return@withContext SourceResult.ParsingError("Empty MangaDex chapter response")
                val data = JSONObject(body).optJSONArray("data")
                    ?: return@withContext SourceResult.ParsingError("MangaDex chapter response is missing its data array")
                val chapters = buildList {
                    for (index in 0 until data.length()) {
                        val chapterObject = data.optJSONObject(index) ?: continue
                        val chapterUuid = chapterObject.optString("id").takeIf(String::isNotBlank) ?: continue
                        val attributes = chapterObject.optJSONObject("attributes") ?: continue
                        val rawNumber = attributes.optString("chapter").trim()
                        val rawTitle = attributes.optString("title").trim()
                        val chapterUrl = "$baseUrl/chapter/$chapterUuid"
                        val displayTitle = when {
                            rawNumber.isNotBlank() && rawTitle.isNotBlank() -> "Chapter $rawNumber — $rawTitle"
                            rawNumber.isNotBlank() -> "Chapter $rawNumber"
                            rawTitle.isNotBlank() -> rawTitle
                            else -> "Chapter"
                        }
                        val (_, number, normalizedTitle) = ChapterNormalizer.normalizeChapter(
                            rawIdentifier = rawNumber.ifBlank { null },
                            rawTitle = displayTitle,
                            url = chapterUrl
                        )
                        add(
                            SourceChapter(
                                identifier = "mangadex-$chapterUuid",
                                number = number,
                                title = normalizedTitle.ifBlank { displayTitle },
                                url = chapterUrl,
                                publishedAt = parseTimestamp(attributes.optString("publishAt"))
                            )
                        )
                    }
                }
                SourceResult.Success(ChapterNormalizer.sortChapters(chapters))
            }
        } catch (failure: IOException) {
            SourceResult.NetworkError("Failed to fetch MangaDex chapters: ${failure.message ?: "network error"}", failure)
        } catch (failure: Exception) {
            SourceResult.ParsingError("Failed to parse MangaDex chapter feed: ${failure.message ?: "invalid response"}")
        }
    }

    private fun apiRequest(path: String, configure: HttpUrl.Builder.() -> Unit = {}): Request {
        val url = "https://api.mangadex.org".toHttpUrl().newBuilder()
            .addPathSegments(path)
            .apply(configure)
            .build()
        return Request.Builder()
            .url(url)
            .header("User-Agent", "MangaFlow-Android/1.0 (https://github.com/hemachandiran843-hub/Mangaflow)")
            .header("Accept", "application/json")
            .build()
    }

    private fun responseError(code: Int): SourceResult<Nothing>? = when {
        code == 429 -> SourceResult.RateLimited(10L, "MangaDex rate limit reached")
        code == 404 -> SourceResult.SourceUnavailable(404, "MangaDex item was not found")
        code in 500..599 -> SourceResult.SourceUnavailable(code, "MangaDex server error")
        code !in 200..299 -> SourceResult.SourceUnavailable(code, "MangaDex returned HTTP $code")
        else -> null
    }

    private fun chooseLocalizedText(obj: JSONObject?): String? {
        if (obj == null) return null
        for (language in listOf("en", "ko", "ja")) {
            obj.optString(language).takeIf(String::isNotBlank)?.let { return it.trim() }
        }
        val keys = obj.keys()
        while (keys.hasNext()) {
            obj.optString(keys.next()).takeIf(String::isNotBlank)?.let { return it.trim() }
        }
        return null
    }

    private fun parseTimestamp(raw: String): Long? = try {
        raw.takeIf(String::isNotBlank)?.let { Instant.parse(it).toEpochMilli() }
    } catch (_: Exception) {
        null
    }

    private fun parseHttpUri(url: String): URI? = try {
        URI(url.trim()).takeIf {
            it.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && it.userInfo == null
        }
    } catch (_: Exception) {
        null
    }
}
