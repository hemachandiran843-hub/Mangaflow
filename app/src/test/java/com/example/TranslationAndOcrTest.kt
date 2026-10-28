package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.domain.model.OcrBox
import com.example.engine.translation.CustomApiTranslationProvider
import com.example.engine.translation.FreeWebTranslationProvider
import com.example.engine.translation.OcrTranslationEngine
import com.example.engine.translation.OnDeviceGlossaryTranslationProvider
import com.example.engine.translation.TranslationManager
import com.example.engine.translation.TranslationRequest
import com.example.engine.translation.TranslationResponse
import com.example.engine.translation.TranslationResult
import com.example.engine.translation.WebpageTranslationHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TranslationAndOcrTest {

    private lateinit var onDeviceProvider: OnDeviceGlossaryTranslationProvider
    private lateinit var freeWebProvider: FreeWebTranslationProvider
    private lateinit var customApiProvider: CustomApiTranslationProvider
    private lateinit var translationManager: TranslationManager
    private lateinit var ocrEngine: OcrTranslationEngine

    @Before
    fun setup() {
        onDeviceProvider = OnDeviceGlossaryTranslationProvider()
        freeWebProvider = FreeWebTranslationProvider()
        customApiProvider = CustomApiTranslationProvider()
        translationManager = TranslationManager(appSettingDao = null)
        ocrEngine = OcrTranslationEngine(translationManager)
    }

    @Test
    fun testOnDeviceGlossary_matchesManhwaVocabulary() = runBlocking {
        val request = TranslationRequest(
            text = "상태창",
            sourceLanguage = "ko",
            targetLanguage = "en",
            isPrivate = false
        )
        val result = onDeviceProvider.translate(request)

        assertTrue(result is TranslationResult.Success)
        val response = (result as TranslationResult.Success).response
        assertEquals("Status Window", response.translatedText)
        assertEquals("on_device", response.providerId)
        assertFalse(response.isCached)
    }

    @Test
    fun testOnDeviceGlossary_multiLanguageSupport() = runBlocking {
        // Spanish target
        val requestEs = TranslationRequest(
            text = "레벨업",
            sourceLanguage = "ko",
            targetLanguage = "es",
            isPrivate = false
        )
        val resultEs = onDeviceProvider.translate(requestEs)
        assertTrue(resultEs is TranslationResult.Success)
        assertEquals("¡Subida de nivel!", (resultEs as TranslationResult.Success).response.translatedText)

        // French target
        val requestFr = TranslationRequest(
            text = "던전",
            sourceLanguage = "ko",
            targetLanguage = "fr",
            isPrivate = false
        )
        val resultFr = onDeviceProvider.translate(requestFr)
        assertTrue(resultFr is TranslationResult.Success)
        assertEquals("Donjon", (resultFr as TranslationResult.Success).response.translatedText)
    }

    @Test
    fun testFreeWebProvider_blocksPrivateBrowsingRequests() = runBlocking {
        freeWebProvider.isCloudPermittedByUser = true

        val privateRequest = TranslationRequest(
            text = "Hello world",
            sourceLanguage = "auto",
            targetLanguage = "es",
            isPrivate = true
        )
        val result = freeWebProvider.translate(privateRequest)

        assertTrue("Private requests must be blocked by cloud provider", result is TranslationResult.PrivateSessionRestricted)
    }

    @Test
    fun testFreeWebProvider_blocksWhenCloudNotPermittedByUser() = runBlocking {
        // Explicitly disabled by default
        freeWebProvider.isCloudPermittedByUser = false

        val request = TranslationRequest(
            text = "Hello world",
            sourceLanguage = "auto",
            targetLanguage = "es",
            isPrivate = false
        )
        val result = freeWebProvider.translate(request)

        assertTrue("Unpermitted cloud translation must return CloudTranslationNotPermitted", result is TranslationResult.CloudTranslationNotPermitted)
    }

    @Test
    fun testCustomApiProvider_blocksPrivateRequests() = runBlocking {
        customApiProvider.isCloudPermittedByUser = true
        customApiProvider.endpointUrl = "https://custom.translate.api/translate"

        val privateRequest = TranslationRequest(
            text = "Secret text",
            sourceLanguage = "auto",
            targetLanguage = "de",
            isPrivate = true
        )
        val result = customApiProvider.translate(privateRequest)
        assertTrue(result is TranslationResult.PrivateSessionRestricted)
    }

    @Test
    fun testTranslationManager_cachesTranslations() = runBlocking {
        translationManager.setActiveProvider("on_device")
        translationManager.setCacheTranslations(true)
        translationManager.clearCache()
        assertEquals(0, translationManager.cachedItemsCount.value)

        val text = "퀘스트 완료"
        val result1 = translationManager.translateText(text, targetLang = "en", isPrivate = false)
        assertTrue(result1 is TranslationResult.Success)
        assertFalse((result1 as TranslationResult.Success).response.isCached)

        // Cache count should now be 1
        assertEquals(1, translationManager.cachedItemsCount.value)

        // Second translation of the same query should hit cache
        val result2 = translationManager.translateText(text, targetLang = "en", isPrivate = false)
        assertTrue(result2 is TranslationResult.Success)
        assertTrue("Second translation should be served from cache", (result2 as TranslationResult.Success).response.isCached)

        // Clear cache
        translationManager.clearCache()
        assertEquals(0, translationManager.cachedItemsCount.value)
    }

    @Test
    fun testTranslationManager_skipsCacheForPrivateBrowsing() = runBlocking {
        translationManager.setActiveProvider("on_device")
        translationManager.setCacheTranslations(true)
        translationManager.clearCache()

        val text = "무림 맹주"
        val result = translationManager.translateText(text, targetLang = "en", isPrivate = true)
        assertTrue(result is TranslationResult.Success)

        // Cache count must remain 0 to preserve private session isolation
        assertEquals("Cache must NOT record items from private sessions", 0, translationManager.cachedItemsCount.value)
    }

    @Test
    fun testTranslationManager_batchTranslation() = runBlocking {
        translationManager.setActiveProvider("on_device")
        val texts = listOf("상태창", "레벨업", "던전")
        val result = translationManager.translateBatch(texts, targetLang = "en", isPrivate = false)

        assertTrue(result is com.example.engine.translation.BatchTranslationResult.Success)
        val batchResponse = (result as com.example.engine.translation.BatchTranslationResult.Success).translations
        assertEquals(3, batchResponse.size)
        assertEquals("Status Window", batchResponse["상태창"])
        assertEquals("Level Up!", batchResponse["레벨업"])
        assertEquals("Dungeon", batchResponse["던전"])
    }

    @Test
    fun testOcrEngine_downsamplesLargeBitmapsAndGeneratesBoxes() = runBlocking {
        // Create an oversized test bitmap (1500 x 2000 px) with simulated white speech bubble
        val width = 1500
        val height = 2000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(100, 100, 100))

        // Draw a simulated white speech bubble in the center
        val bubblePaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(300f, 400f, 900f, 800f, bubblePaint)

        // Downsampling test
        val downscaled = ocrEngine.downsampleIfNeeded(bitmap, maxDimension = 1080)
        assertTrue("Downscaled bitmap dimension must be <= 1080", downscaled.width <= 1080 && downscaled.height <= 1080)

        // Run OCR analysis
        val boxes = ocrEngine.analyzeAndTranslate(bitmap, isPrivate = false)
        assertNotNull(boxes)
        assertTrue("Expected at least one speech bubble box detected", boxes.isNotEmpty())

        val box = boxes.first()
        assertTrue("Relative coordinate leftPercent must be between 0 and 1", box.leftPercent in 0.0f..1.0f)
        assertTrue("Relative coordinate topPercent must be between 0 and 1", box.topPercent in 0.0f..1.0f)
        assertTrue("Relative coordinate widthPercent must be positive", box.widthPercent > 0.0f)
        assertTrue("Relative coordinate heightPercent must be positive", box.heightPercent > 0.0f)
        assertFalse("Translated text should not be empty", box.translatedText.isBlank())
    }

    @Test
    fun testWebpageTranslationHelper_scriptGeneration() {
        val extractScript = WebpageTranslationHelper.generateExtractTextNodesScript()
        assertTrue("Extract script must use TreeWalker", extractScript.contains("document.createTreeWalker"))
        assertTrue("Extract script must filter text nodes", extractScript.contains("NodeFilter.SHOW_TEXT"))
        assertTrue("Extract script must ignore script tags", extractScript.contains("script", ignoreCase = true))

        val translations = mapOf("Hello" to "Hola", "World" to "Mundo")
        val applyScript = WebpageTranslationHelper.generateApplyTranslationScript(translations)
        assertTrue("Apply script must save original text in data-mf-original", applyScript.contains("data-mf-original"))
        assertTrue("Apply script must inject translated text", applyScript.contains("Hola"))

        val restoreScript = WebpageTranslationHelper.generateRestoreScript()
        assertTrue("Restore script must query data-mf-original", restoreScript.contains("data-mf-original"))
        assertTrue("Restore script must remove data-mf-original attribute after restoring", restoreScript.contains("removeAttribute('data-mf-original')"))
    }
}
