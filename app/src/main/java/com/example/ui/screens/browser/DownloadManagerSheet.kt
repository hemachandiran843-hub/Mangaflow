package com.example.ui.screens.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.browser.BrowserDownloadRecord
import com.example.engine.browser.BrowserDownloadStatus
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerSheet(
    downloads: List<BrowserDownloadRecord>,
    isPrivate: Boolean,
    onCancel: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = if (isPrivate) "Private downloads" else "Downloads",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (isPrivate) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "MangaFlow's private download details disappear when private tabs close. The file remains in the app Downloads folder. Android DownloadManager receives the URL and any session cookie used and may retain its own notification/history record.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Files are saved in MangaFlow's app-specific Downloads folder. They remain until you delete them or uninstall the app; no storage permission is needed.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (downloads.isEmpty()) {
                Text("No downloads in this browsing session.", color = TextSecondary, modifier = Modifier.padding(bottom = 24.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                    items(downloads, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            onCancel = { onCancel(item.id) },
                            onOpen = { onOpen(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: BrowserDownloadRecord,
    onCancel: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                item.fileName,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.mimeType?.takeIf { it.isNotBlank() } ?: item.fileName.substringAfterLast('.', "Unknown file type").uppercase(),
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(statusText(item), color = TextSecondary, fontSize = 11.sp)
            if (item.isActive) {
                Spacer(modifier = Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = if (item.totalBytes > 0) item.progressPercent / 100f else 0.05f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item.errorMessage?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (item.isActive) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
                if (item.status == BrowserDownloadStatus.SUCCESSFUL) {
                    Button(onClick = onOpen) { Text("Open") }
                }
            }
        }
    }
}

private fun statusText(item: BrowserDownloadRecord): String = when (item.status) {
    BrowserDownloadStatus.PENDING -> "Waiting to start"
    BrowserDownloadStatus.RUNNING -> if (item.totalBytes > 0) "${item.progressPercent}% • ${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}" else "Downloading • ${formatBytes(item.downloadedBytes)}"
    BrowserDownloadStatus.PAUSED -> "Paused${item.errorMessage?.let { " • $it" }.orEmpty()}"
    BrowserDownloadStatus.SUCCESSFUL -> "Download complete • ${formatBytes(item.totalBytes.takeIf { it > 0 } ?: item.downloadedBytes)}"
    BrowserDownloadStatus.FAILED -> "Failed"
    BrowserDownloadStatus.CANCELLED -> "Cancelled"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${"%.1f".format(kb)} KB"
    return "${"%.1f".format(kb / 1024)} MB"
}
