package com.ytmusic.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ytmusic.core.designsystem.theme.YtBlack

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(YtBlack),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Library & Downloads", color = Color.White)
    }
}
