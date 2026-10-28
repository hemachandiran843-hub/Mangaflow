package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MangaAmethyst
import com.example.ui.theme.MangaPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BrowserBottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabsCount: Int,
    isPrivate: Boolean,
    isDesktopMode: Boolean = false,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenLibrary: () -> Unit,
    onTriggerOcrTranslation: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    onTogglePrivate: () -> Unit,
    onBookmarkCurrentPage: () -> Unit,
    onToggleDesktopMode: () -> Unit = {},
    onFindInPage: () -> Unit = {},
    isPageTranslated: Boolean = false,
    isPageTranslating: Boolean = false,
    onTranslateWebpage: () -> Unit = {},
    onRestoreOriginalWebpage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back
            IconButton(
                onClick = onBack,
                enabled = canGoBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("nav_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (canGoBack) TextPrimary else TextSecondary.copy(alpha = 0.4f)
                )
            }

            // Forward
            IconButton(
                onClick = onForward,
                enabled = canGoForward,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("nav_forward")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (canGoForward) TextPrimary else TextSecondary.copy(alpha = 0.4f)
                )
            }

            // Tabs Switcher Counter Button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .testTag("tabs_button")
                    .clickable { onOpenTabs() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(
                            width = 2.dp,
                            color = if (isPrivate) MangaAmethyst else MangaPrimary,
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$tabsCount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPrivate) MangaAmethyst else MangaPrimary
                    )
                }
            }

            // Library shortcut
            IconButton(
                onClick = onOpenLibrary,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("library_nav_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CollectionsBookmark,
                    contentDescription = "Manhwa Library",
                    tint = MangaPrimary
                )
            }

            // More Options Menu
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("more_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = TextPrimary
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (isPageTranslated) {
                        DropdownMenuItem(
                            text = { Text("Restore Original Page", color = MangaPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = MangaPrimary
                                )
                            },
                            onClick = {
                                showMenu = false
                                onRestoreOriginalWebpage()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(if (isPageTranslating) "Translating..." else "Translate Webpage", color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = if (isPageTranslating) TextSecondary else MangaPrimary
                                )
                            },
                            enabled = !isPageTranslating,
                            onClick = {
                                showMenu = false
                                onTranslateWebpage()
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("OCR Bubble Translate", color = TextPrimary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = MangaPrimary
                            )
                        },
                        onClick = {
                            showMenu = false
                            onTriggerOcrTranslation()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Downloads", color = TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.Download, contentDescription = null, tint = MangaPrimary)
                        },
                        onClick = {
                            showMenu = false
                            onOpenDownloads()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Bookmark Page", color = TextPrimary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        },
                        onClick = {
                            showMenu = false
                            onBookmarkCurrentPage()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Bookmarks", color = TextPrimary) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        },
                        onClick = {
                            showMenu = false
                            onOpenBookmarks()
                        }
                    )

                    if (!isPrivate) {
                        DropdownMenuItem(
                            text = { Text("History", color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            },
                            onClick = {
                                showMenu = false
                                onOpenHistory()
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isPrivate) "Exit Private Mode" else "New Private Tab",
                                color = MangaAmethyst
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MangaAmethyst
                            )
                        },
                        onClick = {
                            showMenu = false
                            onTogglePrivate()
                        }
                    )
                }
            }
        }
    }
}
