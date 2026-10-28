package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MangaFlowApp
import com.example.ui.theme.MangaAdShield
import com.example.ui.theme.MangaAmber
import com.example.ui.theme.MangaAmethyst
import com.example.ui.theme.MangaPrimary
import com.example.ui.theme.MangaSecondary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = MangaFlowApp.instance
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val themeState by viewModel.themeState.collectAsState()
    val searchEngine by viewModel.searchEngine.collectAsState()
    val desktopSite by viewModel.desktopSitePreference.collectAsState()
    val privateBrowsing by viewModel.privateBrowsingEnabled.collectAsState()
    val adBlocker by viewModel.adBlockerEnabled.collectAsState()
    val trackerBlocker by viewModel.trackerBlockerEnabled.collectAsState()
    val cosmeticFiltering by viewModel.cosmeticFilteringEnabled.collectAsState()
    val additionalList by viewModel.additionalListEnabled.collectAsState()
    val totalBlockedAds by viewModel.totalBlockedAds.collectAsState()
    val totalTrackersBlocked by viewModel.totalTrackersBlocked.collectAsState()
    val userAllowlist by viewModel.userAllowlist.collectAsState()
    val lastUpdateTime by viewModel.lastUpdateTime.collectAsState()
    val filterUpdateStatus by viewModel.filterUpdateStatus.collectAsState()
    val translationLang by viewModel.translationLanguage.collectAsState()
    val translationProviderId by viewModel.translationProviderId.collectAsState()
    val translationTargetLang by viewModel.translationTargetLanguage.collectAsState()
    val translationSourceLang by viewModel.translationSourceLanguage.collectAsState()
    val allowCloudTranslation by viewModel.allowCloudTranslation.collectAsState()
    val cacheTranslations by viewModel.cacheTranslations.collectAsState()
    val cachedTranslationsCount by viewModel.cachedTranslationsCount.collectAsState()
    val customEndpoint by viewModel.customEndpoint.collectAsState()
    val customApiKey by viewModel.customApiKey.collectAsState()

    val readerMode by viewModel.readerMode.collectAsState()
    val autoTracking by viewModel.autoProgressTracking.collectAsState()
    val newChapterAlerts by viewModel.newChapterNotifications.collectAsState()
    val chapterMonitoringEnabled by viewModel.chapterMonitoringEnabled.collectAsState()
    val monitoringIntervalHours by viewModel.monitoringIntervalHours.collectAsState()

    var showSearchEngineMenu by remember { mutableStateOf(false) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showSourceLangMenu by remember { mutableStateOf(false) }
    var showReaderModeMenu by remember { mutableStateOf(false) }
    var showDensityMenu by remember { mutableStateOf(false) }
    var showIntervalMenu by remember { mutableStateOf(false) }
    var showAllowlistDialog by remember { mutableStateOf(false) }
    var showCloudInfoDialog by remember { mutableStateOf(false) }
    var newAllowlistInput by remember { mutableStateOf("") }
    var editEndpoint by remember(customEndpoint) { mutableStateOf(customEndpoint) }
    var editApiKey by remember(customApiKey) { mutableStateOf(customApiKey) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Top Header
        Text(
            text = "Settings",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Manage appearance, browser, privacy, and reading preferences",
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // CATEGORY 1: Appearance
        SettingsCategoryHeader("Appearance", Icons.Default.Palette)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Theme Toggle: Dark vs Light
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dark Mode", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (themeState.isDark) "Active (Recommended for OLED reading)" else "Light mode active", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = themeState.isDark,
                        onCheckedChange = { viewModel.setThemeMode(if (it) "DARK" else "LIGHT") },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Accent Color Selector
                Text("Accent Color", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val accents = listOf(
                        Triple(0, MangaPrimary, "Cyber Blue"),
                        Triple(1, MangaSecondary, "Mint Green"),
                        Triple(2, MangaAmethyst, "Amethyst"),
                        Triple(3, MangaAmber, "Sunset Amber")
                    )
                    accents.forEach { (idx, color, name) ->
                        val isSelected = themeState.accentIndex == idx
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) TextPrimary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setAccentIndex(idx) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = name, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // UI Density Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("UI Density", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(themeState.uiDensity, color = TextSecondary, fontSize = 11.sp)
                    }
                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { showDensityMenu = true }
                        ) {
                            Text(themeState.uiDensity, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        DropdownMenu(
                            expanded = showDensityMenu,
                            onDismissRequest = { showDensityMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            listOf("COMPACT", "NORMAL", "SPACIOUS").forEach { density ->
                                DropdownMenuItem(
                                    text = { Text(density, color = TextPrimary) },
                                    onClick = {
                                        viewModel.setUiDensity(density)
                                        showDensityMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // CATEGORY 2: Browser
        SettingsCategoryHeader("Browser", Icons.Default.Public)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Search engine dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Default Search Engine", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(searchEngine, color = TextSecondary, fontSize = 11.sp)
                    }
                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { showSearchEngineMenu = true }
                        ) {
                            Text(searchEngine, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        DropdownMenu(
                            expanded = showSearchEngineMenu,
                            onDismissRequest = { showSearchEngineMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            listOf("DuckDuckGo", "Google", "Bing").forEach { engine ->
                                DropdownMenuItem(
                                    text = { Text(engine, color = TextPrimary) },
                                    onClick = {
                                        viewModel.setSearchEngine(engine)
                                        showSearchEngineMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Desktop site toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Request Desktop Site", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Send desktop User-Agent to websites (Phase 3 Engine)", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = desktopSite,
                        onCheckedChange = viewModel::setDesktopSitePreference,
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // CATEGORY 3: Privacy
        SettingsCategoryHeader("Privacy & Storage", Icons.Default.Security)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Clear browsing history
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Browsing History", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Manage and clear locally saved page history", color = TextSecondary, fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                app.browserRepository.clearHistory()
                                Toast.makeText(context, "Browsing history cleared", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Clear History", color = TextPrimary, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Purge Cookies & Cache
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cookies & Ephemeral Storage", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Purges session cookies and WebView cache", color = TextSecondary, fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            app.privacyManager.purgePrivateData(context)
                            Toast.makeText(context, "Cache and cookies purged", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MangaAmethyst.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Purge Cache", color = MangaAmethyst, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Explicit Anonymity Disclaimer Box
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Notice: Private browsing isolates history, search queries, cookies, and reading statistics on this device. It does not provide network anonymity or conceal your activity from internet service providers, network administrators, or visited websites.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // CATEGORY 4: Content Filtering
        SettingsCategoryHeader("Content Filtering", Icons.Default.Shield)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Ad Blocker Master Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("EasyList Ad Blocker", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Intercepts intrusive banner, popup, and video ad URLs", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = adBlocker,
                        onCheckedChange = viewModel::setAdBlockerEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = MangaPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tracker Blocker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tracker & Profiling Protection", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Blocks fingerprinting and third-party tracking cookies", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = trackerBlocker,
                        onCheckedChange = viewModel::setTrackerBlockerEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = MangaSecondary)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cosmetic Element Collapse
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cosmetic Element Collapse", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Collapses blank spaces left by blocked advertisements", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = cosmeticFiltering,
                        onCheckedChange = viewModel::setCosmeticFilteringEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = MangaPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Additional Aggressive List (Coinminers & aggressive popunders)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aggressive / Cryptominer Blocklist", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Blocks in-browser mining scripts and popup redirectors", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = additionalList,
                        onCheckedChange = viewModel::setAdditionalListEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = MangaSecondary)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Statistics Card
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Blocked Requests", color = TextSecondary, fontSize = 11.sp)
                            Text("$totalBlockedAds ads • $totalTrackersBlocked trackers", color = MangaAdShield, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = viewModel::resetFilterStatistics,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reset", color = TextPrimary, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Allowlist Manager Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("User Allowlist", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${userAllowlist.size} domains whitelisted", color = TextSecondary, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { showAllowlistDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Manage Allowlist", color = TextPrimary, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter update row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Filter Rules Update", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(filterUpdateStatus ?: "EasyList bundle active", color = TextSecondary, fontSize = 11.sp)
                    }
                    Button(
                        onClick = viewModel::refreshFilterLists,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Update Lists", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    }
                }

            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // CATEGORY 5: Translation & Manhwa OCR
        SettingsCategoryHeader("Translation & Image OCR", Icons.Default.Translate)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Translation Provider Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Translation Provider", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        val providerLabel = when (translationProviderId) {
                            "free_web" -> "Free Public Web (Online)"
                            "custom_api" -> "Custom API (LibreTranslate/DeepL)"
                            else -> "On-Device Offline Glossary (Private)"
                        }
                        Text(providerLabel, color = TextSecondary, fontSize = 11.sp)
                    }
                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { showProviderMenu = true }
                        ) {
                            val shortLabel = when (translationProviderId) {
                                "free_web" -> "Web"
                                "custom_api" -> "Custom"
                                else -> "On-Device"
                            }
                            Text(
                                text = shortLabel,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showProviderMenu,
                            onDismissRequest = { showProviderMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("On-Device Offline Glossary", color = TextPrimary) },
                                onClick = {
                                    viewModel.setTranslationProvider("on_device")
                                    showProviderMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Free Public Web Translation", color = TextPrimary) },
                                onClick = {
                                    viewModel.setTranslationProvider("free_web")
                                    showProviderMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Custom API (LibreTranslate/DeepL)", color = TextPrimary) },
                                onClick = {
                                    viewModel.setTranslationProvider("custom_api")
                                    showProviderMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Target Language Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Target Language", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        val targetName = when (translationTargetLang) {
                            "es" -> "Spanish"
                            "fr" -> "French"
                            "de" -> "German"
                            "id" -> "Indonesian"
                            "pt" -> "Portuguese"
                            else -> "English"
                        }
                        Text(targetName, color = TextSecondary, fontSize = 11.sp)
                    }
                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { showLanguageMenu = true }
                        ) {
                            Text(
                                text = translationTargetLang.uppercase(),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            mapOf("en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German", "id" to "Indonesian", "pt" to "Portuguese").forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text("$name ($code)", color = TextPrimary) },
                                    onClick = {
                                        viewModel.setTranslationTargetLanguage(code)
                                        showLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Source Language Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Source Language", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        val sourceName = when (translationSourceLang) {
                            "ko" -> "Korean (한국어)"
                            "ja" -> "Japanese (日本語)"
                            "zh" -> "Chinese (中文)"
                            "en" -> "English"
                            else -> "Auto-Detect"
                        }
                        Text(sourceName, color = TextSecondary, fontSize = 11.sp)
                    }
                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { showSourceLangMenu = true }
                        ) {
                            Text(
                                text = translationSourceLang.uppercase(),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showSourceLangMenu,
                            onDismissRequest = { showSourceLangMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            mapOf("auto" to "Auto-Detect", "ko" to "Korean (한국어)", "ja" to "Japanese (日本語)", "zh" to "Chinese (中文)", "en" to "English").forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name, color = TextPrimary) },
                                    onClick = {
                                        viewModel.setTranslationSourceLanguage(code)
                                        showSourceLangMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Allow Cloud Translation Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow Cloud Translation", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Permits transmitting non-private text to external translation APIs. Private browsing strictly enforces zero cloud transmission.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = allowCloudTranslation,
                        onCheckedChange = viewModel::setAllowCloudTranslation,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF003822),
                            checkedTrackColor = MangaSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Translation Cache Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cache In-Memory Translations", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Accelerates repeated page views ($cachedTranslationsCount cached items)", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = cacheTranslations,
                        onCheckedChange = viewModel::setCacheTranslations,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF003822),
                            checkedTrackColor = MangaSecondary
                        )
                    )
                }

                if (cachedTranslationsCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.clearTranslationCache()
                            Toast.makeText(context, "Translation cache cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Clear Translation Cache ($cachedTranslationsCount)", color = Color(0xFFFFB4AB), fontSize = 11.sp)
                    }
                }

                // Custom API fields (shown when Custom API provider selected)
                if (translationProviderId == "custom_api") {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Custom API Settings", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editEndpoint,
                        onValueChange = { editEndpoint = it },
                        label = { Text("API Endpoint URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editApiKey,
                        onValueChange = { editApiKey = it },
                        label = { Text("API Key (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.saveCustomApiCredentials(editEndpoint, editApiKey)
                            Toast.makeText(context, "Custom API credentials saved", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Credentials", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // CATEGORY 5: Reading
        SettingsCategoryHeader("Reading Preferences", Icons.Default.AutoStories)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Reader Mode layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Default Reader Mode", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(readerMode, color = TextSecondary, fontSize = 11.sp)
                    }
                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { showReaderModeMenu = true }
                        ) {
                            Text(readerMode, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        DropdownMenu(
                            expanded = showReaderModeMenu,
                            onDismissRequest = { showReaderModeMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            listOf("Vertical Strip", "Webtoon", "Single Page").forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode, color = TextPrimary) },
                                    onClick = {
                                        viewModel.setReaderMode(mode)
                                        showReaderModeMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto Progress Tracking
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Progress Tracking", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Automatically records chapter number when reading", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoTracking,
                        onCheckedChange = viewModel::setAutoProgressTracking,
                        colors = SwitchDefaults.colors(checkedThumbColor = MangaSecondary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // CATEGORY 6: Chapter Monitoring & Notifications
        SettingsCategoryHeader("Chapter Monitoring & Notifications", Icons.Default.Notifications)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Master Monitoring Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automated Chapter Monitoring", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Periodically checks library sources in background", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = chapterMonitoringEnabled,
                        onCheckedChange = viewModel::setChapterMonitoringEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = MangaPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Check Frequency Interval Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Check Frequency", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Every $monitoringIntervalHours hours (Android WorkManager)", color = TextSecondary, fontSize = 11.sp)
                    }
                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { showIntervalMenu = true }
                        ) {
                            Text(
                                text = "$monitoringIntervalHours hrs",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showIntervalMenu,
                            onDismissRequest = { showIntervalMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            listOf(3, 6, 12, 24).forEach { hours ->
                                DropdownMenuItem(
                                    text = { Text("Every $hours hours", color = TextPrimary) },
                                    onClick = {
                                        viewModel.setMonitoringIntervalHours(hours)
                                        showIntervalMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Release Notifications Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("System Release Alerts", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Status bar notifications when new chapters release", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = newChapterAlerts,
                        onCheckedChange = viewModel::setNewChapterNotifications,
                        colors = SwitchDefaults.colors(checkedThumbColor = MangaSecondary)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Immediate Check Button
                Button(
                    onClick = { viewModel.checkAllSeriesNow() },
                    colors = ButtonDefaults.buttonColors(containerColor = MangaPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check All Subscribed Titles Now", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Optional Cloud Sync Info Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCloudInfoDialog = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cloud Monitoring & FCM Push (Optional)", color = TextSecondary, fontSize = 11.sp)
                    Text("Learn More", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showCloudInfoDialog) {
            AlertDialog(
                onDismissRequest = { showCloudInfoDialog = false },
                title = { Text("Cloud Monitoring (Optional)", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "MangaFlow operates 100% locally by default. Your device directly checks chapter sources with no cloud account, server, or tracking required.",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Advanced users can optionally connect a self-hosted cloud worker (Node.js/Lambda) via the CloudMonitoringClient interface (documented in docs/OPTIONAL_BACKEND_MONITORING.md) for battery offloading.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showCloudInfoDialog = false }) {
                        Text("Got It")
                    }
                }
            )
        }

        if (showAllowlistDialog) {
            AlertDialog(
                onDismissRequest = { showAllowlistDialog = false },
                title = { Text("Manage Allowlist", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "Websites on this allowlist will not have ads or tracking scripts blocked.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newAllowlistInput,
                                onValueChange = { newAllowlistInput = it },
                                placeholder = { Text("e.g. webtoons.com", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newAllowlistInput.isNotBlank()) {
                                        viewModel.addToAllowlist(newAllowlistInput.trim())
                                        newAllowlistInput = ""
                                    }
                                }
                            ) {
                                Text("Add")
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (userAllowlist.isEmpty()) {
                            Text("No allowed domains.", color = TextSecondary, fontSize = 12.sp)
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(userAllowlist.toList()) { domain ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(domain, color = TextPrimary, fontSize = 13.sp)
                                        IconButton(onClick = { viewModel.removeFromAllowlist(domain) }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showAllowlistDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingsCategoryHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
