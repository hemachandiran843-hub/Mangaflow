package com.example.engine.translation

/**
 * Standard interface for translation providers.
 * Decouples translation consumption from concrete APIs.
 */
interface TranslationProvider {
    val providerId: String
    val displayName: String
    val isOnDevice: Boolean
    val requiresApiKey: Boolean
    val supportedLanguages: List<String>

    /**
     * Translates a single text request.
     */
    suspend fun translate(request: TranslationRequest): TranslationResult

    /**
     * Translates multiple text requests in batch.
     */
    suspend fun translateBatch(requests: List<TranslationRequest>): List<TranslationResult> {
        return requests.map { translate(it) }
    }
}
