package com.example.engine.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Public Web Translation Provider (MyMemory open endpoint).
 * Transmits text to an external service only with explicit user permission.
 * Strictly blocked during private browsing sessions.
 */
class FreeWebTranslationProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    var isCloudPermittedByUser: Boolean = false
) : TranslationProvider {

    override val providerId: String = "public_web"
    override val displayName: String = "Public Web Translation (External)"
    override val isOnDevice: Boolean = false
    override val requiresApiKey: Boolean = false
    override val supportedLanguages: List<String> = listOf("en", "es", "fr", "de", "id", "pt", "ko", "ja", "zh")

    override suspend fun translate(request: TranslationRequest): TranslationResult = withContext(Dispatchers.IO) {
        // Strict Privacy Rule 1: Never transmit private browsing content externally
        if (request.isPrivate) {
            return@withContext TranslationResult.PrivateSessionRestricted
        }

        // Strict Privacy Rule 2: Require explicit user consent to transmit text to third-party servers
        if (!isCloudPermittedByUser) {
            return@withContext TranslationResult.CloudTranslationNotPermitted
        }

        val trimmed = request.text.trim()
        if (trimmed.isEmpty()) {
            return@withContext TranslationResult.Success(
                TranslationResponse(
                    originalText = "",
                    translatedText = "",
                    sourceLanguage = request.sourceLanguage,
                    targetLanguage = request.targetLanguage,
                    providerId = providerId
                )
            )
        }

        val srcLang = if (request.sourceLanguage == "auto") "autodetect" else request.sourceLanguage
        val tgtLang = request.targetLanguage

        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encodedQuery&langpair=$srcLang|$tgtLang"

            val httpRequest = Request.Builder()
                .url(url)
                .header("User-Agent", "MangaFlow-Browser/1.0")
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext TranslationResult.Error("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body?.string()
                    ?: return@withContext TranslationResult.Error("Empty response from translation provider")

                val json = JSONObject(body)
                val responseData = json.getJSONObject("responseData")
                val translatedText = responseData.optString("translatedText", trimmed)

                TranslationResult.Success(
                    TranslationResponse(
                        originalText = trimmed,
                        translatedText = translatedText,
                        sourceLanguage = request.sourceLanguage,
                        targetLanguage = tgtLang,
                        providerId = providerId
                    )
                )
            }
        } catch (_: IOException) {
            // Do not surface the failed request URL; it contains the text being translated.
            TranslationResult.Error("Network failure during web translation")
        } catch (_: Exception) {
            TranslationResult.Error("The translation provider returned an unreadable response")
        }
    }
}
