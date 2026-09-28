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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ytmusic.core.database.entity.TrackEntity
import com.ytmusic.feature.player.PlaybackViewModel
import java.util.Locale

@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val downloadedTracks by libraryViewModel.downloadedTracks.collectAsState()
    val totalBytes by libraryViewModel.totalStorageBytes.collectAsState()
    var trackToDelete by remember { mutableStateOf<TrackEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
            .padding(top = 24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                tint = Color(0xFF06B6D4),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "보관함",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "저장된 영상 ${downloadedTracks.size}개 • 사용 용량 ${formatBytes(totalBytes)}",
                    color = Color(0xFFA1A1AA),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (downloadedTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FileDownloadDone,
                        contentDescription = null,
                        tint = Color(0xFF3F3F46),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "다운로드된 영상이 없습니다",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "플레이어 화면에서 다운로드 버튼을 눌러\n오프라인에 저장해 보세요",
                        color = Color(0xFFA1A1AA),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp, top = 8.dp)
            ) {
                items(downloadedTracks, key = { it.id }) { track ->
                    DownloadedTrackItem(
                        track = track,
                        onPlayClick = {
                            val allMetadata = downloadedTracks.map { libraryViewModel.toTrackMetadata(it) }
                            val target = libraryViewModel.toTrackMetadata(track)
                            playbackViewModel.playTrack(target, allMetadata)
                        },
                        onDeleteClick = {
                            trackToDelete = track
                        }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("영상 삭제") },
            text = { Text("'${track.title}' 영상을 기기에서 완전히 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        libraryViewModel.deleteTrack(track.id)
                        trackToDelete = null
                    }
                ) {
                    Text("삭제", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) {
                    Text("취소")
                }
            },
            containerColor = Color(0xFF18181B),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFA1A1AA)
        )
    }
}

@Composable
private fun DownloadedTrackItem(
    track: TrackEntity,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onPlayClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF27272A))
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        color = Color(0xFFA1A1AA),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (track.fileSize > 0) {
                        Text(
                            text = " • ${formatBytes(track.fileSize)}",
                            color = Color(0xFF06B6D4),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Play icon
            IconButton(onClick = onPlayClick) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "재생",
                    tint = Color(0xFF06B6D4)
                )
            }

            // Delete icon
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "삭제",
                    tint = Color(0xFFA1A1AA)
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format(Locale.US, "%.1f GB", mb / 1024)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}
