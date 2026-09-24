package com.ytmusic.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.core.database.dao.PlaybackHistoryDao
import com.ytmusic.core.database.dao.PlaylistDao
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.PlaylistEntity
import com.ytmusic.core.database.entity.TrackEntity
import com.ytmusic.core.extractor.model.TrackMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeFeedState(
    val quickPicks: List<TrackMetadata> = emptyList(),
    val recentlyPlayed: List<TrackEntity> = emptyList(),
    val mostPlayed: List<TrackEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val historyDao: PlaybackHistoryDao,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao
) : ViewModel() {

    val recentlyPlayed: StateFlow<List<TrackEntity>> = historyDao
        .observeRecentlyPlayedTracks(limit = 15)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayed: StateFlow<List<TrackEntity>> = historyDao
        .observeMostPlayedTracks(limit = 15)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = playlistDao
        .observeAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _quickPicks = MutableStateFlow<List<TrackMetadata>>(emptyList())
    val quickPicks: StateFlow<List<TrackMetadata>> = _quickPicks.asStateFlow()

    init {
        loadDefaultRecommendations()
    }

    private fun loadDefaultRecommendations() {
        // High quality curated starter items
        _quickPicks.value = listOf(
            TrackMetadata(
                id = "dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                artist = "Rick Astley",
                album = "Whenever You Need Somebody",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                durationMs = 212000L
            ),
            TrackMetadata(
                id = "kJQP7kiw5Fk",
                title = "Despacito",
                artist = "Luis Fonsi ft. Daddy Yankee",
                album = "VIDA",
                thumbnailUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
                durationMs = 282000L
            ),
            TrackMetadata(
                id = "JGwWNGJdvx8",
                title = "Shape of You",
                artist = "Ed Sheeran",
                album = "÷ (Divide)",
                thumbnailUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg",
                durationMs = 233000L
            ),
            TrackMetadata(
                id = "fJ9rUzIMcZQ",
                title = "Bohemian Rhapsody",
                artist = "Queen",
                album = "A Night at the Opera",
                thumbnailUrl = "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg",
                durationMs = 355000L
            ),
            TrackMetadata(
                id = "9bZkp7q19f0",
                title = "Gangnam Style",
                artist = "PSY",
                album = "PSY 6 (Six Rules), Part 1",
                thumbnailUrl = "https://i.ytimg.com/vi/9bZkp7q19f0/hqdefault.jpg",
                durationMs = 219000L
            )
        )
    }
}
