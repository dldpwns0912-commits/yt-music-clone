package com.ytmusic.feature.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.util.Locale

internal fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(UnstableApi::class)
@Composable
fun FullPlayer(
    viewModel: PlaybackViewModel,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val track = playbackState.currentTrack ?: return
    val context = LocalContext.current
    val toastMessage by viewModel.downloadToast.collectAsState()

    toastMessage?.let { msg ->
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearDownloadToast()
    }

    val isSubtitlesEnabled by viewModel.isSubtitlesEnabled.collectAsState()
    val currentSubtitle by viewModel.currentSubtitle.collectAsState()

    var showSpeedDialog by remember { mutableStateOf(false) }
    var userSliderPos by remember { mutableStateOf<Float?>(null) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var currentPitch by remember { mutableFloatStateOf(1.0f) }

    val currentMs = userSliderPos?.toLong() ?: playbackState.currentPositionMs
    val totalMs = playbackState.durationMs.coerceAtLeast(1L)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F0F12)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar (Collapse, Title, Subtitles, PIP, Speed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.setFullPlayerExpanded(false) }) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "플레이어 접기",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "재생 중",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row {
                    // Subtitles / Captions Toggle Button
                    IconButton(onClick = { viewModel.toggleSubtitles() }) {
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = "자막 켜기/끄기",
                            tint = if (isSubtitlesEnabled) Color(0xFF06B6D4) else Color(0xFF71717A)
                        )
                    }

                    // PIP Mode Button
                    IconButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val activity = context.findActivity()
                            val params = PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()
                            activity?.enterPictureInPictureMode(params)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "PIP 모드",
                            tint = Color.White
                        )
                    }

                    // Speed & Pitch Modal Button
                    IconButton(onClick = { showSpeedDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "재생 속도/피치 조절",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. 16:9 Video Player View (Plays YouTube Video Surface with Subtitles)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = viewModel.exoPlayer
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = {
                        if (it.player != viewModel.exoPlayer) {
                            it.player = viewModel.exoPlayer
                        }
                    },
                    onRelease = {
                        it.player = null
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Real-time Subtitle Text Overlay
                if (isSubtitlesEnabled && !currentSubtitle.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = currentSubtitle ?: "",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Track Info (Title and Channel)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artist,
                    color = Color(0xFFA1A1AA),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Timeline Slider (Current Time / Duration)
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = (currentMs.toFloat() / totalMs).coerceIn(0f, 1f),
                    onValueChange = { ratio -> userSliderPos = ratio * totalMs },
                    onValueChangeFinished = {
                        userSliderPos?.let { viewModel.seekTo(it.toLong()) }
                        userSliderPos = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF06B6D4),
                        activeTrackColor = Color(0xFF06B6D4),
                        inactiveTrackColor = Color(0xFF27272A)
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentMs),
                        color = Color(0xFFA1A1AA),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatDuration(totalMs),
                        color = Color(0xFFA1A1AA),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Controls: Prev, Large Play/Pause, Next, Download
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Download button
                IconButton(
                    onClick = { viewModel.downloadCurrentTrack() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "다운로드",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Prev
                IconButton(
                    onClick = { viewModel.skipToPrevious() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "이전 곡",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause FAB
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF06B6D4),
                    modifier = Modifier.size(72.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "재생/정지",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Next
                IconButton(
                    onClick = { viewModel.skipToNext() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "다음 곡",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Spacer to balance download button
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }

    // 0.5x ~ 2.0x 속도 및 음높이(피치) 조절 다이얼로그
    if (showSpeedDialog) {
        var tempSpeed by remember { mutableFloatStateOf(currentSpeed) }
        var tempPitch by remember { mutableFloatStateOf(currentPitch) }

        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Text(
                    text = "재생 속도 및 음높이",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "속도: ${String.format(Locale.US, "%.1fx", tempSpeed)}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Slider(
                        value = tempSpeed,
                        onValueChange = { tempSpeed = it },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF06B6D4),
                            activeTrackColor = Color(0xFF06B6D4)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "음높이(Pitch): ${String.format(Locale.US, "%.1fx", tempPitch)}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Slider(
                        value = tempPitch,
                        onValueChange = { tempPitch = it },
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF06B6D4),
                            activeTrackColor = Color(0xFF06B6D4)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        currentSpeed = tempSpeed
                        currentPitch = tempPitch
                        viewModel.setPlaybackSpeedAndPitch(tempSpeed, tempPitch)
                        showSpeedDialog = false
                    }
                ) {
                    Text("적용", color = Color(0xFF06B6D4))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("취소", color = Color(0xFFA1A1AA))
                }
            },
            containerColor = Color(0xFF18181B)
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
