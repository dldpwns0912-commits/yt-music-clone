package com.ytmusic

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ytmusic.core.designsystem.theme.YTMusicTheme
import com.ytmusic.core.media.service.MusicPlaybackService
import com.ytmusic.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            startService(Intent(this, MusicPlaybackService::class.java))
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start MusicPlaybackService: ${e.message}")
        }
        setContent {
            YTMusicTheme {
                MainScreen()
            }
        }
    }
}
