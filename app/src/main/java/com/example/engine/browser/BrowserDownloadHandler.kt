package com.example.engine.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.DownloadListener
import android.webkit.URLUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class BrowserDownloadHandler(
    private val context: Context,
    private val onDownloadStarted: ((fileName: String) -> Unit)? = null
) : DownloadListener {

    private val _lastDownloadMessage = MutableStateFlow<String?>(null)
    val lastDownloadMessage: StateFlow<String?> = _lastDownloadMessage.asStateFlow()

    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long
    ) {
        if (url.isNullOrBlank()) return

        // Validate scheme
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            _lastDownloadMessage.value = "Cannot download: Unsupported URL scheme"
            return
        }

        try {
            val rawFileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            // Sanitize filename to avoid directory traversal
            val cleanFileName = sanitizeFileName(rawFileName)

            val downloadUri = Uri.parse(url)
            val request = DownloadManager.Request(downloadUri).apply {
                setMimeType(mimetype)
                addRequestHeader("User-Agent", userAgent)
                setTitle(cleanFileName)
                setDescription("Downloading $cleanFileName via MangaFlow")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, cleanFileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager != null) {
                downloadManager.enqueue(request)
                val msg = "Download started: $cleanFileName"
                _lastDownloadMessage.value = msg
                onDownloadStarted?.invoke(cleanFileName)
            } else {
                _lastDownloadMessage.value = "Download manager not available"
            }
        } catch (e: Exception) {
            _lastDownloadMessage.value = "Failed to start download: ${e.message ?: "Unknown error"}"
        }
    }

    private fun sanitizeFileName(name: String): String {
        return File(name).name
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "download_${System.currentTimeMillis()}" }
    }
}
