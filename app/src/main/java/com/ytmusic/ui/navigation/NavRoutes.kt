package com.ytmusic.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Search : Screen("search", "검색", Icons.Default.Search)
    data object Library : Screen("library", "보관함", Icons.Default.VideoLibrary)
}

val BottomNavItems = listOf(
    Screen.Search,
    Screen.Library
)
