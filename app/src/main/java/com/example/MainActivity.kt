package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.AppNavigation
import com.example.ui.screens.browser.BrowserViewModel
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.theme.MangaFlowTheme

class MainActivity : ComponentActivity() {
    private val externalOpenUrl = mutableStateOf<String?>(null)
    private val externalOpenRequestId = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        externalOpenUrl.value = intent?.getStringExtra("OPEN_URL")
        if (!externalOpenUrl.value.isNullOrBlank()) {
            externalOpenRequestId.value += 1L
        }

        setContent {
            val app = MangaFlowApp.instance
            val isPrivate by app.browserSessionManager.isPrivateActive.collectAsState()
            val browserViewModel: BrowserViewModel = viewModel()
            val settingsViewModel: SettingsViewModel = viewModel()
            val themeState by settingsViewModel.themeState.collectAsState()

            MangaFlowTheme(
                isDarkTheme = themeState.isDark,
                primaryAccent = themeState.accentColor,
                isPrivateMode = isPrivate
            ) {
                AppNavigation(
                    browserViewModel = browserViewModel,
                    settingsViewModel = settingsViewModel,
                    initialOpenUrl = externalOpenUrl.value,
                    openUrlRequestId = externalOpenRequestId.value,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val openUrl = intent.getStringExtra("OPEN_URL")
        if (!openUrl.isNullOrBlank()) {
            externalOpenUrl.value = openUrl
            externalOpenRequestId.value += 1L
        }
    }
}
