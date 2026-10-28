package com.example.ui.screens.manhwa_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.MangaFlowApp
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.ManhwaEntity
import com.example.ui.theme.MangaGold
import com.example.ui.theme.MangaPrimary
import com.example.ui.theme.MangaSecondary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManhwaDetailScreen(
    manhwaId: Long,
    onBack: () -> Unit,
    onReadChapter: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = MangaFlowApp.instance
    val repo = app.manhwaRepository
    val scope = rememberCoroutineScope()

    val manhwa by repo.getManhwaById(manhwaId).collectAsState(initial = null)
    val chapters by repo.getChapters(manhwaId).collectAsState(initial = emptyList())

    var showStatusMenu by remember { mutableStateOf(false) }
    var notesText by remember { mutableStateOf("") }
    var isCheckingNow by remember { mutableStateOf(false) }
    var checkFeedback by remember { mutableStateOf<String?>(null) }
    var manualChapterIdentifier by remember(manhwaId) { mutableStateOf("") }
    var manualChapterTitle by remember(manhwaId) { mutableStateOf("") }
    var manualChapterUrl by remember(manhwaId) { mutableStateOf("") }
    var isSavingManualProgress by remember { mutableStateOf(false) }
    var manualProgressFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(manhwa) {
        notesText = manhwa?.notes ?: ""
    }

    val currentManhwa = manhwa
    val sourceAdapter = currentManhwa?.let { app.sourceManager.adapterForUrl(it.sourceUrl) }
    val automaticTrackingAvailable = sourceAdapter?.capabilities?.let {
        it.supportsChapterList && it.supportsUpdateDetection
    } == true

    if (currentManhwa == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading manhwa details...", color = TextSecondary)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                text = currentManhwa.title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Card with Cover and Status
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 90.dp, height = 130.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            if (!currentManhwa.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = currentManhwa.coverUrl,
                                    contentDescription = currentManhwa.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(imageVector = Icons.Default.AutoStories, contentDescription = null, tint = TextSecondary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentManhwa.title,
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Source: ${currentManhwa.sourceName}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Status dropdown trigger
                            Box {
                                Surface(
                                    color = MangaPrimary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { showStatusMenu = true }
                                ) {
                                    Text(
                                        text = "Status: ${currentManhwa.readingStatus}",
                                        color = MangaPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showStatusMenu,
                                    onDismissRequest = { showStatusMenu = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    listOf("READING", "PLAN_TO_READ", "COMPLETED", "ON_HOLD", "DROPPED").forEach { st ->
                                        DropdownMenuItem(
                                            text = { Text(st.replace("_", " "), color = TextPrimary) },
                                            onClick = {
                                                showStatusMenu = false
                                                scope.launch { repo.updateStatus(currentManhwa.id, st) }
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Continue Reading button
                            Button(
                                onClick = { onReadChapter(currentManhwa.sourceUrl) },
                                colors = ButtonDefaults.buttonColors(containerColor = MangaPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Read in Browser", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Notes Section
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Personal Notes", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = notesText,
                            onValueChange = {
                                notesText = it
                                scope.launch { repo.updateRatingAndNotes(currentManhwa.id, currentManhwa.rating, it) }
                            },
                            placeholder = { Text("Add reading thoughts or reminder...", color = TextSecondary, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MangaPrimary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Source Monitoring & Updates Section
            item {
                val adapter = sourceAdapter

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Chapter Monitoring", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Adapter: ${adapter?.displayName ?: "Unsupported source"}",
                                    color = MangaSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isCheckingNow = true
                                        checkFeedback = null
                                        val res = app.chapterDetectionService.checkSeries(currentManhwa.id, force = true)
                                        isCheckingNow = false
                                        checkFeedback = when (res.status) {
                                            "SUCCESS" -> "Found ${res.newChaptersCount} new chapter(s)!"
                                            "NO_NEW_CHAPTERS" -> "Already up to date."
                                            "RATE_LIMITED" -> "Rate limited: ${res.errorMessage}"
                                            "NETWORK_ERROR" -> "Network error: ${res.errorMessage}"
                                            else -> "Status: ${res.status}"
                                        }
                                    }
                                },
                                enabled = !isCheckingNow && automaticTrackingAvailable,
                                colors = ButtonDefaults.buttonColors(containerColor = MangaPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                if (isCheckingNow) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Check Now", fontSize = 11.sp)
                                }
                            }
                        }

                        if (!automaticTrackingAvailable) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "This source has no verified automatic release adapter. Browsing still works; use manual chapter progress below.",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }

                        if (checkFeedback != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = checkFeedback ?: "",
                                color = if (checkFeedback?.contains("Found") == true) MangaSecondary else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Monitoring toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-detect new releases", color = TextPrimary, fontSize = 12.sp)
                                Text("Check source during periodic scans", color = TextSecondary, fontSize = 10.sp)
                            }
                            Switch(
                                checked = currentManhwa.isMonitoringEnabled,
                                enabled = automaticTrackingAvailable,
                                onCheckedChange = { isEnabled ->
                                    scope.launch {
                                        repo.updateMonitoringSettings(
                                            id = currentManhwa.id,
                                            isMonitoringEnabled = isEnabled,
                                            notificationsEnabled = currentManhwa.notificationsEnabled
                                        )
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                                    checkedTrackColor = MangaPrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Notification toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Release notifications", color = TextPrimary, fontSize = 12.sp)
                                Text("Alert in system tray when chapter drops", color = TextSecondary, fontSize = 10.sp)
                            }
                            Switch(
                                checked = currentManhwa.notificationsEnabled,
                                onCheckedChange = { isEnabled ->
                                    scope.launch {
                                        repo.updateMonitoringSettings(
                                            id = currentManhwa.id,
                                            isMonitoringEnabled = currentManhwa.isMonitoringEnabled,
                                            notificationsEnabled = isEnabled
                                        )
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                                    checkedTrackColor = MangaSecondary
                                )
                            )
                        }

                        // Last checked status
                        if (currentManhwa.lastCheckStatus != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Last Check: ${currentManhwa.lastCheckStatus}" +
                                        (if (currentManhwa.lastCheckError != null) " (${currentManhwa.lastCheckError})" else ""),
                                color = if (currentManhwa.lastCheckStatus in listOf("SUCCESS", "NO_NEW_CHAPTERS")) TextSecondary else MaterialTheme.colorScheme.error,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Manual chapter progress", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Use this for unsupported sites, or when you want to explicitly mark a chapter read. This does not claim the source has a verified release feed.",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualChapterIdentifier,
                            onValueChange = { manualChapterIdentifier = it },
                            label = { Text("Chapter number or ID *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = manualChapterTitle,
                            onValueChange = { manualChapterTitle = it },
                            label = { Text("Title (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = manualChapterUrl,
                            onValueChange = { manualChapterUrl = it },
                            label = { Text("Chapter URL (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isSavingManualProgress = true
                                    manualProgressFeedback = null
                                    try {
                                        app.trackerService.markManualChapterRead(
                                            seriesId = currentManhwa.id,
                                            identifier = manualChapterIdentifier,
                                            title = manualChapterTitle,
                                            chapterUrl = manualChapterUrl.takeIf { it.isNotBlank() }
                                        )
                                        manualProgressFeedback = "Chapter marked read."
                                        manualChapterIdentifier = ""
                                        manualChapterTitle = ""
                                        manualChapterUrl = ""
                                    } catch (error: Exception) {
                                        manualProgressFeedback = error.message ?: "Could not save manual progress."
                                    } finally {
                                        isSavingManualProgress = false
                                    }
                                }
                            },
                            enabled = manualChapterIdentifier.isNotBlank() && !isSavingManualProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = MangaPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isSavingManualProgress) "Saving…" else "Mark chapter read", fontSize = 12.sp)
                        }
                        manualProgressFeedback?.let { feedback ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                feedback,
                                color = if (feedback == "Chapter marked read.") MangaSecondary else MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Chapter Checklist Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chapters Progress (${chapters.count { it.isRead }} / ${currentManhwa.totalChapters})",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Chapters List
            items(chapters, key = { it.id }) { chapter ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReadChapter(chapter.chapterUrl.ifBlank { currentManhwa.sourceUrl }) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = chapter.isRead,
                            onCheckedChange = { isChecked ->
                                scope.launch {
                                    repo.markChapterRead(currentManhwa.id, chapter.id, isChecked)
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MangaSecondary,
                                uncheckedColor = TextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val chLabel = if (chapter.chapterNumber != null) {
                                "Chapter ${chapter.chapterNumber.toInt()}"
                            } else {
                                chapter.chapterIdentifier
                            }
                            Text(
                                text = chLabel,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (chapter.chapterTitle.isNotBlank()) {
                                Text(
                                    text = chapter.chapterTitle,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (chapter.isRead) {
                            Text("Read", color = MangaSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
