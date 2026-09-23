package com.ytmusic.core.designsystem.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun YTMusicTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = YtMusicDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Pure black edge-to-edge status bar & navigation bar
            window.statusBarColor = YtBlack.toArgb()
            window.navigationBarColor = YtBlack.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            // Dark background -> light icons
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YtMusicTypography,
        shapes = YtMusicShapes,
        content = content
    )
}
