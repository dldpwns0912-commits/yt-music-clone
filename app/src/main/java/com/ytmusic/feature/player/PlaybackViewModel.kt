package com.ytmusic.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackParameters
import com.ytmusic.core.database.dao.PlaylistDao
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.PlaylistTrackCrossRef
import com.ytmusic.core.database.entity.TrackEntity
import com.ytmusic.core.designsystem.palette.ExtractedPalette
import com.ytmusic.core.designsystem.palette.PaletteExtractor
import com.ytmusic.core.downloader.AudioDownloadManager
import com.ytmusic.core.extractor.model.TrackMetadata
import com.ytmusic.core.media.model.PlaybackState
import com.ytmusic.core.media.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerManager: MusicPlayerManager,
    private val paletteExtractor: PaletteExtractor,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val downloadManager: AudioDownloadManager
) : ViewModel() {

    val exoPlayer = playerManager.exoPlayer
    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val currentQueue: StateFlow<List<TrackMetadata>> = playerManager.currentQueue
    val currentIndex: StateFlow<Int> = playerManager.currentIndex

    private val _palette = MutableStateFlow(ExtractedPalette())
    val palette: StateFlow<ExtractedPalette> = _palette.asStateFlow()

    private val _isFullPlayerExpanded = MutableStateFlow(false)
    val isFullPlayerExpanded: StateFlow<Boolean> = _isFullPlayerExpanded.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _downloadToast = MutableStateFlow<String?>(null)
    val downloadToast: StateFlow<String?> = _downloadToast.asStateFlow()

    init {
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack?.thumbnailUrl }
                .distinctUntilChanged()
                .collect { url ->
                    if (!url.isNullOrBlank()) {
                        val pal = paletteExtractor.extractColorsFromUrl(context, url)
                        _palette.value = pal
                    }
                }
        }
    }

    fun playTrack(track: TrackMetadata, queue: List<TrackMetadata> = listOf(track)) {
        playerManager.playTrack(track, queue)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun skipToNext() {
        playerManager.skipToNext()
    }

    fun skipToPrevious() {
        playerManager.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
    }

    fun setPlaybackSpeedAndPitch(speed: Float, pitch: Float) {
        playerManager.setPlaybackSpeed(speed)
        playerManager.exoPlayer.playbackParameters = PlaybackParameters(speed, pitch)
    }

    fun downloadCurrentTrack() {
        val currentTrack = playbackState.value.currentTrack ?: return
        downloadManager.enqueueDownload(currentTrack)
        _downloadToast.value = "'${currentTrack.title}' 다운로드를 시작합니다"
    }

    fun clearDownloadToast() {
        _downloadToast.value = null
    }

    fun setFullPlayerExpanded(expanded: Boolean) {
        _isFullPlayerExpanded.value = expanded
    }
}
