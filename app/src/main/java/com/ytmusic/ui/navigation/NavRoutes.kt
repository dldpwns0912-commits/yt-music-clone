package com.ytmusic.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Search : Screen("search", "Explore", Icons.Default.Explore)
    data object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    data object Equalizer : Screen("equalizer", "Equalizer", Icons.Default.LibraryMusic)
    data object StorageManager : Screen("storage", "Storage", Icons.Default.LibraryMusic)
}

val BottomNavItems = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Library
)
