package com.example.engine.translation

import com.example.data.local.dao.AppSettingDao
import com.example.data.local.entity.AppSettingEntity
import com.example.engine.security.SecureCredentialVault
import java.net.URI
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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val credentialVault: SecureCredentialVault? = null
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

    private val _customApiKey = MutableStateFlow("")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _customEndpoint = MutableStateFlow(customApiProvider.endpointUrl)
    val customEndpoint: StateFlow<String> = _customEndpoint.asStateFlow()

    private val _credentialWarning = MutableStateFlow<String?>(null)
    val credentialWarning: StateFlow<String?> = _credentialWarning.asStateFlow()

    // In-memory translation cache (cleared when requested; strictly excluded for private tabs)
    private val translationCache = ConcurrentHashMap<String, String>()

    init {
        credentialVault?.read(SECURE_CUSTOM_KEY)?.let { key ->
            customApiProvider.apiKey = key
            _customApiKey.value = key
        }
        // Load persisted preferences and migrate any legacy plaintext credential out of Room.
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
                    appSettingDao.getSetting(KEY_CUSTOM_URL)?.takeIf(::isAllowedCustomEndpoint)?.let {
                        customApiProvider.endpointUrl = it
                        _customEndpoint.value = it
                    }
                    val legacyKey = appSettingDao.getSetting(LEGACY_CUSTOM_KEY)
                    if (!legacyKey.isNullOrBlank()) {
                        if (credentialVault?.write(SECURE_CUSTOM_KEY, legacyKey) == true) {
                            customApiProvider.apiKey = legacyKey
                            _customApiKey.value = legacyKey
                        } else {
                            _credentialWarning.value = "The saved API key could not be secured; enter it again in Settings."
                            customApiProvider.apiKey = null
                            _customApiKey.value = ""
                        }
                        appSettingDao.deleteSetting(LEGACY_CUSTOM_KEY)
                    } else if (legacyKey != null) {
                        appSettingDao.deleteSetting(LEGACY_CUSTOM_KEY)
                    }
                    credentialVault?.read(SECURE_CUSTOM_KEY)?.let { key ->
                        customApiProvider.apiKey = key
                        _customApiKey.value = key
                    }
                } catch (_: Exception) {
                    _credentialWarning.value = "Translation credentials could not be loaded securely."
                }
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

    fun setCustomApiCredentials(endpoint: String, key: String?): Boolean {
        val safeEndpoint = endpoint.trim()
        if (!isAllowedCustomEndpoint(safeEndpoint)) return false
        val safeKey = key?.trim()?.ifBlank { null }
        if (safeKey != null) {
            val vault = credentialVault ?: return false
            if (!vault.write(SECURE_CUSTOM_KEY, safeKey)) return false
        } else if (credentialVault != null && !credentialVault.write(SECURE_CUSTOM_KEY, null)) {
            return false
        }

        customApiProvider.endpointUrl = safeEndpoint
        customApiProvider.apiKey = safeKey
        _customEndpoint.value = safeEndpoint
        _customApiKey.value = safeKey.orEmpty()
        _credentialWarning.value = null
        persistSetting(KEY_CUSTOM_URL, safeEndpoint)
        scope.launch {
            try { appSettingDao?.deleteSetting(LEGACY_CUSTOM_KEY) } catch (_: Exception) { }
        }
        return true
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

    private fun isAllowedCustomEndpoint(value: String): Boolean {
        if (value.length !in 10..2048) return false
        return try {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase() ?: return false
            val loopback = host in setOf("localhost", "127.0.0.1", "::1", "[::1]")
            uri.userInfo == null && (scheme == "https" || (scheme == "http" && loopback))
        } catch (_: Exception) {
            false
        }
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

    companion object {
        private const val KEY_CUSTOM_URL = "TRANSLATION_CUSTOM_URL"
        private const val LEGACY_CUSTOM_KEY = "TRANSLATION_CUSTOM_KEY"
        private const val SECURE_CUSTOM_KEY = "custom_translation_api_key"
    }
}
