package com.ytmusic.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ytmusic.core.designsystem.theme.YtBlack
import com.ytmusic.core.designsystem.theme.YtChipBackground
import com.ytmusic.core.designsystem.theme.YtRedBright
import com.ytmusic.core.designsystem.theme.YtSurfaceVariant
import com.ytmusic.core.designsystem.theme.YtTextPrimary
import com.ytmusic.core.designsystem.theme.YtTextSecondary
import com.ytmusic.feature.home.HomeViewModel

@Composable
fun LibraryScreen(
    onNavigateToEqualizer: () -> Unit,
    onNavigateToStorage: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val playlists by homeViewModel.playlists.collectAsState()
    val mostPlayed by homeViewModel.mostPlayed.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(YtBlack),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        item {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        // 2. Quick Library Categories
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryCategoryRow(
                    icon = Icons.Default.Favorite,
                    title = "Liked Music",
                    subtitle = "Auto playlist",
                    iconTint = YtRedBright,
                    onClick = {}
                )
                LibraryCategoryRow(
                    icon = Icons.Default.DownloadDone,
                    title = "Downloads",
                    subtitle = "Manage offline files",
                    iconTint = Color.White,
                    onClick = onNavigateToStorage
                )
                LibraryCategoryRow(
                    icon = Icons.Default.History,
                    title = "History",
                    subtitle = "${mostPlayed.size} played tracks",
                    iconTint = Color.White,
                    onClick = {}
                )
            }
        }

        // 3. Audio & Storage Management Utilities Card
        item {
            Text(
                text = "Settings & Audio Engine",
                color = YtTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = YtSurfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToEqualizer() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Equalizer, contentDescription = "Equalizer", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Equalizer & Volume Normalizer", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "5-band EQ, Bass boost & -14 LUFS normalizer", color = YtTextSecondary, fontSize = 12.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0x15FFFFFF))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToStorage() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = "Storage", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Storage & JSON Backup", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Cache limit, auto-cleanup & JSON export/import", color = YtTextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 4. Playlists Section
        item {
            Text(
                text = "Playlists (${playlists.size})",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (playlists.isEmpty()) {
            item {
                Text(
                    text = "No custom playlists yet. Use 'Storage & JSON Backup' to restore your playlists.",
                    color = YtTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(playlists) { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {}
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(YtChipBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = "Playlist", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = playlist.name, color = YtTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = playlist.description ?: "Playlist", color = YtTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(YtChipBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, color = YtTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = YtTextSecondary, fontSize = 12.sp)
        }
    }
}
