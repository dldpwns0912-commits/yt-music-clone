package com.ytmusic.feature.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
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
    val syncMessage by libraryViewModel.syncMessage.collectAsState()
    val context = LocalContext.current
    var trackToDelete by remember { mutableStateOf<TrackEntity?>(null) }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            libraryViewModel.importLocalFiles(uris)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            libraryViewModel.importFolder(it)
        }
    }

    syncMessage?.let { msg ->
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        libraryViewModel.clearSyncMessage()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        // Compact Sleek Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "보관함",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Storage badge pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF18181B))
                    .border(1.dp, Color(0xFF27272A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${downloadedTracks.size}곡 • ${formatBytes(totalBytes)}",
                    color = Color(0xFF06B6D4),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Daily Sync Button
            IconButton(
                onClick = { libraryViewModel.triggerDailySync() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "일일 동기화 실행",
                    tint = Color(0xFFA1A1AA),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Quick Import Action Bar (Convenient Multi-file & Folder upload)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Multi-file Select Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F1F23))
                    .clickable {
                        multiFilePicker.launch(arrayOf("audio/*", "video/*", "application/octet-stream"))
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "＋ 파일 다중 추가",
                    color = Color(0xFF06B6D4),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 2. Folder Select Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F1F23))
                    .clickable {
                        folderPicker.launch(null)
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📁 폴더 전체 추가",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 3. Scan Downloads Folder
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F1F23))
                    .clickable {
                        libraryViewModel.scanDeviceDownloads()
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚡ 다운로드 스캔",
                    color = Color(0xFFFBBF24),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = Color(0xFF27272A),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

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
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "저장된 영상이 없습니다",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "플레이어 화면에서 다운로드하거나,\n지정 플레이리스트가 하루마다 자동 동기화됩니다",
                        color = Color(0xFFA1A1AA),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp, top = 2.dp)
            ) {
                items(downloadedTracks, key = { it.id }) { track ->
                    CompactDownloadedTrackItem(
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
            text = { Text("'${track.title}' 영상을 기기에서 삭제하시겠습니까?") },
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
private fun CompactDownloadedTrackItem(
    track: TrackEntity,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Compact 16:9 Thumbnail (48 x 32 dp)
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 32.dp)
                .clip(RoundedCornerShape(4.dp))
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

        // Track Title & Details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artist,
                    color = Color(0xFFA1A1AA),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
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

        Spacer(modifier = Modifier.width(8.dp))

        // Discreet Delete Button
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "삭제",
                tint = Color(0xFF71717A),
                modifier = Modifier.size(18.dp)
            )
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
