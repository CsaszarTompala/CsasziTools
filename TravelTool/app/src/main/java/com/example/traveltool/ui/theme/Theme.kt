package com.example.traveltool.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.traveltool.data.ThemeStore

// ── Semantic colour holder used across screens ──────────────────
data class AppColors(
    val primary: Color,
    val accent: Color,
    val green: Color,
    val red: Color,
    val yellow: Color,
    val orange: Color,
    val pink: Color,
    val comment: Color,
    val foreground: Color,
    val current: Color,
    val background: Color,
    val isLight: Boolean,
)

val LocalAppColors = compositionLocalOf { draculaAppColors }

private val draculaAppColors = AppColors(
    primary = DraculaPurple, accent = DraculaCyan, green = DraculaGreen,
    red = DraculaRed, yellow = DraculaYellow, orange = DraculaOrange, pink = DraculaPink,
    comment = DraculaComment, foreground = DraculaForeground,
    current = DraculaCurrent, background = DraculaBackground, isLight = false,
)

private val darkAppColors = AppColors(
    primary = DarkPrimary, accent = DarkSecondary, green = DarkGreen,
    red = DarkRed, yellow = DarkYellow, orange = DarkOrange, pink = DarkPink,
    comment = DarkComment, foreground = DarkForeground,
    current = DarkCurrent, background = DarkBackground, isLight = false,
)

private val brightAppColors = AppColors(
    primary = BrightPrimary, accent = BrightSecondary, green = BrightGreen,
    red = BrightRed, yellow = BrightYellow, orange = BrightOrange, pink = BrightPink,
    comment = BrightComment, foreground = BrightForeground,
    current = BrightCurrent, background = BrightBackground, isLight = true,
)

// ── Color schemes ───────────────────────────────────────────────

private val DraculaColorScheme = darkColorScheme(
    primary            = DraculaPurple,
    onPrimary          = DraculaBackground,
    primaryContainer   = DraculaCurrent,
    onPrimaryContainer = DraculaForeground,
    secondary          = DraculaPink,
    onSecondary        = DraculaBackground,
    secondaryContainer = DraculaCurrent,
    onSecondaryContainer = DraculaForeground,
    tertiary           = DraculaCyan,
    onTertiary         = DraculaBackground,
    tertiaryContainer  = DraculaCurrent,
    onTertiaryContainer = DraculaForeground,
    background         = DraculaBackground,
    onBackground       = DraculaForeground,
    surface            = DraculaBackground,
    onSurface          = DraculaForeground,
    surfaceVariant     = DraculaCurrent,
    onSurfaceVariant   = DraculaComment,
    outline            = DraculaComment,
    error              = DraculaRed,
    onError            = DraculaBackground,
)

private val DarkColorScheme = darkColorScheme(
    primary            = DarkPrimary,
    onPrimary          = DarkBackground,
    primaryContainer   = DarkCurrent,
    onPrimaryContainer = DarkForeground,
    secondary          = DarkSecondary,
    onSecondary        = DarkBackground,
    secondaryContainer = DarkCurrent,
    onSecondaryContainer = DarkForeground,
    tertiary           = DarkAccent,
    onTertiary         = DarkBackground,
    tertiaryContainer  = DarkCurrent,
    onTertiaryContainer = DarkForeground,
    background         = DarkBackground,
    onBackground       = DarkForeground,
    surface            = DarkSurface,
    onSurface          = DarkForeground,
    surfaceVariant     = DarkCurrent,
    onSurfaceVariant   = DarkComment,
    outline            = DarkComment,
    error              = DarkRed,
    onError            = DarkBackground,
)

private val BrightColorScheme = lightColorScheme(
    primary            = BrightPrimary,
    onPrimary          = Color.White,
    primaryContainer   = BrightCurrent,
    onPrimaryContainer = BrightForeground,
    secondary          = BrightSecondary,
    onSecondary        = Color.White,
    secondaryContainer = BrightCurrent,
    onSecondaryContainer = BrightForeground,
    tertiary           = BrightAccent,
    onTertiary         = Color.White,
    tertiaryContainer  = BrightCurrent,
    onTertiaryContainer = BrightForeground,
    background         = BrightBackground,
    onBackground       = BrightForeground,
    surface            = BrightSurface,
    onSurface          = BrightForeground,
    surfaceVariant     = BrightCurrent,
    onSurfaceVariant   = BrightComment,
    outline            = BrightComment,
    error              = BrightRed,
    onError            = Color.White,
)

@Composable
fun TravelToolTheme(
    themeChoice: ThemeStore.ThemeChoice = ThemeStore.ThemeChoice.DRACULA,
    content: @Composable () -> Unit
) {
    val (colorScheme: ColorScheme, appColors: AppColors, isLight: Boolean) = when (themeChoice) {
        ThemeStore.ThemeChoice.DRACULA -> Triple(DraculaColorScheme, draculaAppColors, false)
        ThemeStore.ThemeChoice.DARK    -> Triple(DarkColorScheme, darkAppColors, false)
        ThemeStore.ThemeChoice.BRIGHT  -> Triple(BrightColorScheme, brightAppColors, true)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}