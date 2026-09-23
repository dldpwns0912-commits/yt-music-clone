package com.ytmusic.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Pure OLED Black & YouTube Signature Accents
val YtBlack = Color(0xFF030303)
val YtSurface = Color(0xFF121212)
val YtSurfaceVariant = Color(0xFF212121)
val YtCardSurface = Color(0xFF282828)
val YtRed = Color(0xFFFF0000)
val YtRedBright = Color(0xFFFF4E4E)

// Text & Icon Colors
val YtTextPrimary = Color(0xFFFFFFFF)
val YtTextSecondary = Color(0xFFAAAAAA)
val YtTextTertiary = Color(0xFF717171)

// Chips & Badges
val YtChipBackground = Color(0xFF272727)
val YtChipSelectedBackground = Color(0xFFFFFFFF)
val YtChipTextSelected = Color(0xFF030303)

// Mini-player & Bottom Bar
val YtMiniPlayerBackground = Color(0xFF212121)
val YtBottomNavBackground = Color(0xEB030303) // Frosted semi-translucent

val YtMusicDarkColorScheme = darkColorScheme(
    primary = YtRedBright,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B0000),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color.White,
    onSecondary = YtBlack,
    secondaryContainer = YtSurfaceVariant,
    onSecondaryContainer = Color.White,
    background = YtBlack,
    onBackground = YtTextPrimary,
    surface = YtBlack,
    onSurface = YtTextPrimary,
    surfaceVariant = YtSurfaceVariant,
    onSurfaceVariant = YtTextSecondary,
    surfaceContainer = YtSurface,
    surfaceContainerHigh = YtSurfaceVariant,
    surfaceContainerHighest = YtCardSurface,
    outline = Color(0xFF3E3E3E),
    outlineVariant = Color(0xFF282828)
)
