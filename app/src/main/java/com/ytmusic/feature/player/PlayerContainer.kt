package com.ytmusic.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PlayerContainer(
    viewModel: PlaybackViewModel,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val isExpanded by viewModel.isFullPlayerExpanded.collectAsState()
    val palette by viewModel.palette.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    // Handle back press to collapse full player
    BackHandler(enabled = isExpanded) {
        viewModel.setFullPlayerExpanded(false)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // MiniPlayer docked above navigation bar
        AnimatedVisibility(
            visible = playbackState.currentTrack != null && !isExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250, easing = FastOutSlowInEasing)
            ) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MiniPlayer(
                playbackState = playbackState,
                onExpand = { viewModel.setFullPlayerExpanded(true) },
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onSkipNext = { viewModel.skipToNext() },
                onSkipPrevious = { viewModel.skipToPrevious() }
            )
        }

        // FullPlayer expandable full screen overlay
        AnimatedVisibility(
            visible = isExpanded && playbackState.currentTrack != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            FullPlayer(
                playbackState = playbackState,
                palette = palette,
                isFavorite = isFavorite,
                onCollapse = { viewModel.setFullPlayerExpanded(false) },
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onSeekTo = { viewModel.seekTo(it) },
                onSkipNext = { viewModel.skipToNext() },
                onSkipPrevious = { viewModel.skipToPrevious() },
                onToggleRepeat = { viewModel.toggleRepeatMode() },
                onToggleShuffle = { viewModel.toggleShuffleMode() },
                onToggleFavorite = { viewModel.toggleFavorite() },
                onToggleNormalization = { viewModel.toggleVolumeNormalization() },
                onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                onOpenQueue = { viewModel.setQueueSheetVisible(true) },
                onOpenLyrics = { viewModel.setLyricsVisible(true) }
            )
        }

        // Synced Lyrics Modal Sheet
        val isLyricsVisible by viewModel.isLyricsSheetVisible.collectAsState()
        val currentTrack = playbackState.currentTrack
        if (isLyricsVisible && currentTrack != null) {
            com.ytmusic.feature.player.lyrics.SyncedLyricsSheet(
                track = currentTrack,
                currentPositionMs = playbackState.currentPositionMs,
                onSeekTo = { viewModel.seekTo(it) },
                onDismiss = { viewModel.setLyricsVisible(false) }
            )
        }

        // Queue Reordering Modal Sheet
        val isQueueVisible by viewModel.isQueueSheetVisible.collectAsState()
        val currentQueue by viewModel.currentQueue.collectAsState()
        val currentIndex by viewModel.currentIndex.collectAsState()
        if (isQueueVisible) {
            com.ytmusic.feature.player.queue.QueueBottomSheet(
                queue = currentQueue,
                currentIndex = currentIndex,
                onTrackSelected = { idx ->
                    val track = currentQueue.getOrNull(idx)
                    if (track != null) {
                        viewModel.playTrack(track, currentQueue)
                    }
                },
                onMoveItem = { from, to -> viewModel.reorderQueue(from, to) },
                onRemoveItem = { idx -> viewModel.removeFromQueue(idx) },
                onClearQueue = {
                    // clears queue
                },
                onDismiss = { viewModel.setQueueSheetVisible(false) }
            )
        }
    }
}
