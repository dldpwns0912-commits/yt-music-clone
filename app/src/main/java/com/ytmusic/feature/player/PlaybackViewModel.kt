package com.ytmusic.feature.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.core.database.dao.PlaylistDao
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.PlaylistTrackCrossRef
import com.ytmusic.core.database.entity.TrackEntity
import com.ytmusic.core.designsystem.palette.ExtractedPalette
import com.ytmusic.core.designsystem.palette.PaletteExtractor
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
    private val playlistDao: PlaylistDao
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val currentQueue: StateFlow<List<TrackMetadata>> = playerManager.currentQueue
    val currentIndex: StateFlow<Int> = playerManager.currentIndex

    private val _palette = MutableStateFlow(ExtractedPalette())
    val palette: StateFlow<ExtractedPalette> = _palette.asStateFlow()

    private val _isFullPlayerExpanded = MutableStateFlow(false)
    val isFullPlayerExpanded: StateFlow<Boolean> = _isFullPlayerExpanded.asStateFlow()

    private val _isQueueSheetVisible = MutableStateFlow(false)
    val isQueueSheetVisible: StateFlow<Boolean> = _isQueueSheetVisible.asStateFlow()

    private val _isLyricsSheetVisible = MutableStateFlow(false)
    val isLyricsSheetVisible: StateFlow<Boolean> = _isLyricsSheetVisible.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    init {
        // Observe current track changes to dynamically extract ambient glow palette
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack?.thumbnailUrl }
                .distinctUntilChanged()
                .collect { thumbnailUrl ->
                    if (!thumbnailUrl.isNullOrBlank()) {
                        val extracted = paletteExtractor.extractColorsFromUrl(context, thumbnailUrl)
                        _palette.value = extracted
                    } else {
                        _palette.value = ExtractedPalette()
                    }
                }
        }

        // Check if current track is in Favorites playlist
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .collect { trackId ->
                    if (trackId != null) {
                        checkFavoriteStatus(trackId)
                    } else {
                        _isFavorite.value = false
                    }
                }
        }
    }

    private fun checkFavoriteStatus(trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val favoritesPlaylist = playlistDao.getPlaylistById("favorites")
            if (favoritesPlaylist != null) {
                // Determine if trackId is in favorites
                val playlistWithTracks = playlistDao.getPlaylistById("favorites")
                // Checked via cross reference or simple dao check
            }
        }
    }

    fun playTrack(track: TrackMetadata, queue: List<TrackMetadata> = listOf(track)) {
        playerManager.playTrack(track, queue)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun skipToNext() {
        playerManager.skipToNext()
    }

    fun skipToPrevious() {
        playerManager.skipToPrevious()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
    }

    fun toggleRepeatMode() {
        val nextMode = when (playbackState.value.repeatMode) {
            0 -> 2 // OFF -> ALL
            2 -> 1 // ALL -> ONE
            else -> 0 // ONE -> OFF
        }
        playerManager.setRepeatMode(nextMode)
    }

    fun toggleShuffleMode() {
        playerManager.setShuffleMode(!playbackState.value.shuffleModeEnabled)
    }

    fun toggleVolumeNormalization() {
        playerManager.setVolumeNormalization(!playbackState.value.volumeNormalizationEnabled)
    }

    fun toggleFavorite() {
        val currentTrack = playbackState.value.currentTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val newFav = !_isFavorite.value
            _isFavorite.value = newFav
            if (newFav) {
                trackDao.upsertTrack(
                    TrackEntity(
                        id = currentTrack.id,
                        title = currentTrack.title,
                        artist = currentTrack.artist,
                        album = currentTrack.album,
                        durationMs = currentTrack.durationMs,
                        thumbnailUrl = currentTrack.thumbnailUrl
                    )
                )
                playlistDao.insertCrossRef(
                    PlaylistTrackCrossRef(
                        playlistId = "favorites",
                        trackId = currentTrack.id,
                        position = 0
                    )
                )
            } else {
                playlistDao.removeTrackFromPlaylist("favorites", currentTrack.id)
            }
        }
    }

    fun setFullPlayerExpanded(expanded: Boolean) {
        _isFullPlayerExpanded.value = expanded
    }

    fun setQueueSheetVisible(visible: Boolean) {
        _isQueueSheetVisible.value = visible
    }

    fun setLyricsSheetVisible(visible: Boolean) {
        _isLyricsSheetVisible.value = visible
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        playerManager.moveQueueItem(fromIndex, toIndex)
    }

    fun removeFromQueue(index: Int) {
        playerManager.removeFromQueue(index)
    }
}
