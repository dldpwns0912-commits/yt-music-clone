package com.ytmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ytmusic.core.designsystem.theme.YtBlack
import com.ytmusic.feature.home.HomeScreen
import com.ytmusic.feature.home.HomeViewModel
import com.ytmusic.feature.library.LibraryScreen
import com.ytmusic.feature.player.PlaybackViewModel
import com.ytmusic.feature.player.PlayerContainer
import com.ytmusic.feature.search.SearchScreen
import com.ytmusic.ui.navigation.BottomNavigationBar
import com.ytmusic.ui.navigation.Screen

@Composable
fun MainScreen(
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val isFullPlayerExpanded by playbackViewModel.isFullPlayerExpanded.collectAsState()
    val playbackState by playbackViewModel.playbackState.collectAsState()

    Scaffold(
        bottomBar = {
            if (!isFullPlayerExpanded) {
                BottomNavigationBar(navController = navController)
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(YtBlack)
                .padding(bottom = if (!isFullPlayerExpanded) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            // Main Navigation Host
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        homeViewModel = homeViewModel,
                        onTrackClick = { track, queue ->
                            playbackViewModel.playTrack(track, queue)
                        }
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen()
                }
                composable(Screen.Library.route) {
                    LibraryScreen()
                }
            }

            // Player Overlay (MiniPlayer docked above BottomBar, expands to FullPlayer)
            PlayerContainer(
                viewModel = playbackViewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
