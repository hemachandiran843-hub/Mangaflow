package com.example.ui.screens.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.MangaFlowApp
import com.example.engine.browser.BrowserDownloadHandler
import com.example.engine.browser.BrowserSecurityManager
import com.example.engine.browser.BrowserSessionManager
import com.example.engine.browser.ReaderModeExtractor
import com.example.ui.components.AdShieldSheet
import com.example.ui.components.BrowserAddressBar
import com.example.ui.components.BrowserBottomBar
import com.example.ui.components.FindInPageBar
import com.example.ui.components.HistoryBookmarksSheet
import com.example.ui.components.OcrOverlayView
import com.example.ui.components.TabSwitcherView
import com.example.ui.theme.MangaSecondary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = MangaFlowApp.instance
    val currentTab by viewModel.currentTab.collectAsState()
    val normalTabs by viewModel.normalTabs.collectAsState()
    val privateTabs by viewModel.privateTabs.collectAsState()
    val isPrivate by viewModel.isPrivateMode.collectAsState()
    val blockedAds by viewModel.blockedAdsCount.collectAsState()
    val trackersBlocked by viewModel.trackersBlockedCount.collectAsState()
    val userAllowlist by viewModel.userAllowlist.collectAsState()
    val disabledSites by viewModel.disabledSites.collectAsState()
    val detectedManhwa by viewModel.detectedManhwa.collectAsState()

    val isEditingAddress by viewModel.isEditingAddress.collectAsState()
    val addressInput by viewModel.addressInput.collectAsState()
    val showTabSwitcher by viewModel.showTabSwitcher.collectAsState()
    val showShieldSheet by viewModel.showShieldSheet.collectAsState()
    val showHistorySheet by viewModel.showHistorySheet.collectAsState()
    val showBookmarksSheet by viewModel.showBookmarksSheet.collectAsState()

    val showFindInPage by viewModel.showFindInPage.collectAsState()
    val findQuery by viewModel.findQuery.collectAsState()
    val findActiveMatch by viewModel.findActiveMatch.collectAsState()
    val findTotalMatches by viewModel.findTotalMatches.collectAsState()

    val isOcrActive by viewModel.isOcrActive.collectAsState()
    val isOcrLoading by viewModel.isOcrLoading.collectAsState()
    val ocrBoxes by viewModel.ocrBoxes.collectAsState()

    val isPageTranslated by viewModel.isPageTranslated.collectAsState()
    val isPageTranslating by viewModel.isPageTranslating.collectAsState()
    val pageTranslationError by viewModel.pageTranslationError.collectAsState()
    val selectedTextTranslation by viewModel.selectedTextTranslation.collectAsState()

    val historyList by app.browserRepository.history.collectAsState(initial = emptyList())
    val bookmarksList by app.browserRepository.bookmarks.collectAsState(initial = emptyList())

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var defaultUserAgent by remember { mutableStateOf<String?>(null) }

    val onTriggerPageTranslation = {
        webViewRef?.evaluateJavascript(com.example.engine.translation.WebpageTranslationHelper.generateExtractTextNodesScript()) { jsonResult ->
            try {
                val cleanedJson = if (jsonResult != null && jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                    // Unescape JSON string if double-encoded
                    try {
                        org.json.JSONTokener(jsonResult).nextValue().toString()
                    } catch (_: Exception) {
                        jsonResult
                    }
                } else {
                    jsonResult ?: "[]"
                }

                val jsonArray = org.json.JSONArray(cleanedJson)
                val extractedList = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    val str = jsonArray.optString(i)
                    if (str.isNotBlank()) {
                        extractedList.add(str)
                    }
                }
                viewModel.startPageTranslation(extractedList) { translationsMap ->
                    val applyJs = com.example.engine.translation.WebpageTranslationHelper.generateApplyTranslationScript(translationsMap)
                    webViewRef?.evaluateJavascript(applyJs, null)
                }
            } catch (e: Exception) {
                viewModel.setTabError("Failed to extract page text for translation: ${e.message}")
            }
        }
    }

    val onRestoreOriginalWebpage = {
        val restoreJs = com.example.engine.translation.WebpageTranslationHelper.generateRestoreScript()
        webViewRef?.evaluateJavascript(restoreJs, null)
        viewModel.setPageRestored()
    }

    // Extract current site host
    val currentHost = remember(currentTab?.url) {
        val raw = currentTab?.url ?: ""
        try {
            Uri.parse(raw).host?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }

    val isSiteFilteringEnabled = viewModel.isFilteringEnabledForSite(currentHost)
    val isAllowlisted = viewModel.isDomainAllowlisted(currentHost)

    // Hardware back navigation precedence
    BackHandler(enabled = true) {
        when {
            showFindInPage -> {
                viewModel.closeFindInPage()
                webViewRef?.clearMatches()
            }
            showTabSwitcher -> viewModel.setShowTabSwitcher(false)
            showShieldSheet -> viewModel.setShowShieldSheet(false)
            showBookmarksSheet || showHistorySheet -> {
                viewModel.setShowBookmarksSheet(false)
                viewModel.setShowHistorySheet(false)
            }
            currentTab?.canGoBack == true -> webViewRef?.goBack()
            else -> {
                // If in private tab and multiple tabs open, close tab or return
            }
        }
    }

    // React to Desktop mode toggle on active WebView
    LaunchedEffect(currentTab?.isDesktopMode) {
        val isDesktop = currentTab?.isDesktopMode ?: false
        webViewRef?.settings?.apply {
            userAgentString = if (isDesktop) {
                BrowserSessionManager.DESKTOP_USER_AGENT
            } else {
                defaultUserAgent
            }
            useWideViewPort = isDesktop
            loadWithOverviewMode = isDesktop
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Omnibox Browser Address Bar
            BrowserAddressBar(
                currentUrl = currentTab?.url ?: "about:blank",
                title = currentTab?.title ?: "MangaFlow Browser",
                isLoading = currentTab?.isLoading ?: false,
                progress = currentTab?.progress ?: 0,
                isPrivate = isPrivate,
                blockedAds = blockedAds,
                isReaderMode = currentTab?.isReaderModeActive ?: false,
                isEditing = isEditingAddress,
                addressInput = addressInput,
                onAddressInputChanged = viewModel::onAddressInputChanged,
                onStartEditing = viewModel::startEditingAddress,
                onStopEditing = viewModel::stopEditingAddress,
                onSubmitAddress = { input ->
                    viewModel.submitAddress(input)
                    currentTab?.url?.let { webViewRef?.loadUrl(it) }
                },
                onReload = {
                    viewModel.reload()
                    webViewRef?.reload()
                },
                onStopLoading = {
                    viewModel.stopLoading()
                    webViewRef?.stopLoading()
                },
                onToggleReaderMode = {
                    viewModel.toggleReaderMode()
                    webViewRef?.evaluateJavascript(ReaderModeExtractor.getReaderModeScript(), null)
                },
                onOpenShieldSheet = { viewModel.setShowShieldSheet(true) },
                modifier = Modifier.statusBarsPadding()
            )

            // Optional Find in Page floating toolbar
            if (showFindInPage) {
                FindInPageBar(
                    query = findQuery,
                    activeMatchIndex = findActiveMatch,
                    totalMatchesCount = findTotalMatches,
                    onQueryChanged = { query ->
                        viewModel.onFindQueryChanged(query)
                        if (query.isNotBlank()) {
                            webViewRef?.findAllAsync(query)
                        } else {
                            webViewRef?.clearMatches()
                        }
                    },
                    onFindPrevious = { webViewRef?.findNext(false) },
                    onFindNext = { webViewRef?.findNext(true) },
                    onClose = {
                        viewModel.closeFindInPage()
                        webViewRef?.clearMatches()
                    }
                )
            }

            // Viewport & Active WebView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Live Android WebView Engine
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            defaultUserAgent = settings.userAgentString

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                setSupportZoom(true)
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                                if (isPrivate) {
                                    cacheMode = WebSettings.LOAD_NO_CACHE
                                }
                            }

                            // Setup Find Listener
                            setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                                viewModel.updateFindMatchesCount(activeMatchOrdinal, numberOfMatches)
                            }

                            // Safe Download Handler with path validation
                            setDownloadListener(BrowserDownloadHandler(ctx) { fileName ->
                                Toast.makeText(ctx, "Download started: $fileName", Toast.LENGTH_SHORT).show()
                            })

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val reqUrl = request?.url?.toString() ?: return false
                                    // Handle external non-web schemes (intent:, market:, mailto:, tel:)
                                    if (BrowserSecurityManager.isExternalActionScheme(reqUrl)) {
                                        return BrowserSecurityManager.handleExternalNavigation(ctx, reqUrl)
                                    }
                                    return false
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return null
                                    val pageHost = currentHost ?: try { request.url?.host } catch (_: Exception) { null }

                                    // Content filter request interception
                                    if (app.contentFilterEngine.shouldBlockRequest(reqUrl, pageHost)) {
                                        val (mime, stream) = app.contentFilterEngine.getEmptyResponse()
                                        return WebResourceResponse(mime, "UTF-8", stream)
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: android.webkit.SslErrorHandler?,
                                    error: android.net.http.SslError?
                                ) {
                                    // SECURITY: Strictly cancel on SSL error, do NOT bypass
                                    handler?.cancel()
                                    viewModel.setTabError("SSL Certificate Error: The connection to this site is not secure.")
                                }

                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: android.webkit.RenderProcessGoneDetail?
                                ): Boolean {
                                    // Recover from renderer process crash without crashing the host app
                                    viewModel.handleRendererCrash()
                                    return true
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        viewModel.setTabError(error?.description?.toString() ?: "Failed to load page")
                                    }
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    app.browserSessionManager.updateCurrentTab {
                                        it.copy(
                                            isLoading = true,
                                            progress = 15,
                                            canGoBack = view?.canGoBack() ?: false,
                                            canGoForward = view?.canGoForward() ?: false,
                                            errorMessage = null,
                                            isCrashed = false
                                        )
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val safeUrl = url ?: ""
                                    val title = view?.title ?: ""

                                    app.browserSessionManager.updateCurrentTab {
                                        it.copy(
                                            title = if (title.isNotBlank()) title else safeUrl,
                                            isLoading = false,
                                            progress = 100,
                                            canGoBack = view?.canGoBack() ?: false,
                                            canGoForward = view?.canGoForward() ?: false
                                        )
                                    }

                                    // Cosmetic CSS injection
                                    val cosmeticCss = app.contentFilterEngine.getCosmeticCss()
                                    if (cosmeticCss.isNotBlank()) {
                                        val jsInject = "(function() { var s = document.createElement('style'); s.innerHTML = `$cosmeticCss`; document.head.appendChild(s); })();"
                                        view?.evaluateJavascript(jsInject, null)
                                    }

                                    // Notify Manhwa tracker bridge (suppressed automatically in private tabs)
                                    app.browserTrackerBridge.onPageLoaded(safeUrl, title, isPrivate)

                                    // Record browsing history (strictly excluded for private tabs)
                                    if (app.privacyManager.shouldPersistHistory(isPrivate)) {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            app.browserRepository.recordHistory(safeUrl, title, isPrivate = false)
                                        }
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    app.browserSessionManager.updateCurrentTab {
                                        it.copy(progress = newProgress, isLoading = newProgress < 100)
                                    }
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (!title.isNullOrBlank()) {
                                        app.browserSessionManager.updateCurrentTab { it.copy(title = title) }
                                    }
                                }
                            }

                            currentTab?.url?.let { loadUrl(it) }
                            webViewRef = this
                        }
                    },
                    update = { view ->
                        webViewRef = view
                        val activeUrl = currentTab?.url ?: ""
                        if (activeUrl.isNotBlank() && activeUrl != view.url && !activeUrl.startsWith("about:")) {
                            view.loadUrl(activeUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Error / Crashed Banner Overlay
                if (currentTab?.errorMessage != null || currentTab?.isCrashed == true) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFB4AB),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (currentTab?.isCrashed == true) "Web Page Crashed" else "Page Load Error",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentTab?.errorMessage ?: "An unexpected error occurred while loading this page.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.reload()
                                    webViewRef?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reload Webpage")
                            }
                        }
                    }
                }

                // Floating "Detected Manhwa" banner
                if (detectedManhwa != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                    ) {
                        detectedManhwa?.let { detected ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                tonalElevation = 6.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MangaSecondary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = null,
                                        tint = MangaSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = detected.title,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Chapter ${detected.chapterNumber} detected",
                                            color = MangaSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.saveDetectedToLibrary(detected) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MangaSecondary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text("Add to Library", color = Color(0xFF003822), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(
                                        onClick = { app.browserTrackerBridge.clearDetection() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating Webpage Translation Status Banners
                if (isPageTranslating) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Translating webpage text...",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Cancel",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { viewModel.cancelPageTranslation() }
                            )
                        }
                    }
                } else if (isPageTranslated) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = MangaSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Webpage translated (${viewModel.getActiveTranslationProviderName()})",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Show Original",
                                color = MangaSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onRestoreOriginalWebpage() }
                            )
                        }
                    }
                } else if (pageTranslationError != null) {
                    Surface(
                        color = Color(0xFF3B1E1E),
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFB4AB),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = pageTranslationError ?: "",
                                color = Color(0xFFFFB4AB),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = viewModel::dismissTranslationError,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color(0xFFFFB4AB),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // OCR Speech Bubble Translation Overlay
                if (isOcrActive) {
                    OcrOverlayView(
                        ocrBoxes = ocrBoxes,
                        isLoading = isOcrLoading,
                        providerName = viewModel.getActiveTranslationProviderName(),
                        onRetryAll = { viewModel.retryOcr() },
                        onDismiss = viewModel::dismissOcr
                    )
                }

                // Selected Text Translation Floating Modal
                if (selectedTextTranslation != null) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp)
                            .fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Text Translation",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = viewModel::clearSelectedTextTranslation,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "ORIGINAL (${selectedTextTranslation?.sourceLanguage?.uppercase() ?: "AUTO"}):",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedTextTranslation?.originalText ?: "",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "TRANSLATION (${selectedTextTranslation?.targetLanguage?.uppercase() ?: "EN"}):",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedTextTranslation?.translatedText ?: "",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Translated Text", selectedTextTranslation?.translatedText ?: "")
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied translation to clipboard", Toast.LENGTH_SHORT).show()
                                        viewModel.clearSelectedTextTranslation()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Translation", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Browser Bottom Navigation Bar
            BrowserBottomBar(
                canGoBack = currentTab?.canGoBack ?: false,
                canGoForward = currentTab?.canGoForward ?: false,
                tabsCount = if (isPrivate) privateTabs.size else normalTabs.size,
                isPrivate = isPrivate,
                isDesktopMode = currentTab?.isDesktopMode ?: false,
                isPageTranslated = isPageTranslated,
                isPageTranslating = isPageTranslating,
                onTranslateWebpage = { onTriggerPageTranslation() },
                onRestoreOriginalWebpage = { onRestoreOriginalWebpage() },
                onBack = { webViewRef?.goBack() },
                onForward = { webViewRef?.goForward() },
                onOpenTabs = { viewModel.setShowTabSwitcher(true) },
                onOpenLibrary = onNavigateToLibrary,
                onTriggerOcrTranslation = {
                    webViewRef?.let { wv ->
                        val bitmap = try {
                            val bmp = Bitmap.createBitmap(wv.width.coerceAtLeast(1), wv.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            wv.draw(canvas)
                            bmp
                        } catch (_: Exception) {
                            null
                        }
                        viewModel.runOcrOnSnapshot(bitmap)
                    }
                },
                onOpenBookmarks = { viewModel.setShowBookmarksSheet(true) },
                onOpenHistory = { viewModel.setShowHistorySheet(true) },
                onTogglePrivate = { viewModel.togglePrivateMode(!isPrivate) },
                onBookmarkCurrentPage = {
                    currentTab?.let { tab ->
                        viewModel.toggleBookmarkCurrentPage(tab.url, tab.title)
                    }
                },
                onToggleDesktopMode = { viewModel.toggleDesktopMode() },
                onFindInPage = { viewModel.startFindInPage() }
            )
        }

        // Tab Switcher Overlay
        if (showTabSwitcher) {
            TabSwitcherView(
                normalTabs = normalTabs,
                privateTabs = privateTabs,
                currentTabId = currentTab?.id ?: "",
                isPrivateActive = isPrivate,
                canRestoreTab = viewModel.canRestoreTab.collectAsState().value,
                onSwitchTab = { tabId ->
                    viewModel.switchTab(tabId)
                    val targetTab = (normalTabs + privateTabs).find { it.id == tabId }
                    if (targetTab != null && targetTab.url.isNotBlank()) {
                        webViewRef?.loadUrl(targetTab.url)
                    }
                },
                onCloseTab = { id, ctx -> viewModel.closeTab(id, ctx) },
                onOpenNewTab = { inPrivate ->
                    viewModel.openNewTab(isPrivate = inPrivate)
                    webViewRef?.loadUrl("https://www.webtoons.com/en/")
                },
                onRestoreTab = {
                    viewModel.restoreLastClosedTab()
                },
                onTogglePrivate = viewModel::togglePrivateMode,
                onCloseAllPrivate = viewModel::closeAllPrivateTabs,
                onDismiss = { viewModel.setShowTabSwitcher(false) }
            )
        }

        // Ad Shield Details Sheet with per-site controls
        if (showShieldSheet) {
            AdShieldSheet(
                blockedCount = blockedAds,
                trackersBlockedCount = trackersBlocked,
                currentHost = currentHost,
                isSiteFilteringEnabled = isSiteFilteringEnabled,
                isAllowlisted = isAllowlisted,
                onToggleSiteFiltering = { enabled ->
                    currentHost?.let { viewModel.toggleSiteFiltering(it, enabled) }
                },
                onToggleAllowlist = {
                    currentHost?.let { viewModel.toggleAllowlist(it) }
                },
                onDismiss = { viewModel.setShowShieldSheet(false) }
            )
        }

        // History / Bookmarks Sheet
        if (showBookmarksSheet || showHistorySheet) {
            HistoryBookmarksSheet(
                initialTab = if (showBookmarksSheet) 0 else 1,
                bookmarks = bookmarksList,
                history = historyList,
                onSelectUrl = { selectedUrl ->
                    viewModel.submitAddress(selectedUrl)
                    webViewRef?.loadUrl(selectedUrl)
                },
                onDeleteBookmark = { id ->
                    CoroutineScope(Dispatchers.IO).launch { app.browserRepository.deleteBookmark(id) }
                },
                onDeleteHistoryItem = { id ->
                    CoroutineScope(Dispatchers.IO).launch { app.browserRepository.deleteHistoryItem(id) }
                },
                onClearHistory = {
                    CoroutineScope(Dispatchers.IO).launch { app.browserRepository.clearHistory() }
                },
                onDismiss = {
                    viewModel.setShowBookmarksSheet(false)
                    viewModel.setShowHistorySheet(false)
                }
            )
        }
    }
}
