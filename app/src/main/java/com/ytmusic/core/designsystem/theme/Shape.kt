package com.ytmusic.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val YtMusicShapes = Shapes(
    // Extra Small: Badges, tiny tags
    extraSmall = RoundedCornerShape(4.dp),
    // Small: Album thumbnails in lists, mini player elements
    small = RoundedCornerShape(8.dp),
    // Medium: Cards, dialogs, player cover art
    medium = RoundedCornerShape(12.dp),
    // Large: Full player album artwork, bottom sheets
    large = RoundedCornerShape(16.dp),
    // Extra Large: Pills, mood filter chips
    extraLarge = RoundedCornerShape(50.dp)
)
