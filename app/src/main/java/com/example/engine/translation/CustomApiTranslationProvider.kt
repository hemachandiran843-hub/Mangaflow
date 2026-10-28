package com.example.engine.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Custom / Self-Hosted API Translation Provider (e.g. LibreTranslate, DeepL, or private proxy).
 * Protects credentials by loading them only at runtime.
 */
class CustomApiTranslationProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    var endpointUrl: String = "https://libretranslate.com/translate",
    var apiKey: String? = null,
    var isCloudPermittedByUser: Boolean = false
) : TranslationProvider {

    override val providerId: String = "custom_api"
    override val displayName: String = "Custom Translation API"
    override val isOnDevice: Boolean = false
    override val requiresApiKey: Boolean = true
    override val supportedLanguages: List<String> = listOf("en", "es", "fr", "de", "id", "pt", "ko", "ja", "zh")

    override suspend fun translate(request: TranslationRequest): TranslationResult = withContext(Dispatchers.IO) {
        if (request.isPrivate) {
            return@withContext TranslationResult.PrivateSessionRestricted
        }

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

        try {
            val jsonPayload = JSONObject().apply {
                put("q", trimmed)
                put("source", if (request.sourceLanguage == "auto") "auto" else request.sourceLanguage)
                put("target", request.targetLanguage)
                put("format", "text")
                if (!apiKey.isNullOrBlank()) {
                    put("api_key", apiKey)
                }
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val httpReqBuilder = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)

            if (!apiKey.isNullOrBlank()) {
                httpReqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            client.newCall(httpReqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext TranslationResult.Error("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body?.string()
                    ?: return@withContext TranslationResult.Error("Empty response body from custom API")

                val json = JSONObject(body)
                val translatedText = if (json.has("translatedText")) {
                    json.getString("translatedText")
                } else if (json.has("translations")) {
                    val arr = json.getJSONArray("translations")
                    if (arr.length() > 0) arr.getJSONObject(0).optString("text", trimmed) else trimmed
                } else {
                    trimmed
                }

                TranslationResult.Success(
                    TranslationResponse(
                        originalText = trimmed,
                        translatedText = translatedText,
                        sourceLanguage = request.sourceLanguage,
                        targetLanguage = request.targetLanguage,
                        providerId = providerId
                    )
                )
            }
        } catch (e: IOException) {
            TranslationResult.Error("Failed to reach custom translation API: ${e.message}", e)
        } catch (e: Exception) {
            TranslationResult.Error("Failed to parse response: ${e.message}", e)
        }
    }
}
