package com.example.engine.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A download request is held only in memory; headers are never included in saved history. */
data class BrowserDownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val contentLength: Long,
    val cookieHeader: String? = null
)

enum class BrowserDownloadStatus {
    PENDING, RUNNING, PAUSED, SUCCESSFUL, FAILED, CANCELLED
}

data class BrowserDownloadRecord(
    val id: Long,
    val fileName: String,
    val url: String,
    val mimeType: String?,
    val totalBytes: Long,
    val downloadedBytes: Long = 0L,
    val status: BrowserDownloadStatus = BrowserDownloadStatus.PENDING,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val localUri: String? = null
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0

    val isActive: Boolean
        get() = status in setOf(BrowserDownloadStatus.PENDING, BrowserDownloadStatus.RUNNING, BrowserDownloadStatus.PAUSED)
}

/**
 * Uses Android DownloadManager with an app-specific Downloads destination. Normal history is
 * persisted in its own preference key; private-session records are memory-only and can be purged
 * without deleting the files themselves.
 */
class BrowserDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val systemManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    private val historyPreferences = appContext.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)

    private val _normalDownloads = MutableStateFlow(loadNormalHistory())
    val normalDownloads: StateFlow<List<BrowserDownloadRecord>> = _normalDownloads.asStateFlow()

    private val _privateDownloads = MutableStateFlow<List<BrowserDownloadRecord>>(emptyList())
    val privateDownloads: StateFlow<List<BrowserDownloadRecord>> = _privateDownloads.asStateFlow()

    fun enqueue(request: BrowserDownloadRequest, isPrivate: Boolean): Result<BrowserDownloadRecord> {
        val parsed = try {
            Uri.parse(request.url)
        } catch (_: Exception) {
            return Result.failure(IllegalArgumentException("Invalid download URL"))
        }
        if (parsed.scheme?.lowercase() !in setOf("http", "https") || parsed.host.isNullOrBlank() || parsed.userInfo != null) {
            return Result.failure(IllegalArgumentException("Only credential-free HTTP and HTTPS downloads are supported"))
        }
        val manager = systemManager
            ?: return Result.failure(IllegalStateException("Android DownloadManager is unavailable"))
        val destination = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: return Result.failure(IllegalStateException("App download storage is unavailable"))

        return try {
            if (!destination.exists() && !destination.mkdirs()) {
                throw IllegalStateException("Could not prepare the app Downloads folder")
            }
            val guessed = URLUtil.guessFileName(request.url, request.contentDisposition, request.mimeType)
            val fileName = uniqueFileName(guessed, destination, reservedNames())
            val safeMimeType = request.mimeType?.trim()?.takeIf(::isValidMimeType)
            val downloadRequest = DownloadManager.Request(parsed).apply {
                safeMimeType?.let { setMimeType(it) }
                request.userAgent?.takeIf { isSafeHeaderValue(it, 512) }?.let { addRequestHeader("User-Agent", it) }
                request.cookieHeader?.takeIf { isSafeHeaderValue(it, 8192) }?.let { addRequestHeader("Cookie", it) }
                setTitle(fileName)
                setDescription("Downloading via MangaFlow")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }
            val id = manager.enqueue(downloadRequest)
            val record = BrowserDownloadRecord(
                id = id,
                fileName = fileName,
                url = request.url,
                mimeType = safeMimeType,
                totalBytes = request.contentLength,
                status = BrowserDownloadStatus.PENDING
            )
            if (isPrivate) {
                _privateDownloads.value = listOf(record) + _privateDownloads.value
            } else {
                _normalDownloads.value = upsert(_normalDownloads.value, record)
                persistNormalHistory()
            }
            Result.success(record)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refresh(isPrivate: Boolean) = withContext(Dispatchers.IO) {
        val manager = systemManager ?: return@withContext
        val source = if (isPrivate) _privateDownloads.value else _normalDownloads.value
        val updated = source.map { item -> queryRecord(manager, item) }
        if (isPrivate) {
            _privateDownloads.value = updated
        } else {
            _normalDownloads.value = updated
            persistNormalHistory()
        }
    }

    fun cancel(id: Long, isPrivate: Boolean): Boolean {
        val manager = systemManager ?: return false
        val source = if (isPrivate) _privateDownloads.value else _normalDownloads.value
        if (source.none { it.id == id }) return false
        val removed = try { manager.remove(id) } catch (_: Exception) { 0 }
        if (removed > 0) {
            updateRecord(id, isPrivate) { it.copy(status = BrowserDownloadStatus.CANCELLED, errorMessage = null) }
        }
        return removed > 0
    }

    fun open(id: Long, isPrivate: Boolean): Result<Unit> {
        val manager = systemManager
            ?: return Result.failure(IllegalStateException("Android DownloadManager is unavailable"))
        val record = (if (isPrivate) _privateDownloads.value else _normalDownloads.value).firstOrNull { it.id == id }
            ?: return Result.failure(IllegalStateException("Download is no longer available in this session"))
        if (record.status != BrowserDownloadStatus.SUCCESSFUL) {
            return Result.failure(IllegalStateException("This download is not complete yet"))
        }
        return try {
            val uri = manager.getUriForDownloadedFile(id)
                ?: return Result.failure(IllegalStateException("The completed file could not be opened"))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, record.mimeType?.takeIf(::isValidMimeType) ?: "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Remove private download metadata when the private tab group ends. Downloaded files remain. */
    fun clearPrivateSessionMetadata() {
        _privateDownloads.value = emptyList()
    }

    private fun queryRecord(manager: DownloadManager, record: BrowserDownloadRecord): BrowserDownloadRecord {
        return try {
            val cursor = manager.query(DownloadManager.Query().setFilterById(record.id)) ?: return record
            cursor.use { c ->
                if (!c.moveToFirst()) return record.copy(status = BrowserDownloadStatus.FAILED, errorMessage = "Download record is unavailable")
                val statusColumn = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val reasonColumn = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                val downloadedColumn = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalColumn = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val localUriColumn = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val bytes = if (downloadedColumn >= 0) c.getLong(downloadedColumn).coerceAtLeast(0L) else record.downloadedBytes
                val queriedTotal = if (totalColumn >= 0) c.getLong(totalColumn) else -1L
                val status = if (statusColumn >= 0) c.getInt(statusColumn) else -1
                val reason = if (reasonColumn >= 0) c.getInt(reasonColumn) else 0
                val uri = if (localUriColumn >= 0 && !c.isNull(localUriColumn)) c.getString(localUriColumn) else record.localUri
                when (status) {
                    DownloadManager.STATUS_PENDING -> record.copy(downloadedBytes = bytes, totalBytes = queriedTotal.takeIf { it > 0 } ?: record.totalBytes, status = BrowserDownloadStatus.PENDING)
                    DownloadManager.STATUS_RUNNING -> record.copy(downloadedBytes = bytes, totalBytes = queriedTotal.takeIf { it > 0 } ?: record.totalBytes, status = BrowserDownloadStatus.RUNNING)
                    DownloadManager.STATUS_PAUSED -> record.copy(downloadedBytes = bytes, totalBytes = queriedTotal.takeIf { it > 0 } ?: record.totalBytes, status = BrowserDownloadStatus.PAUSED, errorMessage = pauseReason(reason))
                    DownloadManager.STATUS_SUCCESSFUL -> record.copy(downloadedBytes = bytes, totalBytes = queriedTotal.takeIf { it > 0 } ?: record.totalBytes, status = BrowserDownloadStatus.SUCCESSFUL, errorMessage = null, localUri = uri)
                    DownloadManager.STATUS_FAILED -> record.copy(downloadedBytes = bytes, totalBytes = queriedTotal.takeIf { it > 0 } ?: record.totalBytes, status = BrowserDownloadStatus.FAILED, errorMessage = errorReason(reason))
                    else -> record
                }
            }
        } catch (e: Exception) {
            record.copy(errorMessage = e.message ?: "Unable to read download status")
        }
    }

    private fun updateRecord(id: Long, isPrivate: Boolean, transform: (BrowserDownloadRecord) -> BrowserDownloadRecord) {
        if (isPrivate) {
            _privateDownloads.value = _privateDownloads.value.map { if (it.id == id) transform(it) else it }
        } else {
            _normalDownloads.value = _normalDownloads.value.map { if (it.id == id) transform(it) else it }
            persistNormalHistory()
        }
    }

    private fun upsert(items: List<BrowserDownloadRecord>, record: BrowserDownloadRecord): List<BrowserDownloadRecord> =
        (listOf(record) + items.filterNot { it.id == record.id }).take(MAX_HISTORY_ITEMS)

    private fun reservedNames(): Set<String> =
        (_normalDownloads.value + _privateDownloads.value).mapTo(mutableSetOf()) { it.fileName.lowercase() }

    private fun persistNormalHistory() {
        val array = JSONArray()
        _normalDownloads.value.take(MAX_HISTORY_ITEMS).forEach { record ->
            array.put(JSONObject().apply {
                put("id", record.id)
                put("fileName", record.fileName)
                put("url", record.url)
                put("mimeType", record.mimeType ?: JSONObject.NULL)
                put("totalBytes", record.totalBytes)
                put("downloadedBytes", record.downloadedBytes)
                put("status", record.status.name)
                put("errorMessage", record.errorMessage ?: JSONObject.NULL)
                put("createdAt", record.createdAt)
                put("localUri", record.localUri ?: JSONObject.NULL)
            })
        }
        historyPreferences.edit().putString(KEY_NORMAL_HISTORY, array.toString()).apply()
    }

    private fun loadNormalHistory(): List<BrowserDownloadRecord> = try {
        val json = historyPreferences.getString(KEY_NORMAL_HISTORY, "[]") ?: "[]"
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val status = try { BrowserDownloadStatus.valueOf(item.optString("status")) } catch (_: Exception) { BrowserDownloadStatus.FAILED }
            BrowserDownloadRecord(
                id = item.optLong("id"),
                fileName = item.optString("fileName"),
                url = item.optString("url"),
                mimeType = item.optString("mimeType").takeUnless { it == "null" || it.isBlank() },
                totalBytes = item.optLong("totalBytes"),
                downloadedBytes = item.optLong("downloadedBytes"),
                status = status,
                errorMessage = item.optString("errorMessage").takeUnless { it == "null" || it.isBlank() },
                createdAt = item.optLong("createdAt"),
                localUri = item.optString("localUri").takeUnless { it == "null" || it.isBlank() }
            )
        }.take(MAX_HISTORY_ITEMS)
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        private const val HISTORY_PREFS = "browser_download_history"
        private const val KEY_NORMAL_HISTORY = "normal_downloads_v1"
        private const val MAX_HISTORY_ITEMS = 100
        private val MIME_TYPE_PATTERN = Regex("^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+$")

        private fun isValidMimeType(value: String): Boolean = MIME_TYPE_PATTERN.matches(value)

        private fun isSafeHeaderValue(value: String, maxLength: Int): Boolean =
            value.isNotBlank() && value.length <= maxLength &&
                '\r' !in value && '\n' !in value && '\u0000' !in value

        fun sanitizeFileName(name: String): String {
            val leaf = File(name).name
                .replace(Regex("[\\u0000-\\u001F<>:\"/\\\\|?*]"), "_")
                .trim()
                .trim('.')
            return leaf.take(180).ifBlank { "download" }
        }

        fun uniqueFileName(candidate: String, directory: File, reservedNames: Set<String> = emptySet()): String {
            val safeName = sanitizeFileName(candidate)
            val extension = safeName.substringAfterLast('.', "").takeIf { it.isNotBlank() && safeName.lastIndexOf('.') > 0 }?.let { ".$it" } ?: ""
            val base = if (extension.isNotEmpty()) safeName.dropLast(extension.length) else safeName
            var index = 0
            while (true) {
                val suffix = if (index == 0) "" else " ($index)"
                val name = (base.take((180 - extension.length - suffix.length).coerceAtLeast(1)) + suffix + extension)
                if (!File(directory, name).exists() && name.lowercase() !in reservedNames) return name
                index++
            }
        }

        private fun pauseReason(reason: Int): String? = when (reason) {
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for network"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "Waiting to retry"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
            DownloadManager.PAUSED_UNKNOWN -> "Download paused"
            else -> null
        }

        private fun errorReason(reason: Int): String = when (reason) {
            DownloadManager.ERROR_FILE_ERROR -> "Storage error"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The server rejected the download"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network data error"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage is unavailable"
            DownloadManager.ERROR_CANNOT_RESUME -> "The download cannot be resumed"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "A file with this name already exists"
            else -> "Download failed (code $reason)"
        }
    }
}
