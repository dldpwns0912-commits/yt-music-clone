package com.ytmusic.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackParameters
import com.ytmusic.core.database.dao.PlaylistDao
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.designsystem.palette.ExtractedPalette
import com.ytmusic.core.designsystem.palette.PaletteExtractor
import com.ytmusic.core.downloader.AudioDownloadManager
import com.ytmusic.core.extractor.model.TrackMetadata
import com.ytmusic.core.media.caption.CaptionItem
import com.ytmusic.core.media.caption.SubtitleManager
import com.ytmusic.core.media.model.PlaybackState
import com.ytmusic.core.media.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val downloadManager: AudioDownloadManager,
    private val subtitleManager: SubtitleManager
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

    // Subtitles & Captions State
    private val _captions = MutableStateFlow<List<CaptionItem>>(emptyList())
    val captions: StateFlow<List<CaptionItem>> = _captions.asStateFlow()

    private val _isSubtitlesEnabled = MutableStateFlow(true)
    val isSubtitlesEnabled: StateFlow<Boolean> = _isSubtitlesEnabled.asStateFlow()

    val currentSubtitle: StateFlow<String?> = combine(playbackState, _captions, _isSubtitlesEnabled) { state, caps, enabled ->
        if (!enabled || caps.isEmpty()) return@combine null
        val pos = state.currentPositionMs
        caps.find { pos in it.startMs..(it.endMs) }?.text
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Dynamic Palette Extraction
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

        // Live Subtitle Fetching
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .collect { trackId ->
                    if (!trackId.isNullOrBlank()) {
                        _captions.value = subtitleManager.getCaptions(trackId)
                    } else {
                        _captions.value = emptyList()
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

    fun toggleSubtitles() {
        _isSubtitlesEnabled.value = !_isSubtitlesEnabled.value
    }

    fun downloadCurrentTrack() {
        val currentTrack = playbackState.value.currentTrack ?: return
        try {
            val enqueued = downloadManager.enqueueDownload(currentTrack)
            if (enqueued) {
                _downloadToast.value = "'${currentTrack.title}' 다운로드를 시작합니다"
            } else {
                _downloadToast.value = "다운로드 대기열 추가 실패"
            }
        } catch (e: Exception) {
            _downloadToast.value = "다운로드 오류: ${e.message}"
        }
    }

    fun clearDownloadToast() {
        _downloadToast.value = null
    }

    fun setFullPlayerExpanded(expanded: Boolean) {
        _isFullPlayerExpanded.value = expanded
    }
}
