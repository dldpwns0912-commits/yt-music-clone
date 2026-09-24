package com.ytmusic.core.media.model

import com.ytmusic.core.extractor.model.TrackMetadata

data class PlaybackState(
    val currentTrack: TrackMetadata? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: Int = 0, // 0 = OFF, 1 = ONE, 2 = ALL
    val shuffleModeEnabled: Boolean = false,
    val volumeNormalizationEnabled: Boolean = true,
    val equalizerEnabled: Boolean = false,
    val crossfadeDurationMs: Long = 2000L
)
