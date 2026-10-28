package com.example.engine.browser

import android.webkit.DownloadListener

/** Adapts WebView download callbacks to the browser's normal/private download flows. */
class BrowserDownloadHandler(
    private val manager: BrowserDownloadManager,
    private val isPrivate: () -> Boolean,
    private val cookieHeaderForUrl: (String) -> String?,
    private val onPrivateDownloadConfirmation: (BrowserDownloadRequest) -> Unit,
    private val onStatus: (String) -> Unit
) : DownloadListener {

    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long
    ) {
        if (url.isNullOrBlank()) {
            onStatus("Download not started: the page did not provide a URL")
            return
        }

        val request = BrowserDownloadRequest(
            url = url,
            userAgent = userAgent,
            contentDisposition = contentDisposition,
            mimeType = mimetype,
            contentLength = contentLength,
            cookieHeader = try { cookieHeaderForUrl(url) } catch (_: Exception) { null }
        )
        if (isPrivate()) {
            // The caller must explain OS-level download notifications/history and ask before enqueue.
            onPrivateDownloadConfirmation(request)
            return
        }

        manager.enqueue(request, isPrivate = false)
            .onSuccess { onStatus("Download started: ${it.fileName}") }
            .onFailure { onStatus("Download failed to start: ${it.message ?: "Unknown error"}") }
    }
}
