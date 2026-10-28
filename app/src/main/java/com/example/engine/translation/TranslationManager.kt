package com.example.engine.translation

import com.example.data.local.dao.AppSettingDao
import com.example.data.local.entity.AppSettingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates translation providers, target language preferences,
 * caching policies, and private browsing safeguards.
 */
class TranslationManager(
    private val appSettingDao: AppSettingDao? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val onDeviceProvider = OnDeviceGlossaryTranslationProvider()
    private val freeWebProvider = FreeWebTranslationProvider()
    private val customApiProvider = CustomApiTranslationProvider()

    private val providers = mapOf<String, TranslationProvider>(
        onDeviceProvider.providerId to onDeviceProvider,
        freeWebProvider.providerId to freeWebProvider,
        customApiProvider.providerId to customApiProvider
    )

    private val _activeProviderId = MutableStateFlow("on_device")
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val _targetLanguage = MutableStateFlow("en")
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    private val _sourceLanguage = MutableStateFlow("auto")
    val sourceLanguage: StateFlow<String> = _sourceLanguage.asStateFlow()

    private val _allowCloudTranslation = MutableStateFlow(false)
    val allowCloudTranslation: StateFlow<Boolean> = _allowCloudTranslation.asStateFlow()

    private val _cacheTranslations = MutableStateFlow(true)
    val cacheTranslations: StateFlow<Boolean> = _cacheTranslations.asStateFlow()

    private val _cachedItemsCount = MutableStateFlow(0)
    val cachedItemsCount: StateFlow<Int> = _cachedItemsCount.asStateFlow()

    // In-memory translation cache (cleared when requested; strictly excluded for private tabs)
    private val translationCache = ConcurrentHashMap<String, String>()

    init {
        // Load persisted preferences if database is available
        if (appSettingDao != null) {
            scope.launch {
                try {
                    appSettingDao.getSetting("TRANSLATION_PROVIDER")?.let { _activeProviderId.value = it }
                    appSettingDao.getSetting("TRANSLATION_TARGET_LANG")?.let { _targetLanguage.value = it }
                    appSettingDao.getSetting("TRANSLATION_SOURCE_LANG")?.let { _sourceLanguage.value = it }
                    appSettingDao.getSetting("TRANSLATION_ALLOW_CLOUD")?.let {
                        val allowed = it.toBoolean()
                        _allowCloudTranslation.value = allowed
                        freeWebProvider.isCloudPermittedByUser = allowed
                        customApiProvider.isCloudPermittedByUser = allowed
                    }
                    appSettingDao.getSetting("TRANSLATION_CACHE_ENABLED")?.let {
                        _cacheTranslations.value = it.toBoolean()
                    }
                    val customUrl = appSettingDao.getSetting("TRANSLATION_CUSTOM_URL")
                    val customKey = appSettingDao.getSetting("TRANSLATION_CUSTOM_KEY")
                    if (!customUrl.isNullOrBlank()) customApiProvider.endpointUrl = customUrl
                    if (!customKey.isNullOrBlank()) customApiProvider.apiKey = customKey
                } catch (_: Exception) {}
            }
        }
    }

    fun getAllProviders(): List<TranslationProvider> = providers.values.toList()

    fun getActiveProvider(): TranslationProvider {
        return providers[_activeProviderId.value] ?: onDeviceProvider
    }

    fun setActiveProvider(providerId: String) {
        if (providers.containsKey(providerId)) {
            _activeProviderId.value = providerId
            persistSetting("TRANSLATION_PROVIDER", providerId)
        }
    }

    fun setTargetLanguage(lang: String) {
        _targetLanguage.value = lang
        persistSetting("TRANSLATION_TARGET_LANG", lang)
    }

    fun setSourceLanguage(lang: String) {
        _sourceLanguage.value = lang
        persistSetting("TRANSLATION_SOURCE_LANG", lang)
    }

    fun setAllowCloudTranslation(allowed: Boolean) {
        _allowCloudTranslation.value = allowed
        freeWebProvider.isCloudPermittedByUser = allowed
        customApiProvider.isCloudPermittedByUser = allowed
        persistSetting("TRANSLATION_ALLOW_CLOUD", allowed.toString())
    }

    fun setCacheTranslations(enabled: Boolean) {
        _cacheTranslations.value = enabled
        if (!enabled) clearCache()
        persistSetting("TRANSLATION_CACHE_ENABLED", enabled.toString())
    }

    fun setCustomApiCredentials(endpoint: String, key: String?) {
        customApiProvider.endpointUrl = endpoint.trim()
        customApiProvider.apiKey = key?.trim()?.ifBlank { null }
        persistSetting("TRANSLATION_CUSTOM_URL", customApiProvider.endpointUrl)
        persistSetting("TRANSLATION_CUSTOM_KEY", customApiProvider.apiKey ?: "")
    }

    fun getCustomEndpoint(): String = customApiProvider.endpointUrl

    fun getCustomApiKey(): String = customApiProvider.apiKey ?: ""

    fun clearCache() {
        translationCache.clear()
        _cachedItemsCount.value = 0
    }

    /**
     * Translates a single text string respecting caches and privacy restrictions.
     */
    suspend fun translateText(
        text: String,
        sourceLang: String = _sourceLanguage.value,
        targetLang: String = _targetLanguage.value,
        isPrivate: Boolean = false
    ): TranslationResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return TranslationResult.Success(
                TranslationResponse(
                    originalText = "",
                    translatedText = "",
                    sourceLanguage = sourceLang,
                    targetLanguage = targetLang,
                    providerId = _activeProviderId.value
                )
            )
        }

        val cacheKey = "$sourceLang->$targetLang:$trimmed"

        // Check cache if permitted and NOT in a private session
        if (!isPrivate && _cacheTranslations.value) {
            val cached = translationCache[cacheKey]
            if (cached != null) {
                return TranslationResult.Success(
                    TranslationResponse(
                        originalText = trimmed,
                        translatedText = cached,
                        sourceLanguage = sourceLang,
                        targetLanguage = targetLang,
                        providerId = _activeProviderId.value,
                        isCached = true
                    )
                )
            }
        }

        val provider = getActiveProvider()
        val request = TranslationRequest(
            text = trimmed,
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
            isPrivate = isPrivate
        )

        val result = provider.translate(request)

        // Cache successful response if caching permitted and NOT in private session
        if (result is TranslationResult.Success && !isPrivate && _cacheTranslations.value) {
            translationCache[cacheKey] = result.response.translatedText
            _cachedItemsCount.value = translationCache.size
        }

        return result
    }

    suspend fun translateBatch(
        texts: List<String>,
        sourceLang: String = _sourceLanguage.value,
        targetLang: String = _targetLanguage.value,
        isPrivate: Boolean = false
    ): BatchTranslationResult {
        if (texts.isEmpty()) return BatchTranslationResult.Success(emptyMap())

        val provider = getActiveProvider()
        if (isPrivate && !provider.isOnDevice) {
            return BatchTranslationResult.PrivateSessionRestricted
        }
        if (!provider.isOnDevice && !_allowCloudTranslation.value) {
            return BatchTranslationResult.CloudTranslationNotPermitted
        }

        val resultMap = mutableMapOf<String, String>()
        for (text in texts) {
            when (val res = translateText(text, sourceLang, targetLang, isPrivate)) {
                is TranslationResult.Success -> {
                    resultMap[text] = res.response.translatedText
                }
                is TranslationResult.PrivateSessionRestricted -> {
                    return BatchTranslationResult.PrivateSessionRestricted
                }
                is TranslationResult.CloudTranslationNotPermitted -> {
                    return BatchTranslationResult.CloudTranslationNotPermitted
                }
                is TranslationResult.Error -> {
                    resultMap[text] = text
                }
            }
        }
        return BatchTranslationResult.Success(resultMap)
    }

    private fun persistSetting(key: String, value: String) {
        if (appSettingDao != null) {
            scope.launch {
                try {
                    appSettingDao.setSetting(AppSettingEntity(key = key, value = value))
                } catch (_: Exception) {}
            }
        }
    }
}
