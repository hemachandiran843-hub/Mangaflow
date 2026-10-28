package com.example

import com.example.engine.filter.ContentFilterEngine
import com.example.engine.privacy.PrivacyManager
import com.example.engine.translation.OcrTranslationEngine
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.theme.MangaAmber
import com.example.ui.theme.MangaAmethyst
import com.example.ui.theme.MangaPrimary
import com.example.ui.theme.MangaSecondary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaFlowUnitTest {

    @Test
    fun contentFilter_blocksKnownAdNetworks() {
        val filter = ContentFilterEngine()
        assertTrue(filter.isAdOrTracker("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(filter.isAdOrTracker("https://securepubads.g.doubleclick.net/gampad/ads"))
        assertTrue(filter.isAdOrTracker("https://adsterra.com/direct_link/popunder"))
        assertTrue(filter.isAdOrTracker("https://popads.net/serve/ad_banner.js"))

        // Legitimate manga reader domains must NOT be blocked
        assertFalse(filter.isAdOrTracker("https://www.webtoons.com/en/fantasy/solo-leveling/chapter-120"))
        assertFalse(filter.isAdOrTracker("https://mangadex.org/title/12345/omniscient-reader"))
    }

    @Test
    fun privacyManager_strictlyProtectsPrivateSessions() {
        val privacyManager = PrivacyManager()

        // Normal browsing allows persistence
        assertTrue(privacyManager.shouldPersistHistory(isPrivate = false))
        assertTrue(privacyManager.shouldTrackReadingStats(isPrivate = false))

        // Private browsing must NEVER persist history or reading stats
        assertFalse(privacyManager.shouldPersistHistory(isPrivate = true))
        assertFalse(privacyManager.shouldTrackReadingStats(isPrivate = true))
    }

    @Test
    fun ocrTranslationEngine_translatesManhwaKeywords() = kotlinx.coroutines.runBlocking {
        val engine = OcrTranslationEngine()
        val translatedSystem = engine.translateText("시스템 경고")
        assertTrue(translatedSystem.contains("System") || translatedSystem.contains("Warning"))

        val translatedLevelUp = engine.translateText("레벨업")
        assertEquals("Level Up!", translatedLevelUp)
    }

    @Test
    fun settingsViewModel_themeSwitchingAndAccents() {
        val viewModel = SettingsViewModel()

        // Initial default is Dark theme
        assertTrue(viewModel.themeState.value.isDark)
        assertEquals("DARK", viewModel.themeState.value.themeMode)
        assertEquals(MangaPrimary, viewModel.themeState.value.accentColor)

        // Switch to Light Mode
        viewModel.setThemeMode("LIGHT")
        assertFalse(viewModel.themeState.value.isDark)
        assertEquals("LIGHT", viewModel.themeState.value.themeMode)

        // Change accent color to Mint Green
        viewModel.setAccentIndex(1)
        assertEquals(MangaSecondary, viewModel.themeState.value.accentColor)

        // Change accent color to Amethyst
        viewModel.setAccentIndex(2)
        assertEquals(MangaAmethyst, viewModel.themeState.value.accentColor)

        // Change accent color to Sunset Amber
        viewModel.setAccentIndex(3)
        assertEquals(MangaAmber, viewModel.themeState.value.accentColor)
    }

    @Test
    fun settingsViewModel_translationLanguageRemainsAvailable() {
        val viewModel = SettingsViewModel()
        viewModel.setTranslationLanguage("Spanish")
        assertEquals("Spanish", viewModel.translationLanguage.value)
    }
}
