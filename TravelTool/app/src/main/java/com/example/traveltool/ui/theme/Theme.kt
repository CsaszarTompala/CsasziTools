package com.example.traveltool.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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

@Composable
fun TravelToolTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DraculaBackground.toArgb()
            window.navigationBarColor = DraculaBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DraculaColorScheme,
        typography  = Typography,
        content     = content
    )
}