package com.ytmusic.feature.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun PlayerContainer(
    viewModel: PlaybackViewModel,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val isExpanded by viewModel.isFullPlayerExpanded.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    var isInPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }

    DisposableEffect(activity) {
        val listener = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPip = info.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    if (isInPip) {
        // PiP Mode: Render video full window, zero chrome/controls
        Box(modifier = Modifier.fillMaxSize()) {
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
        }
        return
    }

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
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
