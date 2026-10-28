package com.example.domain.model

import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "about:blank",
    val isPrivate: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isReaderModeActive: Boolean = false,
    val blockedAdsCount: Int = 0,
    val activeDetectedManhwa: DetectedManhwa? = null,
    val isDesktopMode: Boolean = false,
    val errorMessage: String? = null,
    val isCrashed: Boolean = false,
    val findQuery: String? = null,
    val findActiveMatch: Int = 0,
    val findTotalMatches: Int = 0
)

data class DetectedManhwa(
    val title: String,
    val chapterNumber: Float?,
    val chapterTitle: String = "",
    val sourceUrl: String,
    val coverUrl: String? = null,
    val chapterIdentifier: String = chapterNumber?.toString()?.removeSuffix(".0") ?: "unknown",
    val chapterUrl: String = sourceUrl,
    val confidence: Float = 0.5f,
    val sourceId: String = "open_web"
)

data class OcrBox(
    val id: String = UUID.randomUUID().toString(),
    val leftPercent: Float,
    val topPercent: Float,
    val widthPercent: Float,
    val heightPercent: Float,
    val originalText: String,
    val translatedText: String,
    val showOriginal: Boolean = false,
    val confidence: Float = 0.95f
)
