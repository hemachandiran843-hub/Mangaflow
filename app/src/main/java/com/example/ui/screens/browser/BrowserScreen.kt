package com.example.ui.screens.browser

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
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
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.engine.browser.BrowserDownloadRequest
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private data class PendingWebsitePermission(
    val origin: String,
    val displayResources: String,
    val androidPermissions: Array<String>,
    val webRequest: PermissionRequest? = null,
    val webResources: List<String> = emptyList(),
    val geolocationCallback: GeolocationPermissions.Callback? = null
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    val detectedSeriesMatch by viewModel.detectedSeriesMatch.collectAsState()
    val detectedUnreadCount by viewModel.detectedUnreadCount.collectAsState()
    val browserPreferences by viewModel.browserPreferences.collectAsState()
    val readerArticle by viewModel.readerArticle.collectAsState()
    val readerError by viewModel.readerError.collectAsState()
    val webViewGeneration by viewModel.webViewGeneration.collectAsState()
    val privateSessionGeneration by viewModel.privateSessionGeneration.collectAsState()
    val normalDownloads by app.browserDownloadManager.normalDownloads.collectAsState()
    val privateDownloads by app.browserDownloadManager.privateDownloads.collectAsState()

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
    var browserLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var defaultUserAgent by remember { mutableStateOf<String?>(null) }
    var showDownloads by remember { mutableStateOf(false) }
    var unavailablePrivateProfileName by remember { mutableStateOf<String?>(null) }
    var pendingPrivateDownload by remember { mutableStateOf<BrowserDownloadRequest?>(null) }
    var detectedProgressConfirmation by remember { mutableStateOf<com.example.domain.model.DetectedManhwa?>(null) }
    var pendingWebsitePermission by remember { mutableStateOf<PendingWebsitePermission?>(null) }
    val latestBrowserPreferences = rememberUpdatedState(browserPreferences)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val pending = pendingWebsitePermission
        if (pending != null) {
            val allGranted = pending.androidPermissions.all {
                grants[it] == true || context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                try {
                    pending.webRequest?.grant(pending.webResources.toTypedArray())
                    pending.geolocationCallback?.invoke(pending.origin, true, false)
                } catch (_: Exception) {
                    pending.webRequest?.deny()
                    pending.geolocationCallback?.invoke(pending.origin, false, false)
                }
            } else {
                pending.webRequest?.deny()
                pending.geolocationCallback?.invoke(pending.origin, false, false)
            }
            pendingWebsitePermission = null
        }
    }

    LaunchedEffect(readerError) {
        readerError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.dismissReaderError()
        }
    }

    LaunchedEffect(isPrivate) {
        pendingPrivateDownload = null
        pendingWebsitePermission?.let { pending ->
            pending.webRequest?.deny()
            pending.geolocationCallback?.invoke(pending.origin, false, false)
            pendingWebsitePermission = null
        }
        if (isPrivate && !app.privacyManager.supportsIsolatedPrivateProfile()) {
            Toast.makeText(context, "This WebView version lacks isolated private storage; see the Private Browsing notice in the tab switcher.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(showDownloads, isPrivate) {
        if (showDownloads) {
            while (isActive) {
                app.browserDownloadManager.refresh(isPrivate)
                delay(900)
            }
        }
    }

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
    val activePrivateProfileName = app.privacyManager.getPrivateProfileName()
    val privateProfileUnavailable = isPrivate && (
        !app.privacyManager.supportsIsolatedPrivateProfile() || unavailablePrivateProfileName == activePrivateProfileName
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    browserLifecycleResumed = false
                    app.readingSessionTracker.stop()
                    webViewRef?.onPause()
                    webViewRef?.pauseTimers()
                }
                Lifecycle.Event.ON_RESUME -> {
                    browserLifecycleResumed = true
                    webViewRef?.onResume()
                    webViewRef?.resumeTimers()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            browserLifecycleResumed = true
            webViewRef?.onResume()
            webViewRef?.resumeTimers()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            app.readingSessionTracker.stop()
            webViewRef?.onPause()
            webViewRef?.pauseTimers()
        }
    }

    LaunchedEffect(
        currentTab?.id,
        currentTab?.url,
        currentTab?.isLoading,
        currentTab?.isReaderModeActive,
        isPrivate,
        detectedManhwa?.sourceId,
        detectedManhwa?.chapterIdentifier,
        detectedManhwa?.chapterUrl,
        detectedSeriesMatch?.id,
        browserLifecycleResumed,
        showTabSwitcher,
        showDownloads,
        readerArticle,
        webViewRef != null
    ) {
        val tab = currentTab
        val detected = detectedManhwa
        val matchedSeries = detectedSeriesMatch
        val eligible = browserLifecycleResumed &&
            !isPrivate &&
            !showTabSwitcher &&
            !showDownloads &&
            readerArticle == null &&
            tab != null &&
            !tab.isLoading &&
            !tab.isReaderModeActive &&
            !tab.url.isNullOrBlank() &&
            detected != null &&
            matchedSeries != null &&
            app.browserTrackerBridge.isReadingPage(tab.url)
        app.readingSessionTracker.start(
            seriesId = matchedSeries?.id ?: 0L,
            chapterId = null,
            eligible = eligible,
            privateSession = isPrivate,
            chapterIdentity = detected?.chapterUrl ?: detected?.chapterIdentifier
        )
    }

    // Hardware back navigation precedence
    BackHandler(enabled = true) {
        when {
            readerArticle != null -> viewModel.closeReaderMode()
            showDownloads -> showDownloads = false
            pendingWebsitePermission != null -> {
                pendingWebsitePermission?.webRequest?.deny()
                pendingWebsitePermission?.geolocationCallback?.invoke(pendingWebsitePermission?.origin.orEmpty(), false, false)
                pendingWebsitePermission = null
            }
            pendingPrivateDownload != null -> pendingPrivateDownload = null
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

    LaunchedEffect(currentTab?.isDesktopMode, browserPreferences.textZoomPercent, webViewRef) {
        val isDesktop = currentTab?.isDesktopMode ?: false
        webViewRef?.settings?.apply {
            userAgentString = if (isDesktop) BrowserSessionManager.DESKTOP_USER_AGENT else (defaultUserAgent ?: WebSettings.getDefaultUserAgent(context))
            useWideViewPort = isDesktop
            loadWithOverviewMode = isDesktop
            textZoom = browserPreferences.textZoomPercent
        }
    }

    LaunchedEffect(browserPreferences.acceptCookies, browserPreferences.acceptThirdPartyCookies, webViewRef, isPrivate) {
        val activeView = webViewRef ?: return@LaunchedEffect
        try {
            val cookies = if (isPrivate) {
                app.privacyManager.getPrivateCookieManager(activeView, app.privacyManager.getPrivateProfileName())
                    ?: return@LaunchedEffect
            } else CookieManager.getInstance()
            cookies.setAcceptCookie(browserPreferences.acceptCookies)
            cookies.setAcceptThirdPartyCookies(activeView, browserPreferences.acceptThirdPartyCookies)
        } catch (_: Exception) {
            if (!isPrivate) CookieManager.getInstance().setAcceptCookie(browserPreferences.acceptCookies)
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
                    if (privateProfileUnavailable) {
                        Toast.makeText(context, "Private browsing is unavailable because this WebView cannot isolate site storage.", Toast.LENGTH_LONG).show()
                    } else {
                        val submittedUrl = viewModel.submitAddress(input)
                        webViewRef?.loadUrl(submittedUrl)
                    }
                },
                onReload = {
                    if (!privateProfileUnavailable) {
                        viewModel.reload()
                        webViewRef?.reload()
                    }
                },
                onStopLoading = {
                    viewModel.stopLoading()
                    webViewRef?.stopLoading()
                },
                onToggleReaderMode = {
                    if (privateProfileUnavailable) {
                        Toast.makeText(context, "Private browsing is unavailable on this WebView provider.", Toast.LENGTH_LONG).show()
                    } else if (readerArticle != null || currentTab?.isReaderModeActive == true) {
                        viewModel.closeReaderMode()
                    } else {
                        val activeView = webViewRef
                        if (activeView == null) {
                            Toast.makeText(context, "Page is not ready for Reader Mode.", Toast.LENGTH_SHORT).show()
                        } else {
                            activeView.evaluateJavascript(ReaderModeExtractor.getReaderModeScript()) { result ->
                                viewModel.setReaderExtractionResult(ReaderModeExtractor.parseJavascriptResult(result))
                            }
                        }
                    }
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
                // One active WebView is reused. A mode-key recreates it so private tabs can be
                // assigned to a separate WebView profile before the first navigation.
                key(isPrivate, webViewGeneration, privateSessionGeneration) {
                    val privateProfileName = app.privacyManager.getPrivateProfileName()
                    AndroidView(
                        factory = { ctx ->
                            val createdView = WebView(ctx)
                            val configuredProfile = app.privacyManager.configureWebViewProfile(createdView, isPrivate, privateProfileName)
                            if (isPrivate) viewModel.onPrivateWebViewCreated(privateProfileName)
                            val privateCookieManager = if (isPrivate && configuredProfile) {
                                app.privacyManager.getPrivateCookieManager(createdView, privateProfileName)
                            } else null
                            val canLoadPage = !isPrivate || (configuredProfile && privateCookieManager != null)
                            if (isPrivate && !canLoadPage) {
                                unavailablePrivateProfileName = privateProfileName
                                viewModel.setTabError("Private browsing is unavailable because this WebView provider cannot isolate site storage.")
                            } else if (isPrivate) {
                                unavailablePrivateProfileName = null
                            }
                            val activeCookieManager = privateCookieManager ?: CookieManager.getInstance()
                            createdView.apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                defaultUserAgent = settings.userAgentString
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    allowFileAccessFromFileURLs = false
                                    allowUniversalAccessFromFileURLs = false
                                    javaScriptCanOpenWindowsAutomatically = false
                                    setSupportMultipleWindows(false)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                                    loadWithOverviewMode = currentTab?.isDesktopMode ?: false
                                    useWideViewPort = currentTab?.isDesktopMode ?: false
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    setSupportZoom(true)
                                    textZoom = browserPreferences.textZoomPercent
                                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    if (isPrivate && !configuredProfile) cacheMode = WebSettings.LOAD_NO_CACHE
                                }
                                if (!isPrivate || privateCookieManager != null) {
                                    activeCookieManager.setAcceptCookie(browserPreferences.acceptCookies)
                                    activeCookieManager.setAcceptThirdPartyCookies(this, browserPreferences.acceptThirdPartyCookies)
                                }

                                setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                                    viewModel.updateFindMatchesCount(activeMatchOrdinal, numberOfMatches)
                                }

                                setDownloadListener(
                                    BrowserDownloadHandler(
                                        manager = app.browserDownloadManager,
                                        isPrivate = { isPrivate },
                                        cookieHeaderForUrl = { url ->
                                            if (isPrivate && privateCookieManager == null) null else activeCookieManager.getCookie(url)
                                        },
                                        onPrivateDownloadConfirmation = { request -> pendingPrivateDownload = request },
                                        onStatus = { message -> Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show() }
                                    )
                                )

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val reqUrl = request?.url?.toString() ?: return false
                                        if (BrowserSecurityManager.isExternalActionScheme(reqUrl)) {
                                            if (latestBrowserPreferences.value.openExternalLinks) {
                                                if (!BrowserSecurityManager.handleExternalNavigation(ctx, reqUrl)) {
                                                    Toast.makeText(ctx, "No app could open this link.", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(ctx, "External link handling is disabled in Browser settings.", Toast.LENGTH_SHORT).show()
                                            }
                                            return true
                                        }
                                        return false
                                    }

                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val reqUrl = request?.url?.toString() ?: return null
                                        val pageHost = currentHost ?: try { request.url?.host } catch (_: Exception) { null }
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
                                        handler?.cancel()
                                        viewModel.setTabError("SSL Certificate Error: The connection to this site is not secure.")
                                    }

                                    override fun onRenderProcessGone(
                                        view: WebView?,
                                        detail: android.webkit.RenderProcessGoneDetail?
                                    ): Boolean {
                                        viewModel.handleRendererCrash()
                                        try {
                                            view?.let { crashedView ->
                                                (crashedView.parent as? ViewGroup)?.removeView(crashedView)
                                                crashedView.stopLoading()
                                                crashedView.destroy()
                                                if (webViewRef === crashedView) webViewRef = null
                                            }
                                        } catch (_: Exception) { }
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
                                        app.readingSessionTracker.stop()
                                        app.browserTrackerBridge.clearDetection()
                                        app.browserSessionManager.updateCurrentTab {
                                            it.copy(
                                                url = url ?: it.url,
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
                                                url = safeUrl.ifBlank { it.url },
                                                title = if (title.isNotBlank()) title else safeUrl,
                                                isLoading = false,
                                                progress = 100,
                                                canGoBack = view?.canGoBack() ?: false,
                                                canGoForward = view?.canGoForward() ?: false
                                            )
                                        }

                                        val cosmeticCss = app.contentFilterEngine.getCosmeticCss()
                                        if (cosmeticCss.isNotBlank()) {
                                            val cssLiteral = org.json.JSONObject.quote(cosmeticCss)
                                            val jsInject = "(function(){var s=document.createElement('style');s.textContent=$cssLiteral;(document.head||document.documentElement).appendChild(s);})();"
                                            view?.evaluateJavascript(jsInject, null)
                                        }

                                        app.browserTrackerBridge.onPageLoaded(safeUrl, title, isPrivate)
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

                                    override fun onPermissionRequest(request: PermissionRequest) {
                                        val allowedResources = request.resources.filter {
                                            it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                        }
                                        if (!latestBrowserPreferences.value.allowWebsitePermissionPrompts || allowedResources.isEmpty()) {
                                            request.deny()
                                            return
                                        }
                                        val androidPermissions = buildList {
                                            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in allowedResources) add(Manifest.permission.RECORD_AUDIO)
                                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in allowedResources) add(Manifest.permission.CAMERA)
                                        }.toTypedArray()
                                        pendingWebsitePermission = PendingWebsitePermission(
                                            origin = request.origin?.toString().orEmpty(),
                                            displayResources = allowedResources.map {
                                                if (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE) "microphone" else "camera"
                                            }.distinct().joinToString(" and "),
                                            androidPermissions = androidPermissions,
                                            webRequest = request,
                                            webResources = allowedResources
                                        )
                                    }

                                    override fun onPermissionRequestCanceled(request: PermissionRequest) {
                                        if (pendingWebsitePermission?.webRequest == request) {
                                            pendingWebsitePermission = null
                                        }
                                    }

                                    override fun onGeolocationPermissionsShowPrompt(
                                        origin: String?,
                                        callback: GeolocationPermissions.Callback?
                                    ) {
                                        val safeOrigin = origin.orEmpty()
                                        if (!latestBrowserPreferences.value.allowWebsitePermissionPrompts || callback == null) {
                                            callback?.invoke(safeOrigin, false, false)
                                            return
                                        }
                                        pendingWebsitePermission = PendingWebsitePermission(
                                            origin = safeOrigin,
                                            displayResources = "approximate location",
                                            androidPermissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                                            geolocationCallback = callback
                                        )
                                    }
                                }

                                if (canLoadPage) {
                                    currentTab?.url?.takeIf { it.isNotBlank() && it != "about:blank" }?.let(::loadUrl)
                                }
                                webViewRef = this
                            }
                        },
                        update = { view ->
                            webViewRef = view
                            view.settings.textZoom = browserPreferences.textZoomPercent
                            val activeUrl = currentTab?.url ?: ""
                            if (isPrivate && unavailablePrivateProfileName == privateProfileName) {
                                view.stopLoading()
                                if (currentTab?.errorMessage?.contains("cannot isolate site storage") != true) {
                                    viewModel.setTabError("Private browsing is unavailable because this WebView provider cannot isolate site storage.")
                                }
                            } else if (activeUrl.isNotBlank() && activeUrl != view.url && !activeUrl.startsWith("about:")) {
                                view.loadUrl(activeUrl)
                            }
                        },
                        onRelease = { releasedView ->
                            if (isPrivate && viewModel.shouldPurgePrivateProfile(privateProfileName)) {
                                app.privacyManager.clearPrivateProfileData(releasedView, privateProfileName)
                            }
                            try {
                                (releasedView.parent as? ViewGroup)?.removeView(releasedView)
                                releasedView.stopLoading()
                                releasedView.webChromeClient = null
                                releasedView.webViewClient = WebViewClient()
                                releasedView.destroy()
                            } catch (_: Exception) { }
                            if (webViewRef === releasedView) webViewRef = null
                            if (app.browserSessionManager.privateTabs.value.isEmpty()) {
                                app.browserDownloadManager.clearPrivateSessionMetadata()
                            }
                            viewModel.onBrowserWebViewReleased(privateProfileName.takeIf { isPrivate })
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

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

                // A detected chapter is only a suggestion; library progress changes require an explicit confirmation.
                if (detectedManhwa != null && !isPrivate && readerArticle == null) {
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
                                            text = "Chapter ${detected.chapterIdentifier} • ${(detected.confidence * 100).toInt()}% match",
                                            color = MangaSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        detectedSeriesMatch?.let {
                                            Text("In library • $detectedUnreadCount unread", color = TextSecondary, fontSize = 10.sp)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            if (detectedSeriesMatch == null) viewModel.saveDetectedToLibrary(detected)
                                            else detectedProgressConfirmation = detected
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MangaSecondary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            if (detectedSeriesMatch == null) "Add to Library" else "Update Progress",
                                            color = Color(0xFF003822),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
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

                readerArticle?.let { article ->
                    ReaderModeOverlay(
                        article = article,
                        onClose = viewModel::closeReaderMode,
                        modifier = Modifier.align(Alignment.Center)
                    )
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
                onOpenDownloads = { showDownloads = true },
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
                    val previousTab = currentTab
                    val activeView = webViewRef
                    if (previousTab != null && activeView != null) {
                        try {
                            val state = Bundle()
                            if (activeView.saveState(state) != null) viewModel.saveWebViewState(previousTab.id, state)
                        } catch (_: Exception) { }
                    }
                    val targetTab = (normalTabs + privateTabs).find { it.id == tabId }
                    viewModel.switchTab(tabId)
                    if (targetTab != null && targetTab.url.isNotBlank() && targetTab.isPrivate == isPrivate && !privateProfileUnavailable) {
                        val restored = viewModel.getWebViewState(tabId)?.let { state ->
                            try { webViewRef?.restoreState(state) != null } catch (_: Exception) { false }
                        } ?: false
                        if (!restored) webViewRef?.loadUrl(targetTab.url)
                    }
                },
                onCloseTab = { id, ctx -> viewModel.closeTab(id, ctx) },
                onOpenNewTab = { inPrivate ->
                    val created = viewModel.openNewTab(isPrivate = inPrivate)
                    if (created != null && created.isPrivate == isPrivate && !privateProfileUnavailable) webViewRef?.loadUrl(created.url)
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
                    if (!privateProfileUnavailable) {
                        val submittedUrl = viewModel.submitAddress(selectedUrl)
                        webViewRef?.loadUrl(submittedUrl)
                    } else {
                        Toast.makeText(context, "Private browsing is unavailable on this WebView provider.", Toast.LENGTH_LONG).show()
                    }
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

        if (showDownloads) {
            DownloadManagerSheet(
                downloads = if (isPrivate) privateDownloads else normalDownloads,
                isPrivate = isPrivate,
                onCancel = { id ->
                    if (!app.browserDownloadManager.cancel(id, isPrivate)) {
                        Toast.makeText(context, "The download could not be cancelled.", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpen = { id ->
                    app.browserDownloadManager.open(id, isPrivate).onFailure {
                        Toast.makeText(context, it.message ?: "Could not open the downloaded file.", Toast.LENGTH_LONG).show()
                    }
                },
                onDismiss = { showDownloads = false }
            )
        }

        pendingPrivateDownload?.let { request ->
            AlertDialog(
                onDismissRequest = { pendingPrivateDownload = null },
                title = { Text("Download from Private Browsing?") },
                text = {
                    Text("The file will remain in MangaFlow's app Downloads folder after private tabs close. Android DownloadManager receives the download URL and any session cookie needed to fetch it, and Android may retain a notification or history record. MangaFlow keeps its own private download details in memory only and clears them when the private session ends. Continue?")
                },
                confirmButton = {
                    Button(onClick = {
                        pendingPrivateDownload = null
                        app.browserDownloadManager.enqueue(request, isPrivate = true)
                            .onSuccess { Toast.makeText(context, "Private download started: ${it.fileName}", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, "Download failed to start: ${it.message}", Toast.LENGTH_LONG).show() }
                    }) { Text("Download") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingPrivateDownload = null }) { Text("Cancel") }
                }
            )
        }

        pendingWebsitePermission?.let { pending ->
            AlertDialog(
                onDismissRequest = {
                    pending.webRequest?.deny()
                    pending.geolocationCallback?.invoke(pending.origin, false, false)
                    pendingWebsitePermission = null
                },
                title = { Text("Allow ${pending.displayResources}?") },
                text = {
                    Text("${pending.origin.ifBlank { "This website" }} requests access to ${pending.displayResources}. MangaFlow will ask Android for the required permission too. No site permission is granted without your confirmation.")
                },
                confirmButton = {
                    Button(onClick = {
                        val notGranted = pending.androidPermissions.filter {
                            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (notGranted.isEmpty()) {
                            try {
                                pending.webRequest?.grant(pending.webResources.toTypedArray())
                                pending.geolocationCallback?.invoke(pending.origin, true, false)
                            } catch (_: Exception) {
                                pending.webRequest?.deny()
                                pending.geolocationCallback?.invoke(pending.origin, false, false)
                            }
                            pendingWebsitePermission = null
                        } else {
                            permissionLauncher.launch(notGranted.toTypedArray())
                        }
                    }) { Text("Allow") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pending.webRequest?.deny()
                        pending.geolocationCallback?.invoke(pending.origin, false, false)
                        pendingWebsitePermission = null
                    }) { Text("Deny") }
                }
            )
        }

        detectedProgressConfirmation?.let { detected ->
            val matchedSeries = detectedSeriesMatch
            AlertDialog(
                onDismissRequest = { detectedProgressConfirmation = null },
                title = { Text("Update reading progress?") },
                text = {
                    Column {
                        Text("Mark ${matchedSeries?.title ?: detected.title} — Chapter ${detected.chapterIdentifier} as read?")
                        Spacer(modifier = Modifier.height(8.dp))
                        if (detected.confidence < 0.75f) {
                            Text("The chapter detection may be inaccurate (${(detected.confidence * 100).toInt()}% match). Check the title and chapter before confirming.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                        Text("This changes library progress only after confirmation and does not invent reading time. Private browsing never updates normal progress.", color = TextSecondary, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (!isPrivate && matchedSeries != null) viewModel.confirmDetectedChapterRead(detected)
                            detectedProgressConfirmation = null
                        },
                        enabled = matchedSeries != null && !isPrivate
                    ) { Text("Mark as read") }
                },
                dismissButton = {
                    TextButton(onClick = { detectedProgressConfirmation = null }) { Text("Cancel") }
                }
            )
        }
    }
}
