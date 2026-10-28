package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.browser.BrowserScreen
import com.example.ui.screens.browser.BrowserViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.library.LibraryViewModel
import com.example.ui.screens.manhwa_detail.ManhwaDetailScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.stats.ReadingStatsScreen
import com.example.ui.screens.stats.ReadingStatsViewModel
import com.example.ui.screens.updates.UpdatesScreen
import com.example.ui.theme.MangaAmethyst
import com.example.ui.theme.TextSecondary

enum class AppDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LIBRARY("Library", Icons.Default.CollectionsBookmark),
    BROWSER("Browser", Icons.Default.Public),
    UPDATES("Updates", Icons.Default.Notifications),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation(
    browserViewModel: BrowserViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    statsViewModel: ReadingStatsViewModel = viewModel(),
    initialOpenUrl: String? = null,
    openUrlRequestId: Long = 0L,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(AppDestination.HOME) }
    var selectedManhwaId by remember { mutableStateOf<Long?>(null) }
    var viewingStatsFromHome by remember { mutableStateOf(false) }
    val isPrivate by browserViewModel.isPrivateMode.collectAsState()

    LaunchedEffect(openUrlRequestId, initialOpenUrl) {
        if (!initialOpenUrl.isNullOrBlank()) {
            browserViewModel.openNormalAddress(initialOpenUrl)
            currentDestination = AppDestination.BROWSER
        }
    }

    // Android Hardware Back Handler behavior
    BackHandler(enabled = selectedManhwaId != null || viewingStatsFromHome || currentDestination != AppDestination.HOME) {
        when {
            selectedManhwaId != null -> selectedManhwaId = null
            viewingStatsFromHome -> viewingStatsFromHome = false
            currentDestination != AppDestination.HOME -> currentDestination = AppDestination.HOME
        }
    }

    Scaffold(
        bottomBar = {
            if (selectedManhwaId == null && !viewingStatsFromHome) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    AppDestination.values().forEach { destination ->
                        val isSelected = currentDestination == destination
                        val activeColor = if (isPrivate) MangaAmethyst else MaterialTheme.colorScheme.primary
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                selectedManhwaId = null
                                viewingStatsFromHome = false
                                currentDestination = destination
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label
                                )
                            },
                            label = {
                                Text(
                                    text = destination.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = activeColor,
                                selectedTextColor = activeColor,
                                indicatorColor = activeColor.copy(alpha = 0.15f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("bottom_nav_${destination.name.lowercase()}")
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                selectedManhwaId != null -> {
                    ManhwaDetailScreen(
                        manhwaId = selectedManhwaId!!,
                        onBack = { selectedManhwaId = null },
                        onReadChapter = { url ->
                            selectedManhwaId = null
                            currentDestination = AppDestination.BROWSER
                            browserViewModel.submitAddress(url)
                        }
                    )
                }

                viewingStatsFromHome -> {
                    ReadingStatsScreen(
                        viewModel = statsViewModel
                    )
                }

                currentDestination == AppDestination.HOME -> {
                    HomeScreen(
                        onNavigateToBrowser = { url ->
                            currentDestination = AppDestination.BROWSER
                            browserViewModel.submitAddress(url)
                        },
                        onNavigateToLibrary = { currentDestination = AppDestination.LIBRARY },
                        onNavigateToStats = { viewingStatsFromHome = true },
                        onNavigateToUpdates = { currentDestination = AppDestination.UPDATES },
                        onSelectManhwaDetail = { id -> selectedManhwaId = id }
                    )
                }

                currentDestination == AppDestination.LIBRARY -> {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onOpenManhwaInBrowser = { url ->
                            currentDestination = AppDestination.BROWSER
                            browserViewModel.submitAddress(url)
                        },
                        onSelectManhwaDetail = { id ->
                            selectedManhwaId = id
                        }
                    )
                }

                currentDestination == AppDestination.BROWSER -> {
                    BrowserScreen(
                        viewModel = browserViewModel,
                        onNavigateToLibrary = { currentDestination = AppDestination.LIBRARY }
                    )
                }

                currentDestination == AppDestination.UPDATES -> {
                    UpdatesScreen(
                        onOpenInBrowser = { url ->
                            currentDestination = AppDestination.BROWSER
                            browserViewModel.submitAddress(url)
                        },
                        onNavigateToSettings = { currentDestination = AppDestination.SETTINGS }
                    )
                }

                currentDestination == AppDestination.SETTINGS -> {
                    SettingsScreen(
                        viewModel = settingsViewModel
                    )
                }
            }
        }
    }
}
