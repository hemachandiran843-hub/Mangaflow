package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MangaDarkColorScheme = darkColorScheme(
    primary = MangaPrimary,
    onPrimary = MangaOnPrimary,
    primaryContainer = MangaSurfaceVariant,
    onPrimaryContainer = MangaPrimary,
    secondary = MangaSecondary,
    onSecondary = MangaOnSecondary,
    secondaryContainer = MangaSurfaceLight,
    onSecondaryContainer = MangaSecondary,
    tertiary = MangaAmethyst,
    onTertiary = MangaOnAmethyst,
    background = MangaDarkBg,
    onBackground = TextPrimary,
    surface = MangaSurface,
    onSurface = TextPrimary,
    surfaceVariant = MangaSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle
)

private val MangaLightColorScheme = lightColorScheme(
    primary = MangaPrimary,
    onPrimary = MangaOnPrimary,
    primaryContainer = MangaLightSurfaceVariant,
    onPrimaryContainer = MangaPrimary,
    secondary = MangaSecondary,
    onSecondary = MangaOnSecondary,
    secondaryContainer = MangaLightSurfaceLight,
    onSecondaryContainer = Color(0xFF005230),
    tertiary = MangaAmethyst,
    onTertiary = MangaOnAmethyst,
    background = MangaLightBg,
    onBackground = TextPrimaryLight,
    surface = MangaLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = MangaLightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderSubtleLight
)

@Composable
fun MangaFlowTheme(
    isDarkTheme: Boolean = true,
    primaryAccent: Color = MangaPrimary,
    isPrivateMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseScheme = if (isDarkTheme) MangaDarkColorScheme else MangaLightColorScheme

    val colorScheme = if (isPrivateMode) {
        baseScheme.copy(
            primary = MangaAmethyst,
            onPrimary = MangaOnAmethyst,
            background = if (isDarkTheme) Color(0xFF0C0913) else Color(0xFFF7F2FD),
            surface = if (isDarkTheme) Color(0xFF13101D) else Color(0xFFFFFFFF),
            surfaceVariant = if (isDarkTheme) Color(0xFF1F1A2F) else Color(0xFFEFE8F9)
        )
    } else {
        baseScheme.copy(
            primary = primaryAccent,
            onPrimaryContainer = primaryAccent
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDarkTheme && !isPrivateMode
                controller.isAppearanceLightNavigationBars = !isDarkTheme && !isPrivateMode
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
