package com.example.engine.translation

/**
 * Data structures and result types for MangaFlow configurable translation engine.
 */

data class TranslationRequest(
    val text: String,
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "en",
    val isPrivate: Boolean = false
)

data class TranslationResponse(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val providerId: String,
    val isCached: Boolean = false
)

sealed class TranslationResult {
    data class Success(val response: TranslationResponse) : TranslationResult()
    data class Error(val message: String, val cause: Throwable? = null) : TranslationResult()
    object PrivateSessionRestricted : TranslationResult()
    object CloudTranslationNotPermitted : TranslationResult()
}

sealed class BatchTranslationResult {
    data class Success(val translations: Map<String, String>) : BatchTranslationResult()
    data class Error(val message: String, val cause: Throwable? = null) : BatchTranslationResult()
    object PrivateSessionRestricted : BatchTranslationResult()
    object CloudTranslationNotPermitted : BatchTranslationResult()
}

data class TranslationConfig(
    val providerId: String = "on_device",
    val targetLanguage: String = "en",
    val sourceLanguage: String = "auto",
    val allowCloudTranslation: Boolean = false,
    val cacheTranslations: Boolean = true,
    val customApiKey: String? = null,
    val customEndpointUrl: String? = null
)
